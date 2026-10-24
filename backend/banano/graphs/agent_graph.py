"""Grafo 3: el agente de intervención (S2).

preparar_contexto -> planificar (Grok elige herramienta) <-> herramientas -> cerrar.

Grok decide con llamadas nativas qué consultar y cuándo terminar; no hay una secuencia fija.
cerrar valida la propuesta con reglas deterministas (policy), la guarda y arma la respuesta.
Un "sent" es una orden: la confirmación real llega después desde Android.
"""

import json
import logging
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from datetime import timedelta
from typing import Any, TypedDict

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph
from langgraph.runtime import Runtime
from pydantic import BaseModel, ValidationError

from banano.agent import policy
from banano.agent.store import InterventionStore, StoreError, effective_status, utcnow
from banano.agent.tools import (
    READ_TOOLS,
    TOOLS,
    concluir,
    consultar_evidencia,
    consultar_evidencia_uso,
    consultar_historial,
    consultar_historial_intervenciones,
    ejecutar_intervencion,
)
from banano.config import Settings
from banano.llm.client import LLMError, ToolLLM
from banano.llm.prompts import AGENT_SYSTEM, agent_request_text
from banano.safety.text import clean_text
from banano.schemas.agent import AgentStepOut, AnalyzeRequest, AnalyzeResponse, InterventionOut

logger = logging.getLogger(__name__)

MAX_REASON_CHARS = 240
READ_NAMES = {tool.__name__ for tool in READ_TOOLS}


@dataclass
class AgentContext:
    """Solo dependencias. Lo de cada análisis va en el estado, así Studio puede llenarlo."""

    llm: ToolLLM
    store: InterventionStore
    settings: Settings


class AgentState(TypedDict, total=False):
    request: AnalyzeRequest
    device_id: str
    deadline: float  # time.monotonic()
    messages: list[BaseMessage]
    steps: list[dict[str, Any]]
    cycles: int
    evidence: dict[str, Any] | None
    terminal: BaseModel | None
    failure: str | None
    response: AnalyzeResponse


def short_reason(text: str) -> str:
    text = clean_text(text)
    if len(text) <= MAX_REASON_CHARS:
        return text
    return text[:MAX_REASON_CHARS].rsplit(" ", 1)[0].rstrip(",;:") + "."


def preparar_contexto(state: AgentState, runtime: Runtime[AgentContext]) -> dict[str, Any]:
    # Desde Studio la petición llega como dict; desde la API, ya validada.
    request = AnalyzeRequest.model_validate(state["request"])
    return {
        "request": request,
        "deadline": time.monotonic() + runtime.context.settings.agent_total_timeout_s,
        "messages": [SystemMessage(AGENT_SYSTEM), HumanMessage(agent_request_text(request))],
    }


async def planificar(state: AgentState, runtime: Runtime[AgentContext]) -> dict[str, Any]:
    ctx = runtime.context
    remaining = state["deadline"] - time.monotonic()
    if state.get("cycles", 0) >= ctx.settings.agent_max_cycles:
        return {"failure": "max_cycles"}
    if remaining <= 0:
        return {"failure": "timeout"}
    try:
        reply = await ctx.llm.step(
            state["messages"],
            list(TOOLS.values()),
            timeout=min(ctx.settings.agent_llm_timeout_s, remaining),
        )
    except LLMError as exc:
        logger.warning("El agente no respondió: %s", exc)
        return {"failure": "llm_unavailable"}
    if not reply.tool_calls:
        return {"failure": "invalid_output"}
    return {"messages": [*state["messages"], reply], "cycles": state.get("cycles", 0) + 1}


async def herramientas(state: AgentState, runtime: Runtime[AgentContext]) -> dict[str, Any]:
    ctx = runtime.context
    calls = state["messages"][-1].tool_calls  # type: ignore[attr-defined]
    has_reads = any(call["name"] in READ_NAMES for call in calls)
    messages, steps = list(state["messages"]), list(state.get("steps", []))
    evidence, terminal = state.get("evidence"), None
    for call in calls:
        start = time.monotonic()
        tool = TOOLS.get(call["name"])
        result: dict[str, Any]
        ends_here = False
        if tool is None:
            result = {"status": "error", "error": "unknown_tool"}
        elif call["name"] not in READ_NAMES and (has_reads or terminal is not None):
            # Terminar en el mismo turno en que pide datos sería decidir sin haberlos visto.
            result = {"status": "error", "error": "Primero revisa los resultados; luego termina."}
        else:
            try:
                args = tool.model_validate(call["args"])
            except ValidationError as exc:
                result = {"status": "error", "error": f"argumentos inválidos: {exc.error_count()}"}
            else:
                if isinstance(args, consultar_evidencia_uso):
                    result = consultar_evidencia(state["request"].usage, args)
                    if result["status"] == "ok":
                        evidence = result
                elif isinstance(args, consultar_historial_intervenciones):
                    result = await consultar_historial(
                        ctx.store,
                        state["device_id"],
                        args,
                        utcnow(),
                        timedelta(seconds=ctx.settings.agent_confirm_window_s),
                    )
                else:
                    terminal, ends_here = args, True
                    result = {"status": "ok", "nota": "recibido"}
        # La intervención se anota en cerrar, ya con su resultado de la política.
        if not ends_here:
            steps.append(
                {
                    "tool": call["name"],
                    "args": call["args"] if isinstance(call["args"], dict) else {},
                    "status": result["status"],
                    "ms": int((time.monotonic() - start) * 1000),
                }
            )
        content = json.dumps(result, ensure_ascii=False, default=str)
        messages.append(ToolMessage(content=content, tool_call_id=call["id"]))
    return {"messages": messages, "steps": steps, "evidence": evidence, "terminal": terminal}


