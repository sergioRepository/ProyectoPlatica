package com.lavidanoesunbanano.app.domain.blocking

import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.isBlockedAt
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * Anti-autosabotaje: reconoce las ventanas desde donde se apaga o se desinstala el bloqueo.
 *
 * Solo usa el paquete y el texto del evento (el título de la ventana), sin leer la pantalla:
 * la página del servicio en Accesibilidad y el diálogo de desinstalar muestran el nombre de
 * la app. "Info de la app" no se reconoce porque su título no dice de qué app es.
 */
object SabotageGuard {

    /** Quien programa la desactivación la tiene apagada después de esto. */
    val DISABLE_DELAY: Duration = Duration.ofHours(24)

    /** Lo mínimo que hay que escribir para desactivarla ya: fricción, no un examen. */
    const val MIN_REASON_CHARS = 15

    private val SYSTEM_SETTINGS = setOf(
        "com.android.settings",
        // One UI tiene Accesibilidad en una app aparte.
        "com.samsung.accessibility",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    fun isSabotage(packageName: String, windowTexts: List<String>, appLabel: String): Boolean =
        packageName in SYSTEM_SETTINGS && windowTexts.any { it.contains(appLabel, ignoreCase = true) }

    /** Encendida, y sin una desactivación programada que ya haya llegado. */
    fun protectionOn(enabled: Boolean, offAt: Instant?, now: Instant): Boolean =
        enabled && (offAt == null || now < offAt)

    /** El usuario siempre puede salir: nadie aprueba este texto y no se guarda en ningún lado. */
    fun canDisableNow(reason: String): Boolean = reason.trim().length >= MIN_REASON_CHARS

    /**
     * Con la protección encendida y dentro del horario de bloqueo, las reglas no se editan
     * desde la app: si se pudiera, apagar el bloqueo sería quitar la app de la lista.
     */
    fun rulesLocked(protectionOn: Boolean, ranges: List<BlockRange>, now: LocalDateTime): Boolean =
        protectionOn && ranges.isBlockedAt(now)
}
