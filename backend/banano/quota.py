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

QuotaReason = Literal["plan_quota", "rejection_cap", "no_credits"]


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


def check_judge(
    device: dict[str, Any] | None, settings: Settings, request_type: str
) -> QuotaReason | None:
    """Revisa sin contar nada: la evaluación se cuenta solo si Peel responde. `device` None:
    base caída (la ruta decide qué hacer)."""
    if device is None:
        return None
    if device["rejections"] >= settings.daily_rejection_cap:
        return "rejection_cap"
    if request_type == "emergency":
        return None
    benefits = settings.benefits(device["plan"])
    if device["llm_calls"] >= benefits.daily_evaluations:
        return "plan_quota"
    if device["included_used"] >= benefits.included_accesses and device["credits"] <= 0:
        return "no_credits"
    return None


async def take_agent_call(
    store: Store, device: dict[str, Any] | None, settings: Settings
) -> QuotaReason | None:
    """Reserva un análisis de hoy; Gratis tiene tope 0. `device` None: base caída."""
    if device is None:
        return None
    cap = settings.benefits(device["plan"]).agent_daily_cap
    allowed = await bump(store, device["device_id"], settings, "agent_calls", cap)
    return None if allowed else "plan_quota"
