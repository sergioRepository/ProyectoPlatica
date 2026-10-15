import threading
import time
from collections import deque
from collections.abc import Callable


class RateLimiter:
    """Ventana deslizante en memoria por clave.

    En Vercel cada instancia lleva su propia cuenta, así que el límite es aproximado; para
    una app de una sola persona basta (decisión aceptada en la Fase 1).
    """

    def __init__(self, clock: Callable[[], float] = time.monotonic, max_keys: int = 10_000):
        self._clock = clock
        self._max_keys = max_keys
        self._hits: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def hit(self, key: str, limit: int, window_s: float) -> float | None:
        """Registra un intento. Devuelve None si pasa, o los segundos que faltan si no.

        Un límite de 0 o menos desactiva el control.
        """
        if limit <= 0:
            return None
        now = self._clock()
        with self._lock:
            hits = self._hits.setdefault(key, deque())
            while hits and now - hits[0] >= window_s:
                hits.popleft()
            if len(hits) >= limit:
                return window_s - (now - hits[0])
            hits.append(now)
            if len(self._hits) > self._max_keys:
                self._evict(now, window_s)
            return None

    def _evict(self, now: float, window_s: float) -> None:
        stale = [k for k, hits in self._hits.items() if not hits or now - hits[-1] >= window_s]
        for key in stale:
            del self._hits[key]
