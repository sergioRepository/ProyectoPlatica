package com.lavidanoesunbanano.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lavidanoesunbanano.app.domain.model.ThemeMode
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Ajustes del usuario que no son parte del perfil: cupos, tema y cuándo cambió el perfil. */
interface SettingsStore {
    val limits: Flow<UnlockLimits>
    val theme: Flow<ThemeMode>

    /** Última vez que cambió el perfil o el tono; si es después de la plantilla, está desactualizada. */
    val profileChangedAt: Flow<Long?>

    suspend fun setExtraTimeDaily(value: Int)
    suspend fun setEmergencyDaily(value: Int)
    suspend fun setTheme(mode: ThemeMode)
    suspend fun markProfileChanged(at: Long)
}

@Singleton
class DataStoreSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {

    override val limits: Flow<UnlockLimits> = dataStore.data.map { prefs ->
        val defaults = UnlockLimits()
        defaults.copy(
            extraTimeDaily = prefs[EXTRA_TIME_DAILY] ?: defaults.extraTimeDaily,
            emergencyDaily = prefs[EMERGENCY_DAILY] ?: defaults.emergencyDaily,
        )
    }.distinctUntilChanged()

    override val theme: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: ThemeMode.DARK
    }.distinctUntilChanged()

    override val profileChangedAt: Flow<Long?> = dataStore.data.map { it[PROFILE_CHANGED_AT] }.distinctUntilChanged()

    override suspend fun setExtraTimeDaily(value: Int) {
        dataStore.edit { it[EXTRA_TIME_DAILY] = value.coerceIn(0, UnlockLimits.MAX_EXTRA_TIME_DAILY) }
    }

    override suspend fun setEmergencyDaily(value: Int) {
        dataStore.edit { it[EMERGENCY_DAILY] = value.coerceIn(0, UnlockLimits.MAX_EMERGENCY_DAILY) }
    }

    override suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    override suspend fun markProfileChanged(at: Long) {
        dataStore.edit { it[PROFILE_CHANGED_AT] = at }
    }

    private companion object {
        val EXTRA_TIME_DAILY = intPreferencesKey("extra_time_daily")
        val EMERGENCY_DAILY = intPreferencesKey("emergency_daily")
        val THEME = stringPreferencesKey("theme")
        val PROFILE_CHANGED_AT = longPreferencesKey("profile_changed_at")
    }
}
