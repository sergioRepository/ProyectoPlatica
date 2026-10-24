"""Escenarios del spec S2 con un LLM falso: Grok elige herramientas y la política decide."""

from datetime import timedelta

from banano.agent.store import utcnow
from banano.graphs.agent_graph import run_agent_graph
from banano.llm.client import LLMError
from tests.fakes import (
    NOW_MS,
    TEST_DEVICE_ID,
    FakeToolLLM,
    MemoryStore,
    analyze_request,
    make_settings,
    tool_call,
    usage_session,
)

TIKTOK = "com.zhiliaoapp.musically"
EVIDENCE = tool_call("consultar_evidencia_uso", ventana_minutos=60)
HISTORY = tool_call("consultar_historial_intervenciones", limite=5)


def intervene(minutes: int = 10, apps: list[str] | None = None) -> dict:
    return tool_call(
        "ejecutar_intervencion",
        duration_minutes=minutes,
        target_apps=apps or ["TikTok"],
        reason="Llevas 32 minutos en TikTok en la última hora, en tu rato de estudio.",
    )


async def run(llm, store=None, **request_overrides):
    store = store if store is not None else MemoryStore()
    request = analyze_request(**request_overrides)
    response = await run_agent_graph(request, TEST_DEVICE_ID, llm, store, make_settings())
    return response, store


async def test_andres_evidence_history_and_focus_session_is_sent():
    llm = FakeToolLLM([EVIDENCE], [HISTORY], [intervene()])

    response, store = await run(llm)

    assert response.decision == "intervene"
    assert [s.tool for s in response.steps] == [
        "consultar_evidencia_uso",
        "consultar_historial_intervenciones",
        "ejecutar_intervencion",
    ]
    assert [s.status for s in response.steps] == ["ok", "no_data", "sent"]
    assert response.intervention is not None
    assert response.intervention.status == "sent"
    # El nombre visible se resuelve al paquete de la lista.
    assert response.intervention.target_apps == [TIKTOK]
    saved = store.events[0]
    assert saved["status"] == "sent"
    assert saved["evidence_summary"]["apps"][0]["minutos"] == 32.0


async def test_carlos_legit_use_ends_without_history_or_execution():
    usage = {
        "status": "ok",
        "collected_at_ms": NOW_MS,
        "sessions": [usage_session("com.android.chrome", "Chrome", 170, 165)],
    }
    llm = FakeToolLLM(
        [EVIDENCE],
        [tool_call("concluir", decision="no_action", reason="Usa el navegador para trabajar.")],
    )

    response, store = await run(llm, usage=usage)

    assert response.decision == "no_action"
    assert [s.tool for s in response.steps] == ["consultar_evidencia_uso"]
    assert response.intervention is None
    assert store.events[0]["status"] is None


async def test_permission_missing_ends_in_insufficient_evidence():
    usage = {"status": "permission_missing", "collected_at_ms": NOW_MS}
    llm = FakeToolLLM(
        [EVIDENCE],
        [tool_call("concluir", decision="insufficient_evidence", reason="Falta el permiso.")],
    )

    response, _ = await run(llm, usage=usage)

    assert response.steps[0].status == "permission_missing"
    assert response.decision == "insufficient_evidence"


async def test_intervening_without_evidence_is_rejected_by_policy():
    usage = {"status": "permission_missing", "collected_at_ms": NOW_MS}
    llm = FakeToolLLM([EVIDENCE], [intervene()])

    response, _ = await run(llm, usage=usage)

    assert response.intervention is not None
    assert response.intervention.status == "rejected"
    assert response.intervention.policy_code == "no_evidence"


async def test_policy_rejects_duration_and_apps_outside_the_list():
    bad_minutes, _ = await run(FakeToolLLM([EVIDENCE], [intervene(minutes=45)]))
    bad_app, _ = await run(FakeToolLLM([EVIDENCE], [intervene(apps=["WhatsApp"])]))

    assert bad_minutes.intervention.policy_code == "duration_not_allowed"
    assert bad_app.intervention.policy_code == "target_not_allowed"


async def test_daniela_focus_already_active_and_daily_cap():
    active, _ = await run(
        FakeToolLLM([EVIDENCE], [intervene()]), focus_active_until_ms=NOW_MS + 600_000
    )
    store = MemoryStore()
    for _ in range(4):
        await run(FakeToolLLM([EVIDENCE], [intervene()]), store=store)
    capped, _ = await run(FakeToolLLM([EVIDENCE], [intervene()]), store=store)

    assert active.intervention.policy_code == "focus_already_active"
    assert capped.intervention.policy_code == "daily_cap"


async def test_terminating_in_the_same_turn_as_reading_is_not_allowed():
    llm = FakeToolLLM([EVIDENCE, intervene()], [intervene()])

    response, _ = await run(llm)

    # El primer turno ignora la intervención; el segundo, ya con la evidencia vista, la manda.
    assert llm.turns == 2
    assert [s.status for s in response.steps] == ["ok", "error", "sent"]


async def test_llm_timeout_is_agent_unavailable_and_never_claims_analysis():
    response, store = await run(FakeToolLLM(LLMError("El LLM no respondió a tiempo")))

    assert response.decision == "agent_unavailable"
    assert response.reason == ""
    assert response.intervention is None
    assert store.events[0]["error_code"] == "llm_unavailable"


async def test_loop_is_capped():
    response, store = await run(FakeToolLLM([EVIDENCE], [EVIDENCE], [EVIDENCE], [EVIDENCE]))

    assert response.decision == "agent_unavailable"
    assert store.events[0]["error_code"] == "max_cycles"


async def test_unknown_tool_and_bad_args_are_reported_and_the_agent_continues():
    llm = FakeToolLLM(
        [tool_call("borrar_cupos")],
        [tool_call("consultar_evidencia_uso", ventana_minutos=5)],
        [tool_call("concluir", decision="no_action", reason="Nada que hacer.")],
    )

    response, _ = await run(llm)

    assert [s.status for s in response.steps] == ["error", "error"]
    assert response.decision == "no_action"


async def test_store_down_history_is_unavailable_and_nothing_is_sent():
    store = MemoryStore()
    store.fail = True
    llm = FakeToolLLM([EVIDENCE], [HISTORY], [intervene()])

    response, _ = await run(llm, store=store)

    assert response.steps[1].status == "unavailable"
    assert response.intervention.status == "rejected"
    assert response.intervention.policy_code == "store_unavailable"
    assert response.persisted is False


async def test_same_request_id_replays_without_calling_the_llm():
    store = MemoryStore()
    request = analyze_request()
    first = await run_agent_graph(
        request, TEST_DEVICE_ID, FakeToolLLM([EVIDENCE], [intervene()]), store, make_settings()
    )
    llm = FakeToolLLM()
    again = await run_agent_graph(request, TEST_DEVICE_ID, llm, store, make_settings())

    assert llm.turns == 0
    assert again.replayed
    assert again.intervention.id == first.intervention.id
    # Nadie confirmó en la ventana: el estado real es desconocido.
    store.events[0]["created_at"] = utcnow() - timedelta(minutes=5)
    late = await run_agent_graph(request, TEST_DEVICE_ID, llm, store, make_settings())
    assert late.intervention.status == "unknown"


async def test_reason_is_cleaned_and_short():
    long_reason = "Mucho TikTok " * 40 + " :) #foco"
    llm = FakeToolLLM(
        [EVIDENCE], [tool_call("concluir", decision="no_action", reason=long_reason[:400])]
    )

    response, _ = await run(llm)

    assert len(response.reason) <= 240
    assert ":)" not in response.reason
