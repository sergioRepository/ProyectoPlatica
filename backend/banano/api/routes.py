import asyncio
import hmac
import logging
from collections.abc import Awaitable
from datetime import date, timedelta
from typing import Annotated, Any, Literal
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response, status
from pydantic import BaseModel

from banano import __version__
from banano.agent.store import Store, StoreError, utcnow
from banano.api.deps import (
    DeviceDep,
    PlayDep,
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
from banano.billing import (
    BillingError,
    PlayNotFoundError,
    PlayUnavailableError,
    decode_notification,
    handle_notification,
    verify_credits,
    verify_push,
    verify_subscription,
)
from banano.config import Settings
from banano.graphs.agent_graph import run_agent_graph
from banano.graphs.judge_graph import QUOTA_LINES, JudgeUnavailableError, run_judge_graph
from banano.graphs.template_graph import TemplateGenerationError, run_template_graph
from banano.llm.client import LLMError, StructuredLLM, ToolLLM
from banano.llm.usage import metering
from banano.quota import QuotaReason, bump, check_judge, plan_limit, quota_day, take_agent_call
from banano.schemas.agent import AnalyzeRequest, AnalyzeResponse, ConfirmRequest, ConfirmResponse
from banano.schemas.devices import (
    Consents,
    DeviceStatus,
    PlanRequest,
    RegisterRequest,
    RegisterResponse,
    VerifyRequest,
    VerifyResponse,
)
from banano.schemas.events import EventBatch, EventsAccepted
from banano.schemas.judge import JudgeRequest, JudgeResponse
from banano.schemas.profile import Profile
from banano.schemas.reports import ContentReport
from banano.schemas.template import TemplateResponse

router = APIRouter()
logger = logging.getLogger(__name__)


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


# Sin límite por hora: revocar una autorización o borrar los datos nunca debe dar 429.
@router.put("/devices/me/consents")
async def update_consents(body: Consents, device_id: DeviceDep, store: StoreDep) -> Consents:
    try:
        await store.save_consents(device_id, body.model_dump())
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudo guardar la autorización."
        ) from exc
    return body


def device_status(device: dict[str, Any], settings: Settings) -> DeviceStatus:
    limit = plan_limit(settings, device["plan"])
    benefits = settings.benefits(device["plan"])
    return DeviceStatus(
        plan=device["plan"],
        would_pay=device["would_pay"],
        daily_limit=limit,
        used_today=device["llm_calls"],
        remaining_today=max(limit - device["llm_calls"], 0),
        consent_excuses=device["consent_excuses"],
        consent_analytics=device["consent_analytics"],
        subscription_state=device["subscription_state"],
        expiry_time=device["expiry_time"],
        auto_renewing=device["auto_renewing"],
        credits=device["credits"],
        included_daily=benefits.included_accesses,
        included_remaining=max(benefits.included_accesses - device["included_used"], 0),
        agent_enabled=benefits.auto_review,
    )


async def read_status(store: Store, device_id: str, settings: Settings) -> DeviceStatus:
    try:
        device = await store.get_device(device_id, quota_day(settings))
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudo leer el dispositivo."
        ) from exc
    if device is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Dispositivo desconocido.")
    return device_status(device, settings)


@router.get("/devices/me")
async def get_device_status(
    device_id: ManagedDevice, settings: SettingsDep, store: StoreDep
) -> DeviceStatus:
    return await read_status(store, device_id, settings)


@router.put("/devices/me/plan")
async def update_plan(
    body: PlanRequest, device_id: ManagedDevice, settings: SettingsDep, store: StoreDep
) -> DeviceStatus:
    """El plan lo decide solo el servidor a partir del derecho validado: `body.plan` se
    ignora y solo se guarda la respuesta de «¿pagarías?»."""
    try:
        known = await store.set_would_pay(device_id, body.would_pay)
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudo guardar la respuesta."
        ) from exc
    if not known:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Dispositivo desconocido.")
    return await read_status(store, device_id, settings)


@router.post("/events")
async def receive_events(
    body: EventBatch, device_id: ManagedDevice, settings: SettingsDep, store: StoreDep
) -> EventsAccepted:
    try:
        device = await store.get_device(device_id, quota_day(settings))
        # Sin la autorización no se guarda nada, aunque el teléfono lo envíe.
        if device is None or not device["consent_analytics"]:
            return EventsAccepted(accepted=0)
        await store.save_events(device_id, [event.model_dump() for event in body.events])
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudieron guardar los eventos."
        ) from exc
    return EventsAccepted(accepted=len(body.events))


