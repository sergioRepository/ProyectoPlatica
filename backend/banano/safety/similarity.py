from collections.abc import Iterable
from difflib import SequenceMatcher

from banano.safety.text import normalize_for_matching


def similarity(a: str, b: str) -> float:
    """Parecido entre 0 y 1: el mayor entre la similitud de caracteres y la de palabras."""
    na, nb = normalize_for_matching(a), normalize_for_matching(b)
    if not na or not nb:
        return 0.0
    if na == nb:
        return 1.0
    chars = SequenceMatcher(None, na, nb).ratio()
    words_a, words_b = set(na.split()), set(nb.split())
    words = len(words_a & words_b) / len(words_a | words_b)
    return max(chars, words)


def is_near_duplicate(text: str, others: Iterable[str], threshold: float) -> bool:
    return any(similarity(text, other) >= threshold for other in others)
