"""Grafo 1: generar la plantilla de mensajes.

analizar_perfil -> generar_mensajes -> revisar_calidad, y vuelta a generar_mensajes si
quedan menos del mínimo (máximo `template_max_retries` reintentos y dentro del tiempo total).
generar_mensajes pide los mensajes en lotes paralelos para caber en el límite de Vercel.
"""

import asyncio
import logging
import math
import re
import time
from collections import Counter
from dataclasses import dataclass
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph
from langgraph.runtime import Runtime
from pydantic import BaseModel, Field

from banano.config import Settings
from banano.llm.client import LLMError, StructuredLLM
from banano.llm.prompts import (
    ANALYSIS_SYSTEM,
    analysis_request,
    generation_request,
    generator_system,
)
from banano.safety.guardrails import forbidden_terms, violates_guardrails
from banano.safety.placeholders import has_placeholder, placeholders_valid, word_count
from banano.safety.similarity import is_near_duplicate
from banano.safety.text import (
    clean_text,
    has_emoticon,
    has_hashtag,
    normalize_for_matching,
    strip_emojis,
)
from banano.schemas.profile import Profile
from banano.schemas.template import (
    MESSAGE_CONTEXTS,
    TemplateMessage,
    TemplateResponse,
    VerdictLines,
)

logger = logging.getLogger(__name__)

MAX_WORDS = 25
MAX_MESSAGES = 80
MAX_VERDICT_LINES = 15
DUPLICATE_THRESHOLD = 0.85
# Con menos tiempo que esto no vale la pena otro intento de generación.
MIN_SECONDS_FOR_RETRY = 30.0

DEFAULT_APPROVED = ("Aprobado. Que valga la pena.", "Vale. Úsalo bien y vuelve a lo tuyo.")
DEFAULT_REJECTED = ("Rechazado. La app sigue ahí; tu meta también.", "No. Vuelve a lo tuyo.")

# Palabras frecuentes que no sirven para decir si un mensaje es personal.
_COMMON_WORDS = frozenset(
    {
        "porque", "quiero", "tengo", "hacer", "sobre", "cuando", "donde", "entre", "estar",
        "mucho", "mucha", "todos", "todas", "puedo", "siempre", "nunca", "despues", "antes",
        "ahora", "algun", "alguna", "alguno", "tiempo", "cosas", "minutos",
    }
)  # fmt: skip


class ProfileAnalysis(BaseModel):
    angles: list[str] = Field(description="Ángulos de ataque concretos")
    real_life: list[str] = Field(description="Cosas del mundo real que se pierde")
    summary: str = Field(description="Resumen del perfil para el juez, dos o tres frases")


class DraftMessage(BaseModel):
    text: str = Field(description="Mensaje de máximo 25 palabras con placeholders permitidos")
    context: str = Field(description="general | reincidente | noche | mañana | tras_rechazo")
    intensity: int = Field(description="De 1 a 5")


class DraftBatch(BaseModel):
    messages: list[DraftMessage]
    verdict_approved: list[str] = Field(default_factory=list)
    verdict_rejected: list[str] = Field(default_factory=list)


class TemplateGenerationError(Exception):
    pass


@dataclass
class TemplateContext:
    llm: StructuredLLM
    settings: Settings
    deadline: float  # time.monotonic()


class TemplateState(TypedDict, total=False):
    profile: Profile
    analysis: ProfileAnalysis
    accepted: list[DraftMessage]
    candidates: list[DraftMessage]
    approved_lines: list[str]
    rejected_lines: list[str]
    generation_runs: int
    retry: bool


def _remaining(context: TemplateContext) -> float:
    return context.deadline - time.monotonic()


def _call_timeout(context: TemplateContext) -> float:
    remaining = _remaining(context)
    if remaining <= 0:
        raise LLMError("Se acabó el tiempo para generar la plantilla")
    return min(context.settings.generate_llm_timeout_s, remaining)


def profile_keywords(profile: Profile) -> set[str]:
    text = " ".join(
        [
            profile.name,
            profile.main_goal,
            *profile.secondary_goals,
            profile.goal_reason,
            profile.typical_excuses,
            *profile.blocked_apps,
        ]
    )
    words = normalize_for_matching(text).split()
    return {word for word in words if len(word) >= 5 and word not in _COMMON_WORDS}


def is_generic(text: str, keywords: set[str]) -> bool:
    """Sin placeholders y sin nada del perfil: le serviría a cualquiera."""
    if has_placeholder(text):
        return False
    return not set(normalize_for_matching(text).split()) & keywords


