from typing import Literal, Self

from pydantic import BaseModel, Field, model_validator

from banano.schemas.agent import Strict


class Consents(Strict):
    """Autorizaciones de datos. excuses y analytics son opcionales: el texto de las excusas
    puede traer datos sensibles y ninguna actividad puede condicionarse a entregarlos
    (Decreto 1377 de 2013, art. 6). Para revocar las otras dos está DELETE /devices/me."""

    excuses: bool
    phone_usage: bool
    cross_border: bool
    analytics: bool
    text_version: str = Field(min_length=1, max_length=40)

    @model_validator(mode="after")
    def required_are_granted(self) -> Self:
        if not (self.phone_usage and self.cross_border):
            raise ValueError("Faltan las autorizaciones obligatorias.")
        return self


Plan = Literal["free", "monthly", "semester"]
WouldPay = Literal["yes", "no", "maybe"]


class PlanRequest(Strict):
    plan: Plan
    # La respuesta es sobre este plan: al cambiar de plan sin responder, queda vacía.
    would_pay: WouldPay | None = None


class DeviceStatus(BaseModel):
    plan: Plan
    would_pay: WouldPay | None
    daily_limit: int
    used_today: int
    remaining_today: int
    consent_excuses: bool
    consent_analytics: bool


class RegisterRequest(BaseModel):
    consents: Consents
    # Solo se exige si el servidor define ENROLLMENT_CODE (para cerrar el registro).
    enrollment_code: str | None = Field(default=None, max_length=200)


class RegisterResponse(BaseModel):
    device_id: str
    device_token: str
