"""Cliente de Grok a través del gateway del reto (espejo de la API de xAI)."""

import asyncio
import json
import logging
import re
import time
from typing import Protocol

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langchain_xai import ChatXAI
from pydantic import BaseModel

from banano.config import Settings, StructuredMethod

logger = logging.getLogger(__name__)


class LLMError(Exception):
    """El LLM no devolvió algo utilizable a tiempo."""


class LLMNotConfiguredError(Exception):
    def __init__(self, missing: list[str]) -> None:
        super().__init__(f"Faltan variables de entorno: {', '.join(missing)}")
        self.missing = missing


class StructuredLLM(Protocol):
    async def generate[T: BaseModel](
        self, schema: type[T], *, system: str, user: str, timeout: float
    ) -> T: ...


def json_instructions(schema: type[BaseModel]) -> str:
    return (
        "Responde solo con un objeto JSON válido, sin texto adicional ni bloques de código, "
        "que cumpla este JSON Schema:\n"
        + json.dumps(schema.model_json_schema(), ensure_ascii=False)
    )


_FENCE_RE = re.compile(r"^```(?:json)?\s*|\s*```$")


def parse_json_model[T: BaseModel](schema: type[T], text: str) -> T:
    """Extrae el primer objeto JSON del texto y lo valida con Pydantic."""
    text = _FENCE_RE.sub("", text.strip())
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end <= start:
        raise ValueError("La respuesta no trae un objeto JSON")
    return schema.model_validate_json(text[start : end + 1])


class GrokStructuredLLM:
    """Pide salida estructurada con el método configurado. Si el gateway no lo soporta o la
    salida no valida, repite pidiendo JSON en texto y lo valida con Pydantic."""

    def __init__(self, model: BaseChatModel, method: StructuredMethod) -> None:
        self._model = model
        self._method = method

    async def generate[T: BaseModel](
        self, schema: type[T], *, system: str, user: str, timeout: float
    ) -> T:
        deadline = time.monotonic() + timeout
        hinted_system = f"{system}\n\n{json_instructions(schema)}"
        first_system = hinted_system if self._method == "json_mode" else system
        try:
            structured = self._model.with_structured_output(schema, method=self._method)
            result = await asyncio.wait_for(
                structured.ainvoke([SystemMessage(first_system), HumanMessage(user)]), timeout
            )
            return result if isinstance(result, schema) else schema.model_validate(result)
        except TimeoutError as exc:
            raise LLMError("El LLM no respondió a tiempo") from exc
        except Exception as exc:
            logger.warning(
                "La salida estructurada (%s) falló con %s; uso el respaldo en texto",
                self._method,
                type(exc).__name__,
            )

        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise LLMError("El LLM no respondió a tiempo")
        try:
            reply = await asyncio.wait_for(
                self._model.ainvoke([SystemMessage(hinted_system), HumanMessage(user)]), remaining
            )
            return parse_json_model(schema, reply.text)
        except TimeoutError as exc:
            raise LLMError("El LLM no respondió a tiempo") from exc
        except Exception as exc:
            raise LLMError(f"Respuesta inválida del LLM: {type(exc).__name__}") from exc


class ToolLLM(Protocol):
    """Un turno del agente: el modelo responde con llamadas a herramientas."""

    async def step(
        self, messages: list[BaseMessage], tools: list[type[BaseModel]], *, timeout: float
    ) -> AIMessage: ...


class GrokToolLLM:
    """Llamadas nativas a herramientas (verificadas contra el gateway con smoke_grok)."""

    def __init__(self, model: BaseChatModel) -> None:
        self._model = model

    async def step(
        self, messages: list[BaseMessage], tools: list[type[BaseModel]], *, timeout: float
    ) -> AIMessage:
        # "required": cada turno es una herramienta; el texto suelto no sirve para decidir.
        bound = self._model.bind_tools(tools, tool_choice="required")
        try:
            reply = await asyncio.wait_for(bound.ainvoke(messages), timeout)
        except TimeoutError as exc:
            raise LLMError("El LLM no respondió a tiempo") from exc
        except Exception as exc:
            raise LLMError(f"Falló la llamada al LLM: {type(exc).__name__}") from exc
        if not isinstance(reply, AIMessage):
            raise LLMError("Respuesta inválida del LLM")
        return reply


def _grok_chat(
    settings: Settings, *, model: str | None, model_env: str, timeout: float, max_retries: int
) -> ChatXAI:
    required = {"XAI_BASE_URL": settings.xai_base_url, "XAI_API_KEY": settings.xai_api_key}
    required[model_env] = model
    missing = [name for name, value in required.items() if not value]
    if missing or settings.xai_api_key is None:
        raise LLMNotConfiguredError(missing)
    return ChatXAI(
        model=model,
        api_key=settings.xai_api_key.get_secret_value(),
        # ChatXAI por su cuenta lee XAI_API_BASE; el spec usa XAI_BASE_URL, así que va explícita.
        base_url=settings.xai_base_url,
        timeout=timeout,
        max_retries=max_retries,
    )


def build_grok_llm(
    settings: Settings, *, model: str | None, model_env: str, timeout: float, max_retries: int
) -> GrokStructuredLLM:
    chat = _grok_chat(
        settings, model=model, model_env=model_env, timeout=timeout, max_retries=max_retries
    )
    return GrokStructuredLLM(chat, settings.llm_structured_method)


def build_agent_llm(settings: Settings) -> GrokToolLLM:
    # El agente usa el modelo rápido del juez: hasta 3 turnos tienen que caber en 20 s.
    return GrokToolLLM(
        _grok_chat(
            settings,
            model=settings.grok_model_judge,
            model_env="GROK_MODEL_JUDGE",
            timeout=settings.agent_llm_timeout_s,
            max_retries=0,
        )
    )


def build_generate_llm(settings: Settings) -> GrokStructuredLLM:
    return build_grok_llm(
        settings,
        model=settings.grok_model_generate,
        model_env="GROK_MODEL_GENERATE",
        timeout=settings.generate_llm_timeout_s,
        max_retries=2,
    )


def build_judge_llm(settings: Settings) -> GrokStructuredLLM:
    # Sin reintentos: el teléfono espera 8 s en total y sin red aplica la regla offline.
    return build_grok_llm(
        settings,
        model=settings.grok_model_judge,
        model_env="GROK_MODEL_JUDGE",
        timeout=settings.judge_llm_timeout_s,
        max_retries=0,
    )
