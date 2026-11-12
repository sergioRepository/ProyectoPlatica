import asyncio
from uuid import uuid4

from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda
from pydantic import BaseModel

from banano.config import ModelPrice
from banano.llm.client import GrokStructuredLLM, GrokToolLLM, LLMError
from banano.llm.usage import UsageMeter, _meter, cost_usd, metering, tokens_of
from tests.fakes import (
    TEST_DEVICE_ID,
    MemoryStore,
    analyze_request,
    auth_headers,
    consented_store,
    evaluation,
    judge_request,
    make_settings,
)

USAGE = {"input_tokens": 100, "output_tokens": 20, "total_tokens": 150}
PRICE = ModelPrice(input_per_million=2, output_per_million=10)


class Probe(BaseModel):
    saludo: str


class ScriptedModel:
    """Un modelo que responde lo que se le diga y cuenta como Grok."""

    model_name = "grok-de-prueba"

    def __init__(self, parsed=None, parsing_error=None, text="", fail=None):
        self.parsed, self.parsing_error, self.text, self.fail = parsed, parsing_error, text, fail

    def with_structured_output(self, schema, method, include_raw):
        assert include_raw

        async def answer(_):
            if self.fail:
                raise self.fail
            return {
                "raw": AIMessage(content="", usage_metadata=USAGE),
                "parsed": self.parsed,
                "parsing_error": self.parsing_error,
            }

        return RunnableLambda(answer)

    async def ainvoke(self, _):
        return AIMessage(content=self.text, usage_metadata=USAGE)

    def bind_tools(self, tools, tool_choice):
        async def answer(_):
            return AIMessage(content="", tool_calls=[], usage_metadata=USAGE)

        return RunnableLambda(answer)


def meter(**prices) -> UsageMeter:
    return UsageMeter(TEST_DEVICE_ID, "free", "judge", uuid4(), prices)


async def measured(call, **prices) -> UsageMeter:
    usage = meter(**prices)
    token = _meter.set(usage)
    try:
        await call
    except LLMError:
        pass
    finally:
        _meter.reset(token)
    return usage


def generate(model):
    return GrokStructuredLLM(model, "json_schema").generate(
        Probe, system="s", user="u", timeout=5, node="evaluar"
    )


def test_reasoning_tokens_count_as_output() -> None:
    assert tokens_of(USAGE) == (100, 50)


def test_output_is_kept_when_the_total_already_matches() -> None:
    assert tokens_of({"input_tokens": 10, "output_tokens": 5, "total_tokens": 15}) == (10, 5)


def test_no_usage_means_no_tokens() -> None:
    assert tokens_of(None) == (None, None)


def test_cost_needs_a_price_and_tokens() -> None:
    assert cost_usd(PRICE, 1_000_000, 500_000) == 7.0
    assert cost_usd(None, 100, 50) is None
    assert cost_usd(PRICE, None, None) is None


async def test_structured_call_keeps_its_usage_and_cost() -> None:
    usage = await measured(
        generate(ScriptedModel(parsed=Probe(saludo="hola"))), **{"grok-de-prueba": PRICE}
    )

    assert len(usage.rows) == 1
    row = usage.rows[0]
    assert (row["node"], row["model"], row["status"]) == ("evaluar", "grok-de-prueba", "ok")
    assert (row["input_tokens"], row["output_tokens"]) == (100, 50)
    assert row["cost_usd"] == 0.0007


async def test_a_parsing_error_falls_back_and_both_calls_are_recorded() -> None:
    model = ScriptedModel(parsing_error=ValueError("mal"), text='{"saludo": "hola"}')

    result = await GrokStructuredLLM(model, "json_schema").generate(
        Probe, system="s", user="u", timeout=5
    )
    usage = await measured(generate(model))

    assert result == Probe(saludo="hola")
    assert [row["status"] for row in usage.rows] == ["error", "ok"]
    assert [row["input_tokens"] for row in usage.rows] == [100, 100]


