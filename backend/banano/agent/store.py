"""Historial del agente en PostgreSQL. Vercel no tiene disco: la base es la memoria."""

import asyncio
from datetime import UTC, date, datetime, timedelta
from functools import cache
from pathlib import Path
from typing import Any, Literal, Protocol
from uuid import UUID

import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

JSON_COLUMNS = ("steps", "evidence_summary", "proposed_action")
COLUMNS = (
    "id",
    "device_id",
    "request_id",
    "trigger",
    "steps",
    "evidence_summary",
    "decision",
    "reason",
    "proposed_action",
    "policy_code",
    "status",
    "error_code",
)


SQL_DIR = Path(__file__).resolve().parents[2] / "sql"


@cache
def schema_sql() -> str:
    """El esquema de backend/sql/, en orden. Es idempotente: se puede aplicar siempre."""
    return "\n".join(p.read_text(encoding="utf-8") for p in sorted(SQL_DIR.glob("*.sql")))


class StoreError(Exception):
    """La base no respondió o falló: el agente no debe fingir que consultó o guardó."""


USAGE_COLUMNS = (
    "id",
    "device_id",
    "plan",
    "graph",
    "node",
    "model",
    "input_tokens",
    "output_tokens",
    "cost_usd",
    "latency_ms",
    "status",
    "request_id",
)
DailyCounter = Literal["llm_calls", "rejections", "templates"]
DAILY_COUNTERS: tuple[DailyCounter, ...] = ("llm_calls", "rejections", "templates")
CONSENT_FIELDS = ("excuses", "phone_usage", "cross_border", "analytics", "text_version")


class InterventionStore(Protocol):
    async def get_by_request(self, device_id: str, request_id: UUID) -> dict[str, Any] | None: ...

    async def recent(self, device_id: str, limit: int) -> list[dict[str, Any]]: ...

    async def count_interventions_since(self, device_id: str, since: datetime) -> int: ...

    async def save(self, event: dict[str, Any]) -> None: ...

    async def confirm(
        self, device_id: str, event_id: UUID, status: str, error_code: str | None
    ) -> dict[str, Any] | None: ...


class DeviceStore(Protocol):
    async def get_device(
        self, device_id: str, day: date, timeout: float | None = None
    ) -> dict[str, Any] | None:
        """El dispositivo con sus contadores de `day`."""
        ...

    async def save_consents(self, device_id: str, consents: dict[str, Any]) -> None: ...

    async def set_plan(self, device_id: str, plan: str, would_pay: str | None) -> bool: ...

    async def record_usage(self, rows: list[dict[str, Any]]) -> None: ...

    async def save_events(self, device_id: str, events: list[dict[str, Any]]) -> None:
        """Un evento repetido (mismo id) se ignora."""
        ...

    async def increment(
        self,
        device_id: str,
        day: date,
        counter: DailyCounter,
        limit: int,
        timeout: float | None = None,
    ) -> bool:
        """Suma 1 al contador si sigue por debajo de `limit`. Atómico."""
        ...


class Store(InterventionStore, DeviceStore, Protocol):
    """Lo que necesita la API."""


def effective_status(event: dict[str, Any], now: datetime, window: timedelta) -> str | None:
    """Un "sent" que nadie confirmó a tiempo se lee como "unknown": no se sabe si se aplicó."""
    if event.get("status") == "sent" and now - event["created_at"] > window:
        return "unknown"
    return event.get("status")


class UnconfiguredStore:
    """Sin DATABASE_URL: toda operación falla como una base caída, y el agente no ejecuta."""

    async def _fail(self, *_: Any) -> Any:
        raise StoreError("not_configured")

    get_by_request = recent = count_interventions_since = save = confirm = _fail
    get_device = save_consents = set_plan = increment = record_usage = save_events = _fail


