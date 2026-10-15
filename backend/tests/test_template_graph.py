import pytest

from banano.graphs.template_graph import (
    DEFAULT_REJECTED,
    DraftBatch,
    ProfileAnalysis,
    TemplateGenerationError,
    chunk_counts,
    personalize_literals,
    run_template_graph,
)
from banano.llm.client import LLMError
from banano.schemas.template import MESSAGE_CONTEXTS
from tests.fakes import (
    GOOD_MESSAGES,
    FakeLLM,
    batch,
    make_settings,
    sample_analysis,
    sample_profile,
)


async def test_generates_template_without_retry_when_enough_messages() -> None:
    llm = FakeLLM({ProfileAnalysis: sample_analysis(), DraftBatch: batch(GOOD_MESSAGES[:6])})

    result = await run_template_graph(sample_profile(), llm, make_settings())

    assert llm.count(ProfileAnalysis) == 1
    assert llm.count(DraftBatch) == 1
    assert [m.id for m in result.messages] == ["m01", "m02", "m03", "m04", "m05", "m06"]
    assert [m.text for m in result.messages] == GOOD_MESSAGES[:6]
    assert all(m.context in MESSAGE_CONTEXTS for m in result.messages)
    assert result.profile_summary == "Quiere ser el mejor programador del mundo."


async def test_review_drops_what_breaks_the_rules() -> None:
    bad = [
        "¿{app} otra vez, {nombre}? Así piensas ser {meta}. Qué mierda!",  # repetido
        "Tu exnovia sí terminó sus proyectos, {nombre}.",  # tema prohibido por el usuario
        "Si sigues en {app}, mejor ni existir.",  # guardarraíl global
        "Otra vez en {app}. #FracasoTotal",  # hashtag
        "Otra vez {app}, {nombre} :)",  # emoticono
        "A tus {edad} años sigues en {app}.",  # placeholder no permitido
        " ".join(["palabra"] * 26) + " {app}",  # más de 25 palabras
        "La disciplina es la clave del éxito.",  # genérico
    ]
    with_emoji = "Tu proyecto llora 😢 mientras abres {app}."
    llm = FakeLLM(
        {
            ProfileAnalysis: sample_analysis(),
            DraftBatch: batch(GOOD_MESSAGES[:4] + bad + [with_emoji]),
        }
    )

    result = await run_template_graph(sample_profile(), llm, make_settings())

    assert [m.text for m in result.messages] == GOOD_MESSAGES[:4] + [
        "Tu proyecto llora mientras abres {app}."
    ]
    assert llm.count(DraftBatch) == 1


async def test_retries_when_too_few_messages_survive() -> None:
    llm = FakeLLM(
        {
            ProfileAnalysis: sample_analysis(),
            DraftBatch: [batch(GOOD_MESSAGES[:2]), batch(GOOD_MESSAGES[2:5])],
        }
    )

    result = await run_template_graph(sample_profile(), llm, make_settings())

    assert llm.count(DraftBatch) == 2
    assert len(result.messages) == 5
    retry_prompt = llm.calls[-1][2]
    assert GOOD_MESSAGES[0] in retry_prompt
    assert "Deja verdict_approved y verdict_rejected vacíos." in retry_prompt


async def test_stops_after_max_retries() -> None:
    llm = FakeLLM({ProfileAnalysis: sample_analysis(), DraftBatch: batch(GOOD_MESSAGES[:1])})

    result = await run_template_graph(sample_profile(), llm, make_settings())

    assert llm.count(DraftBatch) == 3  # la primera vez y dos reintentos
    assert len(result.messages) == 1


async def test_fails_when_below_minimum_acceptable() -> None:
    llm = FakeLLM({ProfileAnalysis: sample_analysis(), DraftBatch: batch(GOOD_MESSAGES[:1])})

    with pytest.raises(TemplateGenerationError):
        await run_template_graph(sample_profile(), llm, make_settings(template_min_acceptable=2))


async def test_verdict_lines_are_cleaned_and_have_defaults() -> None:
    llm = FakeLLM(
        {
            ProfileAnalysis: sample_analysis(summary="Quiere programar 🚀 y no puede #foco"),
            DraftBatch: batch(
                GOOD_MESSAGES[:6], approved=["Vale 👍, 10 minutos.", "#Aprobado", ""], rejected=[]
            ),
        }
    )

    result = await run_template_graph(sample_profile(), llm, make_settings())

    assert result.verdict_lines.approved == ["Vale, 10 minutos."]
    assert result.verdict_lines.rejected == list(DEFAULT_REJECTED)
    assert result.profile_summary == "Quiere programar y no puede"


async def test_respects_the_total_time_budget() -> None:
    llm = FakeLLM({ProfileAnalysis: sample_analysis(), DraftBatch: batch(GOOD_MESSAGES[:6])})

    with pytest.raises(LLMError):
        await run_template_graph(sample_profile(), llm, make_settings(template_total_timeout_s=0))
    assert llm.calls == []


async def test_generator_prompt_carries_the_profile_and_rules() -> None:
    llm = FakeLLM({ProfileAnalysis: sample_analysis(), DraftBatch: batch(GOOD_MESSAGES[:6])})

    await run_template_graph(sample_profile(), llm, make_settings())

    system = next(s for schema, s, _ in llm.calls if schema is DraftBatch)
    assert "Sarcasmo nivel 4/5. Groserías: sí. Jerga de Colombia." in system
    assert "Los temas que el usuario marcó como prohibidos: mi exnovia." in system
    assert "{app}, {nombre}, {meta}, {intentos_hoy}, {minutos_hoy}, {hora}" in system


def test_personalize_literals() -> None:
    profile = sample_profile()

    assert personalize_literals("Alex, deja TikTok ya.", profile) == "{nombre}, deja {app} ya."
    assert personalize_literals("Otra vez instagram.", profile) == "Otra vez {app}."
    assert personalize_literals("Instagram o TikTok, da igual.", profile) is None
    assert personalize_literals("Alexander no es Alex.", profile) == "Alexander no es {nombre}."


async def test_literal_app_names_become_placeholders() -> None:
    texts = GOOD_MESSAGES[:4] + [
        "El libro de la mesa se ríe mientras abres TikTok otra vez.",
        "Instagram, TikTok, lo que sea: tu meta sigue esperando.",
    ]
    llm = FakeLLM({ProfileAnalysis: sample_analysis(), DraftBatch: batch(texts)})

    result = await run_template_graph(sample_profile(), llm, make_settings())

    assert [m.text for m in result.messages][4:] == [
        "El libro de la mesa se ríe mientras abres {app} otra vez."
    ]


def test_chunk_counts() -> None:
    assert chunk_counts(60, 20) == [20, 20, 20]
    assert chunk_counts(25, 20) == [13, 12]
    assert chunk_counts(5, 20) == [5]


async def test_generates_in_parallel_chunks_and_survives_a_failed_one() -> None:
    llm = FakeLLM(
        {
            ProfileAnalysis: sample_analysis(),
            DraftBatch: [
                batch(GOOD_MESSAGES[:2]),
                LLMError("sin respuesta"),
                batch(GOOD_MESSAGES[2:4]),
            ],
        }
    )

    result = await run_template_graph(sample_profile(), llm, make_settings(template_chunk_size=2))

    prompts = [user for schema, _, user in llm.calls if schema is DraftBatch]
    assert len(prompts) == 3
    assert "Además escribe 8 frases" in prompts[0]
    assert all("Deja verdict_approved" in prompt for prompt in prompts[1:])
    assert [m.text for m in result.messages] == GOOD_MESSAGES[:4]
