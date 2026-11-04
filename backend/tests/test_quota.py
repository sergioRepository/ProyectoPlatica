from datetime import UTC, date, datetime

import pytest

from banano.graphs.judge_graph import JudgeEvaluation
from banano.graphs.template_graph import DraftBatch, ProfileAnalysis
from banano.llm.client import LLMError
from banano.quota import plan_limit, quota_day
from tests.fakes import (
    GOOD_MESSAGES,
    TEST_DEVICE_ID,
    FakeLLM,
    FakeToolLLM,
    MemoryStore,
    analyze_request,
    auth_headers,
    batch,
    consented_store,
    evaluation,
    judge_request,
    make_settings,
    sample_analysis,
    sample_profile,
    tool_call,
)

SETTINGS = make_settings()
TODAY = quota_day(SETTINGS)


def used(store: MemoryStore, counter: str = "llm_calls") -> int:
    return store.daily.get((TEST_DEVICE_ID, TODAY), {}).get(counter, 0)


def judge(client, **overrides):
    body = judge_request(**overrides).model_dump()
    return client.post("/excuses/judge", json=body, headers=auth_headers())


def spend(store: MemoryStore, calls: int, counter: str = "llm_calls") -> None:
    store.daily[(TEST_DEVICE_ID, TODAY)] = {counter: calls}


def analyze(client, **overrides):
    body = analyze_request(**overrides).model_dump(mode="json")
    return client.post("/agent/analyze", json=body, headers=auth_headers())


def conclude() -> FakeToolLLM:
    return FakeToolLLM([tool_call("concluir", decision="no_action", reason="Todo bien.")])


def test_limits_come_from_the_settings() -> None:
    settings = make_settings(plan_limit_free=1, plan_limit_monthly=7, plan_limit_semester=9)

    assert plan_limit(settings, "free") == 1
    assert plan_limit(settings, "monthly") == 7
    assert plan_limit(settings, "semester") == 9


def test_the_day_ends_at_midnight_in_colombia() -> None:
    before = datetime(2026, 11, 5, 4, 59, tzinfo=UTC)
    after = datetime(2026, 11, 5, 5, 0, tzinfo=UTC)

    assert quota_day(SETTINGS, before) == date(2026, 11, 4)
    assert quota_day(SETTINGS, after) == date(2026, 11, 5)


def test_each_judgment_uses_one_call(make_client) -> None:
    store = consented_store()
    client = make_client(judge_llm=FakeLLM({JudgeEvaluation: evaluation()}), store=store)

    assert judge(client).json()["reason_code"] == "legit_work"
    assert used(store) == 1


def test_without_quota_extra_time_is_rejected_without_llm(make_client) -> None:
    store = consented_store()
    spend(store, 3)
    llm = FakeLLM({JudgeEvaluation: evaluation()})

    verdict = judge(make_client(judge_llm=llm, store=store)).json()

    assert (verdict["valid"], verdict["reason_code"]) == (False, "plan_quota")
    assert verdict["granted_minutes"] == 0
    assert llm.calls == []


def test_without_quota_an_emergency_is_approved_without_llm(make_client) -> None:
    store = consented_store()
    spend(store, 3)
    llm = FakeLLM({JudgeEvaluation: evaluation()})

    verdict = judge(make_client(judge_llm=llm, store=store), type="emergency").json()

    assert (verdict["valid"], verdict["reason_code"]) == (True, "plan_quota")
    assert verdict["granted_minutes"] == SETTINGS.emergency_minutes
    assert llm.calls == []


def test_paid_plans_have_their_own_limit(make_client) -> None:
    store = consented_store(plan="semester")
    spend(store, 3)
    client = make_client(judge_llm=FakeLLM({JudgeEvaluation: evaluation()}), store=store)

    assert judge(client).json()["reason_code"] == "legit_work"


def test_a_crisis_is_answered_with_no_quota_left(make_client) -> None:
    store = consented_store()
    spend(store, 3)
    client = make_client(judge_llm=FakeLLM({}), store=store)

    verdict = judge(client, reason="Mi mamá se desmayó y voy para el hospital").json()

    assert (verdict["valid"], verdict["reason_code"]) == (True, "crisis")


def test_a_prefilter_rejection_does_not_use_quota(make_client) -> None:
    store = consented_store()
    client = make_client(judge_llm=FakeLLM({}), store=store)

    assert judge(client, reason="un ratito").json()["reason_code"] == "too_short"
    assert used(store) == 0


def test_rejections_are_counted_and_capped_without_llm(make_client) -> None:
    store = consented_store(plan="monthly")
    rejected = evaluation(valid=False, reason_code="boredom", message="No.")
    llm = FakeLLM({JudgeEvaluation: rejected})
    client = make_client(make_settings(daily_rejection_cap=2), judge_llm=llm, store=store)

    codes = [judge(client, attempts_today=i).json()["reason_code"] for i in range(3)]

    assert codes == ["boredom", "boredom", "rejection_cap"]
    assert len(llm.calls) == 2
    assert used(store, "rejections") == 2


