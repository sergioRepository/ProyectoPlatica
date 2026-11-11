package com.lavidanoesunbanano.app.domain.blocking

import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.isBlockedAt
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * Anti-autosabotaje dentro de la app: en horario de bloqueo deja las reglas bajo llave y pide
 * una frase de confirmación para apagarse. No toca ninguna pantalla del sistema: Google Play
 * prohíbe usar Accesibilidad para estorbar la desactivación o la desinstalación.
 */
object SabotageGuard {

    /** Quien programa la desactivación la tiene apagada después de esto. */
    val DISABLE_DELAY: Duration = Duration.ofHours(24)

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")

    /** Encendida, y sin una desactivación programada que ya haya llegado. */
    fun protectionOn(enabled: Boolean, offAt: Instant?, now: Instant): Boolean =
        enabled && (offAt == null || now < offAt)

    /** El usuario siempre puede salir: basta escribir la frase; no importan mayúsculas, tildes ni espacios de más. */
    fun canDisableNow(typed: String, phrase: String): Boolean = normalize(typed) == normalize(phrase)

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .trim()
            .replace(SPACES, " ")

    /**
     * Con la protección encendida y dentro del horario de bloqueo, las reglas no se editan
     * desde la app: si se pudiera, apagar el bloqueo sería quitar la app de la lista.
     */
    fun rulesLocked(protectionOn: Boolean, ranges: List<BlockRange>, now: LocalDateTime): Boolean =
        protectionOn && ranges.isBlockedAt(now)
}
