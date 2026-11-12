"""Historial del agente en PostgreSQL. Vercel no tiene disco: la base es la memoria."""

import asyncio
from collections.abc import Awaitable, Callable
from datetime import UTC, date, datetime, timedelta
from functools import cache
from pathlib import Path
from typing import Any, Literal, Protocol, TypeVar
from uuid import UUID, uuid4

import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

T = TypeVar("T")

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
# Todo lo que el servidor guarda de un dispositivo.
DEVICE_TABLES = (
    "agent_events",
    "llm_usage",
    "app_events",
    "content_reports",
    "device_daily_usage",
    "device_consents",
    "credit_ledger",
    "entitlements",
    "devices",
)
DailyCounter = Literal["llm_calls", "agent_calls", "rejections", "templates"]
DAILY_COUNTERS: tuple[DailyCounter, ...] = ("llm_calls", "agent_calls", "rejections", "templates")
# Estados de Google que dan acceso; CANCELED también mientras no llegue su expiry_time.
ENTITLED_STATES = ("SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD")
CANCELED_STATE = "SUBSCRIPTION_STATE_CANCELED"
# Estado propio: una compra que otra reemplazó (cambio de plan) deja de dar acceso.
REPLACED_STATE = "REPLACED"
ACCESS_INCLUDED = "included"
ACCESS_CREDIT = "credit"
ENTITLEMENT_COLUMNS = (
    "purchase_token",
    "device_id",
    "product_id",
    "plan",
    "state",
    "expiry_time",
    "auto_renewing",
    "linked_purchase_token",
    "account_hash",
)
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

    async def set_would_pay(self, device_id: str, would_pay: str | None) -> bool: ...

    async def record_usage(self, rows: list[dict[str, Any]]) -> None: ...

    async def save_events(self, device_id: str, events: list[dict[str, Any]]) -> None:
        """Un evento repetido (mismo id) se ignora."""
        ...

    async def delete_device(self, device_id: str) -> None:
        """Borra todo lo del dispositivo, o nada si falla."""
        ...

    async def save_report(self, device_id: str, report: dict[str, Any]) -> None: ...

    async def purge(self, before: datetime, reports_before: datetime, daily_before: date) -> None:
        """Aplica la retención."""
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

    async def save_entitlement(self, entitlement: dict[str, Any]) -> str | None:
        """Guarda el derecho validado con Google y lo liga a `device_id`. Si estaba ligado a otra
        instalación (reinstalación), lo re-liga y le traslada el saldo de créditos; devuelve esa
        instalación anterior. Invalida la compra que esta reemplazó (`linked_purchase_token`).
        `entitlement["day"]` es el día de cupo para el registro del traslado."""
        ...

    async def find_entitlement(self, purchase_token: str) -> dict[str, Any] | None: ...

    async def grant_credits(
        self, device_id: str, purchase_token: str, amount: int, day: date
    ) -> bool:
        """Suma los créditos de una compra una sola vez por token. `False` si ya se dieron."""
        ...

    async def refund_credits(self, purchase_token: str, day: date) -> bool:
        """Descuenta los créditos de una compra reembolsada; el saldo puede quedar negativo."""
        ...

    async def spend_access(
        self, device_id: str, ref: str, day: date, included_cap: int
    ) -> str | None:
        """Paga un acceso de 10 minutos: primero un incluido de `day`, si no un crédito.
        Idempotente por `ref`. `None` si no hay con qué pagar."""
        ...

    async def access_source(self, device_id: str, ref: str) -> str | None:
        """Con qué se pagó el acceso `ref`, si ya se pagó."""
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
    get_device = save_consents = set_would_pay = increment = record_usage = save_events = _fail
    delete_device = save_report = purge = _fail
    save_entitlement = find_entitlement = grant_credits = refund_credits = _fail
    spend_access = access_source = _fail


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
        self.reports: list[dict[str, Any]] = []
        # Derechos por purchase_token y libro de créditos (columnas de las tablas de 007).
        self.entitlements: dict[str, dict[str, Any]] = {}
        self.ledger: list[dict[str, Any]] = []
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
        now = utcnow()
        mine = [e for e in self.entitlements.values() if e["device_id"] == device_id]
        active = [
            e
            for e in mine
            if e["state"] in ENTITLED_STATES
            or (e["state"] == CANCELED_STATE and e["expiry_time"] and e["expiry_time"] > now)
        ]
        best = max(active, key=lambda e: e["expiry_time"] or now, default=None)
        latest = max(mine, key=lambda e: e["updated_at"], default=None)
        rows = [r for r in self.ledger if r["device_id"] == device_id]
        return {
            **device,
            **{name: counters.get(name, 0) for name in DAILY_COUNTERS},
            "plan": best["plan"] if best else "free",
            "subscription_state": latest["state"] if latest else None,
            "expiry_time": latest["expiry_time"] if latest else None,
            "auto_renewing": bool(latest and latest["auto_renewing"]),
            "credits": sum(r["delta"] for r in rows),
            "included_used": sum(
                1 for r in rows if r["kind"] == "access" and r["delta"] == 0 and r["day"] == day
            ),
        }

    async def save_consents(self, device_id: str, consents: dict[str, Any]) -> None:
        self._check()
        blank = {"device_id": device_id, "plan": "free", "would_pay": None}
        self.devices.setdefault(device_id, blank).update(
            consent_excuses=consents["excuses"], consent_analytics=consents["analytics"]
        )
        self.consents.append({"device_id": device_id, **consents})

    async def record_usage(self, rows: list[dict[str, Any]]) -> None:
        self._check()
        self.usage.extend({"created_at": utcnow(), **row} for row in rows)

    async def save_events(self, device_id: str, events: list[dict[str, Any]]) -> None:
        self._check()
        for event in events:
            row = {"device_id": device_id, "received_at": utcnow(), **event}
            self.app_events.setdefault(event["id"], row)

    async def delete_device(self, device_id: str) -> None:
        self._check()
        self.events = [e for e in self.events if e["device_id"] != device_id]
        self.usage = [u for u in self.usage if u["device_id"] != device_id]
        self.consents = [c for c in self.consents if c["device_id"] != device_id]
        self.reports = [r for r in self.reports if r["device_id"] != device_id]
        self.app_events = {
            key: e for key, e in self.app_events.items() if e["device_id"] != device_id
        }
        self.daily = {key: v for key, v in self.daily.items() if key[0] != device_id}
        self.ledger = [r for r in self.ledger if r["device_id"] != device_id]
        self.entitlements = {
            t: e for t, e in self.entitlements.items() if e["device_id"] != device_id
        }
        self.devices.pop(device_id, None)

    async def save_report(self, device_id: str, report: dict[str, Any]) -> None:
        self._check()
        self.reports.append({"device_id": device_id, "created_at": utcnow(), **report})

    async def purge(self, before: datetime, reports_before: datetime, daily_before: date) -> None:
        self._check()
        self.reports = [r for r in self.reports if r["created_at"] >= reports_before]
        self.events = [e for e in self.events if e["created_at"] >= before]
        self.usage = [u for u in self.usage if u["created_at"] >= before]
        self.app_events = {
            key: e for key, e in self.app_events.items() if e["received_at"] >= before
        }
        self.daily = {key: v for key, v in self.daily.items() if key[1] >= daily_before}

    async def set_would_pay(self, device_id: str, would_pay: str | None) -> bool:
        self._check()
        if device_id not in self.devices:
            return False
        self.devices[device_id]["would_pay"] = would_pay
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


    def _balance(self, device_id: str) -> int:
        return sum(r["delta"] for r in self.ledger if r["device_id"] == device_id)

    def _add_entry(self, device_id: str, delta: int, kind: str, ref: str, day: date) -> bool:
        if any(r["kind"] == kind and r["ref"] == ref for r in self.ledger):
            return False
        self.ledger.append(
            {
                "device_id": device_id,
                "delta": delta,
                "kind": kind,
                "ref": ref,
                "day": day,
                "created_at": utcnow(),
            }
        )
        return True

    async def save_entitlement(self, entitlement: dict[str, Any]) -> str | None:
        self._check()
        token, device_id = entitlement["purchase_token"], entitlement["device_id"]
        previous = self.entitlements.get(token)
        moved_from = previous["device_id"] if previous else None
        if previous and moved_from != device_id:
            balance = self._balance(moved_from)
            ref = f"{token}|{moved_from}|{previous['updated_at'].isoformat()}"
            day = entitlement["day"]
            if balance > 0 and self._add_entry(moved_from, -balance, "transfer_out", ref, day):
                self._add_entry(device_id, balance, "transfer_in", ref, day)
        self.entitlements[token] = {
            **{c: entitlement.get(c) for c in ENTITLEMENT_COLUMNS},
            "auto_renewing": bool(entitlement.get("auto_renewing")),
            "updated_at": utcnow(),
        }
        linked = entitlement.get("linked_purchase_token")
        if linked and linked != token and linked in self.entitlements:
            self.entitlements[linked].update(state=REPLACED_STATE, updated_at=utcnow())
        return moved_from if moved_from and moved_from != device_id else None

    async def find_entitlement(self, purchase_token: str) -> dict[str, Any] | None:
        self._check()
        found = self.entitlements.get(purchase_token)
        return dict(found) if found else None

    async def grant_credits(
        self, device_id: str, purchase_token: str, amount: int, day: date
    ) -> bool:
        self._check()
        return self._add_entry(device_id, amount, "purchase", purchase_token, day)

    async def refund_credits(self, purchase_token: str, day: date) -> bool:
        self._check()
        bought = [r for r in self.ledger if r["kind"] == "purchase" and r["ref"] == purchase_token]
        if not bought:
            return False
        first = bought[0]
        return self._add_entry(first["device_id"], -first["delta"], "refund", purchase_token, day)

    async def spend_access(
        self, device_id: str, ref: str, day: date, included_cap: int
    ) -> str | None:
        self._check()
        if (source := await self.access_source(device_id, ref)) is not None:
            return source
        used = sum(
            1
            for r in self.ledger
            if r["device_id"] == device_id
            and r["kind"] == "access"
            and r["delta"] == 0
            and r["day"] == day
        )
        if used < included_cap:
            self._add_entry(device_id, 0, "access", ref, day)
            return ACCESS_INCLUDED
        if self._balance(device_id) > 0:
            self._add_entry(device_id, -1, "access", ref, day)
            return ACCESS_CREDIT
        return None

    async def access_source(self, device_id: str, ref: str) -> str | None:
        self._check()
        for r in self.ledger:
            if r["kind"] == "access" and r["ref"] == ref and r["device_id"] == device_id:
                return ACCESS_INCLUDED if r["delta"] == 0 else ACCESS_CREDIT
        return None


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
        async with await self._connect() as conn:
            cursor = await conn.execute(sql, params)
            return await cursor.fetchall() if cursor.description else []

    async def _connect(self) -> psycopg.AsyncConnection:
        conn = await psycopg.AsyncConnection.connect(
            self._url,
            autocommit=True,
            connect_timeout=max(1, round(self._timeout_s)),
            row_factory=dict_row,
        )
        if not self._schema_ready:
            # Las tablas se crean solas (IF NOT EXISTS): desplegar no exige correr
            # init_db ni manejar la URL de la base fuera de Vercel.
            await conn.execute(schema_sql())
            self._schema_ready = True
        return conn

    async def _transaction(self, work: Callable[[Any], Awaitable[T]]) -> T:
        """Corre `work(conn)` en una transacción: todo o nada."""

        async def run() -> T:
            async with await self._connect() as conn, conn.transaction():
                return await work(conn)

        try:
            return await asyncio.wait_for(run(), self._timeout_s)
        except (psycopg.Error, OSError, TimeoutError) as exc:
            raise StoreError(type(exc).__name__) from exc

    @staticmethod
    async def _one(conn: Any, sql: str, params: tuple[Any, ...]) -> dict[str, Any] | None:
        cursor = await conn.execute(sql, params)
        return await cursor.fetchone()

    @staticmethod
    async def _lock_device(conn: Any, device_id: str) -> None:
        # Un candado por dispositivo durante la transacción: dos pedidos simultáneos no gastan
        # el mismo crédito ni se cruzan con un traslado de saldo.
        await conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (device_id,))

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
        # El plan lo decide el derecho vigente, no devices.plan (el cliente ya no lo elige).
        access = (
            "(state = ANY(%s) OR (state = %s AND expiry_time > now())) AND device_id = d.device_id"
        )
        rows = await self._fetch(
            # Columnas explícitas: devices.plan está en desuso y no debe pisar el plan efectivo.
            "SELECT d.device_id, d.would_pay, d.consent_excuses, d.consent_analytics, "
            f"{counters}, "
            f"coalesce((SELECT plan FROM entitlements WHERE {access} "
            "ORDER BY expiry_time DESC NULLS LAST LIMIT 1), 'free') AS plan, "
            "l.state AS subscription_state, l.expiry_time AS expiry_time, "
            "coalesce(l.auto_renewing, false) AS auto_renewing, "
            "(SELECT coalesce(sum(delta), 0)::int FROM credit_ledger "
            "WHERE device_id = d.device_id) AS credits, "
            "(SELECT count(*)::int FROM credit_ledger WHERE device_id = d.device_id "
            "AND kind = 'access' AND delta = 0 AND day = %s) AS included_used "
            "FROM devices d LEFT JOIN device_daily_usage u "
            "ON u.device_id = d.device_id AND u.day = %s "
            "LEFT JOIN LATERAL (SELECT state, expiry_time, auto_renewing FROM entitlements "
            "WHERE device_id = d.device_id ORDER BY updated_at DESC LIMIT 1) l ON true "
            "WHERE d.device_id = %s",
            (list(ENTITLED_STATES), CANCELED_STATE, day, day, device_id),
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

    async def delete_device(self, device_id: str) -> None:
        # Una sola sentencia: se borra todo o no se borra nada.
        deletes = ", ".join(
            f"d{i} AS (DELETE FROM {table} WHERE device_id = %s)"
            for i, table in enumerate(DEVICE_TABLES)
        )
        await self._fetch(f"WITH {deletes} SELECT 1", (device_id,) * len(DEVICE_TABLES))

    async def save_report(self, device_id: str, report: dict[str, Any]) -> None:
        await self._fetch(
            "INSERT INTO content_reports (id, device_id, source, reason, message) "
            "VALUES (%s, %s, %s, %s, %s)",
            (uuid4(), device_id, report["source"], report["reason"], report["message"]),
        )

    async def purge(self, before: datetime, reports_before: datetime, daily_before: date) -> None:
        await self._fetch(
            "WITH a AS (DELETE FROM agent_events WHERE created_at < %s), "
            "b AS (DELETE FROM llm_usage WHERE created_at < %s), "
            "c AS (DELETE FROM app_events WHERE received_at < %s), "
            "d AS (DELETE FROM device_daily_usage WHERE day < %s), "
            "e AS (DELETE FROM content_reports WHERE created_at < %s) SELECT 1",
            (before, before, before, daily_before, reports_before),
        )

    async def set_would_pay(self, device_id: str, would_pay: str | None) -> bool:
        rows = await self._fetch(
            "UPDATE devices SET would_pay = %s, updated_at = now() "
            "WHERE device_id = %s RETURNING device_id",
            (would_pay, device_id),
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


    async def save_entitlement(self, entitlement: dict[str, Any]) -> str | None:
        token, device_id = entitlement["purchase_token"], entitlement["device_id"]
        values = tuple(
            bool(entitlement.get(c)) if c == "auto_renewing" else entitlement.get(c)
            for c in ENTITLEMENT_COLUMNS
        )
        updates = ", ".join(f"{c} = EXCLUDED.{c}" for c in ENTITLEMENT_COLUMNS[1:])

        async def work(conn: Any) -> str | None:
            previous = await self._one(
                conn,
                "SELECT device_id, updated_at::text AS stamp FROM entitlements "
                "WHERE purchase_token = %s FOR UPDATE",
                (token,),
            )
            await conn.execute(
                f"INSERT INTO entitlements ({', '.join(ENTITLEMENT_COLUMNS)}) "
                f"VALUES ({', '.join('%s' for _ in ENTITLEMENT_COLUMNS)}) "
                f"ON CONFLICT (purchase_token) DO UPDATE SET {updates}, updated_at = now()",
                values,
            )
            linked = entitlement.get("linked_purchase_token")
            if linked and linked != token:
                await conn.execute(
                    "UPDATE entitlements SET state = %s, updated_at = now() "
                    "WHERE purchase_token = %s",
                    (REPLACED_STATE, linked),
                )
            if previous is None or previous["device_id"] == device_id:
                return None
            moved_from = previous["device_id"]
            await self._lock_device(conn, moved_from)
            row = await self._one(
                conn,
                "SELECT coalesce(sum(delta), 0)::int AS balance FROM credit_ledger "
                "WHERE device_id = %s",
                (moved_from,),
            )
            balance = row["balance"] if row else 0
            # La referencia lleva el estado anterior: repetir el mismo re-ligado no traslada dos
            # veces, y uno nuevo sí traslada.
            ref = f"{token}|{moved_from}|{previous['stamp']}"
            if balance > 0:
                moved = await self._one(
                    conn,
                    "INSERT INTO credit_ledger (device_id, delta, kind, ref, day) "
                    "VALUES (%s, %s, 'transfer_out', %s, %s) "
                    "ON CONFLICT (kind, ref) DO NOTHING RETURNING id",
                    (moved_from, -balance, ref, entitlement["day"]),
                )
                if moved:
                    await conn.execute(
                        "INSERT INTO credit_ledger (device_id, delta, kind, ref, day) "
                        "VALUES (%s, %s, 'transfer_in', %s, %s)",
                        (device_id, balance, ref, entitlement["day"]),
                    )
            return moved_from

        return await self._transaction(work)

    async def find_entitlement(self, purchase_token: str) -> dict[str, Any] | None:
        rows = await self._fetch(
            "SELECT * FROM entitlements WHERE purchase_token = %s", (purchase_token,)
        )
        return rows[0] if rows else None

    async def grant_credits(
        self, device_id: str, purchase_token: str, amount: int, day: date
    ) -> bool:
        rows = await self._fetch(
            "INSERT INTO credit_ledger (device_id, delta, kind, ref, day) "
            "VALUES (%s, %s, 'purchase', %s, %s) ON CONFLICT (kind, ref) DO NOTHING RETURNING id",
            (device_id, amount, purchase_token, day),
        )
        return bool(rows)

    async def refund_credits(self, purchase_token: str, day: date) -> bool:
        # shortcut: se descuenta a la instalación que compró aunque el saldo se haya trasladado;
        # buscar al dueño actual si los reembolsos tras reinstalar se vuelven frecuentes.
        rows = await self._fetch(
            "INSERT INTO credit_ledger (device_id, delta, kind, ref, day) "
            "SELECT device_id, -delta, 'refund', ref, %s FROM credit_ledger "
            "WHERE kind = 'purchase' AND ref = %s "
            "ON CONFLICT (kind, ref) DO NOTHING RETURNING id",
            (day, purchase_token),
        )
        return bool(rows)

    async def spend_access(
        self, device_id: str, ref: str, day: date, included_cap: int
    ) -> str | None:
        async def work(conn: Any) -> str | None:
            await self._lock_device(conn, device_id)
            done = await self._one(
                conn,
                "SELECT delta FROM credit_ledger WHERE kind = 'access' AND ref = %s "
                "AND device_id = %s",
                (ref, device_id),
            )
            if done:
                return ACCESS_INCLUDED if done["delta"] == 0 else ACCESS_CREDIT
            row = await self._one(
                conn,
                "SELECT coalesce(sum(delta), 0)::int AS balance, "
                "(count(*) FILTER (WHERE kind = 'access' AND delta = 0 AND day = %s))::int "
                "AS used FROM credit_ledger WHERE device_id = %s",
                (day, device_id),
            )
            balance, used = (row["balance"], row["used"]) if row else (0, 0)
            if used < included_cap:
                delta, source = 0, ACCESS_INCLUDED
            elif balance > 0:
                delta, source = -1, ACCESS_CREDIT
            else:
                return None
            await conn.execute(
                "INSERT INTO credit_ledger (device_id, delta, kind, ref, day) "
                "VALUES (%s, %s, 'access', %s, %s)",
                (device_id, delta, ref, day),
            )
            return source

        return await self._transaction(work)

    async def access_source(self, device_id: str, ref: str) -> str | None:
        rows = await self._fetch(
            "SELECT delta FROM credit_ledger WHERE kind = 'access' AND ref = %s "
            "AND device_id = %s",
            (ref, device_id),
        )
        if not rows:
            return None
        return ACCESS_INCLUDED if rows[0]["delta"] == 0 else ACCESS_CREDIT


def utcnow() -> datetime:
    return datetime.now(UTC)
