from fastapi import FastAPI

from banano import __version__
from banano.api.routes import router
from banano.config import Settings, get_settings
from banano.llm.client import StructuredLLM
from banano.ratelimit import RateLimiter


def create_app(
    settings: Settings | None = None,
    *,
    generate_llm: StructuredLLM | None = None,
    judge_llm: StructuredLLM | None = None,
) -> FastAPI:
    """Crea la app. Los tests pasan su configuración y un LLM falso; si no hay LLM, se
    construye el de Grok la primera vez que se necesita."""
    settings = settings or get_settings()
    app = FastAPI(
        title="lavidanoesunbanano-api",
        version=__version__,
        docs_url="/docs" if settings.enable_docs else None,
        redoc_url=None,
        openapi_url="/openapi.json" if settings.enable_docs else None,
    )
    app.state.settings = settings
    app.state.rate_limiter = RateLimiter()
    app.state.generate_llm = generate_llm
    app.state.judge_llm = judge_llm
    app.include_router(router)
    return app


# Punto de entrada para uvicorn y Vercel ([tool.vercel] en pyproject.toml).
app = create_app()
