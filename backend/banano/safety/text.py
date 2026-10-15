"""Limpieza de texto: la app no muestra emojis, emoticonos ni hashtags en ninguna parte."""

import re
import unicodedata

import emoji

# Lo que el paquete emoji no cubra: pictogramas sueltos, banderas, selectores de variación,
# uniones de ancho cero y keycaps.
_PICTOGRAPH_RE = re.compile(
    "["
    "\U0001f000-\U0001faff"  # bloques de emoji, pictogramas y banderas
    "☀-➿"  # símbolos misceláneos y dingbats
    "⬀-⯿"  # flechas y símbolos (estrellas, círculos)
    "︎️"  # selectores de variación
    "‍"  # unión de ancho cero
    "⃣"  # keycap
    "]"
)

_EMOTICON_RE = re.compile(
    r"""
    (?<![\w/])(?:
        [:;=][-o*'^]?[)\](\[dDpP/\\|@3oO*}{](?!\w)   # :) ;-( =D :P :/ :3
      | \(:|\):                                     # (: y ):
      | </?3                                        # <3 y </3
      | \^[_.-]?\^                                  # ^^ ^_^
      | [xX][dD]+(?!\w)                             # xD XD
      | :v(?!\w)                                    # :v
      | :'[()]                                      # :'( :')
      | [-oOT][_.][-oOT](?!\w)                      # -_- o_o T_T
    )
    """,
    re.VERBOSE,
)

# Hashtag: '#' seguido de una letra (así "#7" no cuenta).
_HASHTAG_RE = re.compile(r"(?<![\w&])#[^\W\d_]\w*")

_SPACE_BEFORE_PUNCT_RE = re.compile(r"\s+([.,;:!?)])")
_MULTISPACE_RE = re.compile(r"\s+")


def _tidy(text: str) -> str:
    text = _MULTISPACE_RE.sub(" ", text).strip()
    return _SPACE_BEFORE_PUNCT_RE.sub(r"\1", text)


def _remove_emojis(text: str) -> str:
    # Se cambian por espacio para no pegar palabras ni emoticonos vecinos; _tidy limpia.
    return _PICTOGRAPH_RE.sub(" ", emoji.replace_emoji(text, replace=" "))


def strip_emojis(text: str) -> str:
    """Quita emojis y pictogramas, y arregla los espacios que quedan."""
    return _tidy(_remove_emojis(text))


def has_emoji(text: str) -> bool:
    return emoji.emoji_count(text) > 0 or _PICTOGRAPH_RE.search(text) is not None


def has_emoticon(text: str) -> bool:
    return _EMOTICON_RE.search(text) is not None


def has_hashtag(text: str) -> bool:
    return _HASHTAG_RE.search(text) is not None


def clean_text(text: str) -> str:
    """Quita emojis, emoticonos y hashtags. Para textos sueltos como el veredicto del juez."""
    text = _remove_emojis(text)
    text = _EMOTICON_RE.sub(" ", text)
    text = _HASHTAG_RE.sub(" ", text)
    return _tidy(text)


def is_clean(text: str) -> bool:
    return not (has_emoji(text) or has_emoticon(text) or has_hashtag(text))


def normalize_for_matching(text: str) -> str:
    """Minúsculas, sin tildes ni signos, espacios simples. Solo para comparar textos."""
    decomposed = unicodedata.normalize("NFKD", text.lower())
    without_marks = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    return _MULTISPACE_RE.sub(" ", re.sub(r"[^\w]+", " ", without_marks)).strip()
