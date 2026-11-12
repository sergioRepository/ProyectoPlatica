from datetime import datetime
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
    # Se acepta (los APK 0.4.0 lo envían) pero se ignora: el plan lo decide el servidor.
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
    subscription_state: str | None
    expiry_time: datetime | None
    auto_renewing: bool
    credits: int
    included_daily: int
    included_remaining: int
    agent_enabled: bool


class VerifyRequest(Strict):
    """Una compra que la app vio en Google Play. El servidor la valida con Google."""

    product_id: str = Field(min_length=1, max_length=100, pattern=r"^[a-z0-9._]+$")
    # El token va en la URL de la API de Google y es la llave del registro de créditos: solo el
    # alfabeto de los tokens de Play, sin "/" ni "..", para que un texto no valide la compra de
    # otro token ni la canjee dos veces.
    purchase_token: str = Field(
        min_length=10, max_length=2000, pattern=r"^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$"
    )


class VerifyResponse(BaseModel):
    # subscription | pending | granted | already_granted
    outcome: str
    device: DeviceStatus


class RegisterRequest(BaseModel):
    consents: Consents
    # Solo se exige si el servidor define ENROLLMENT_CODE (para cerrar el registro).
    enrollment_code: str | None = Field(default=None, max_length=200)


class RegisterResponse(BaseModel):
    device_id: str
    device_token: str
