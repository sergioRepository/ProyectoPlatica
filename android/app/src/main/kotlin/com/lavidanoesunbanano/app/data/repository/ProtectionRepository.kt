package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Salida con fricción del anti-autosabotaje: el usuario siempre puede desactivarlo, ya mismo
 * escribiendo la frase de confirmación o programándolo para dentro de 24 horas. Lo escrito no se guarda.
 */
class ProtectionRepository @Inject constructor(
    private val settings: SettingsStore,
    private val events: EventRepository,
    private val clock: Clock,
) {
    /** `false` si lo escrito no es la frase. */
    suspend fun disableNow(typed: String, phrase: String): Boolean {
        if (!SabotageGuard.canDisableNow(typed, phrase)) return false
        settings.setAntiSabotage(false)
        track(ACTION_DISABLED_NOW)
        return true
    }

    suspend fun scheduleDisable(): Instant {
        val at = clock.instant().plus(SabotageGuard.DISABLE_DELAY)
        settings.setAntiSabotageOffAt(at.toEpochMilli())
        track(ACTION_SCHEDULED)
        return at
    }

    suspend fun cancelScheduled() = settings.setAntiSabotageOffAt(null)

    private suspend fun track(action: String) =
        events.track(AppEvents.SABOTAGE_ATTEMPT, null, "action" to JsonPrimitive(action))

    private companion object {
        const val ACTION_DISABLED_NOW = "disabled_now"
        const val ACTION_SCHEDULED = "scheduled"
    }
}
