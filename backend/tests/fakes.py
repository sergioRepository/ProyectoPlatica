"""Dobles de prueba y datos de ejemplo. Los tests nunca llaman a Grok real."""

from typing import Any
from uuid import uuid4

from langchain_core.messages import AIMessage, BaseMessage

from banano.agent.store import MemoryStore  # noqa: F401 (los tests la importan de aquí)
from banano.auth import sign_device_token
from banano.config import Settings
from banano.graphs.judge_graph import JudgeEvaluation
from banano.graphs.template_graph import DraftBatch, DraftMessage, ProfileAnalysis
from banano.schemas.agent import AnalyzeRequest
from banano.schemas.judge import JudgeRequest
from banano.schemas.profile import Profile

TEST_SECRET = "secreto-de-prueba-largo-y-aleatorio"
TEST_ENROLLMENT_CODE = "codigo-de-prueba"
TEST_DEVICE_ID = "dispositivo-de-prueba-01"

GOOD_MESSAGES = [
    "¿{app} otra vez, {nombre}? Así piensas ser {meta}. Qué mierda.",
    "Van {intentos_hoy} intentos hoy. Los grandes programadores también viven en {app}. Mentira.",
    "Son las {hora} y tu código sigue sin compilar mientras haces scroll.",
    "Llevas {minutos_hoy} minutos aquí. Con eso terminabas el ejercicio de algoritmos.",
    "Afuera hay un parque y un sol que no necesitan wifi, {nombre}.",
    "Dijiste solo cinco minutos hace tres excusas. {meta} no se construye en {app}.",
    "Felicitaciones: nuevo récord de aperturas de {app}. Lástima que no dan premio.",
    "Tu teclado te extraña, {nombre}. {app} no.",
]


class FakeLLM:
    """Responde según el esquema pedido.

    Cada respuesta puede ser un objeto o una excepción, o una lista de ellos: una por
    llamada, y la última se repite.
    """

    def __init__(self, responses: dict[type, Any]) -> None:
        self._responses = {
            schema: list(value) if isinstance(value, list) else [value]
            for schema, value in responses.items()
        }
        self.calls: list[tuple[type, str, str]] = []

    async def generate(self, schema: type, *, system: str, user: str, timeout: float) -> Any:
        self.calls.append((schema, system, user))
        queue = self._responses[schema]
        value = queue.pop(0) if len(queue) > 1 else queue[0]
        if isinstance(value, BaseException):
            raise value
        return value

    def count(self, schema: type) -> int:
        return sum(1 for called, _, _ in self.calls if called is schema)


def tool_call(name: str, **args: Any) -> dict[str, Any]:
    return {"name": name, "args": args}


class FakeToolLLM:
    """Un turno por respuesta: cada una es una lista de tool_calls o una excepción."""

    def __init__(self, *turns: list[dict[str, Any]] | BaseException) -> None:
        self._turns = list(turns)
        self.seen: list[list[BaseMessage]] = []

    async def step(
        self, messages: list[BaseMessage], tools: list[Any], *, timeout: float
    ) -> AIMessage:
        self.seen.append(list(messages))
        turn = self._turns.pop(0)
        if isinstance(turn, BaseException):
            raise turn
        calls = [{**call, "id": f"c{len(self.seen)}-{i}"} for i, call in enumerate(turn)]
        return AIMessage(content="", tool_calls=calls)

    @property
    def turns(self) -> int:
        return len(self.seen)