async def cerrar(state: AgentState, runtime: Runtime[AgentContext]) -> dict[str, Any]:
    ctx = runtime.context
    request, device_id, settings = state["request"], state["device_id"], ctx.settings
    steps = list(state.get("steps", []))
    terminal, failure = state.get("terminal"), state.get("failure")
    proposed: dict[str, Any] | None = None
    status: str | None = None
    code: str | None = None
    if failure or terminal is None:
        decision, reason = "agent_unavailable", ""
    elif isinstance(terminal, concluir):
        decision, reason = terminal.decision, short_reason(terminal.reason)
    else:
        assert isinstance(terminal, ejecutar_intervencion)
        decision, reason = "intervene", short_reason(terminal.reason)
        code, targets = policy.check(terminal, request, state.get("evidence") is not None)
        if code is None:
            try:
                since = utcnow() - timedelta(days=1)
                done = await ctx.store.count_interventions_since(device_id, since)
                code = "daily_cap" if done >= settings.agent_daily_interventions else None
            except StoreError:
                code = "store_unavailable"
        proposed = {
            "type": "start_focus_session",
            "duration_minutes": terminal.duration_minutes,
            "target_apps": targets or terminal.target_apps,
        }
        status = "rejected" if code else "sent"
        steps.append({"tool": "ejecutar_intervencion", "args": proposed, "status": status})

    event_id = uuid.uuid4()
    evidence = state.get("evidence")
    event = {
        "id": event_id,
        "device_id": device_id,
        "request_id": request.request_id,
        "trigger": request.trigger,
        "steps": steps,
        "evidence_summary": {"apps": evidence["apps"][:10]} if evidence else None,
        "decision": decision,
        "reason": reason,
        "proposed_action": proposed,
        "policy_code": code,
        "status": status,
        "error_code": failure,
    }
    persisted = True
    try:
        await ctx.store.save(event)
    except StoreError as exc:
        logger.warning("No se pudo guardar el análisis: %s", exc)
        persisted = False
        # Sin registro no hay idempotencia ni forma de confirmar: no se manda la orden.
        if status == "sent":
            status, code = "rejected", "store_unavailable"
            steps[-1]["status"] = status
    intervention = (
        InterventionOut(
            id=event_id if persisted else None,
            duration_minutes=proposed["duration_minutes"],
            target_apps=proposed["target_apps"],
            status=status,  # type: ignore[arg-type]
            policy_code=code,
        )
        if proposed is not None
        else None
    )
    return {
        "response": AnalyzeResponse(
            decision=decision,  # type: ignore[arg-type]
            reason=reason,
            steps=[AgentStepOut(**s) for s in steps],
            intervention=intervention,
            persisted=persisted,
        )
    }


def _after_plan(state: AgentState) -> str:
    return "cerrar" if state.get("failure") else "herramientas"


def _after_tools(state: AgentState) -> str:
    return "cerrar" if state.get("terminal") is not None else "planificar"


def build_graph(
    wrap: Callable[[Any], Any] = lambda node: node, context_schema: type | None = AgentContext
) -> CompiledStateGraph:
    """Studio pasa `wrap` para inyectar LLM y store reales, y `context_schema=None` porque su
    servidor intentaría construir el contexto con el JSON que recibe."""
    builder = StateGraph(AgentState, context_schema=context_schema)
    builder.add_node("preparar_contexto", wrap(preparar_contexto))
    builder.add_node("planificar", wrap(planificar))
    builder.add_node("herramientas", wrap(herramientas))
    builder.add_node("cerrar", wrap(cerrar))
    builder.add_edge(START, "preparar_contexto")
    builder.add_edge("preparar_contexto", "planificar")
    builder.add_conditional_edges("planificar", _after_plan, ["herramientas", "cerrar"])
    builder.add_conditional_edges("herramientas", _after_tools, ["planificar", "cerrar"])
    builder.add_edge("cerrar", END)
    return builder.compile()


AGENT_GRAPH = build_graph()


def replay(event: dict[str, Any], confirm_window: timedelta) -> AnalyzeResponse:
    """La misma request_id ya se analizó: se devuelve lo guardado con su estado actual."""
    proposed = event.get("proposed_action")
    status = effective_status(event, utcnow(), confirm_window)
    return AnalyzeResponse(
        decision=event["decision"],
        reason=event["reason"],
        steps=[AgentStepOut(**s) for s in event["steps"]],
        intervention=InterventionOut(
            id=event["id"],
            duration_minutes=proposed["duration_minutes"],
            target_apps=proposed["target_apps"],
            status=status,  # type: ignore[arg-type]
            policy_code=event["policy_code"],
        )
        if proposed
        else None,
        persisted=True,
        replayed=True,
    )


async def run_agent_graph(
    request: AnalyzeRequest,
    device_id: str,
    llm: ToolLLM,
    store: InterventionStore,
    settings: Settings,
) -> AnalyzeResponse:
    try:
        previous = await store.get_by_request(device_id, request.request_id)
    except StoreError:
        previous = None
    if previous is not None:
        return replay(previous, timedelta(seconds=settings.agent_confirm_window_s))
    final = await AGENT_GRAPH.ainvoke(
        {"request": request, "device_id": device_id},
        context=AgentContext(llm=llm, store=store, settings=settings),
    )
    return final["response"]
