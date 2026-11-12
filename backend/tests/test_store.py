"""El mismo contrato para MemoryStore y, si hay TEST_DATABASE_URL, para PostgresStore real.

Para correrlo contra Postgres (el del compose):
    $env:TEST_DATABASE_URL = "postgresql://banano:banano@localhost:5432/banano"
"""

import asyncio
import os
from datetime import date, timedelta
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

    await store.record_usage(
        [
            {
                "id": uuid4(),
                "device_id": device,
                "plan": "free",
                "graph": "judge",
                "node": "evaluar",
                "model": "m",
                "input_tokens": 10,
                "output_tokens": None,
                "cost_usd": None,
                "latency_ms": 5,
                "status": "timeout",
                "request_id": uuid4(),
            }
        ]
    )

    app_event = {
        "id": uuid4(),
        "name": "block_shown",
        "app": "com.instagram.android",
        "occurred_at": utcnow(),
        "props": {"attempt": 2},
    }
    await store.save_events(device, [app_event])
    await store.save_events(device, [app_event])

    await store.save_report(
        device, {"message": "Mensaje", "reason": "offensive", "source": "verdict"}
    )
    await store.purge(
        utcnow() - timedelta(days=90), utcnow() - timedelta(days=90), date(2020, 1, 1)
    )
    assert await store.recent(device, 10)

    today = date(2026, 11, 4)
    assert await store.get_device(device, today) is None
    consents = {
        "excuses": False,
        "phone_usage": True,
        "cross_border": True,
        "analytics": True,
        "text_version": "v1",
    }
    await store.save_consents(device, consents)
    await store.save_consents(device, {**consents, "excuses": True, "analytics": False})
    saved = await store.get_device(device, today)
    assert saved is not None and saved["consent_excuses"] is True
    assert saved["consent_analytics"] is False
    assert (saved["plan"], saved["would_pay"], saved["llm_calls"]) == ("free", None, 0)

    assert await store.set_would_pay(device, "maybe") is True
    assert await store.set_would_pay(other, None) is False

    assert await store.increment(device, today, "llm_calls", 2) is True
    assert await store.increment(device, today, "llm_calls", 2) is True
    assert await store.increment(device, today, "llm_calls", 2) is False
    assert await store.increment(device, today, "rejections", 2) is True
    assert await store.increment(device, today, "agent_calls", 1) is True
    assert await store.increment(device, today, "agent_calls", 1) is False
    tomorrow = today + timedelta(days=1)
    assert await store.increment(device, tomorrow, "llm_calls", 2) is True
    saved = await store.get_device(device, today)
    assert saved is not None and saved["plan"] == "free" and saved["would_pay"] == "maybe"
    assert (saved["llm_calls"], saved["rejections"], saved["templates"]) == (2, 1, 0)
    assert (saved["agent_calls"], saved["credits"], saved["included_used"]) == (1, 0, 0)

    await store.delete_device(device)
    assert await store.get_device(device, today) is None
    assert await store.recent(device, 10) == []
    assert len(await store.recent(other, 10)) == 1
    await store.delete_device(device)

    future = utcnow() + timedelta(days=1)
    await store.purge(future, future, tomorrow)
    assert await store.recent(other, 10) == []