def make_settings(**overrides: Any) -> Settings:
    values: dict[str, Any] = {
        "xai_base_url": "https://gateway.invalid/v1",
        "xai_api_key": "pk_prueba",
        "grok_model_generate": "modelo-generador-de-prueba",
        "grok_model_judge": "modelo-juez-de-prueba",
        "enrollment_code": TEST_ENROLLMENT_CODE,
        "device_token_secret": TEST_SECRET,
        "template_target_messages": 6,
        "template_min_messages": 4,
        "template_max_retries": 2,
        "template_min_acceptable": 1,
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


def auth_headers(device_id: str = TEST_DEVICE_ID, secret: str = TEST_SECRET) -> dict[str, str]:
    return {"Authorization": f"Bearer {sign_device_token(device_id, secret)}"}


def sample_profile(**overrides: Any) -> Profile:
    values: dict[str, Any] = {
        "name": "Alex",
        "main_goal": "ser el mejor programador del mundo",
        "secondary_goals": ["leer un libro al mes"],
        "goal_reason": "Quiero vivir de construir software",
        "blocked_apps": ["Instagram", "TikTok"],
        "schedule_summary": "lunes a viernes de 08:00 a 18:00",
        "typical_excuses": "solo cinco minutos, ya casi termino",
        "sarcasm_level": 4,
        "profanity_allowed": True,
        "country": "Colombia",
        "forbidden_topics": "mi exnovia",
    }
    values.update(overrides)
    return Profile(**values)


def sample_analysis(summary: str = "Quiere ser el mejor programador del mundo.") -> ProfileAnalysis:
    return ProfileAnalysis(
        angles=["Dice que quiere programar pero vive en Instagram"],
        real_life=["Un parque a dos cuadras"],
        summary=summary,
    )


def drafts(texts: list[str], context: str = "general", intensity: int = 4) -> list[DraftMessage]:
    return [DraftMessage(text=text, context=context, intensity=intensity) for text in texts]


def batch(
    texts: list[str], approved: list[str] | None = None, rejected: list[str] | None = None
) -> DraftBatch:
    return DraftBatch(
        messages=drafts(texts),
        verdict_approved=approved if approved is not None else ["Vale, {nombre}. Úsalo bien."],
        verdict_rejected=rejected if rejected is not None else ["No, {nombre}. Vuelve a lo tuyo."],
    )


def judge_request(**overrides: Any) -> JudgeRequest:
    values: dict[str, Any] = {
        "type": "extra_time",
        "reason": "Tengo que responderle al cliente que me escribió por Instagram",
        "app": "Instagram",
        "profile_summary": "Quiere ser el mejor programador del mundo.",
        "attempts_today": 3,
        "extra_time_granted_today": 0,
        "recent_excuses": [],
        "local_time": "21:30",
    }
    values.update(overrides)
    return JudgeRequest(**values)


NOW_MS = 1_800_000_000_000
MINUTE_MS = 60_000


def usage_session(package: str, label: str, minutes_ago: float, minutes: float) -> dict[str, Any]:
    return {
        "package": package,
        "label": label,
        "start_ms": int(NOW_MS - minutes_ago * MINUTE_MS),
        "minutes": minutes,
    }


def analyze_request(**overrides: Any) -> AnalyzeRequest:
    """Andrés: estudia Java y abre TikTok una y otra vez."""
    values: dict[str, Any] = {
        "request_id": uuid4(),
        "trigger": "manual",
        "local_time": "16:10",
        "main_goal": "aprender Java",
        "blocked_apps": [{"package": "com.zhiliaoapp.musically", "label": "TikTok"}],
        "usage": {
            "status": "ok",
            "collected_at_ms": NOW_MS,
            "sessions": [
                usage_session("com.zhiliaoapp.musically", "TikTok", 55, 12),
                usage_session("com.zhiliaoapp.musically", "TikTok", 30, 20),
                usage_session("com.android.chrome", "Chrome", 5, 4),
            ],
        },
    }
    values.update(overrides)
    return AnalyzeRequest(**values)


def evaluation(**overrides: Any) -> JudgeEvaluation:
    values: dict[str, Any] = {
        "valid": True,
        "confidence": 0.8,
        "reason_code": "legit_work",
        "is_crisis": False,
        "message": "Vale, te creo. Diez minutos y vuelves.",
    }
    values.update(overrides)
    return JudgeEvaluation(**values)
