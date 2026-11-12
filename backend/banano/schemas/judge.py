from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, Field

ExcuseType = Literal["emergency", "extra_time"]
ReasonCode = Literal[
    "legit_work",
    "legit_emergency",
    "boredom",
    "repeated",
    "over_quota",
    "crisis",
    "too_short",
    "plan_quota",
    "rejection_cap",
    "no_credits",
]

Excuse = Annotated[str, Field(max_length=1000)]


class JudgeRequest(BaseModel):
    """Entrada del spec. Los campos marcados como extensión tienen valores por defecto."""

    type: ExcuseType
    reason: str = Field(max_length=1000)
    app: str = Field(min_length=1, max_length=80)
    profile_summary: str = Field(default="", max_length=1500)
    attempts_today: int = Field(default=0, ge=0, le=10_000)
    extra_time_granted_today: int = Field(default=0, ge=0, le=100)
    recent_excuses: list[Excuse] = Field(default_factory=list, max_length=20)
    local_time: str = Field(pattern=r"^\d{2}:\d{2}$")
    # Extensiones: el prefiltro necesita el cupo de emergencias y los límites configurables,
    # y el tono depende del perfil.
    emergencies_granted_today: int = Field(default=0, ge=0, le=100)
    extra_time_daily_limit: int = Field(default=3, ge=0, le=50)
    emergency_daily_limit: int = Field(default=2, ge=0, le=50)
    sarcasm_level: int = Field(default=3, ge=1, le=5)
    profanity_allowed: bool = False
    country: str = Field(default="Colombia", max_length=40)
    # Modo amable en Ajustes: sin sarcasmo ni burla, aunque rechace.
    kind_mode: bool = False
    # Identifica el pedido de 10 minutos: repetirlo no evalúa ni cobra otra vez.
    request_id: UUID | None = None


class JudgeResponse(BaseModel):
    valid: bool
    confidence: float = Field(ge=0, le=1)
    granted_minutes: int = Field(ge=0)
    message: str
    reason_code: ReasonCode
    # Con qué se pagó un acceso de 10 minutos aprobado: incluido en el plan o un crédito.
    access_source: Literal["included", "credit"] | None = None
    credits: int | None = None
    included_remaining: int | None = None