def normalize_context(value: str) -> str:
    wanted = normalize_for_matching(value).replace(" ", "_")
    for context in MESSAGE_CONTEXTS:
        if normalize_for_matching(context).replace(" ", "_") == wanted:
            return context
    return "general"


def _literal(term: str) -> re.Pattern[str]:
    return re.compile(rf"(?<!\w){re.escape(term)}(?!\w)")


def personalize_literals(text: str, profile: Profile) -> str | None:
    """Cambia el nombre del usuario por {nombre} y una app bloqueada escrita a mano por {app}.

    El mensaje se muestra al abrir cualquier app bloqueada: si nombra una app concreta no
    sirve para las demás. Devuelve None si nombra varias.
    """
    if len(profile.name) >= 3:
        text = _literal(profile.name).sub("{nombre}", text)
    mentioned = [app for app in profile.blocked_apps if _literal(app.lower()).search(text.lower())]
    if len(mentioned) > 1:
        return None
    if mentioned:
        pattern = re.compile(_literal(mentioned[0]).pattern, re.IGNORECASE)
        text = pattern.sub("{app}", text)
    return text


def _rejection_reason(
    text: str, seen: list[str], blocked: list[str], keywords: set[str]
) -> str | None:
    if not text:
        return "vacio"
    if has_emoticon(text) or has_hashtag(text):
        return "simbolos"
    if not placeholders_valid(text):
        return "placeholder"
    if word_count(text) > MAX_WORDS:
        return "largo"
    if violates_guardrails(text, blocked):
        return "guardarrail"
    if is_generic(text, keywords):
        return "generico"
    if is_near_duplicate(text, seen, DUPLICATE_THRESHOLD):
        return "repetido"
    return None


def review_messages(
    candidates: list[DraftMessage], accepted: list[DraftMessage], profile: Profile
) -> tuple[list[DraftMessage], Counter[str]]:
    """Limpia emojis y descarta lo repetido, genérico o fuera de los guardarraíles."""
    blocked = forbidden_terms(profile.forbidden_topics)
    keywords = profile_keywords(profile)
    seen = [message.text for message in accepted]
    kept: list[DraftMessage] = []
    dropped: Counter[str] = Counter()
    for draft in candidates:
        text = personalize_literals(strip_emojis(draft.text), profile)
        reason = "varias_apps" if text is None else _rejection_reason(text, seen, blocked, keywords)
        if text is None or reason:
            dropped[reason] += 1
            continue
        seen.append(text)
        kept.append(
            DraftMessage(
                text=text,
                context=normalize_context(draft.context),
                intensity=min(max(draft.intensity, 1), 5),
            )
        )
    return kept, dropped


def clean_verdict_lines(
    lines: list[str], profile: Profile, blocked: list[str], fallback: tuple[str, ...]
) -> list[str]:
    cleaned: list[str] = []
    for line in lines:
        text = personalize_literals(clean_text(line), profile)
        if (
            not text
            or not placeholders_valid(text)
            or word_count(text) > MAX_WORDS
            or violates_guardrails(text, blocked)
            or is_near_duplicate(text, cleaned, DUPLICATE_THRESHOLD)
        ):
            continue
        cleaned.append(text)
        if len(cleaned) == MAX_VERDICT_LINES:
            break
    return cleaned or list(fallback)


async def analizar_perfil(
    state: TemplateState, runtime: Runtime[TemplateContext]
) -> dict[str, Any]:
    context = runtime.context
    analysis = await context.llm.generate(
        ProfileAnalysis,
        system=ANALYSIS_SYSTEM,
        user=analysis_request(state["profile"]),
        timeout=_call_timeout(context),
    )
    return {
        "analysis": analysis,
        "accepted": [],
        "approved_lines": [],
        "rejected_lines": [],
        "generation_runs": 0,
    }


def chunk_counts(total: int, chunk_size: int) -> list[int]:
    """Reparte `total` en lotes parejos de como mucho `chunk_size`."""
    chunks = max(1, math.ceil(total / max(chunk_size, 1)))
    base, extra = divmod(total, chunks)
    return [base + (1 if index < extra else 0) for index in range(chunks)]


