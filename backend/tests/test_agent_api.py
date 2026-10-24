from uuid import uuid4

from tests.fakes import (
    FakeToolLLM,
    MemoryStore,
    analyze_request,
    auth_headers,
    make_settings,
    tool_call,
)


def body() -> dict:
    return analyze_request().model_dump(mode="json")


def sent_intervention(make_client, store: MemoryStore) -> tuple:
    llm = FakeToolLLM(
        [tool_call("consultar_evidencia_uso", ventana_minutos=60)],
        [
            tool_call(
                "ejecutar_intervencion",
                duration_minutes=10,
                target_apps=["TikTok"],
                reason="Mucho TikTok.",
            )
        ],
    )
    client = make_client(agent_llm=llm, store=store)
    response = client.post("/agent/analyze", json=body(), headers=auth_headers())
    return client, response


def test_analyze_needs_a_device_token(make_client) -> None:
    response = make_client(agent_llm=FakeToolLLM(), store=MemoryStore()).post(
        "/agent/analyze", json=body()
    )

    assert response.status_code == 401


def test_analyze_rejects_unknown_fields(make_client) -> None:
    client = make_client(agent_llm=FakeToolLLM(), store=MemoryStore())

    response = client.post(
        "/agent/analyze", json={**body(), "extra_time_daily_limit": 99}, headers=auth_headers()
    )

    assert response.status_code == 422


def test_analyze_then_confirm_executed(make_client) -> None:
    store = MemoryStore()
    client, response = sent_intervention(make_client, store)
    assert response.status_code == 200
    intervention = response.json()["intervention"]
    assert intervention["status"] == "sent"

    confirm = client.post(
        f"/agent/interventions/{intervention['id']}/result",
        json={"status": "executed"},
        headers=auth_headers(),
    )

    assert confirm.status_code == 200
    assert confirm.json()["status"] == "executed"
    assert store.events[0]["confirmed_at"] is not None


def test_another_device_cannot_confirm(make_client) -> None:
    store = MemoryStore()
    client, response = sent_intervention(make_client, store)
    event_id = response.json()["intervention"]["id"]

    other = client.post(
        f"/agent/interventions/{event_id}/result",
        json={"status": "executed"},
        headers=auth_headers("otro-dispositivo"),
    )
    unknown = client.post(
        f"/agent/interventions/{uuid4()}/result",
        json={"status": "failed"},
        headers=auth_headers(),
    )

    assert other.status_code == 404
    assert unknown.status_code == 404
    assert store.events[0]["status"] == "sent"


def test_without_database_nothing_is_sent(make_client) -> None:
    # Sin DATABASE_URL, get_store usa UnconfiguredStore.
    llm = FakeToolLLM(
        [tool_call("consultar_evidencia_uso", ventana_minutos=60)],
        [
            tool_call(
                "ejecutar_intervencion", duration_minutes=10, target_apps=["TikTok"], reason="x"
            )
        ],
    )
    client = make_client(make_settings(database_url=None), agent_llm=llm)

    response = client.post("/agent/analyze", json=body(), headers=auth_headers())

    assert response.json()["intervention"]["status"] == "rejected"
    assert response.json()["intervention"]["policy_code"] == "store_unavailable"
    assert response.json()["persisted"] is False


def test_agent_has_its_own_rate_limit(make_client) -> None:
    turns = [[tool_call("concluir", decision="no_action", reason="Nada.")]] * 2
    client = make_client(
        make_settings(rate_limit_agent_per_hour=1),
        agent_llm=FakeToolLLM(*turns),
        store=MemoryStore(),
    )

    first = client.post("/agent/analyze", json=body(), headers=auth_headers())
    second = client.post("/agent/analyze", json=body(), headers=auth_headers())

    assert first.status_code == 200
    assert second.status_code == 429
