"""Señales de una emergencia real en una excusa, sin LLM.

Una señal no aprueba por sí sola: evita que el prefiltro rechace la excusa (por corta,
repetida o por cupo) y deja que el juez decida. Si el LLM no responde, la señal basta
para aprobar con tono humano.
"""

import re
from typing import Literal

from banano.safety.text import normalize_for_matching

CrisisKind = Literal["self_harm", "emergency"]

# Patrones sobre texto normalizado: minúsculas y sin tildes.
_SELF_HARM = re.compile(
    r"\b(suicid\w*|matarme|quitarme la vida|no quiero (seguir )?vivir|me quiero morir"
    r"|quiero morirme|hacerme dano|autolesi\w*|cortarme|acabar con mi vida)\b"
)

_EMERGENCY = re.compile(
    r"\b("
    # salud
    r"hospital\w*|urgencias|ambulancia|clinica|infarto|convulsi\w*|desmay\w*|sangr\w*"
    r"|accidente|fractur\w*|no respira|inconsciente|paro cardi\w*|derrame|intoxica\w*"
    r"|envenen\w*|anafila\w*|sobredosis|trabajo de parto|contracciones|se cayo"
    r"|murio|fallecio"
    # seguridad
    r"|incendio|terremoto|temblor|inundaci\w*|me robaron|me estan robando|asalt\w*"
    r"|secuestr\w*|amenaz\w*|violencia|me (estan )?siguiendo|policia|auxilio"
    r"|herid[oa]s?|en peligro|desaparecid[oa]s?|nino perdido|ninos perdidos"
    r"|123|911"
    r")\b"
)


def detect_crisis(text: str) -> CrisisKind | None:
    normalized = normalize_for_matching(text)
    if _SELF_HARM.search(normalized):
        return "self_harm"
    if _EMERGENCY.search(normalized):
        return "emergency"
    return None
