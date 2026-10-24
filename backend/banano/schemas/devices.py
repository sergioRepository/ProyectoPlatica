from pydantic import BaseModel, Field

from banano.auth import DEVICE_ID_PATTERN


class RegisterRequest(BaseModel):
    device_id: str = Field(
        pattern=DEVICE_ID_PATTERN, description="Generado por la app en el primer arranque"
    )
    enrollment_code: str = Field(min_length=1, max_length=200)


class RegisterResponse(BaseModel):
    device_token: str
