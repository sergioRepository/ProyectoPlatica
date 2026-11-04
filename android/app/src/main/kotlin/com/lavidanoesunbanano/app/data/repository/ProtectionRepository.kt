package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Salida con fricción del anti-autosabotaje: el usuario siempre puede desactivarlo, ya mismo
 * escribiendo por qué o programándolo para dentro de 24 horas. El motivo no se guarda.
 */
class ProtectionRepository @Inject constructor(
    private val settings: SettingsStore,
    private val events: EventRepository,
    private val clock: Clock,
) {
    suspend fun recordAttempt(app: String) = track(app, ACTION_ATTEMPT)

    /** `false` si el motivo es muy corto. */
    suspend fun disableNow(reason: String, app: String? = null): Boolean {
        if (!SabotageGuard.canDisableNow(reason)) return false
        settings.setAntiSabotage(false)
        track(app, ACTION_DISABLED_NOW)
        return true
    }

    suspend fun scheduleDisable(app: String? = null): Instant {
        val at = clock.instant().plus(SabotageGuard.DISABLE_DELAY)
        settings.setAntiSabotageOffAt(at.toEpochMilli())
        track(app, ACTION_SCHEDULED)
        return at
    }

    suspend fun cancelScheduled() = settings.setAntiSabotageOffAt(null)

    private suspend fun track(app: String?, action: String) =
        events.track(AppEvents.SABOTAGE_ATTEMPT, app, "action" to JsonPrimitive(action))

    private companion object {
        const val ACTION_ATTEMPT = "attempt"
        const val ACTION_DISABLED_NOW = "disabled_now"
        const val ACTION_SCHEDULED = "scheduled"
    }
}
