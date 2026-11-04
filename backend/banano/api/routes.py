import hmac
from typing import Annotated, Literal
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel

from banano import __version__
from banano.agent.store import StoreError
from banano.api.deps import (
    DeviceDep,
    RateLimiterDep,
    SettingsDep,
    StoreDep,
    client_ip,
    enforce_rate_limit,
    get_agent_llm,
    get_generate_llm,
    get_judge_llm,
    rate_limited,
    require_consent,
)
from banano.auth import sign_device_token
from banano.graphs.agent_graph import run_agent_graph
from banano.graphs.judge_graph import JudgeUnavailableError, run_judge_graph
from banano.graphs.template_graph import TemplateGenerationError, run_template_graph
from banano.llm.client import LLMError, StructuredLLM, ToolLLM
from banano.schemas.agent import AnalyzeRequest, AnalyzeResponse, ConfirmRequest, ConfirmResponse
from banano.schemas.devices import Consents, RegisterRequest, RegisterResponse
from banano.schemas.judge import JudgeRequest, JudgeResponse
from banano.schemas.profile import Profile
from banano.schemas.template import TemplateResponse

router = APIRouter()


class HealthResponse(BaseModel):
    status: Literal["ok"]
    version: str


@router.get("/health")
def health() -> HealthResponse:
    return HealthResponse(status="ok", version=__version__)


@router.post("/devices/register")
async def register_device(
    body: RegisterRequest,
    request: Request,
    settings: SettingsDep,
    limiter: RateLimiterDep,
    store: StoreDep,
) -> RegisterResponse:
    if settings.device_token_secret is None:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Registro sin configurar (falta DEVICE_TOKEN_SECRET).",
        )
    enforce_rate_limit(
        limiter, f"register:{client_ip(request)}", settings.rate_limit_register_per_hour
    )
    if settings.enrollment_code is not None:
        expected = settings.enrollment_code.get_secret_value().encode()
        if not hmac.compare_digest((body.enrollment_code or "").encode(), expected):
            raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Código de registro incorrecto.")
    # Lo genera el servidor: sin código, un id elegido por el cliente daría el token de otro.
    device_id = str(uuid4())
    try:
        await store.save_consents(device_id, body.consents.model_dump())
    except StoreError as exc:
        # Sin la autorización guardada no hay registro.
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudo guardar la autorización."
        ) from exc
    token = sign_device_token(device_id, settings.device_token_secret.get_secret_value())
    return RegisterResponse(device_id=device_id, device_token=token)


ManagedDevice = Annotated[
    str, Depends(rate_limited("device", lambda s: s.rate_limit_device_per_hour))
]


@router.put("/devices/me/consents")
async def update_consents(body: Consents, device_id: ManagedDevice, store: StoreDep) -> Consents:
    try:
        await store.save_consents(device_id, body.model_dump())
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudo guardar la autorización."
        ) from exc
    return body


GenerateDevice = Annotated[
    str, Depends(rate_limited("generate", lambda s: s.rate_limit_generate_per_hour))
]
JudgeDevice = Annotated[str, Depends(rate_limited("judge", lambda s: s.rate_limit_judge_per_hour))]


@router.post("/templates/generate")
async def generate_template(
    profile: Profile,
    device_id: GenerateDevice,
    settings: SettingsDep,
    store: StoreDep,
    llm: Annotated[StructuredLLM, Depends(get_generate_llm)],
) -> TemplateResponse:
    await require_consent(store, device_id, settings)
    try:
        return await run_template_graph(profile, llm, settings)
    except (TemplateGenerationError, LLMError) as exc:
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY,
            detail="No se pudo generar la plantilla. Intenta de nuevo.",
        ) from exc


AgentDevice = Annotated[str, Depends(rate_limited("agent", lambda s: s.rate_limit_agent_per_hour))]


@router.post("/agent/analyze")
async def analyze(
    body: AnalyzeRequest,
    device_id: AgentDevice,
    settings: SettingsDep,
    store: StoreDep,
    llm: Annotated[ToolLLM, Depends(get_agent_llm)],
) -> AnalyzeResponse:
    await require_consent(store, device_id, settings)
    # Si el LLM falla, responde agent_unavailable (200): el teléfono sigue con su bloqueo local.
    return await run_agent_graph(body, device_id, llm, store, settings)


@router.post("/agent/interventions/{event_id}/result")
async def confirm_intervention(
    event_id: UUID, body: ConfirmRequest, device_id: DeviceDep, store: StoreDep
) -> ConfirmResponse:
    try:
        event = await store.confirm(device_id, event_id, body.status, body.error_code)
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudo guardar la confirmación."
        ) from exc
    if event is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Intervención desconocida.")
    return ConfirmResponse(id=event["id"], status=event["status"])


@router.post("/excuses/judge")
async def judge_excuse(
    body: JudgeRequest,
    device_id: JudgeDevice,
    settings: SettingsDep,
    store: StoreDep,
    llm: Annotated[StructuredLLM, Depends(get_judge_llm)],
) -> JudgeResponse:
    await require_consent(store, device_id, settings, excuses=True)
    try:
        return await run_judge_graph(body, llm, settings)
    except JudgeUnavailableError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="El juez no respondió a tiempo. Aplica la regla sin conexión.",
        ) from exc
