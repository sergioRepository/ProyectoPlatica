from typing import Annotated

from pydantic import BaseModel, Field

ShortText = Annotated[str, Field(max_length=120)]


class Profile(BaseModel):
    """Perfil del onboarding. Solo lo que necesita el generador de la plantilla."""

    name: str = Field(min_length=1, max_length=40, description="Nombre o apodo")
    main_goal: str = Field(min_length=3, max_length=200, description="Meta principal")
    secondary_goals: list[ShortText] = Field(default_factory=list, max_length=10)
    goal_reason: str = Field(default="", max_length=500, description="Por qué importa la meta")
    blocked_apps: list[ShortText] = Field(default_factory=list, max_length=60)
    schedule_summary: str = Field(default="", max_length=300, description="Horarios de bloqueo")
    typical_excuses: str = Field(default="", max_length=500)
    sarcasm_level: int = Field(ge=1, le=5)
    profanity_allowed: bool
    country: str = Field(default="Colombia", max_length=40, description="País o jerga")
    forbidden_topics: str = Field(default="", max_length=300)