class MemoryStore:
    """Misma semántica que PostgresStore, en memoria del proceso. La usan los tests y
    LangGraph Studio; `fail` simula la base caída."""

    def __init__(self) -> None:
        self.events: list[dict[str, Any]] = []
        self.devices: dict[str, dict[str, Any]] = {}
        self.consents: list[dict[str, Any]] = []
        self.daily: dict[tuple[str, date], dict[str, int]] = {}
        self.usage: list[dict[str, Any]] = []
        self.app_events: dict[UUID, dict[str, Any]] = {}
        self.fail = False

    def _check(self) -> None:
        if self.fail:
            raise StoreError("OperationalError")

    async def get_by_request(self, device_id: str, request_id: UUID) -> dict[str, Any] | None:
        self._check()
        match = [
            e for e in self.events if e["device_id"] == device_id and e["request_id"] == request_id
        ]
        return dict(match[0]) if match else None

    async def recent(self, device_id: str, limit: int) -> list[dict[str, Any]]:
        self._check()
        mine = [dict(e) for e in self.events if e["device_id"] == device_id]
        return sorted(mine, key=lambda e: e["created_at"], reverse=True)[:limit]

    async def count_interventions_since(self, device_id: str, since: datetime) -> int:
        self._check()
        return sum(
            1
            for e in self.events
            if e["device_id"] == device_id
            and e["created_at"] >= since
            and e["status"] in ("sent", "executed", "failed")
        )

    async def save(self, event: dict[str, Any]) -> None:
        self._check()
        if any(
            e["device_id"] == event["device_id"] and e["request_id"] == event["request_id"]
            for e in self.events
        ):
            raise StoreError("UniqueViolation")
        defaults = {"created_at": utcnow(), "error_code": None, "confirmed_at": None}
        self.events.append({**defaults, "observed_outcome": None, **event})

    async def confirm(
        self, device_id: str, event_id: UUID, status: str, error_code: str | None
    ) -> dict[str, Any] | None:
        self._check()
        for e in self.events:
            if e["id"] == event_id and e["device_id"] == device_id:
                if e["status"] == "sent":
                    e.update(status=status, error_code=error_code, confirmed_at=utcnow())
                return dict(e)
        return None

    async def get_device(
        self, device_id: str, day: date, timeout: float | None = None
    ) -> dict[str, Any] | None:
        self._check()
        device = self.devices.get(device_id)
        if device is None:
            return None
        counters = self.daily.get((device_id, day), {})
        return {**device, **{name: counters.get(name, 0) for name in DAILY_COUNTERS}}

    async def save_consents(self, device_id: str, consents: dict[str, Any]) -> None:
        self._check()
        blank = {"device_id": device_id, "plan": "free", "would_pay": None}
        self.devices.setdefault(device_id, blank).update(
            consent_excuses=consents["excuses"], consent_analytics=consents["analytics"]
        )
        self.consents.append({"device_id": device_id, **consents})

    async def record_usage(self, rows: list[dict[str, Any]]) -> None:
        self._check()
        self.usage.extend(rows)

    async def save_events(self, device_id: str, events: list[dict[str, Any]]) -> None:
        self._check()
        for event in events:
            self.app_events.setdefault(event["id"], {"device_id": device_id, **event})

    async def set_plan(self, device_id: str, plan: str, would_pay: str | None) -> bool:
        self._check()
        if device_id not in self.devices:
            return False
        self.devices[device_id].update(plan=plan, would_pay=would_pay)
        return True

    async def increment(
        self,
        device_id: str,
        day: date,
        counter: DailyCounter,
        limit: int,
        timeout: float | None = None,
    ) -> bool:
        self._check()
        counters = self.daily.setdefault((device_id, day), {})
        if counters.get(counter, 0) >= limit:
            return False
        counters[counter] = counters.get(counter, 0) + 1
        return True


