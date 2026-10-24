"""Criterio de aceptación: ningún texto de la app ni de la IA contiene emojis.

Se comprueba con un detector independiente del filtro: el paquete emoji y la categoría
Unicode "So" (símbolos), que cubre pictogramas que el paquete no conozca.
"""

import unicodedata
import xml.etree.ElementTree as ET
from collections.abc import Iterator
from pathlib import Path
from typing import Any

import emoji
import pytest

from banano.graphs.judge_graph import JudgeEvaluation
from banano.graphs.template_graph import DraftBatch, ProfileAnalysis
from banano.safety.text import has_emoticon, has_hashtag
from tests.fakes import (
    GOOD_MESSAGES,
    FakeLLM,
    auth_headers,
    batch,
    evaluation,
    judge_request,
    sample_analysis,
    sample_profile,
)

ANDROID_DIR = Path(__file__).resolve().parents[2] / "android"


def assert_no_emoji(text: str) -> None:
    assert emoji.emoji_count(text) == 0, text
    assert not [c for c in text if unicodedata.category(c) == "So"], text


def strings_in(value: Any) -> Iterator[str]:
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for item in value.values():
            yield from strings_in(item)
    elif isinstance(value, list):
        for item in value:
            yield from strings_in(item)


def test_api_outputs_have_no_emojis_even_if_the_llm_sends_them(make_client) -> None:
    noisy = [text.replace(".", " 😂.") for text in GOOD_MESSAGES[:6]] + ["Nuevo récord 🏆 {app} 🔥"]
    generate_llm = FakeLLM(
        {
            ProfileAnalysis: sample_analysis(summary="Quiere programar 🚀 pero 📱 lo atrapa"),
            DraftBatch: batch(noisy, approved=["Vale ✅ {nombre}"], rejected=["No 🙅 :( #nope"]),
        }
    )
    judge_llm = FakeLLM({JudgeEvaluation: evaluation(message="Jajaja 🤣 no ❌ :v #nunca")})
    client = make_client(generate_llm=generate_llm, judge_llm=judge_llm)

    template = client.post(
        "/templates/generate", json=sample_profile().model_dump(), headers=auth_headers()
    )
    verdict = client.post(
        "/excuses/judge", json=judge_request().model_dump(), headers=auth_headers()
    )

    assert template.status_code == verdict.status_code == 200
    texts = list(strings_in(template.json())) + list(strings_in(verdict.json()))
    assert texts
    for text in texts:
        assert_no_emoji(text)
        assert not has_emoticon(text), text
        assert not has_hashtag(text), text


def android_strings() -> list[tuple[Path, str]]:
    found = []
    for path in sorted(ANDROID_DIR.glob("app/src/*/res/values*/strings.xml")):
        for element in ET.parse(path).getroot().iter():
            if element.tag in {"string", "item"} and element.text:
                found.append((path, element.text))
    return found


def test_android_strings_have_no_emojis() -> None:
    strings = android_strings()
    if not strings:
        pytest.skip("No está la carpeta android (por ejemplo, dentro de Docker)")
    for path, text in strings:
        assert emoji.emoji_count(text) == 0, f"{path.name}: {text}"
        assert not [c for c in text if unicodedata.category(c) == "So"], f"{path.name}: {text}"
