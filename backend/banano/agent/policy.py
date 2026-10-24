"""Validación determinista de la intervención que propone Grok. El modelo nunca es la
autoridad: si algo no cuadra, se rechaza con un código estable."""

from banano.agent.tools import ejecutar_intervencion
from banano.schemas.agent import AnalyzeRequest

ALLOWED_DURATIONS = frozenset({5, 10, 15, 20, 30})


def resolve_targets(names: list[str], request: AnalyzeRequest) -> list[str] | None:
    """Paquetes de la lista del usuario; acepta el nombre visible. `None` si alguna no está."""
    by_name = {}
    for app in request.blocked_apps:
        by_name[app.package.casefold()] = app.package
        by_name[app.label.casefold()] = app.package
    packages = [by_name.get(name.casefold()) for name in names]
    if any(p is None for p in packages):
        return None
    return list(dict.fromkeys(p for p in packages if p))


def check(
    action: ejecutar_intervencion, request: AnalyzeRequest, evidence_ok: bool
) -> tuple[str | None, list[str]]:
    """Devuelve (código de rechazo o None, paquetes objetivo). Cupo y base van aparte."""
    if not evidence_ok:
        return "no_evidence", []
    if action.duration_minutes not in ALLOWED_DURATIONS:
        return "duration_not_allowed", []
    targets = resolve_targets(action.target_apps, request)
    if not targets:
        return "target_not_allowed", []
    focus_until = request.focus_active_until_ms
    if focus_until is not None and focus_until > request.usage.collected_at_ms:
        return "focus_already_active", targets
    return None, targets
