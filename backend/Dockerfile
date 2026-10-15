# Imagen para desarrollo local. En producción el backend corre en Vercel, que no usa Docker.
FROM python:3.12-slim

COPY --from=ghcr.io/astral-sh/uv:0.13.0 /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    PYTHONUNBUFFERED=1

WORKDIR /srv

COPY pyproject.toml uv.lock .python-version ./
RUN uv sync --frozen --no-dev

COPY banano ./banano

RUN useradd --create-home --uid 10001 api
USER api

ENV PATH="/srv/.venv/bin:$PATH"
EXPOSE 8000
CMD ["uvicorn", "banano.main:app", "--host", "0.0.0.0", "--port", "8000"]
