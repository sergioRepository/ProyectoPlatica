from functools import lru_cache
from typing import Literal

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

StructuredMethod = Literal["json_schema", "function_calling", "json_mode"]


class Settings(BaseSettings):
    """Configuración leída del entorno (en local también de backend/.env).

    Nada de Grok va escrito en el código: URL del gateway, llave y modelos llegan siempre
    por variables de entorno. En Vercel se configuran en el proyecto.
    """

    # env_ignore_empty: una variable vacía (ENROLLMENT_CODE=) cuenta como no configurada.
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore", env_ignore_empty=True
    )

    # Gateway de Grok
    xai_base_url: str | None = None
    xai_api_key: SecretStr | None = None
    grok_model_generate: str | None = None
    grok_model_judge: str | None = None
    # Método de salida estructurada; si falla se cae a JSON en texto validado con Pydantic.
    llm_structured_method: StructuredMethod = "json_schema"
    generate_llm_timeout_s: float = 120.0
    # El teléfono espera 8 s en total: el juez debe contestar antes.
    judge_llm_timeout_s: float = 6.0

    # Registro de dispositivos
    enrollment_code: SecretStr | None = None
    device_token_secret: SecretStr | None = None

    # Límite de peticiones (en memoria; en Vercel cada instancia cuenta por separado)
    rate_limit_judge_per_hour: int = 30
    rate_limit_generate_per_hour: int = 5
    rate_limit_register_per_hour: int = 10

    # Plantilla de mensajes
    template_target_messages: int = 60
    template_min_messages: int = 40
    template_max_retries: int = 2
    template_min_acceptable: int = 10
    # Los mensajes se piden en lotes paralelos: un solo pedido de 60 no cabe a tiempo con
    # un modelo que razona.
    template_chunk_size: int = 20
    # Vercel corta a los 300 s: la generación completa debe caber con margen.
    template_total_timeout_s: float = 240.0

    # Juez de excusas
    min_reason_chars: int = 15
    repeat_similarity: float = 0.85
    extra_time_minutes: int = 10
    emergency_minutes: int = 15

    # Agente de intervención (S2)
    database_url: SecretStr | None = None
    # Neon Free suspende la base sin uso; despertarla toma uno o dos segundos.
    db_timeout_s: float = 5.0
    rate_limit_agent_per_hour: int = 12
    agent_llm_timeout_s: float = 6.0
    agent_total_timeout_s: float = 20.0
    agent_max_cycles: int = 3
    agent_daily_interventions: int = 4
    # Un "sent" sin confirmar después de esto se lee como "unknown".
    agent_confirm_window_s: float = 120.0

    enable_docs: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()
