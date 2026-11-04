"""Medición del consumo de Grok: una fila en llm_usage por llamada.

Los clientes de Grok se comparten entre peticiones, así que el dispositivo y el grafo llegan
por un ContextVar. Sin medidor activo (Studio, scripts) las llamadas no dejan registro.

Las filas se escriben al final de la petición y no después de responder: Vercel puede congelar
la función en cuanto sale la respuesta. Por eso la escritura tiene su propio límite de tiempo y
nunca falla hacia afuera.
"""

import asyncio
import logging
import time
from collections.abc import AsyncIterator, Iterator
from contextlib import asynccontextmanager, contextmanager
from contextvars import ContextVar
from dataclasses import dataclass, field
from typing import Any
from uuid import UUID, uuid4

from banano.agent.store import DeviceStore, StoreError
from banano.config import ModelPrice, Settings

logger = logging.getLogger(__name__)


def tokens_of(usage: dict[str, Any] | None) -> tuple[int | None, int | None]:
    """xAI cuenta el razonamiento aparte de completion_tokens y lo cobra como salida, así que
    la salida se saca del total: en un modelo que razona, output_tokens se queda corto."""
    if not usage:
        return None, None
    used_in, used_out = usage.get("input_tokens", 0), usage.get("output_tokens", 0)
    return used_in, max(used_out, usage.get("total_tokens", 0) - used_in)


def cost_usd(price: ModelPrice | None, used_in: int | None, used_out: int | None) -> float | None:
    if price is None or used_in is None or used_out is None:
        return None
    return round(
        (used_in * price.input_per_million + used_out * price.output_per_million) / 1_000_000, 6
    )


@dataclass
class UsageMeter:
    device_id: str
    plan: str | None
    graph: str
    request_id: UUID
    prices: dict[str, ModelPrice]
    rows: list[dict[str, Any]] = field(default_factory=list)

    def add(
        self, node: str, model: str, usage: dict[str, Any] | None, latency_ms: int, status: str
    ) -> None:
        used_in, used_out = tokens_of(usage)
        self.rows.append(
            {
                "id": uuid4(),
                "device_id": self.device_id,
                "plan": self.plan,
                "graph": self.graph,
                "node": node,
                "model": model,
                "input_tokens": used_in,
                "output_tokens": used_out,
                "cost_usd": cost_usd(self.prices.get(model), used_in, used_out),
                "latency_ms": latency_ms,
                "status": status,
                "request_id": self.request_id,
            }
        )


_meter: ContextVar[UsageMeter | None] = ContextVar("usage_meter", default=None)


class TrackedCall:
    """Lo que el cliente anota de una llamada mientras ocurre."""

    raw: Any = None
    status: str = "error"


@contextmanager
def tracked_call(node: str, model: str) -> Iterator[TrackedCall]:
    """Mide una llamada a Grok. Falla en "error" salvo que el cliente la marque "ok"."""
    call = TrackedCall()
    start = time.monotonic()
    try:
        yield call
    except TimeoutError:
        call.status = "timeout"
        raise
    finally:
        meter = _meter.get()
        if meter is not None:
            usage = getattr(call.raw, "usage_metadata", None)
            elapsed = int((time.monotonic() - start) * 1000)
            meter.add(node, model, usage, elapsed, call.status)


@asynccontextmanager
async def metering(
    store: DeviceStore,
    settings: Settings,
    device_id: str,
    device: dict[str, Any] | None,
    graph: str,
    request_id: UUID | None = None,
) -> AsyncIterator[None]:
    """Registra las llamadas a Grok del bloque. Fallar aquí no afecta la respuesta."""
    meter = UsageMeter(
        device_id=device_id,
        plan=device["plan"] if device else None,
        graph=graph,
        request_id=request_id or uuid4(),
        prices=settings.llm_prices,
    )
    token = _meter.set(meter)
    try:
        yield
    finally:
        _meter.reset(token)
        if meter.rows:
            try:
                await asyncio.wait_for(
                    store.record_usage(meter.rows), settings.usage_write_timeout_s
                )
            except (StoreError, TimeoutError) as exc:
                logger.warning("No se pudo registrar el uso del LLM: %s", type(exc).__name__)
