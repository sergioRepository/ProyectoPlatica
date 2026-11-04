from datetime import timedelta
from uuid import uuid4

import pytest

from banano.agent.store import utcnow
from banano.quota import quota_day
from tests.fakes import (
    TEST_CONSENTS,
    TEST_DEVICE_ID,
    FakeLLM,
    auth_headers,
    consented_store,
    judge_request,
    make_settings,
)

OTHER = "otro-dispositivo"
CRON = {"Authorization": "Bearer secreto-del-cron"}
SETTINGS = make_settings(cron_secret="secreto-del-cron")
TODAY = quota_day(SETTINGS)


def event(device_id: str, created_at=None) -> dict:
    return {
        "id": uuid4(),
        "device_id": device_id,
        "request_id": uuid4(),
        "created_at": created_at or utcnow(),
        "status": None,
    }


async def filled_store():
    store = consented_store(TEST_DEVICE_ID, OTHER)
    for device_id in (TEST_DEVICE_ID, OTHER):
        store.events.append(event(device_id))
        store.consents.append({"device_id": device_id})
        store.daily[(device_id, TODAY)] = {"llm_calls": 1}
        await store.record_usage([{"id": uuid4(), "device_id": device_id}])
        await store.save_events(device_id, [{"id": uuid4(), "name": "block_exit"}])
        await store.save_report(device_id, {"message": "m", "reason": "other", "source": "verdict"})
    return store


async def test_deleting_removes_everything_of_that_device_only(make_client) -> None:
    store = await filled_store()

    response = make_client(store=store).delete("/devices/me", headers=auth_headers())

    assert response.status_code == 204
    leftovers = [
        store.events,
        store.usage,
        store.consents,
        store.reports,
        list(store.app_events.values()),
    ]
    assert all(row["device_id"] == OTHER for rows in leftovers for row in rows)
    assert all(len(rows) == 1 for rows in leftovers)
    assert list(store.devices) == [OTHER]
    assert list(store.daily) == [(OTHER, TODAY)]


async def test_a_deleted_device_can_no_longer_use_the_llm(make_client) -> None:
    store = await filled_store()
    llm = FakeLLM({})
    client = make_client(judge_llm=llm, store=store)

    client.delete("/devices/me", headers=auth_headers())
    response = client.post(
        "/excuses/judge", json=judge_request().model_dump(), headers=auth_headers()
    )

    assert response.status_code == 403
    assert llm.calls == []


REPORT = {"message": "Eres un inútil", "reason": "offensive", "source": "verdict"}


def test_a_report_is_saved_with_its_device(make_client) -> None:
    store = consented_store()

    response = make_client(store=store).post("/reports", json=REPORT, headers=auth_headers())

    assert response.status_code == 201
    assert store.reports[0]["device_id"] == TEST_DEVICE_ID
    assert store.reports[0]["message"] == REPORT["message"]
    assert store.reports[0]["created_at"]


def test_a_report_works_without_analytics(make_client) -> None:
    store = consented_store(consent_analytics=False, consent_excuses=False)

    response = make_client(store=store).post("/reports", json=REPORT, headers=auth_headers())

    assert response.status_code == 201


def test_a_report_needs_a_token(make_client) -> None:
    assert make_client(store=consented_store()).post("/reports", json=REPORT).status_code == 401


@pytest.mark.parametrize(
    "bad",
    [
        {"reason": "me contó algo de salud"},
        {"source": "otra"},
        {"message": ""},
        {"message": "x" * 501},
        {"comment": "texto libre"},
    ],
)
def test_a_report_is_validated(make_client, bad: dict) -> None:
    client = make_client(store=consented_store())

    response = client.post("/reports", json={**REPORT, **bad}, headers=auth_headers())

    assert response.status_code == 422


def test_a_failed_report_is_not_hidden(make_client) -> None:
    store = consented_store()
    store.fail = True

    response = make_client(store=store).post("/reports", json=REPORT, headers=auth_headers())

    assert response.status_code == 503


def test_deleting_is_idempotent(make_client) -> None:
    client = make_client(store=consented_store())

    codes = [client.delete("/devices/me", headers=auth_headers()).status_code for _ in range(2)]

    assert codes == [204, 204]


def test_deleting_needs_a_token(make_client) -> None:
    assert make_client(store=consented_store()).delete("/devices/me").status_code == 401


def test_a_failed_delete_is_reported(make_client) -> None:
    store = consented_store()
    store.fail = True

    response = make_client(store=store).delete("/devices/me", headers=auth_headers())

    assert response.status_code == 503


def old_and_new_store():
    store = consented_store()
    old, new = utcnow() - timedelta(days=91), utcnow() - timedelta(days=89)
    store.events += [event(TEST_DEVICE_ID, old), event(TEST_DEVICE_ID, new)]
    store.usage += [{"created_at": old}, {"created_at": new}]
    store.reports += [
        {"created_at": utcnow() - timedelta(days=181)},
        {"created_at": utcnow() - timedelta(days=179)},
    ]
    store.app_events = {"viejo": {"received_at": old}, "nuevo": {"received_at": new}}
    store.daily = {
        (TEST_DEVICE_ID, TODAY - timedelta(days=8)): {},
        (TEST_DEVICE_ID, TODAY - timedelta(days=6)): {},
    }
    return store


def test_retention_removes_only_what_expired(make_client) -> None:
    store = old_and_new_store()

    response = make_client(SETTINGS, store=store).get("/internal/retention", headers=CRON)

    assert response.status_code == 200
    assert len(store.events) == len(store.usage) == 1
    assert list(store.app_events) == ["nuevo"]
    assert len(store.reports) == 1
    assert len(store.daily) == 1


def test_retention_keeps_devices_and_consents(make_client) -> None:
    store = old_and_new_store()
    store.consents.append({"device_id": TEST_DEVICE_ID})

    make_client(SETTINGS, store=store).get("/internal/retention", headers=CRON)

    assert TEST_DEVICE_ID in store.devices
    assert len(store.consents) == 1


def test_retention_needs_the_cron_secret(make_client) -> None:
    store = old_and_new_store()
    client = make_client(SETTINGS, store=store)

    assert client.get("/internal/retention").status_code == 401
    assert client.get("/internal/retention", headers=auth_headers()).status_code == 401
    assert len(store.events) == 2


def test_retention_is_closed_without_a_configured_secret(make_client) -> None:
    client = make_client(store=old_and_new_store())

    assert client.get("/internal/retention", headers=CRON).status_code == 503


def test_privacy_actions_are_never_rate_limited(make_client) -> None:
    settings = make_settings(rate_limit_device_per_hour=1)
    client = make_client(settings, store=consented_store())
    client.get("/devices/me", headers=auth_headers())

    limited = client.get("/devices/me", headers=auth_headers())
    revoked = client.put("/devices/me/consents", json=TEST_CONSENTS, headers=auth_headers())
    deleted = client.delete("/devices/me", headers=auth_headers())

    assert limited.status_code == 429
    assert (revoked.status_code, deleted.status_code) == (200, 204)
