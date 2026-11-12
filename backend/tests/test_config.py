import pytest

from banano.config import PlanBenefits, Settings
from tests.fakes import make_settings


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


def test_benefits_free_has_no_paid_extras() -> None:
    settings = make_settings()

    assert settings.benefits("free") == PlanBenefits(settings.plan_limit_free, 0, False, 0)


@pytest.mark.parametrize("plan", ["monthly", "semester"])
def test_benefits_paid_plans_include_accesses_and_auto_review(plan: str) -> None:
    settings = make_settings()

    benefits = settings.benefits(plan)

    assert benefits.included_accesses == settings.plan_included_accesses_paid
    assert benefits.auto_review is True
    assert benefits.agent_daily_cap == settings.agent_daily_cap


def test_benefits_unknown_plan_falls_back_to_free() -> None:
    settings = make_settings()

    assert settings.benefits("inventado") == settings.benefits("free")


def test_benefits_values_come_from_settings_fields() -> None:
    settings = make_settings(
        plan_limit_free=1,
        plan_limit_monthly=7,
        plan_limit_semester=9,
        plan_included_accesses_paid=5,
        agent_daily_cap=11,
    )

    assert settings.benefits("free") == PlanBenefits(1, 0, False, 0)
    assert settings.benefits("monthly") == PlanBenefits(7, 5, True, 11)
    assert settings.benefits("semester") == PlanBenefits(9, 5, True, 11)
