"""Topes diarios por plan. Se revisan antes de llamar a Grok.

Una llamada es una operación: un juicio o un análisis, sin importar sus turnos. Si la base no
responde se deja pasar: el límite por hora sigue acotando el gasto, y el usuario no pierde el
juez porque Neon esté dormida.
"""

import logging
from datetime import UTC, date, datetime, timedelta, timezone
from typing import Any, Literal

from banano.agent.store import DailyCounter, Store, StoreError
from banano.config import Settings

logger = logging.getLogger(__name__)

QuotaReason = Literal["plan_quota", "rejection_cap"]


def plan_limit(settings: Settings, plan: str) -> int:
    return settings.benefits(plan).daily_evaluations


def quota_day(settings: Settings, now: datetime | None = None) -> date:
    """El día del cupo termina a medianoche de Colombia, no de UTC."""
    zone = timezone(timedelta(hours=settings.quota_utc_offset_hours))
    return (now or datetime.now(UTC)).astimezone(zone).date()


async def bump(
    store: Store,
    device_id: str,
    settings: Settings,
    counter: DailyCounter,
    limit: int = 1_000_000,
) -> bool:
    """Suma al contador de hoy si no llegó a `limit`. Sin base responde que sí."""
    try:
        return await store.increment(
            device_id, quota_day(settings), counter, limit, settings.gate_db_timeout_s
        )
    except StoreError as exc:
        logger.warning("No se pudo actualizar %s: %s", counter, exc)
        return True


async def take_llm_call(
    store: Store,
    device: dict[str, Any] | None,
    settings: Settings,
    *,
    reserve: int = 0,
    judge: bool = False,
) -> QuotaReason | None:
    """Reserva una llamada de hoy; devuelve el motivo si no hay. `device` None: base caída."""
    if device is None:
        return None
    if judge and device["rejections"] >= settings.daily_rejection_cap:
        return "rejection_cap"
    limit = plan_limit(settings, device["plan"]) - reserve
    allowed = await bump(store, device["device_id"], settings, "llm_calls", limit)
    return None if allowed else "plan_quota"
