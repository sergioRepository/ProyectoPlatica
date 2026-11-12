from uuid import UUID

import pytest

from banano.auth import verify_device_token
from banano.graphs.judge_graph import JudgeEvaluation
from banano.graphs.template_graph import DraftBatch, ProfileAnalysis
from banano.llm.client import LLMError
from tests.fakes import (
    GOOD_MESSAGES,
    TEST_CONSENTS,
    TEST_DEVICE_ID,
    TEST_ENROLLMENT_CODE,
    TEST_SECRET,
    FakeLLM,
    MemoryStore,
    auth_headers,
    batch,
    consented_store,
    evaluation,
    judge_request,
    make_settings,
    sample_analysis,
    sample_profile,
)


def judge_body(**overrides) -> dict:
    return judge_request(**overrides).model_dump()


def profile_body() -> dict:
    return sample_profile().model_dump()


def register_body(**consents) -> dict:
    return {"consents": {**TEST_CONSENTS, **consents}}


def test_register_needs_no_code_and_saves_the_consents(make_client) -> None:
    store = MemoryStore()
    response = make_client(store=store).post("/devices/register", json=register_body())

    assert response.status_code == 200
    device_id = response.json()["device_id"]
    assert str(UUID(device_id)) == device_id
    assert verify_device_token(response.json()["device_token"], TEST_SECRET) == device_id
    assert store.devices[device_id]["consent_excuses"] is True
    assert store.consents == [{"device_id": device_id, **TEST_CONSENTS}]


def test_register_ignores_a_device_id_from_the_client(make_client) -> None:
    client = make_client(store=MemoryStore())

    response = client.post("/devices/register", json={**register_body(), "device_id": "ajeno"})

    assert response.json()["device_id"] != "ajeno"


@pytest.mark.parametrize("missing", ["phone_usage", "cross_border"])
def test_register_requires_the_mandatory_consents(make_client, missing: str) -> None:
    store = MemoryStore()
    client = make_client(store=store)

    response = client.post("/devices/register", json=register_body(**{missing: False}))

    assert response.status_code == 422
    assert store.devices == {}


def test_register_works_without_the_optional_consents(make_client) -> None:
    client = make_client(store=MemoryStore())

    response = client.post("/devices/register", json=register_body(excuses=False, analytics=False))

    assert response.status_code == 200


def test_register_without_consents_is_rejected(make_client) -> None:
    client = make_client(store=MemoryStore())

    response = client.post("/devices/register", json={"device_id": TEST_DEVICE_ID})

    assert response.status_code == 422


def test_enrollment_code_closes_the_registration(make_client) -> None:
    client = make_client(make_settings(enrollment_code=TEST_ENROLLMENT_CODE), store=MemoryStore())

    closed = client.post("/devices/register", json=register_body())
    wrong = client.post("/devices/register", json={**register_body(), "enrollment_code": "otro"})
    right = client.post(
        "/devices/register", json={**register_body(), "enrollment_code": TEST_ENROLLMENT_CODE}
    )

    assert closed.status_code == wrong.status_code == 403
    assert right.status_code == 200


def test_register_needs_the_token_secret(make_client) -> None:
    client = make_client(make_settings(device_token_secret=None), store=MemoryStore())

    assert client.post("/devices/register", json=register_body()).status_code == 503


def test_register_fails_if_the_consents_cannot_be_saved(make_client) -> None:
    store = MemoryStore()
    store.fail = True

    response = make_client(store=store).post("/devices/register", json=register_body())

    assert response.status_code == 503


def test_register_is_rate_limited_per_ip(make_client) -> None:
    client = make_client(make_settings(rate_limit_register_per_hour=2), store=MemoryStore())

    codes = [client.post("/devices/register", json=register_body()).status_code for _ in range(3)]

    assert codes == [200, 200, 429]


def test_consents_can_be_updated_and_keep_their_history(make_client) -> None:
    store = consented_store()
    client = make_client(store=store)

    response = client.put(
        "/devices/me/consents",
        json={**TEST_CONSENTS, "analytics": False},
        headers=auth_headers(),
    )

    assert response.status_code == 200
    assert store.devices[TEST_DEVICE_ID]["consent_analytics"] is False
    assert [row["analytics"] for row in store.consents] == [False]


def test_mandatory_consents_cannot_be_revoked_by_update(make_client) -> None:
    client = make_client(store=consented_store())

    response = client.put(
        "/devices/me/consents",
        json={**TEST_CONSENTS, "cross_border": False},
        headers=auth_headers(),
    )

    assert response.status_code == 422


