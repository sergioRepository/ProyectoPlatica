from fastapi.testclient import TestClient

from banano import __version__
from banano.main import app


def test_health_reports_ok_and_version() -> None:
    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "version": __version__}
