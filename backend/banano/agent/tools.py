"""Herramientas del agente. Los nombres de las clases son los que ve Grok (bind_tools)."""

from datetime import datetime, timedelta
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from banano.agent.store import InterventionStore, StoreError, effective_status
from banano.schemas.agent import UsageSnapshot

MINUTE_MS = 60_000


class consultar_evidencia_uso(BaseModel):  # noqa: N801 (el nombre es el de la herramienta)
    """Minutos y aperturas por app en la ventana pedida, medidos por Android. Si no hay
    permiso o datos lo dice; nunca inventa uso."""

    ventana_minutos: int = Field(ge=15, le=1440, description="Minutos hacia atrás desde ahora")
    apps: list[str] | None = Field(
        default=None, max_length=20, description="Paquetes o nombres; vacío es todas"
    )


class consultar_historial_intervenciones(BaseModel):  # noqa: N801
    """Intervenciones anteriores de este dispositivo, de la más reciente a la más vieja, con
    su estado real: sent (enviada sin confirmar), executed, failed, unknown o rejected."""

    limite: int = Field(default=5, ge=1, le=10)


class ejecutar_intervencion(BaseModel):  # noqa: N801
    """Pide al teléfono una sesión de enfoque: bloquea esas apps de la lista durante esos
    minutos. El backend la valida y puede rechazarla. Úsala solo con evidencia de uso."""

    model_config = ConfigDict(extra="forbid")

    duration_minutes: int = Field(description="5, 10, 15, 20 o 30")
    target_apps: list[str] = Field(
        min_length=1, max_length=10, description="Paquetes de apps bloqueadas"
    )
    reason: str = Field(max_length=400, description="Motivo breve y neutro, según la evidencia")


class concluir(BaseModel):  # noqa: N801
    """Termina sin intervenir: el uso no lo justifica o faltan datos fiables."""

    model_config = ConfigDict(extra="forbid")

    decision: Literal["no_action", "insufficient_evidence"]
    reason: str = Field(max_length=400, description="Motivo breve y neutro")


READ_TOOLS = (consultar_evidencia_uso, consultar_historial_intervenciones)
TERMINAL_TOOLS = (ejecutar_intervencion, concluir)
TOOLS = {tool.__name__: tool for tool in READ_TOOLS + TERMINAL_TOOLS}


def consultar_evidencia(usage: UsageSnapshot, args: consultar_evidencia_uso) -> dict[str, Any]:
    if usage.status != "ok":
        return {"status": usage.status}
    since = usage.collected_at_ms - args.ventana_minutos * MINUTE_MS
    wanted = {a.casefold() for a in args.apps or []}
    apps: dict[str, dict[str, Any]] = {}
    for s in usage.sessions:
        if wanted and s.package.casefold() not in wanted and s.label.casefold() not in wanted:
            continue
        # Solo cuenta la parte de la sesión que cae dentro de la ventana.
        end = s.start_ms + s.minutes * MINUTE_MS
        inside = (min(end, usage.collected_at_ms) - max(s.start_ms, since)) / MINUTE_MS
        if inside <= 0:
            continue
        app = apps.setdefault(
            s.package,
            {"package": s.package, "label": s.label, "category": s.category, "minutos": 0.0},
        )
        app["minutos"] += inside
        app["aperturas"] = app.get("aperturas", 0) + 1
    result: dict[str, Any] = {"ventana_minutos": args.ventana_minutos, "fuente": "android_usage"}
    if not apps:
        return {"status": "no_data", **result}
    ranked = sorted(apps.values(), key=lambda a: a["minutos"], reverse=True)
    for app in ranked:
        app["minutos"] = round(app["minutos"], 1)
    return {"status": "ok", **result, "truncated": usage.truncated, "apps": ranked}


async def consultar_historial(
    store: InterventionStore,
    device_id: str,
    args: consultar_historial_intervenciones,
    now: datetime,
    confirm_window: timedelta,
) -> dict[str, Any]:
    try:
        rows = await store.recent(device_id, args.limite)
    except StoreError:
        # Falló la consulta: no es lo mismo que no tener historial.
        return {"status": "unavailable"}
    if not rows:
        return {"status": "no_data"}
    return {
        "status": "ok",
        "intervenciones": [
            {
                "fecha": row["created_at"].isoformat(timespec="minutes"),
                "decision": row["decision"],
                "accion": row["proposed_action"],
                "estado": effective_status(row, now, confirm_window),
                "politica": row["policy_code"],
            }
            for row in rows
        ],
    }
