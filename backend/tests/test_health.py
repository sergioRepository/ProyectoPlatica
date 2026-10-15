from banano import __version__
from tests.fakes import make_settings


def test_health_reports_ok_and_version(make_client) -> None:
    response = make_client().get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "version": __version__}


def test_docs_are_off_unless_enabled(make_client) -> None:
    assert make_client().get("/docs").status_code == 404
    assert make_client(make_settings(enable_docs=True)).get("/docs").status_code == 200
