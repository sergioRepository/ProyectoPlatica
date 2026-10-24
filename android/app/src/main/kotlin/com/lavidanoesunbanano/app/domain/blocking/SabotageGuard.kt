package com.lavidanoesunbanano.app.domain.blocking

import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.isBlockedAt
import java.time.LocalDateTime

/**
 * Anti-autosabotaje: reconoce las ventanas desde donde se apaga o se desinstala el bloqueo.
 *
 * Solo usa el paquete y el texto del evento (el título de la ventana), sin leer la pantalla:
 * la página del servicio en Accesibilidad y el diálogo de desinstalar muestran el nombre de
 * la app. "Info de la app" no se reconoce porque su título no dice de qué app es.
 */
object SabotageGuard {

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

    /**
     * Con la protección encendida y dentro del horario de bloqueo, las reglas no se editan
     * desde la app: si se pudiera, apagar el bloqueo sería quitar la app de la lista.
     */
    fun rulesLocked(protectionOn: Boolean, ranges: List<BlockRange>, now: LocalDateTime): Boolean =
        protectionOn && ranges.isBlockedAt(now)
}
