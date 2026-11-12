from dataclasses import dataclass
from functools import lru_cache
from typing import Literal

from pydantic import BaseModel, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

StructuredMethod = Literal["json_schema", "function_calling", "json_mode"]


class ModelPrice(BaseModel):
    """En dólares por millón de tokens."""

    input_per_million: float
    output_per_million: float


@dataclass(frozen=True)
class PlanBenefits:
    daily_evaluations: int
    included_accesses: int
    auto_review: bool
    agent_daily_cap: int


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
    judge_llm_timeout_s: float = 5.5

    # Registro de dispositivos. Con ENROLLMENT_CODE definido, el registro exige el código:
    # sirve para cerrarlo si hay abuso. Vacío, el registro es abierto.
    enrollment_code: SecretStr | None = None
    device_token_secret: SecretStr | None = None

    # Límite de peticiones (en memoria; en Vercel cada instancia cuenta por separado)
    rate_limit_judge_per_hour: int = 30
    rate_limit_generate_per_hour: int = 5
    rate_limit_register_per_hour: int = 10
    rate_limit_device_per_hour: int = 60

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
    # Para consultas en el camino del juez: el teléfono espera 8 s en total.
    gate_db_timeout_s: float = 0.5
    usage_write_timeout_s: float = 0.8
    rate_limit_agent_per_hour: int = 12
    agent_llm_timeout_s: float = 6.0
    agent_total_timeout_s: float = 20.0
    agent_max_cycles: int = 3
    agent_daily_interventions: int = 4
    # Un "sent" sin confirmar después de esto se lee como "unknown".
    agent_confirm_window_s: float = 120.0

    # Evaluaciones de Peel al día por plan.
    plan_limit_free: int = 3
    plan_limit_monthly: int = 20
    plan_limit_semester: int = 20
    # Accesos de 10 min incluidos al día en los planes de pago.
    plan_included_accesses_paid: int = 3
    # Análisis automáticos al día en los planes de pago.
    agent_daily_cap: int = 96
    # Productos de Google Play (deben coincidir con Play Console).
    play_subscription_id: str = "premium"
    play_base_plan_monthly: str = "monthly"
    play_base_plan_semester: str = "semester"
    play_credits_product_id: str = "creditos_10"
    credits_per_pack: int = 10

    # Google Play Developer API y notificaciones (RTDN). La llave de la cuenta de servicio es
    # el JSON completo, en una sola variable; nunca va al repo ni a los logs.
    google_play_package_name: str = "com.lavidanoesunbanano.app"
    google_play_service_account_json: SecretStr | None = None
    play_api_timeout_s: float = 10.0
    # El push de Pub/Sub firma un JWT: su audiencia y la cuenta de servicio que lo emite.
    rtdn_audience: str | None = None
    rtdn_service_account_email: str | None = None
    # Después de esta cantidad de rechazos del día, el juez responde sin llamar a Grok.
    daily_rejection_cap: int = 5
    daily_template_cap: int = 2
    # Colombia no cambia de hora, así que basta un desfase fijo.
    quota_utc_offset_hours: int = -5

    # Precio por modelo para calcular cost_usd, en JSON. Un modelo sin precio guarda sus tokens
    # con el costo vacío.
    llm_prices: dict[str, ModelPrice] = {}

    # Retención, en días. La aplica el cron de vercel.json, que se autentica con CRON_SECRET.
    retention_days: int = 90
    retention_reports_days: int = 180
    retention_daily_usage_days: int = 7
    cron_secret: SecretStr | None = None

    enable_docs: bool = False

    def benefits(self, plan: str) -> PlanBenefits:
        """Beneficios por plan: fuente de verdad, igual que `PlanCatalog` en Android."""
        if plan == "monthly":
            evaluations = self.plan_limit_monthly
        elif plan == "semester":
            evaluations = self.plan_limit_semester
        else:
            return PlanBenefits(self.plan_limit_free, 0, False, 0)
        return PlanBenefits(
            evaluations, self.plan_included_accesses_paid, True, self.agent_daily_cap
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