def test_device_without_consents_cannot_use_the_llm(make_client) -> None:
    llm = FakeLLM({})
    client = make_client(judge_llm=llm, store=MemoryStore())

    response = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())

    assert response.status_code == 403
    assert llm.calls == []


def test_judge_refuses_without_the_excuses_consent(make_client) -> None:
    store = consented_store(consent_excuses=False)
    client = make_client(judge_llm=FakeLLM({}), store=store)

    response = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())

    assert response.status_code == 403


def test_generate_refuses_a_device_without_consents(make_client) -> None:
    client = make_client(generate_llm=FakeLLM({}), store=MemoryStore())

    response = client.post("/templates/generate", json=profile_body(), headers=auth_headers())

    assert response.status_code == 403


def test_a_store_outage_does_not_stop_an_emergency(make_client) -> None:
    store = consented_store()
    store.fail = True
    client = make_client(judge_llm=FakeLLM({JudgeEvaluation: evaluation()}), store=store)

    emergency = client.post(
        "/excuses/judge", json=judge_body(type="emergency"), headers=auth_headers()
    )
    extra_time = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())

    # Sin saldo verificable los 10 minutos no se regalan: la app aplica la regla sin conexión.
    assert (emergency.status_code, extra_time.status_code) == (200, 503)


def test_judge_requires_a_valid_token(make_client) -> None:
    client = make_client(judge_llm=FakeLLM({JudgeEvaluation: evaluation()}))

    missing = client.post("/excuses/judge", json=judge_body())
    forged = client.post(
        "/excuses/judge", json=judge_body(), headers=auth_headers(secret="otro-secreto")
    )

    assert missing.status_code == forged.status_code == 401


def test_judge_answers_with_the_spec_schema(make_client) -> None:
    client = make_client(
        judge_llm=FakeLLM({JudgeEvaluation: evaluation()}), store=consented_store()
    )

    response = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())

    assert response.status_code == 200
    assert set(response.json()) == {
        "valid",
        "confidence",
        "granted_minutes",
        "message",
        "reason_code",
        "access_source",
        "credits",
        "included_remaining",
    }
    assert response.json()["granted_minutes"] == 10


def test_judge_is_rate_limited_per_device(make_client) -> None:
    client = make_client(
        make_settings(rate_limit_judge_per_hour=1),
        judge_llm=FakeLLM({JudgeEvaluation: evaluation()}),
        store=consented_store(TEST_DEVICE_ID, "otro-dispositivo-0001"),
    )

    first = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())
    second = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())
    other_device = client.post(
        "/excuses/judge", json=judge_body(), headers=auth_headers(device_id="otro-dispositivo-0001")
    )

    assert (first.status_code, second.status_code, other_device.status_code) == (200, 429, 200)
    assert int(second.headers["Retry-After"]) > 0


def test_judge_unavailable_returns_503(make_client) -> None:
    client = make_client(judge_llm=FakeLLM({JudgeEvaluation: LLMError("sin respuesta")}))

    response = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())

    assert response.status_code == 503


def test_judge_needs_the_token_secret(make_client) -> None:
    client = make_client(make_settings(device_token_secret=None))

    response = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())

    assert response.status_code == 503


def test_generate_returns_the_template(make_client) -> None:
    llm = FakeLLM({ProfileAnalysis: sample_analysis(), DraftBatch: batch(GOOD_MESSAGES[:6])})
    client = make_client(generate_llm=llm)

    response = client.post("/templates/generate", json=profile_body(), headers=auth_headers())

    assert response.status_code == 200
    body = response.json()
    assert [m["id"] for m in body["messages"]][:2] == ["m01", "m02"]
    assert set(body) == {"messages", "verdict_lines", "profile_summary"}


def test_generate_reports_llm_failure_as_502(make_client) -> None:
    llm = FakeLLM({ProfileAnalysis: LLMError("sin respuesta")})
    client = make_client(generate_llm=llm)

    response = client.post("/templates/generate", json=profile_body(), headers=auth_headers())

    assert response.status_code == 502


def test_missing_llm_configuration_is_explicit(make_client) -> None:
    client = make_client(make_settings(xai_api_key=None, grok_model_generate=None))

    response = client.post("/templates/generate", json=profile_body(), headers=auth_headers())

    assert response.status_code == 503
    assert "XAI_API_KEY" in response.json()["detail"]
    assert "GROK_MODEL_GENERATE" in response.json()["detail"]
