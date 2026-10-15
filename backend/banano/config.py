from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Configuración leída del entorno (en local también de backend/.env).

    Nada de Grok va escrito en el código: URL del gateway, llave y modelos llegan siempre
    por variables de entorno. En Vercel se configuran en el proyecto.
    """

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    xai_base_url: str | None = None
    xai_api_key: SecretStr | None = None
    grok_model_generate: str | None = None
    grok_model_judge: str | None = None

    enrollment_code: SecretStr | None = None
    device_token_secret: SecretStr | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
