import pytest

from banano.config import Settings


def test_empty_variables_count_as_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ENROLLMENT_CODE", "")
    monkeypatch.setenv("DEVICE_TOKEN_SECRET", "")
    monkeypatch.setenv("XAI_API_KEY", "")

    settings = Settings(_env_file=None)

    assert settings.enrollment_code is None
    assert settings.device_token_secret is None
    assert settings.xai_api_key is None


def test_variables_are_read_from_the_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("XAI_BASE_URL", "https://gateway.invalid/v1")
    monkeypatch.setenv("GROK_MODEL_JUDGE", "modelo-juez-de-prueba")
    monkeypatch.setenv("LLM_STRUCTURED_METHOD", "json_mode")

    settings = Settings(_env_file=None)

    assert settings.xai_base_url == "https://gateway.invalid/v1"
    assert settings.grok_model_judge == "modelo-juez-de-prueba"
    assert settings.llm_structured_method == "json_mode"
