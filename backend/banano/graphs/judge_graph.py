"""Grafo 2: el juez de excusas.

detectar_crisis -> prefiltro -> evaluar -> redactar_veredicto.

detectar_crisis (sin LLM) va antes del prefiltro, así una emergencia real corta, repetida o
con el cupo agotado nunca se rechaza (ajuste aprobado en el plan). Si hay señal de crisis, se
aprueba directo con tono humano, como dice el spec: en la prueba real el LLM rechazó
"accidente" con sarcasmo, así que no decide en ese caso.

El juez hace una sola llamada al LLM, que decide y redacta a la vez; redactar_veredicto
limpia el texto y aplica las reglas de negocio sin LLM, para caber en los 8 s del teléfono.
"""

import logging
import zlib
from dataclasses import dataclass
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph
from langgraph.runtime import Runtime
from pydantic import BaseModel, Field

from banano.config import Settings
from banano.llm.client import LLMError, StructuredLLM
from banano.llm.prompts import judge_request_text, judge_system
from banano.safety.crisis import CrisisKind, detect_crisis
from banano.safety.similarity import is_near_duplicate
from banano.safety.text import clean_text, normalize_for_matching
from banano.schemas.judge import ExcuseType, JudgeRequest, JudgeResponse, ReasonCode

logger = logging.getLogger(__name__)

MAX_MESSAGE_CHARS = 240

PREFILTER_LINES: dict[str, tuple[str, ...]] = {
    "over_quota": (
        "Se acabó el cupo de hoy. Mañana negociamos.",
        "Ya gastaste los permisos de hoy. La app sigue ahí mañana.",
    ),
    "too_short": (
        "Eso no alcanza ni para excusa. Escribe una frase completa.",
        "Con tan poco no hay nada que juzgar. Explica de verdad.",
    ),
    "repeated": (
        "Esa excusa ya la usaste. Ni la excusa es nueva.",
        "Repetir la excusa no la vuelve verdad.",
    ),
}
APPROVED_FALLBACK = ("Aprobado. Que valga la pena.",)
REJECTED_FALLBACK = ("Rechazado. Vuelve a lo tuyo.",)


class JudgeEvaluation(BaseModel):
    valid: bool
    confidence: float = Field(description="Entre 0 y 1")
    reason_code: str = Field(description="legit_work | legit_emergency | boredom")
    is_crisis: bool = Field(description="Solo si hay una emergencia real de salud o seguridad")
    message: str = Field(description="Una o dos frases, máximo 25 palabras")


class JudgeUnavailableError(Exception):
    """El LLM no respondió y no hay señal de crisis: la app aplica la regla offline."""


@dataclass
class JudgeContext:
    llm: StructuredLLM
    settings: Settings


class JudgeState(TypedDict, total=False):
    request: JudgeRequest
    crisis: CrisisKind | None
    rejection: ReasonCode | None
    evaluation: JudgeEvaluation | None
    response: JudgeResponse


def over_quota(request: JudgeRequest) -> bool:
    if request.type == "extra_time":
        return request.extra_time_granted_today >= request.extra_time_daily_limit
    return request.emergencies_granted_today >= request.emergency_daily_limit


def prefilter_rejection(request: JudgeRequest, settings: Settings) -> ReasonCode | None:
    if over_quota(request):
        return "over_quota"
    if len(request.reason.strip()) < settings.min_reason_chars:
        return "too_short"
    if is_near_duplicate(request.reason, request.recent_excuses, settings.repeat_similarity):
        return "repeated"
    return None


def crisis_message(kind: CrisisKind | None, country: str, minutes: int) -> str:
    if kind == "self_harm":
        line = (
            "Lo que escribiste importa más que cualquier app. "
            "Si estás pensando en hacerte daño, llama ya "
        )
        colombia = normalize_for_matching(country) == "colombia"
        number = "a la línea 123" if colombia else "a la línea de emergencias"
        return f"{line}{number} o habla con alguien de confianza."
    return (
        "Ve. Lo importante es que tú y los tuyos estén bien. "
        f"Tienes {minutes} minutos; si necesitas más, vuelve a pedirlo."
    )


def _pick(options: tuple[str, ...], seed: str) -> str:
    return options[zlib.crc32(seed.encode()) % len(options)]


def _clamp(value: float) -> float:
    return min(max(value, 0.0), 1.0)


def _shorten(text: str) -> str:
    if len(text) <= MAX_MESSAGE_CHARS:
        return text
    return text[:MAX_MESSAGE_CHARS].rsplit(" ", 1)[0].rstrip(",;:") + "."