@router.post("/reports", status_code=status.HTTP_201_CREATED)
async def report_content(body: ContentReport, device_id: ManagedDevice, store: StoreDep) -> None:
    try:
        await store.save_report(device_id, body.model_dump())
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudo guardar el reporte."
        ) from exc


@router.delete("/devices/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_device(device_id: DeviceDep, store: StoreDep) -> None:
    try:
        await store.delete_device(device_id)
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se pudieron borrar los datos."
        ) from exc


@router.get("/internal/retention", include_in_schema=False)
async def apply_retention(
    settings: SettingsDep,
    store: StoreDep,
    authorization: Annotated[str | None, Header()] = None,
) -> dict[str, bool]:
    """Lo llama a diario el cron de Vercel, que envía CRON_SECRET como token."""
    if settings.cron_secret is None:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail="Falta CRON_SECRET.")
    expected = f"Bearer {settings.cron_secret.get_secret_value()}".encode()
    if not hmac.compare_digest((authorization or "").encode(), expected):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="No autorizado.")
    now = utcnow()
    daily = timedelta(days=settings.retention_daily_usage_days)
    try:
        await store.purge(
            now - timedelta(days=settings.retention_days),
            now - timedelta(days=settings.retention_reports_days),
            quota_day(settings) - daily,
        )
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, detail="No se aplicó la retención."
        ) from exc
    return {"ok": True}


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
    device = await require_consent(store, device_id, settings)
    if device is not None and device["templates"] >= settings.daily_template_cap:
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS, detail="Ya generaste tus mensajes hoy."
        )
    try:
        async with metering(store, settings, device_id, device, "template"):
            template = await run_template_graph(profile, llm, settings)
    except (TemplateGenerationError, LLMError) as exc:
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY,
            detail="No se pudo generar la plantilla. Intenta de nuevo.",
        ) from exc
    # Solo cuenta si salió bien: quien recién llega no se queda sin plantilla por un fallo.
    await bump(store, device_id, settings, "templates")
    return template


AgentDevice = Annotated[str, Depends(rate_limited("agent", lambda s: s.rate_limit_agent_per_hour))]


@router.post("/agent/analyze")
async def analyze(
    body: AnalyzeRequest,
    device_id: AgentDevice,
    settings: SettingsDep,
    store: StoreDep,
    llm: Annotated[ToolLLM, Depends(get_agent_llm)],
) -> AnalyzeResponse:
    device = await require_consent(store, device_id, settings)

    async def quota() -> QuotaReason | None:
        return await take_agent_call(store, device, settings)

    # Si el LLM falla, responde agent_unavailable (200): el teléfono sigue con su bloqueo local.
    async with metering(store, settings, device_id, device, "agent", body.request_id):
        return await run_agent_graph(body, device_id, llm, store, settings, quota)


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
    device = await require_consent(store, device_id, settings, excuses=True)

    # Sin saldo verificable no se regalan minutos; las emergencias no dependen de él.
    if device is None and body.type == "extra_time":
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="No se pudo comprobar tu saldo. Aplica la regla sin conexión.",
        )

    day = quota_day(settings)
    # El mismo pedido repetido (se perdió la respuesta) no se evalúa ni se cobra otra vez.
    ref = f"{device_id}:{body.request_id or uuid4()}"
    if body.type == "extra_time" and body.request_id is not None:
        paid = await _safe(store.access_source(device_id, ref))
        if paid is not None:
            return await _with_balance(
                JudgeResponse(
                    valid=True,
                    confidence=1.0,
                    granted_minutes=settings.extra_time_minutes,
                    message="Ya estaba aprobado.",
                    reason_code="legit_work",
                    access_source=paid,
                ),
                store,
                device_id,
                day,
                settings,
            )

    async def quota() -> QuotaReason | None:
        return check_judge(device, settings, body.type)

    async def evaluated() -> None:
        # shortcut: comprobar y contar por separado permite pasarse por una o dos con peticiones
        # simultáneas; el límite por hora lo acota; pasarlo a una sola sentencia si se nota.
        await bump(store, device_id, settings, "llm_calls")

    try:
        async with metering(store, settings, device_id, device, "judge"):
            verdict = await run_judge_graph(body, llm, settings, quota, evaluated)
    except JudgeUnavailableError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="El juez no respondió a tiempo. Aplica la regla sin conexión.",
        ) from exc
    if verdict.reason_code == "boredom":
        await bump(store, device_id, settings, "rejections")
    # Solo un acceso de 10 minutos aprobado por Peel se paga; una crisis nunca se cobra.
    if body.type != "extra_time" or not verdict.valid or verdict.reason_code not in PAID_CODES:
        return verdict
    assert device is not None  # la base caída ya respondió 503 arriba
    included = settings.benefits(device["plan"]).included_accesses
    source = await _safe(store.spend_access(device_id, ref, day, included))
    if source is None:
        # Otro pedido gastó el último crédito mientras Peel evaluaba.
        return JudgeResponse(
            valid=False,
            confidence=1.0,
            granted_minutes=0,
            message=QUOTA_LINES["no_credits"][0],
            reason_code="no_credits",
        )
    return await _with_balance(
        verdict.model_copy(update={"access_source": source}), store, device_id, day, settings
    )


