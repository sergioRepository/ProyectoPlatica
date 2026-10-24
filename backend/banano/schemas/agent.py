"""Contrato del agente de intervención (S2). La evidencia de uso la trae el teléfono."""

from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

UsageStatus = Literal["ok", "permission_missing", "error"]


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class UsageSession(Strict):
    """Un rato continuo de una app al frente, según UsageStatsManager."""

    package: str = Field(min_length=1, max_length=200)
    label: str = Field(max_length=80)
    category: str = Field(default="", max_length=30)
    start_ms: int = Field(ge=0)
    minutes: float = Field(ge=0, le=1440)


class UsageSnapshot(Strict):
    status: UsageStatus
    collected_at_ms: int = Field(ge=0)
    # El teléfono recorta las sesiones más viejas si pasan del tope.
    truncated: bool = False
    sessions: list[UsageSession] = Field(default_factory=list, max_length=300)


class BlockedApp(Strict):
    package: str = Field(min_length=1, max_length=200)
    label: str = Field(max_length=80)


class AnalyzeRequest(Strict):
    request_id: UUID
    trigger: Literal["periodic", "manual"]
    local_time: str = Field(pattern=r"^\d{2}:\d{2}$")
    profile_summary: str = Field(default="", max_length=1500)
    main_goal: str = Field(default="", max_length=200)
    blocked_apps: list[BlockedApp] = Field(default_factory=list, max_length=50)
    focus_active_until_ms: int | None = Field(default=None, ge=0)
    usage: UsageSnapshot


Decision = Literal["intervene", "no_action", "insufficient_evidence", "agent_unavailable"]
StepStatus = Literal[
    "ok", "no_data", "permission_missing", "unavailable", "error", "sent", "rejected"
]


class AgentStepOut(BaseModel):
    """Una herramienta que pidió el agente y lo que pasó al ejecutarla."""

    tool: str
    args: dict[str, Any] = Field(default_factory=dict)
    status: StepStatus
    ms: int = 0


class InterventionOut(BaseModel):
    id: UUID | None
    type: Literal["start_focus_session"] = "start_focus_session"
    duration_minutes: int
    target_apps: list[str]
    # "sent" es una orden al teléfono, no una intervención aplicada: eso lo confirma Android.
    status: Literal["sent", "rejected", "executed", "failed", "unknown"]
    policy_code: str | None = None


class AnalyzeResponse(BaseModel):
    decision: Decision
    reason: str
    steps: list[AgentStepOut]
    intervention: InterventionOut | None = None
    persisted: bool
    # La misma request_id ya se había analizado: se devuelve lo guardado, sin llamar al LLM.
    replayed: bool = False


class ConfirmRequest(Strict):
    status: Literal["executed", "failed"]
    error_code: str | None = Field(default=None, max_length=40)


class ConfirmResponse(BaseModel):
    id: UUID
    status: str