def _normalize_code(code: str, valid: bool, kind: ExcuseType) -> ReasonCode:
    if not valid:
        return "boredom"
    if code == "legit_work":
        return "legit_work"
    if code == "legit_emergency":
        return "legit_emergency"
    return "legit_emergency" if kind == "emergency" else "legit_work"


def compose_verdict(state: JudgeState, settings: Settings) -> JudgeResponse:
    request = state["request"]
    crisis = state.get("crisis")
    evaluation = state.get("evaluation")

    # Crisis por palabras clave, o una que el LLM detectó aunque no hubiera palabras clave.
    if crisis or (evaluation is not None and evaluation.is_crisis):
        return JudgeResponse(
            valid=True,
            confidence=1.0 if crisis or evaluation is None else _clamp(evaluation.confidence),
            granted_minutes=settings.emergency_minutes,
            message=crisis_message(crisis, request.country, settings.emergency_minutes),
            reason_code="crisis",
        )
    rejection = state.get("rejection")
    if rejection:
        return JudgeResponse(
            valid=False,
            confidence=1.0,
            granted_minutes=0,
            message=_pick(PREFILTER_LINES[rejection], request.reason),
            reason_code=rejection,
        )
    if evaluation is None:
        raise JudgeUnavailableError("El juez no respondió a tiempo")

    valid = evaluation.valid
    is_extra_time = request.type == "extra_time"
    minutes = settings.extra_time_minutes if is_extra_time else settings.emergency_minutes
    message = clean_text(evaluation.message)
    if not message or "{" in message or "}" in message:
        message = _pick(APPROVED_FALLBACK if valid else REJECTED_FALLBACK, request.reason)
    return JudgeResponse(
        valid=valid,
        confidence=_clamp(evaluation.confidence),
        granted_minutes=minutes if valid else 0,
        message=_shorten(message),
        reason_code=_normalize_code(evaluation.reason_code, valid, request.type),
    )


def detectar_crisis(state: JudgeState) -> dict[str, Any]:
    return {"crisis": detect_crisis(state["request"].reason)}


def prefiltro(state: JudgeState, runtime: Runtime[JudgeContext]) -> dict[str, Any]:
    return {"rejection": prefilter_rejection(state["request"], runtime.context.settings)}


async def evaluar(state: JudgeState, runtime: Runtime[JudgeContext]) -> dict[str, Any]:
    context = runtime.context
    request = state["request"]
    try:
        evaluation = await context.llm.generate(
            JudgeEvaluation,
            system=judge_system(request),
            user=judge_request_text(request),
            timeout=context.settings.judge_llm_timeout_s,
        )
    except LLMError as exc:
        logger.warning("El juez no respondió: %s", exc)
        return {"evaluation": None}
    return {"evaluation": evaluation}


def redactar_veredicto(state: JudgeState, runtime: Runtime[JudgeContext]) -> dict[str, Any]:
    return {"response": compose_verdict(state, runtime.context.settings)}


def _after_crisis_check(state: JudgeState) -> str:
    return "redactar_veredicto" if state.get("crisis") else "prefiltro"


def _after_prefilter(state: JudgeState) -> str:
    return "redactar_veredicto" if state.get("rejection") else "evaluar"


def _build_graph() -> CompiledStateGraph:
    builder = StateGraph(JudgeState, context_schema=JudgeContext)
    builder.add_node("detectar_crisis", detectar_crisis)
    builder.add_node("prefiltro", prefiltro)
    builder.add_node("evaluar", evaluar)
    builder.add_node("redactar_veredicto", redactar_veredicto)
    builder.add_edge(START, "detectar_crisis")
    builder.add_conditional_edges(
        "detectar_crisis", _after_crisis_check, ["prefiltro", "redactar_veredicto"]
    )
    builder.add_conditional_edges("prefiltro", _after_prefilter, ["evaluar", "redactar_veredicto"])
    builder.add_edge("evaluar", "redactar_veredicto")
    builder.add_edge("redactar_veredicto", END)
    return builder.compile()


JUDGE_GRAPH = _build_graph()


async def run_judge_graph(
    request: JudgeRequest, llm: StructuredLLM, settings: Settings
) -> JudgeResponse:
    final = await JUDGE_GRAPH.ainvoke(
        {"request": request}, context=JudgeContext(llm=llm, settings=settings)
    )
    return final["response"]
