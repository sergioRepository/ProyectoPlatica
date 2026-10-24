import re

ALLOWED_PLACEHOLDERS = frozenset({"nombre", "meta", "app", "intentos_hoy", "minutos_hoy", "hora"})

_PLACEHOLDER_RE = re.compile(r"\{([^{}]*)\}")
_WORD_CHAR_RE = re.compile(r"\w")


def placeholders_valid(text: str) -> bool:
    """Solo placeholders permitidos y ninguna llave suelta."""
    if any(name not in ALLOWED_PLACEHOLDERS for name in _PLACEHOLDER_RE.findall(text)):
        return False
    rest = _PLACEHOLDER_RE.sub("", text)
    return "{" not in rest and "}" not in rest


def has_placeholder(text: str) -> bool:
    return _PLACEHOLDER_RE.search(text) is not None


def word_count(text: str) -> int:
    """Cada placeholder cuenta como una palabra; los signos sueltos no cuentan."""
    tokens = _PLACEHOLDER_RE.sub(" X ", text).split()
    return sum(1 for token in tokens if _WORD_CHAR_RE.search(token))
