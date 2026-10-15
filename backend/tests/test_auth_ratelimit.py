from banano.auth import sign_device_token, verify_device_token
from banano.ratelimit import RateLimiter

SECRET = "un-secreto"
DEVICE = "abcdefghijklmnop1234"


def test_token_round_trip() -> None:
    assert verify_device_token(sign_device_token(DEVICE, SECRET), SECRET) == DEVICE


def test_token_rejects_wrong_secret_and_tampering() -> None:
    token = sign_device_token(DEVICE, SECRET)
    version, device, signature = token.split(".")

    assert verify_device_token(token, "otro-secreto") is None
    assert verify_device_token(f"{version}.{'z' * 20}.{signature}", SECRET) is None
    assert verify_device_token(f"v0.{device}.{signature}", SECRET) is None
    assert verify_device_token("no-es-un-token", SECRET) is None
    assert verify_device_token(f"{version}.corto.{signature}", SECRET) is None


class FakeClock:
    def __init__(self) -> None:
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now


def test_rate_limiter_sliding_window() -> None:
    clock = FakeClock()
    limiter = RateLimiter(clock=clock)

    assert limiter.hit("juez:a", limit=2, window_s=60) is None
    assert limiter.hit("juez:a", limit=2, window_s=60) is None
    clock.now += 10
    assert limiter.hit("juez:a", limit=2, window_s=60) == 50
    assert limiter.hit("juez:b", limit=2, window_s=60) is None

    clock.now += 51
    assert limiter.hit("juez:a", limit=2, window_s=60) is None


def test_rate_limiter_zero_means_unlimited() -> None:
    limiter = RateLimiter()
    assert all(limiter.hit("k", limit=0, window_s=60) is None for _ in range(100))


def test_rate_limiter_evicts_old_keys() -> None:
    clock = FakeClock()
    limiter = RateLimiter(clock=clock, max_keys=2)
    limiter.hit("a", limit=5, window_s=60)
    clock.now += 61
    limiter.hit("b", limit=5, window_s=60)
    limiter.hit("c", limit=5, window_s=60)

    assert "a" not in limiter._hits
