"""Eventos de comportamiento. Lista cerrada de claves y valores sin espacios: por aquí no puede
entrar el texto de una excusa."""

from typing import Annotated, Literal, Self
from uuid import UUID

from pydantic import AwareDatetime, BaseModel, Field, StringConstraints, model_validator

from banano.schemas.agent import Strict

EventName = Literal[
    "block_shown",
    "block_exit",
    "extra_time_requested",
    "emergency_requested",
    "quota_reached",
    "focus_session_started",
    "focus_session_completed",
    "plan_selected",
    "sabotage_attempt",
]

_VERDICT_PROPS = frozenset({"verdict", "reason_code"})
ALLOWED_PROPS: dict[str, frozenset[str]] = {
    "block_shown": frozenset({"attempt"}),
    "block_exit": frozenset(),
    "extra_time_requested": _VERDICT_PROPS,
    "emergency_requested": _VERDICT_PROPS,
    "quota_reached": frozenset({"source"}),
    "focus_session_started": frozenset({"duration_minutes"}),
    "focus_session_completed": frozenset({"duration_minutes"}),
    "plan_selected": frozenset({"plan", "would_pay"}),
    "sabotage_attempt": frozenset({"action"}),
}

Code = Annotated[str, StringConstraints(pattern=r"^[a-z0-9_]{1,40}$")]
MAX_EVENTS = 100


class AppEvent(Strict):
    id: UUID
    name: EventName
    # Paquete de la app, no su contenido.
    app: str | None = Field(default=None, max_length=200, pattern=r"^[A-Za-z0-9._]+$")
    occurred_at: AwareDatetime
    props: dict[str, bool | int | Code] = Field(default_factory=dict)

    @model_validator(mode="after")
    def props_are_allowed(self) -> Self:
        unknown = set(self.props) - ALLOWED_PROPS[self.name]
        if unknown:
            raise ValueError(f"Propiedades no permitidas en {self.name}: {sorted(unknown)}")
        return self


class EventBatch(Strict):
    events: list[AppEvent] = Field(min_length=1, max_length=MAX_EVENTS)


class EventsAccepted(BaseModel):
    accepted: int
