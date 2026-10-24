"""Guardarraíles deterministas para los mensajes generados.

El prompt ya prohíbe estos temas; esto atrapa lo que se cuele. La lista global es corta a
propósito: solo frases que nunca tienen un uso aceptable en la app.
"""

import re
from collections.abc import Iterable

from banano.safety.text import normalize_for_matching

# Autolesión, "no valer nada" y burlas del cuerpo u orientación (texto normalizado).
_ALWAYS_BLOCKED = re.compile(
    r"\b("
    r"suicid\w*|matate|muerete|cortate|ahorcate|autolesi\w*|quitarte la vida|hacerte dano"
    r"|no vales (nada|ni)|no sirves para nada|mejor ni existir|mejor no existir"
    r"|gord[oa]s?|obes[oa]s?|marica|maricon\w*"
    r")\b"
)

# Se parte antes de normalizar: la normalización quita las comas.
_TOPIC_SPLIT_RE = re.compile(r"[,;/\n]|\s+[yo]\s+", re.IGNORECASE)
_LEADING_WORDS = {
    "mi",
    "mis",
    "el",
    "la",
    "los",
    "las",
    "de",
    "del",
    "sobre",
    "nada",
    "temas",
    "tema",
}


def forbidden_terms(topics: str) -> list[str]:
    """Convierte "mi exnovia, mi peso" en ["exnovia", "peso"]."""
    terms: list[str] = []
    for chunk in _TOPIC_SPLIT_RE.split(topics):
        words = normalize_for_matching(chunk).split()
        while words and words[0] in _LEADING_WORDS:
            words.pop(0)
        term = " ".join(words)
        if len(term) >= 3:
            terms.append(term)
    return terms


def violates_guardrails(text: str, user_forbidden: Iterable[str] = ()) -> bool:
    normalized = normalize_for_matching(text)
    if _ALWAYS_BLOCKED.search(normalized):
        return True
    return any(re.search(rf"\b{re.escape(term)}\b", normalized) for term in user_forbidden)
