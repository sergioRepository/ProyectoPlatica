import asyncio

import pytest
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda
from pydantic import BaseModel

from banano.llm.client import (
    GrokStructuredLLM,
    LLMError,
    LLMNotConfiguredError,
    build_generate_llm,
    build_judge_llm,
    parse_json_model,
)
from tests.fakes import make_settings


class Probe(BaseModel):
    saludo: str
    numero: int


async def test_falls_back_to_json_text_when_structured_output_fails() -> None:
    # El modelo falso no soporta salida estructurada: obliga a usar el respaldo.
    model = GenericFakeChatModel(
        messages=iter([AIMessage(content='```json\n{"saludo": "hola", "numero": 3}\n```')])
    )

    result = await GrokStructuredLLM(model, "json_schema").generate(
        Probe, system="s", user="u", timeout=5
    )

    assert result == Probe(saludo="hola", numero=3)


async def test_invalid_fallback_raises_llm_error() -> None:
    model = GenericFakeChatModel(messages=iter([AIMessage(content="no es JSON")]))

    with pytest.raises(LLMError):
        await GrokStructuredLLM(model, "json_schema").generate(
            Probe, system="s", user="u", timeout=5
        )


class SlowModel:
    async def _sleep(self, _: object) -> None:
        await asyncio.sleep(1)

    def with_structured_output(self, schema: type, method: str) -> RunnableLambda:
        return RunnableLambda(self._sleep)

    async def ainvoke(self, messages: object) -> None:
        await asyncio.sleep(1)


async def test_timeout_raises_llm_error() -> None:
    with pytest.raises(LLMError):
        await GrokStructuredLLM(SlowModel(), "json_schema").generate(  # type: ignore[arg-type]
            Probe, system="s", user="u", timeout=0.05
        )


def test_parse_json_model_ignores_surrounding_text() -> None:
    text = 'Claro, aquí va: {"saludo": "hola", "numero": 1} Listo.'
    assert parse_json_model(Probe, text) == Probe(saludo="hola", numero=1)


def test_chat_xai_uses_the_gateway_url_from_settings() -> None:
    llm = build_judge_llm(make_settings(xai_base_url="https://gateway.invalid/v1"))

    chat = llm._model
    assert chat.xai_api_base == "https://gateway.invalid/v1"
    assert str(chat.root_client.base_url).startswith("https://gateway.invalid/v1")
    assert chat.model_name == "modelo-juez-de-prueba"
    assert chat.max_retries == 0


def test_missing_configuration_lists_the_variables() -> None:
    with pytest.raises(LLMNotConfiguredError) as error:
        build_generate_llm(make_settings(xai_base_url=None, grok_model_generate=None))

    assert error.value.missing == ["XAI_BASE_URL", "GROK_MODEL_GENERATE"]