class PostgresStore:
    # shortcut: una conexión por consulta (3 a 4 por análisis), cambiar a un pool si la latencia de
    # Neon pesa en el límite de 20 s.
    def __init__(self, url: str, timeout_s: float) -> None:
        self._url = url
        self._timeout_s = timeout_s
        self._schema_ready = False

    async def _fetch(
        self, sql: str, params: tuple[Any, ...], timeout: float | None = None
    ) -> list[dict[str, Any]]:
        try:
            # El límite va aquí y no en `options=-c statement_timeout`: el pooler de Neon
            # (PgBouncer) rechaza ese parámetro de arranque.
            return await asyncio.wait_for(self._run(sql, params), timeout or self._timeout_s)
        except (psycopg.Error, OSError, TimeoutError) as exc:
            raise StoreError(type(exc).__name__) from exc

    async def _run(self, sql: str, params: tuple[Any, ...]) -> list[dict[str, Any]]:
        async with await psycopg.AsyncConnection.connect(
            self._url,
            autocommit=True,
            connect_timeout=max(1, round(self._timeout_s)),
            row_factory=dict_row,
        ) as conn:
            if not self._schema_ready:
                # Las tablas se crean solas (IF NOT EXISTS): desplegar no exige correr
                # init_db ni manejar la URL de la base fuera de Vercel.
                await conn.execute(schema_sql())
                self._schema_ready = True
            cursor = await conn.execute(sql, params)
            return await cursor.fetchall() if cursor.description else []

    async def get_by_request(self, device_id: str, request_id: UUID) -> dict[str, Any] | None:
        rows = await self._fetch(
            "SELECT * FROM agent_events WHERE device_id = %s AND request_id = %s",
            (device_id, request_id),
        )
        return rows[0] if rows else None

    async def recent(self, device_id: str, limit: int) -> list[dict[str, Any]]:
        return await self._fetch(
            "SELECT * FROM agent_events WHERE device_id = %s ORDER BY created_at DESC LIMIT %s",
            (device_id, limit),
        )

    async def count_interventions_since(self, device_id: str, since: datetime) -> int:
        rows = await self._fetch(
            "SELECT count(*) AS n FROM agent_events WHERE device_id = %s AND created_at >= %s "
            "AND status IN ('sent', 'executed', 'failed')",
            (device_id, since),
        )
        return rows[0]["n"]

    async def save(self, event: dict[str, Any]) -> None:
        values = tuple(
            Jsonb(event[c]) if c in JSON_COLUMNS and event[c] is not None else event[c]
            for c in COLUMNS
        )
        placeholders = ", ".join("%s" for _ in COLUMNS)
        await self._fetch(
            f"INSERT INTO agent_events ({', '.join(COLUMNS)}) VALUES ({placeholders})", values
        )

    async def confirm(
        self, device_id: str, event_id: UUID, status: str, error_code: str | None
    ) -> dict[str, Any] | None:
        # Idempotente: repetir la misma confirmación devuelve el evento sin cambiarlo.
        rows = await self._fetch(
            "UPDATE agent_events SET status = %s, error_code = %s, confirmed_at = now() "
            "WHERE id = %s AND device_id = %s AND status = 'sent' RETURNING *",
            (status, error_code, event_id, device_id),
        )
        if rows:
            return rows[0]
        rows = await self._fetch(
            "SELECT * FROM agent_events WHERE id = %s AND device_id = %s", (event_id, device_id)
        )
        return rows[0] if rows else None

    async def get_device(
        self, device_id: str, day: date, timeout: float | None = None
    ) -> dict[str, Any] | None:
        counters = ", ".join(f"coalesce(u.{name}, 0) AS {name}" for name in DAILY_COUNTERS)
        rows = await self._fetch(
            f"SELECT d.*, {counters} FROM devices d LEFT JOIN device_daily_usage u "
            "ON u.device_id = d.device_id AND u.day = %s WHERE d.device_id = %s",
            (day, device_id),
            timeout,
        )
        return rows[0] if rows else None

    async def save_consents(self, device_id: str, consents: dict[str, Any]) -> None:
        # Una sola sentencia: el estado vigente y su prueba se guardan juntos o no se guardan.
        await self._fetch(
            "WITH d AS (INSERT INTO devices (device_id, consent_excuses, consent_analytics) "
            "VALUES (%s, %s, %s) ON CONFLICT (device_id) DO UPDATE SET "
            "consent_excuses = EXCLUDED.consent_excuses, "
            "consent_analytics = EXCLUDED.consent_analytics, updated_at = now() "
            "RETURNING device_id) "
            f"INSERT INTO device_consents (device_id, {', '.join(CONSENT_FIELDS)}) "
            "SELECT d.device_id, %s, %s, %s, %s, %s FROM d",
            (
                device_id,
                consents["excuses"],
                consents["analytics"],
                *(consents[field] for field in CONSENT_FIELDS),
            ),
        )

    async def record_usage(self, rows: list[dict[str, Any]]) -> None:
        row = "(" + ", ".join("%s" for _ in USAGE_COLUMNS) + ")"
        await self._fetch(
            f"INSERT INTO llm_usage ({', '.join(USAGE_COLUMNS)}) "
            f"VALUES {', '.join(row for _ in rows)}",
            tuple(r[column] for r in rows for column in USAGE_COLUMNS),
        )

    async def save_events(self, device_id: str, events: list[dict[str, Any]]) -> None:
        rows = ", ".join("(%s, %s, %s, %s, %s, %s)" for _ in events)
        await self._fetch(
            f"INSERT INTO app_events (id, device_id, name, app, occurred_at, props) VALUES {rows} "
            "ON CONFLICT (id) DO NOTHING",
            tuple(
                value
                for e in events
                for value in (
                    e["id"],
                    device_id,
                    e["name"],
                    e["app"],
                    e["occurred_at"],
                    Jsonb(e["props"]),
                )
            ),
        )

    async def set_plan(self, device_id: str, plan: str, would_pay: str | None) -> bool:
        rows = await self._fetch(
            "UPDATE devices SET plan = %s, would_pay = %s, updated_at = now() "
            "WHERE device_id = %s RETURNING device_id",
            (plan, would_pay, device_id),
        )
        return bool(rows)

    async def increment(
        self,
        device_id: str,
        day: date,
        counter: DailyCounter,
        limit: int,
        timeout: float | None = None,
    ) -> bool:
        if limit <= 0:
            return False
        rows = await self._fetch(
            f"INSERT INTO device_daily_usage AS u (device_id, day, {counter}) VALUES (%s, %s, 1) "
            f"ON CONFLICT (device_id, day) DO UPDATE SET {counter} = u.{counter} + 1 "
            f"WHERE u.{counter} < %s RETURNING device_id",
            (device_id, day, limit),
            timeout,
        )
        return bool(rows)


def utcnow() -> datetime:
    return datetime.now(UTC)
