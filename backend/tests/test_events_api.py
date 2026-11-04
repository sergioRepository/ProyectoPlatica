from uuid import uuid4

import pytest

from tests.fakes import MemoryStore, auth_headers, consented_store


def event(**overrides) -> dict:
    values = {
        "id": str(uuid4()),
        "name": "block_shown",
        "app": "com.instagram.android",
        "occurred_at": "2026-11-04T20:00:00Z",
        "props": {"attempt": 3},
    }
    return {**values, **overrides}


def send(client, *events):
    return client.post("/events", json={"events": list(events)}, headers=auth_headers())


def test_events_need_a_device_token(make_client) -> None:
    client = make_client(store=consented_store())

    assert client.post("/events", json={"events": [event()]}).status_code == 401


def test_a_batch_is_saved(make_client) -> None:
    store = consented_store()

    response = send(make_client(store=store), event(), event(name="block_exit", props={}))

    assert response.json() == {"accepted": 2}
    assert len(store.app_events) == 2


def test_a_retried_batch_does_not_duplicate(make_client) -> None:
    store = consented_store()
    client = make_client(store=store)
    same = event()

    send(client, same)
    send(client, same)

    assert len(store.app_events) == 1


def test_without_consent_nothing_is_saved(make_client) -> None:
    store = consented_store(consent_analytics=False)

    assert send(make_client(store=store), event()).json() == {"accepted": 0}
    assert store.app_events == {}


def test_an_unknown_device_saves_nothing(make_client) -> None:
    store = MemoryStore()

    assert send(make_client(store=store), event()).json() == {"accepted": 0}


def test_a_store_outage_asks_for_a_retry(make_client) -> None:
    store = consented_store()
    store.fail = True

    assert send(make_client(store=store), event()).status_code == 503


def test_at_most_100_events_per_call(make_client) -> None:
    client = make_client(store=consented_store())

    assert send(client, *[event() for _ in range(100)]).status_code == 200
    assert send(client, *[event() for _ in range(101)]).status_code == 422


def test_an_empty_batch_is_rejected(make_client) -> None:
    assert send(make_client(store=consented_store())).status_code == 422


@pytest.mark.parametrize(
    "bad",
    [
        {"name": "excusa_enviada"},
        {"props": {"reason": "tengo cita con el psiquiatra"}},
        {"name": "emergency_requested", "props": {"verdict": "tengo cita con el psiquiatra"}},
        {"name": "emergency_requested", "props": {"reason_code": "Cita Medica"}},
        {"name": "block_exit", "props": {"attempt": 1}},
        {"occurred_at": "2026-11-04T20:00:00"},
        {"app": "no es un paquete"},
        {"excuse": "extra"},
    ],
)
def test_anything_outside_the_contract_is_rejected(make_client, bad: dict) -> None:
    store = consented_store()

    assert send(make_client(store=store), event(**bad)).status_code == 422
    assert store.app_events == {}


def test_a_verdict_event_keeps_its_codes(make_client) -> None:
    store = consented_store()
    props = {"verdict": "approved", "reason_code": "legit_work"}

    send(make_client(store=store), event(name="emergency_requested", props=props))

    assert next(iter(store.app_events.values()))["props"] == props
