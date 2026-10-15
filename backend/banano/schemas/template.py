from typing import Literal, get_args

from pydantic import BaseModel, Field

MessageContext = Literal["general", "reincidente", "noche", "mañana", "tras_rechazo"]
MESSAGE_CONTEXTS: tuple[str, ...] = get_args(MessageContext)


class TemplateMessage(BaseModel):
    id: str
    text: str
    context: MessageContext
    intensity: int = Field(ge=1, le=5)


class VerdictLines(BaseModel):
    approved: list[str]
    rejected: list[str]


class TemplateResponse(BaseModel):
    """Formato del spec más `profile_summary`, que la app guarda y manda al juez."""

    messages: list[TemplateMessage]
    verdict_lines: VerdictLines
    profile_summary: str
