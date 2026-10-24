import math
from collections.abc import Callable
from typing import Annotated

from fastapi import Depends, Header, HTTPException, Request, status

from banano.agent.store import InterventionStore, PostgresStore, UnconfiguredStore
from banano.auth import verify_device_token
from banano.config import Settings
from banano.llm.client import (
    LLMNotConfiguredError,
    StructuredLLM,
    ToolLLM,
    build_agent_llm,
    build_generate_llm,
    build_judge_llm,
)
from banano.ratelimit import RateLimiter

HOUR_S = 3600.0


def get_settings(request: Request) -> Settings:
    return request.app.state.settings


def get_rate_limiter(request: Request) -> RateLimiter:
    return request.app.state.rate_limiter


SettingsDep = Annotated[Settings, Depends(get_settings)]
RateLimiterDep = Annotated[RateLimiter, Depends(get_rate_limiter)]


def client_ip(request: Request) -> str:
    """En Vercel la IP real llega en X-Forwarded-For."""
    forwarded = request.headers.get("x-forwarded-for", "").split(",")[0].strip()
    if forwarded:
        return forwarded
    return request.client.host if request.client else "desconocida"


def enforce_rate_limit(limiter: RateLimiter, key: str, limit: int) -> None:
    retry_after = limiter.hit(key, limit, HOUR_S)
    if retry_after is not None:
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Demasiadas peticiones. Espera un rato.",
            headers={"Retry-After": str(math.ceil(retry_after))},
        )


def require_device(
    settings: SettingsDep, authorization: Annotated[str | None, Header()] = None
) -> str:
    """Devuelve el id del dispositivo dueño del token."""
    if settings.device_token_secret is None:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Registro de dispositivos sin configurar (falta DEVICE_TOKEN_SECRET).",
        )
    scheme, _, token = (authorization or "").partition(" ")
    device_id = None
    if scheme.lower() == "bearer" and token:
        device_id = verify_device_token(
            token.strip(), settings.device_token_secret.get_secret_value()
        )
    if device_id is None:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            detail="Token de dispositivo inválido o ausente.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return device_id


DeviceDep = Annotated[str, Depends(require_device)]


def rate_limited(bucket: str, limit_of: Callable[[Settings], int]) -> Callable[..., str]:
    """Dependencia: autentica el dispositivo y cuenta la petición en su cupo por hora."""

    def dependency(device_id: DeviceDep, settings: SettingsDep, limiter: RateLimiterDep) -> str:
        enforce_rate_limit(limiter, f"{bucket}:{device_id}", limit_of(settings))
        return device_id

    return dependency


def _llm_from_state(
    request: Request, attribute: str, build: Callable[[Settings], StructuredLLM | ToolLLM]
) -> StructuredLLM | ToolLLM:
    llm = getattr(request.app.state, attribute)
    if llm is None:
        try:
            llm = build(request.app.state.settings)
        except LLMNotConfiguredError as exc:
            raise HTTPException(
                status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"LLM sin configurar: faltan {', '.join(exc.missing)}.",
            ) from exc
        setattr(request.app.state, attribute, llm)
    return llm


def get_generate_llm(request: Request) -> StructuredLLM:
    return _llm_from_state(request, "generate_llm", build_generate_llm)  # type: ignore[return-value]


def get_judge_llm(request: Request) -> StructuredLLM:
    return _llm_from_state(request, "judge_llm", build_judge_llm)  # type: ignore[return-value]


def get_agent_llm(request: Request) -> ToolLLM:
    return _llm_from_state(request, "agent_llm", build_agent_llm)  # type: ignore[return-value]


def get_store(request: Request) -> InterventionStore:
    store = request.app.state.store
    if store is None:
        settings: Settings = request.app.state.settings
        store = (
            PostgresStore(settings.database_url.get_secret_value(), settings.db_timeout_s)
            if settings.database_url
            else UnconfiguredStore()
        )
        request.app.state.store = store
    return store
