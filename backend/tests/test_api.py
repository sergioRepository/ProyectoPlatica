from banano.auth import verify_device_token
from banano.graphs.judge_graph import JudgeEvaluation
from banano.graphs.template_graph import DraftBatch, ProfileAnalysis
from banano.llm.client import LLMError
from tests.fakes import (
    GOOD_MESSAGES,
    TEST_DEVICE_ID,
    TEST_ENROLLMENT_CODE,
    TEST_SECRET,
    FakeLLM,
    auth_headers,
    batch,
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


def test_register_returns_a_signed_token(make_client) -> None:
    response = make_client().post(
        "/devices/register",
        json={"device_id": TEST_DEVICE_ID, "enrollment_code": TEST_ENROLLMENT_CODE},
    )

    assert response.status_code == 200
    assert verify_device_token(response.json()["device_token"], TEST_SECRET) == TEST_DEVICE_ID


def test_register_rejects_a_wrong_code(make_client) -> None:
    response = make_client().post(
        "/devices/register", json={"device_id": TEST_DEVICE_ID, "enrollment_code": "otro"}
    )

    assert response.status_code == 403


def test_register_needs_configuration(make_client) -> None:
    client = make_client(make_settings(enrollment_code=None))

    response = client.post(
        "/devices/register",
        json={"device_id": TEST_DEVICE_ID, "enrollment_code": TEST_ENROLLMENT_CODE},
    )

    assert response.status_code == 503


def test_register_validates_the_device_id(make_client) -> None:
    response = make_client().post(
        "/devices/register", json={"device_id": "corto", "enrollment_code": TEST_ENROLLMENT_CODE}
    )

    assert response.status_code == 422


def test_register_is_rate_limited_per_ip(make_client) -> None:
    client = make_client(make_settings(rate_limit_register_per_hour=2))
    body = {"device_id": TEST_DEVICE_ID, "enrollment_code": "otro"}

    codes = [client.post("/devices/register", json=body).status_code for _ in range(3)]

    assert codes == [403, 403, 429]


def test_judge_requires_a_valid_token(make_client) -> None:
    client = make_client(judge_llm=FakeLLM({JudgeEvaluation: evaluation()}))

    missing = client.post("/excuses/judge", json=judge_body())
    forged = client.post(
        "/excuses/judge", json=judge_body(), headers=auth_headers(secret="otro-secreto")
    )

    assert missing.status_code == forged.status_code == 401


def test_judge_answers_with_the_spec_schema(make_client) -> None:
    client = make_client(judge_llm=FakeLLM({JudgeEvaluation: evaluation()}))

    response = client.post("/excuses/judge", json=judge_body(), headers=auth_headers())

    assert response.status_code == 200
    assert set(response.json()) == {
        "valid",
        "confidence",
        "granted_minutes",
        "message",
        "reason_code",
    }
    assert response.json()["granted_minutes"] == 10


def test_judge_is_rate_limited_per_device(make_client) -> None:
    client = make_client(
        make_settings(rate_limit_judge_per_hour=1),
        judge_llm=FakeLLM({JudgeEvaluation: evaluation()}),
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
