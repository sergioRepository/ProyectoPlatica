from collections.abc import Callable
from typing import Any

import pytest
from fastapi.testclient import TestClient

from banano.config import Settings
from banano.main import create_app
from tests.fakes import make_settings


@pytest.fixture
def make_client() -> Callable[..., TestClient]:
    """Crea un cliente con configuración de prueba; nunca lee backend/.env."""

    def factory(
        settings: Settings | None = None,
        generate_llm: Any = None,
        judge_llm: Any = None,
    ) -> TestClient:
        app = create_app(
            settings or make_settings(), generate_llm=generate_llm, judge_llm=judge_llm
        )
        return TestClient(app)

    return factory
