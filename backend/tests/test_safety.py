import pytest

from banano.safety.crisis import detect_crisis
from banano.safety.guardrails import forbidden_terms, violates_guardrails
from banano.safety.placeholders import placeholders_valid, word_count
from banano.safety.similarity import is_near_duplicate, similarity
from banano.safety.text import (
    clean_text,
    has_emoji,
    has_emoticon,
    has_hashtag,
    normalize_for_matching,
    strip_emojis,
)


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("Hola 😂 mundo 🎉.", "Hola mundo."),
        ("Bandera 🇨🇴 fuera", "Bandera fuera"),
        ("Corazón ❤️ con selector", "Corazón con selector"),
        ("Paso 1️⃣ listo", "Paso listo"),
        ("Familia 👨‍👩‍👧 unida", "Familia unida"),
        ("Estrella ⭐ suelta", "Estrella suelta"),
    ],
)
def test_strip_emojis(text: str, expected: str) -> None:
    assert strip_emojis(text) == expected
    assert not has_emoji(strip_emojis(text))


def test_plain_text_has_no_emoji() -> None:
    assert not has_emoji("RECHAZADO · excusa repetida. ¿Qué pasó, ñero?")


@pytest.mark.parametrize(
    "emoticon", [":)", ":-(", ";P", "=D", "xD", ":v", "<3", "^_^", "T_T", "-_-", ":'("]
)
def test_detects_emoticons(emoticon: str) -> None:
    assert has_emoticon(f"Otra vez en la app {emoticon} de verdad")


@pytest.mark.parametrize(
    "text",
    ["Son las 3:30 de la tarde", "PD: vuelve a trabajar", "(ver el punto 8)", "Bien, xilófono"],
)
def test_normal_text_is_not_an_emoticon(text: str) -> None:
    assert not has_emoticon(text)


def test_hashtags() -> None:
    assert has_hashtag("Otra vez #NoMásReels")
    assert not has_hashtag("Es tu intento #7 de hoy")


def test_clean_text_removes_every_symbol() -> None:
    assert clean_text("Vale 👍 :) #Aprobado, listo.") == "Vale, listo."
    assert clean_text("Vale👍:)") == "Vale"


def test_normalize_for_matching() -> None:
    assert normalize_for_matching("¿Qué PASÓ, Ñandú?") == "que paso nandu"


@pytest.mark.parametrize(
    ("text", "kind"),
    [
        ("accidente", "emergency"),
        ("Estoy en urgencias con mi papá", "emergency"),
        ("Me robaron el celular en la calle", "emergency"),
        ("me quiero morir", "self_harm"),
        ("Tengo una urgencia de trabajo", None),
        ("quiero ver memes un rato", None),
    ],
)
def test_detect_crisis(text: str, kind: str | None) -> None:
    assert detect_crisis(text) == kind


def test_similarity() -> None:
    base = "Necesito revisar el correo del trabajo"
    assert similarity(base, base.upper()) == 1.0
    assert is_near_duplicate(base + " ya", [base], 0.85)
    assert not is_near_duplicate("Quiero ver memes", [base], 0.85)


def test_placeholders() -> None:
    assert placeholders_valid("¿{app} otra vez, {nombre}? Así no llegas a {meta}.")
    assert not placeholders_valid("A tus {edad} años")
    assert not placeholders_valid("Llave suelta {app")
    assert not placeholders_valid("Llave suelta app}")
    assert word_count("¿{app} otra vez, {nombre}?") == 4


def test_guardrails() -> None:
    terms = forbidden_terms("mi exnovia, mi peso y la religión")
    assert terms == ["exnovia", "peso", "religion"]
    assert violates_guardrails("Tu exnovia sí terminó sus proyectos", terms)
    assert not violates_guardrails("Ganas pesos programando, no mirando", terms)
    assert violates_guardrails("No vales nada frente a tu meta")
    assert not violates_guardrails("Qué feo verte otra vez en Instagram")