async def generar_mensajes(
    state: TemplateState, runtime: Runtime[TemplateContext]
) -> dict[str, Any]:
    context = runtime.context
    accepted = state["accepted"]
    first_run = state["generation_runs"] == 0
    missing = context.settings.template_target_messages - len(accepted)
    # En los reintentos se pide un poco más, porque la revisión siempre descarta algunos.
    count = missing if first_run else max(missing, 10) + 5
    analysis = state["analysis"]
    system = generator_system(state["profile"])
    avoid = [message.text for message in accepted]
    counts = chunk_counts(count, context.settings.template_chunk_size)
    timeout = _call_timeout(context)
    # Cada lote ataca ángulos distintos para que no se repitan entre sí.
    requests = [
        context.llm.generate(
            DraftBatch,
            system=system,
            user=generation_request(
                analysis.angles[index :: len(counts)] or analysis.angles,
                analysis.real_life,
                profile=state["profile"],
                count=size,
                avoid=avoid,
                with_verdicts=first_run and index == 0,
            ),
            timeout=timeout,
        )
        for index, size in enumerate(counts)
    ]
    results = await asyncio.gather(*requests, return_exceptions=True)
    batches = [result for result in results if isinstance(result, DraftBatch)]
    errors = [result for result in results if isinstance(result, BaseException)]
    for error in errors:
        if not isinstance(error, LLMError):
            raise error
    if not batches:
        raise errors[0]
    if errors:
        logger.warning("%d de %d lotes fallaron; sigo con el resto", len(errors), len(results))
    return {
        "candidates": [message for batch in batches for message in batch.messages],
        "approved_lines": state["approved_lines"]
        + [line for batch in batches for line in batch.verdict_approved],
        "rejected_lines": state["rejected_lines"]
        + [line for batch in batches for line in batch.verdict_rejected],
        "generation_runs": state["generation_runs"] + 1,
    }


def revisar_calidad(state: TemplateState, runtime: Runtime[TemplateContext]) -> dict[str, Any]:
    context = runtime.context
    kept, dropped = review_messages(state["candidates"], state["accepted"], state["profile"])
    accepted = state["accepted"] + kept
    if dropped:
        logger.info("Revisión: %d aceptados, descartados %s", len(kept), dict(dropped))
    retry = (
        len(accepted) < context.settings.template_min_messages
        and state["generation_runs"] <= context.settings.template_max_retries
        and _remaining(context) > MIN_SECONDS_FOR_RETRY
    )
    return {"accepted": accepted, "candidates": [], "retry": retry}


def _after_review(state: TemplateState) -> str:
    return "generar_mensajes" if state["retry"] else END


def _build_graph() -> CompiledStateGraph:
    builder = StateGraph(TemplateState, context_schema=TemplateContext)
    builder.add_node("analizar_perfil", analizar_perfil)
    builder.add_node("generar_mensajes", generar_mensajes)
    builder.add_node("revisar_calidad", revisar_calidad)
    builder.add_edge(START, "analizar_perfil")
    builder.add_edge("analizar_perfil", "generar_mensajes")
    builder.add_edge("generar_mensajes", "revisar_calidad")
    builder.add_conditional_edges("revisar_calidad", _after_review, ["generar_mensajes", END])
    return builder.compile()


TEMPLATE_GRAPH = _build_graph()


async def run_template_graph(
    profile: Profile, llm: StructuredLLM, settings: Settings
) -> TemplateResponse:
    context = TemplateContext(
        llm=llm,
        settings=settings,
        deadline=time.monotonic() + settings.template_total_timeout_s,
    )
    final = await TEMPLATE_GRAPH.ainvoke({"profile": profile}, context=context)
    accepted: list[DraftMessage] = final["accepted"][:MAX_MESSAGES]
    if len(accepted) < settings.template_min_acceptable:
        raise TemplateGenerationError(f"Solo quedaron {len(accepted)} mensajes válidos")
    blocked = forbidden_terms(profile.forbidden_topics)
    return TemplateResponse(
        messages=[
            TemplateMessage(
                id=f"m{index:02d}",
                text=message.text,
                context=message.context,
                intensity=message.intensity,
            )
            for index, message in enumerate(accepted, start=1)
        ],
        verdict_lines=VerdictLines(
            approved=clean_verdict_lines(
                final["approved_lines"], profile, blocked, DEFAULT_APPROVED
            ),
            rejected=clean_verdict_lines(
                final["rejected_lines"], profile, blocked, DEFAULT_REJECTED
            ),
        ),
        profile_summary=clean_text(final["analysis"].summary)[:1500],
    )