def test_at_the_rejection_cap_an_emergency_is_still_approved(make_client) -> None:
    store = consented_store()
    spend(store, 5, "rejections")
    client = make_client(judge_llm=FakeLLM({}), store=store)

    verdict = judge(client, type="emergency").json()

    assert (verdict["valid"], verdict["reason_code"]) == (True, "rejection_cap")


def test_a_store_outage_lets_the_judge_call_the_llm(make_client) -> None:
    store = consented_store()
    store.fail = True
    llm = FakeLLM({JudgeEvaluation: evaluation()})

    verdict = judge(make_client(judge_llm=llm, store=store)).json()

    assert verdict["reason_code"] == "legit_work"
    assert len(llm.calls) == 1


def test_a_failed_llm_still_used_the_call(make_client) -> None:
    store = consented_store()
    llm = FakeLLM({JudgeEvaluation: LLMError("sin respuesta")})

    response = judge(make_client(judge_llm=llm, store=store))

    assert response.status_code == 503
    assert used(store) == 1


def test_one_analysis_uses_one_call_whatever_its_turns(make_client) -> None:
    store = consented_store()
    llm = FakeToolLLM(
        [tool_call("consultar_evidencia_uso", ventana_minutos=60)],
        [tool_call("concluir", decision="no_action", reason="Todo bien.")],
    )

    assert analyze(make_client(agent_llm=llm, store=store)).json()["decision"] == "no_action"
    assert llm.turns == 2
    assert used(store) == 1


def test_without_quota_the_agent_decides_without_llm(make_client) -> None:
    store = consented_store()
    spend(store, 3)
    llm = conclude()

    body = analyze(make_client(agent_llm=llm, store=store)).json()

    assert body["decision"] == "plan_quota"
    assert (body["intervention"], body["persisted"]) == (None, False)
    assert llm.turns == 0


def test_a_periodic_analysis_leaves_the_last_call_for_the_judge(make_client) -> None:
    store = consented_store()
    spend(store, 2)
    client = make_client(agent_llm=conclude(), store=store)

    assert analyze(client, trigger="periodic").json()["decision"] == "plan_quota"
    assert analyze(client, trigger="manual").json()["decision"] == "no_action"


def test_a_replayed_analysis_uses_no_quota(make_client) -> None:
    store = consented_store()
    client = make_client(agent_llm=conclude(), store=store)
    request = analyze_request()

    for _ in range(2):
        client.post("/agent/analyze", json=request.model_dump(mode="json"), headers=auth_headers())

    assert used(store) == 1


def test_status_shows_what_is_left_today(make_client) -> None:
    store = consented_store()
    spend(store, 2)

    body = make_client(store=store).get("/devices/me", headers=auth_headers()).json()

    assert (body["plan"], body["daily_limit"], body["remaining_today"]) == ("free", 3, 1)


def test_status_never_goes_negative(make_client) -> None:
    store = consented_store()
    spend(store, 9)

    body = make_client(store=store).get("/devices/me", headers=auth_headers()).json()

    assert body["remaining_today"] == 0


def test_status_of_an_unknown_device(make_client) -> None:
    client = make_client(store=MemoryStore())

    assert client.get("/devices/me", headers=auth_headers()).status_code == 404


def test_plan_can_be_changed_and_keeps_the_answer(make_client) -> None:
    store = consented_store()
    client = make_client(store=store)

    response = client.put(
        "/devices/me/plan",
        json={"plan": "monthly", "would_pay": "maybe"},
        headers=auth_headers(),
    )

    assert response.status_code == 200
    assert (response.json()["daily_limit"], response.json()["would_pay"]) == (20, "maybe")
    assert store.devices[TEST_DEVICE_ID]["plan"] == "monthly"


@pytest.mark.parametrize("body", [{"plan": "gratis"}, {"plan": "free", "would_pay": "quizas"}, {}])
def test_plan_is_validated(make_client, body: dict) -> None:
    client = make_client(store=consented_store())

    assert client.put("/devices/me/plan", json=body, headers=auth_headers()).status_code == 422


def generate(client):
    body = sample_profile().model_dump()
    return client.post("/templates/generate", json=body, headers=auth_headers())


def template_llm() -> FakeLLM:
    return FakeLLM({ProfileAnalysis: sample_analysis(), DraftBatch: batch(GOOD_MESSAGES)})


def test_templates_are_capped_per_day(make_client) -> None:
    store = consented_store()
    settings = make_settings(daily_template_cap=1)
    client = make_client(settings, generate_llm=template_llm(), store=store)

    assert [generate(client).status_code for _ in range(2)] == [200, 429]


def test_a_failed_template_does_not_count(make_client) -> None:
    store = consented_store()
    llm = FakeLLM({ProfileAnalysis: LLMError("sin respuesta")})

    assert generate(make_client(generate_llm=llm, store=store)).status_code == 502
    assert used(store, "templates") == 0
