"""Las consultas corren de verdad si hay TEST_DATABASE_URL; el formato se prueba siempre."""

import asyncio
import os
from datetime import date
from decimal import Decimal
from uuid import uuid4

import psycopg
import pytest
from psycopg.rows import dict_row

from banano.agent.store import PostgresStore, utcnow
from scripts.usage_report import QUERIES_DIR, render, table, title_of

DB_URL = os.environ.get("TEST_DATABASE_URL")
CONSENTS = {
    "excuses": True,
    "phone_usage": True,
    "cross_border": True,
    "analytics": True,
    "text_version": "v1",
}


def test_there_is_a_query_for_each_question() -> None:
    assert len(list(QUERIES_DIR.glob("*.sql"))) == 6


@pytest.mark.parametrize("path", sorted(QUERIES_DIR.glob("*.sql")), ids=lambda p: p.stem)
def test_queries_only_read_and_have_a_title(path) -> None:
    sql = path.read_text(encoding="utf-8")

    assert title_of(sql)
    for word in ("INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER "):
        assert word not in sql.upper()


def test_table_formats_values() -> None:
    text = table([{"plan": "free", "costo": Decimal("0.25"), "nota": None, "x": "a|b"}])

    assert text.splitlines()[0] == "| plan | costo | nota | x |"
    assert text.splitlines()[2] == "| free | 0.25 | - | a\\|b |"


def test_an_empty_query_says_so() -> None:
    assert table([]) == "Sin datos."


def test_the_report_has_a_section_per_query() -> None:
    report = render(lambda _: [], date(2026, 11, 4))

    assert "Generado el 2026-11-04." in report
    assert report.count("## ") == 6
    assert '## Respuestas a "¿Pagarías?" por plan' in report


async def seed(store: PostgresStore, device: str) -> None:
    await store.save_consents(device, CONSENTS)
    await store.set_would_pay(device, "maybe")
    request_id = uuid4()
    await store.save(
        {
            "id": uuid4(),
            "device_id": device,
            "request_id": request_id,
            "trigger": "manual",
            "steps": [],
            "evidence_summary": None,
            "decision": "intervene",
            "reason": "x",
            "proposed_action": None,
            "policy_code": None,
            "status": "executed",
            "error_code": None,
        }
    )
    await store.record_usage(
        [
            {
                "id": uuid4(),
                "device_id": device,
                "plan": "monthly",
                "graph": "agent",
                "node": "planificar",
                "model": "m",
                "input_tokens": 100,
                "output_tokens": 50,
                "cost_usd": 0.5,
                "latency_ms": 10,
                "status": "ok",
                "request_id": request_id,
            }
        ]
    )
    names = {
        "block_shown": {"attempt": 1},
        "block_exit": {},
        "emergency_requested": {"verdict": "approved", "reason_code": "legit_work"},
        "quota_reached": {"source": "judge"},
    }
    await store.save_events(
        device,
        [
            {"id": uuid4(), "name": name, "app": "com.x", "occurred_at": utcnow(), "props": props}
            for name, props in names.items()
        ],
    )


@pytest.mark.skipif(not DB_URL, reason="sin TEST_DATABASE_URL")
def test_queries_run_against_postgres() -> None:
    device = f"test-{uuid4()}"
    store = PostgresStore(DB_URL or "", timeout_s=3)
    asyncio.run(seed(store, device), loop_factory=asyncio.SelectorEventLoop)

    with psycopg.connect(DB_URL or "", row_factory=dict_row) as conn:
        conn.read_only = True
        report = render(lambda sql: conn.execute(sql).fetchall(), date(2026, 11, 4))

    assert device in report
    assert "intervencion ejecutada" in report
    assert "maybe" in report
    assert "Sin datos." not in report

    asyncio.run(store.delete_device(device), loop_factory=asyncio.SelectorEventLoop)
