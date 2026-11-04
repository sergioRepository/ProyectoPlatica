from typing import Literal

from pydantic import Field

from banano.schemas.agent import Strict


class ContentReport(Strict):
    """El motivo es de lista cerrada: un campo libre invitaría a contar datos de salud."""

    message: str = Field(min_length=1, max_length=500)
    reason: Literal["offensive", "harmful", "wrong", "other"]
    source: Literal["block_message", "verdict"]