PAID_CODES = ("legit_work", "legit_emergency")


async def _safe[T](operation: Awaitable[T]) -> T:
    """Una escritura de dinero que falla no regala ni cobra: la app aplica la regla sin conexión."""
    try:
        return await operation
    except StoreError as exc:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="No se pudo cobrar el acceso. Aplica la regla sin conexión.",
        ) from exc


async def _with_balance(
    verdict: JudgeResponse, store: Store, device_id: str, day: date, settings: Settings
) -> JudgeResponse:
    """Añade el saldo que queda tras pagar. Si la base no responde, se omite: ya se pagó."""
    try:
        device = await store.get_device(device_id, day, settings.gate_db_timeout_s)
    except StoreError:
        return verdict
    if device is None:
        return verdict
    included = settings.benefits(device["plan"]).included_accesses
    return verdict.model_copy(
        update={
            "credits": device["credits"],
            "included_remaining": max(included - device["included_used"], 0),
        }
    )


BillingDevice = Annotated[
    str, Depends(rate_limited("billing", lambda s: s.rate_limit_device_per_hour))
]


@router.post("/billing/verify")
async def verify_purchase(
    body: VerifyRequest,
    device_id: BillingDevice,
    settings: SettingsDep,
    store: StoreDep,
    client: PlayDep,
) -> VerifyResponse:
    """Valida con Google una compra que la app vio y la aplica. El único camino a un plan de
    pago o a créditos: el teléfono nunca decide."""
    day = quota_day(settings)
    try:
        if await store.get_device(device_id, day) is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Dispositivo desconocido.")
        if body.product_id == settings.play_subscription_id:
            entitlement = await verify_subscription(
                store, client, settings, device_id, body.purchase_token, day
            )
            pending = entitlement["state"] == "SUBSCRIPTION_STATE_PENDING"
            outcome = "pending" if pending else "subscription"
        else:
            outcome = await verify_credits(
                store, client, settings, device_id, body.product_id, body.purchase_token, day
            )
    except BillingError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, detail=str(exc)) from exc
    except PlayNotFoundError as exc:
        raise HTTPException(
            status.HTTP_404_NOT_FOUND, detail="Google Play no reconoce esa compra."
        ) from exc
    except (PlayUnavailableError, StoreError) as exc:
        logger.warning("No se pudo validar una compra: %s", type(exc).__name__)
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="No se pudo validar la compra. Se reintenta al volver a abrir la app.",
        ) from exc
    return VerifyResponse(outcome=outcome, device=await read_status(store, device_id, settings))


@router.post("/billing/rtdn", status_code=status.HTTP_204_NO_CONTENT, include_in_schema=False)
async def receive_rtdn(
    request: Request,
    settings: SettingsDep,
    store: StoreDep,
    client: PlayDep,
    authorization: Annotated[str | None, Header()] = None,
) -> Response:
    """Push de Pub/Sub con las notificaciones de Google Play. 2xx acusa recibo; cualquier otro
    código hace que Pub/Sub reintente."""
    if not settings.rtdn_audience or not settings.rtdn_service_account_email:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail="RTDN sin configurar.")
    try:
        await asyncio.to_thread(verify_push, authorization, settings)
    except Exception as exc:  # firma, audiencia, emisor o remitente incorrectos
        logger.warning("Push de RTDN rechazado: %s", type(exc).__name__)
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="No autorizado.") from exc
    try:
        notification = decode_notification(await request.json())
    except ValueError:
        # Reintentar un mensaje mal formado no lo arregla: se acusa y se descarta.
        logger.warning("Push de RTDN mal formado")
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    try:
        day = quota_day(settings)
        result = await handle_notification(notification, store, client, settings, day)
    except BillingError as exc:
        logger.warning("RTDN de una compra que no es de esta app: %s", exc)
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    except (PlayUnavailableError, StoreError) as exc:
        logger.warning("RTDN sin aplicar, Pub/Sub reintenta: %s", type(exc).__name__)
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail="Reintenta.") from exc
    logger.info("RTDN aplicada: %s", result)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
