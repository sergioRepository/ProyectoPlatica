"""Historial del agente en PostgreSQL. Vercel no tiene disco: la base es la memoria."""

from datetime import UTC, datetime, timedelta
from functools import cache
from pathlib import Path
from typing import Any, Protocol
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


class InterventionStore(Protocol):
    async def get_by_request(self, device_id: str, request_id: UUID) -> dict[str, Any] | None: ...

    async def recent(self, device_id: str, limit: int) -> list[dict[str, Any]]: ...

    async def count_interventions_since(self, device_id: str, since: datetime) -> int: ...

    async def save(self, event: dict[str, Any]) -> None: ...

    async def confirm(
        self, device_id: str, event_id: UUID, status: str, error_code: str | None
    ) -> dict[str, Any] | None: ...


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


class MemoryStore:
    """Misma semántica que PostgresStore, en memoria del proceso. La usan los tests y
    LangGraph Studio; `fail` simula la base caída."""

    def __init__(self) -> None:
        self.events: list[dict[str, Any]] = []
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


class PostgresStore:
    # shortcut: una conexión por consulta (3 a 4 por análisis), cambiar a un pool si la latencia de
    # Neon pesa en el límite de 20 s.
    def __init__(self, url: str, timeout_s: float) -> None:
        self._url = url
        self._timeout_s = timeout_s
        self._schema_ready = False

    async def _fetch(self, sql: str, params: tuple[Any, ...]) -> list[dict[str, Any]]:
        try:
            async with await psycopg.AsyncConnection.connect(
                self._url,
                autocommit=True,
                connect_timeout=max(1, round(self._timeout_s)),
                options=f"-c statement_timeout={int(self._timeout_s * 1000)}",
                row_factory=dict_row,
            ) as conn:
                if not self._schema_ready:
                    # Las tablas se crean solas (IF NOT EXISTS): desplegar no exige correr
                    # init_db ni manejar la URL de la base fuera de Vercel.
                    await conn.execute(schema_sql())
                    self._schema_ready = True
                cursor = await conn.execute(sql, params)
                return await cursor.fetchall() if cursor.description else []
        except (psycopg.Error, OSError) as exc:
            raise StoreError(type(exc).__name__) from exc

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


def utcnow() -> datetime:
    return datetime.now(UTC)