async def money_contract(store) -> None:
    """Créditos, accesos y derechos: lo que mueve dinero se prueba igual en los dos stores."""
    consents = {
        "excuses": True,
        "phone_usage": True,
        "cross_border": True,
        "analytics": False,
        "text_version": "prueba",
    }
    first, second = f"test-{uuid4()}", f"test-{uuid4()}"
    for device in (first, second):
        await store.save_consents(device, consents)
    today = date(2026, 11, 11)
    pack = f"compra-{uuid4()}"

    # Una compra de créditos se suma una sola vez, aunque se valide dos veces.
    assert await store.grant_credits(first, pack, 2, today) is True
    assert await store.grant_credits(first, pack, 2, today) is False
    assert (await store.get_device(first, today))["credits"] == 2

    # Primero el incluido del día, después los créditos, y luego nada.
    plan = f"plan-{uuid4()}"
    paid = [await store.spend_access(first, f"{first}:{n}", today, 1, plan) for n in range(4)]
    assert paid == ["included", "credit", "credit", None]
    # El mismo pedido no se cobra dos veces.
    assert await store.spend_access(first, f"{first}:1", today, 1, plan) == "credit"
    assert await store.access_source(first, f"{first}:1") == "credit"
    assert await store.access_source(first, f"{first}:3") is None
    # Los incluidos usados se ven en el dispositivo que tiene la suscripción, no en este.
    assert (await store.get_device(first, today))["credits"] == 0
    # El incluido es por día: mañana vuelve.
    tomorrow = today + timedelta(days=1)
    assert await store.spend_access(first, f"{first}:5", tomorrow, 1, plan) == "included"
    # Y es de la suscripción, no de la instalación: otra instalación con la misma no lo renueva.
    await store.grant_credits(second, f"compra-{uuid4()}", 1, today)
    assert await store.spend_access(second, f"{second}:6", tomorrow, 1, plan) == "credit"
    # Sin suscripción no hay incluidos.
    assert await store.spend_access(second, f"{second}:7", tomorrow, 1, None) is None

    # Reembolso: se descuenta lo comprado, una vez, aunque el saldo quede negativo.
    assert await store.refund_credits(pack, today) is True
    assert await store.refund_credits(pack, today) is False
    assert (await store.get_device(first, today))["credits"] == -2

    # Re-ligado al reinstalar: el plan y el saldo pasan a la instalación nueva, una sola vez.
    await store.grant_credits(first, f"compra-{uuid4()}", 7, today)
    token = f"suscripcion-{uuid4()}"
    entitlement = {
        "purchase_token": token,
        "device_id": first,
        "product_id": "premium",
        "plan": "semester",
        "state": "SUBSCRIPTION_STATE_ACTIVE",
        "expiry_time": utcnow() + timedelta(days=30),
        "auto_renewing": True,
        "linked_purchase_token": None,
        "account_hash": None,
        "day": today,
    }
    assert await store.save_entitlement(entitlement) is None
    assert (await store.get_device(first, today))["plan"] == "semester"
    assert await store.save_entitlement({**entitlement, "device_id": second}) == first
    moved, left = await store.get_device(second, today), await store.get_device(first, today)
    assert (moved["plan"], moved["credits"]) == ("semester", 5)
    assert moved["plan_ref"] is not None and left["plan_ref"] is None
    assert (left["plan"], left["credits"]) == ("free", 0)
    assert await store.save_entitlement({**entitlement, "device_id": second}) is None
    assert (await store.get_device(second, today))["credits"] == 5
    assert (await store.find_entitlement(token))["device_id"] == second

    # Cambio de plan: la compra reemplazada deja de dar acceso.
    newer = f"suscripcion-{uuid4()}"
    await store.save_entitlement(
        {
            **entitlement,
            "purchase_token": newer,
            "device_id": second,
            "plan": "monthly",
            "linked_purchase_token": token,
        }
    )
    assert (await store.find_entitlement(token))["state"] == "REPLACED"
    assert (await store.get_device(second, today))["plan"] == "monthly"

    # Reembolso tras un traslado: se descuenta a quien tiene hoy el saldo, no a quien compró.
    third = f"test-{uuid4()}"
    await store.save_consents(third, consents)
    moving = f"compra-{uuid4()}"
    await store.grant_credits(second, moving, 10, today)
    await store.save_entitlement(
        {
            **entitlement,
            "purchase_token": newer,
            "device_id": third,
            "plan": "monthly",
            "linked_purchase_token": token,
        }
    )
    assert (await store.get_device(third, today))["credits"] == 15
    assert await store.refund_credits(moving, today) is True
    assert (await store.get_device(third, today))["credits"] == 5
    assert (await store.get_device(second, today))["credits"] == 0

    # Borrar los datos se lleva los derechos; el registro de créditos se anonimiza y el mismo
    # token de compra no se puede canjear otra vez.
    await store.delete_device(third)
    assert await store.find_entitlement(newer) is None
    reborn = f"test-{uuid4()}"
    await store.save_consents(reborn, consents)
    assert await store.grant_credits(reborn, moving, 10, today) is False
    assert (await store.get_device(reborn, today))["credits"] == 0


async def test_memory_store_contract():
    await contract(MemoryStore())


async def test_memory_store_money_contract():
    await money_contract(MemoryStore())


@pytest.mark.skipif(not DB_URL, reason="sin TEST_DATABASE_URL")
def test_postgres_store_contract():
    # psycopg async no corre en el ProactorEventLoop de Windows.
    store = PostgresStore(DB_URL or "", timeout_s=3)
    asyncio.run(contract(store), loop_factory=asyncio.SelectorEventLoop)


@pytest.mark.skipif(not DB_URL, reason="sin TEST_DATABASE_URL")
def test_postgres_store_money_contract():
    store = PostgresStore(DB_URL or "", timeout_s=3)
    asyncio.run(money_contract(store), loop_factory=asyncio.SelectorEventLoop)


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