async def test_an_unparsed_reply_without_error_also_falls_back() -> None:
    usage = await measured(generate(ScriptedModel(parsed=None, text='{"saludo": "hola"}')))

    assert [row["status"] for row in usage.rows] == ["error", "ok"]


async def test_a_timeout_is_recorded_without_tokens() -> None:
    usage = await measured(generate(ScriptedModel(fail=TimeoutError())))

    assert [(r["status"], r["input_tokens"]) for r in usage.rows] == [("timeout", None)]


async def test_without_a_meter_nothing_is_recorded() -> None:
    fake = GenericFakeChatModel(messages=iter([AIMessage(content='{"saludo": "hola"}')]))

    assert await GrokStructuredLLM(fake, "json_schema").generate(
        Probe, system="s", user="u", timeout=5
    ) == Probe(saludo="hola")


async def test_tool_calls_read_the_usage_of_the_message() -> None:
    llm = GrokToolLLM(ScriptedModel())

    usage = await measured(llm.step([], [], timeout=5, node="planificar"))

    assert [(r["node"], r["output_tokens"]) for r in usage.rows] == [("planificar", 50)]


def test_the_judge_records_its_call(make_client) -> None:
    store = consented_store(plan="monthly")
    llm = GrokStructuredLLM(ScriptedModel(parsed=evaluation()), "json_schema")
    client = make_client(judge_llm=llm, store=store)

    response = client.post(
        "/excuses/judge", json=judge_request().model_dump(), headers=auth_headers()
    )

    assert response.status_code == 200
    row = store.usage[0]
    assert (row["device_id"], row["plan"], row["graph"]) == (TEST_DEVICE_ID, "monthly", "judge")
    assert row["node"] == "evaluar"


def test_the_agent_records_with_its_request_id(make_client) -> None:
    store = consented_store(plan="monthly")
    client = make_client(agent_llm=GrokToolLLM(ScriptedModel()), store=store)
    request = analyze_request()

    client.post("/agent/analyze", json=request.model_dump(mode="json"), headers=auth_headers())

    assert {row["request_id"] for row in store.usage} == {request.request_id}
    assert {row["graph"] for row in store.usage} == {"agent"}


def test_an_answer_without_llm_records_nothing(make_client) -> None:
    store = consented_store()
    client = make_client(judge_llm=None, store=store)

    response = client.post(
        "/excuses/judge",
        json=judge_request(reason="un ratito").model_dump(),
        headers=auth_headers(),
    )

    assert response.status_code == 200
    assert store.usage == []


def test_the_excuse_never_reaches_the_usage_rows(make_client) -> None:
    store = consented_store()
    llm = GrokStructuredLLM(ScriptedModel(parsed=evaluation()), "json_schema")
    client = make_client(judge_llm=llm, store=store)
    secret = "Tengo una cita con el psiquiatra a las tres"

    client.post(
        "/excuses/judge", json=judge_request(reason=secret).model_dump(), headers=auth_headers()
    )

    assert store.usage
    assert secret not in str(store.usage)
    assert secret not in str(store.devices) + str(store.daily) + str(store.events)


async def test_a_failing_store_does_not_break_the_response() -> None:
    store = MemoryStore()
    store.fail = True

    async with metering(store, make_settings(), TEST_DEVICE_ID, None, "judge"):
        _meter.get().add("evaluar", "m", USAGE, 1, "ok")

    assert store.usage == []


async def test_a_slow_store_does_not_hold_the_response() -> None:
    class SlowStore(MemoryStore):
        async def record_usage(self, rows) -> None:
            await asyncio.sleep(5)

    settings = make_settings(usage_write_timeout_s=0.01)

    async with metering(SlowStore(), settings, TEST_DEVICE_ID, None, "judge"):
        _meter.get().add("evaluar", "m", USAGE, 1, "ok")


async def test_prices_are_read_from_the_environment(monkeypatch) -> None:
    from banano.config import Settings

    monkeypatch.setenv("LLM_PRICES", '{"grok":{"input_per_million":1.5,"output_per_million":3}}')

    assert Settings(_env_file=None).llm_prices["grok"].output_per_million == 3
