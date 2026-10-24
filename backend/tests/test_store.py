"""El mismo contrato para MemoryStore y, si hay TEST_DATABASE_URL, para PostgresStore real.

Para correrlo contra Postgres (el del compose):
    $env:TEST_DATABASE_URL = "postgresql://banano:banano@localhost:5432/banano"
"""

import asyncio
import os
from datetime import timedelta
from uuid import uuid4

import psycopg
import pytest

from banano.agent.store import PostgresStore, effective_status, schema_sql, utcnow
from tests.fakes import MemoryStore

DB_URL = os.environ.get("TEST_DATABASE_URL")


def event(device_id: str, status: str | None = "sent") -> dict:
    return {
        "id": uuid4(),
        "device_id": device_id,
        "request_id": uuid4(),
        "trigger": "manual",
        "steps": [{"tool": "consultar_evidencia_uso", "status": "ok"}],
        "evidence_summary": {"apps": [{"label": "TikTok", "minutos": 25.0}]},
        "decision": "intervene",
        "reason": "Uso alto",
        "proposed_action": {"type": "start_focus_session", "duration_minutes": 10},
        "policy_code": None,
        "status": status,
        "error_code": None,
    }


async def contract(store) -> None:
    device = f"test-{uuid4()}"
    other = f"test-{uuid4()}"
    first, second = event(device), event(device, status=None)
    await store.save(first)
    await store.save(second)
    await store.save(event(other))

    found = await store.get_by_request(device, first["request_id"])
    assert found is not None and found["proposed_action"]["duration_minutes"] == 10
    assert await store.get_by_request(other, first["request_id"]) is None

    recent = await store.recent(device, 10)
    assert {e["id"] for e in recent} == {first["id"], second["id"]}
    assert await store.count_interventions_since(device, utcnow() - timedelta(hours=1)) == 1

    confirmed = await store.confirm(device, first["id"], "executed", None)
    assert confirmed is not None and confirmed["status"] == "executed"
    # Repetir la confirmación no cambia nada, y otro dispositivo no puede confirmar.
    again = await store.confirm(device, first["id"], "failed", "x")
    assert again is not None and again["status"] == "executed"
    assert await store.confirm(other, first["id"], "executed", None) is None


async def test_memory_store_contract():
    await contract(MemoryStore())


@pytest.mark.skipif(not DB_URL, reason="sin TEST_DATABASE_URL")
def test_postgres_store_contract():
    # psycopg async no corre en el ProactorEventLoop de Windows.
    store = PostgresStore(DB_URL or "", timeout_s=3)
    asyncio.run(contract(store), loop_factory=asyncio.SelectorEventLoop)


async def test_postgres_store_applies_the_schema_once(monkeypatch):
    executed: list[str] = []

    class FakeCursor:
        description = None

    class FakeConn:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *_):
            return False

        async def execute(self, sql, params=None):
            executed.append(sql)
            return FakeCursor()

    async def connect(*_, **__):
        return FakeConn()

    monkeypatch.setattr(psycopg.AsyncConnection, "connect", connect)
    store = PostgresStore("postgresql://x", timeout_s=3)
    await store.save(event("d"))
    await store.save(event("d"))

    assert schema_sql() and "CREATE TABLE IF NOT EXISTS agent_events" in schema_sql()
    assert executed.count(schema_sql()) == 1
    assert len(executed) == 3


def test_unconfirmed_sent_reads_as_unknown():
    now = utcnow()
    window = timedelta(minutes=2)
    old = {"status": "sent", "created_at": now - timedelta(minutes=3)}
    fresh = {"status": "sent", "created_at": now}
    assert effective_status(old, now, window) == "unknown"
    assert effective_status(fresh, now, window) == "sent"
    assert effective_status({**old, "status": "executed"}, now, window) == "executed"
