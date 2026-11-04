package com.lavidanoesunbanano.app.domain.excuse

import java.text.Normalizer

/**
 * Las mismas señales de `backend/banano/safety/crisis.py`, para cuando el texto no llega al
 * servidor: sin red o sin la autorización de excusas. Si cambian allá, cambian aquí.
 */
object CrisisDetector {
    private val SELF_HARM = Regex(
        """\b(suicid\w*|matarme|quitarme la vida|no quiero (seguir )?vivir|me quiero morir""" +
            """|quiero morirme|hacerme dano|autolesi\w*|cortarme|acabar con mi vida)\b""",
    )

    private val EMERGENCY = Regex(
        """\b(hospital\w*|urgencias|ambulancia|clinica|infarto|convulsi\w*|desmay\w*|sangr\w*""" +
            """|accidente|fractur\w*|no respira|inconsciente|paro cardi\w*|derrame|intoxica\w*""" +
            """|envenen\w*|anafila\w*|sobredosis|trabajo de parto|contracciones|se cayo""" +
            """|murio|fallecio""" +
            """|incendio|terremoto|temblor|inundaci\w*|me robaron|me estan robando|asalt\w*""" +
            """|secuestr\w*|amenaz\w*|violencia|me (estan )?siguiendo|policia|auxilio""" +
            """|herid[oa]s?|en peligro|desaparecid[oa]s?|nino perdido|ninos perdidos""" +
            """|123|911)\b""",
    )

    fun isCrisis(text: String): Boolean {
        val normalized = normalize(text)
        return SELF_HARM.containsMatchIn(normalized) || EMERGENCY.containsMatchIn(normalized)
    }

    /** Minúsculas, sin tildes ni signos: igual que `normalize_for_matching` en el backend. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^\p{L}\p{N}_]+"""), " ")
            .trim()
}
