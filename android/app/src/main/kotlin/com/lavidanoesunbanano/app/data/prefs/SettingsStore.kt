package com.lavidanoesunbanano.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lavidanoesunbanano.app.domain.model.Consents
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Ajustes del usuario que no son parte del perfil: cupos, protección y cuándo cambió el perfil. */
interface SettingsStore {
    val limits: Flow<UnlockLimits>

    /** Última vez que cambió el perfil o el tono; si es después de la plantilla, está desactualizada. */
    val profileChangedAt: Flow<Long?>

    /** Anti-autosabotaje: tapar en horario las ventanas desde donde se apaga el bloqueo. */
    val antiSabotage: Flow<Boolean>

    /** Desactivación programada del anti-autosabotaje; `null` si no hay. */
    val antiSabotageOffAt: Flow<Long?>

    /** Modo amable: sin mensajes sarcásticos de la plantilla ni sarcasmo en el juez. */
    val kindMode: Flow<Boolean>

    /** El servicio se conectó alguna vez: desde ahí, si se apaga, se avisa. */
    val blockerSetUp: Flow<Boolean>

    /** Autorizaciones de datos; `null` si todavía no se han pedido. */
    val consents: Flow<Consents?>

    /** El servidor ya tiene las autorizaciones vigentes. */
    val consentsSynced: Flow<Boolean>

    suspend fun setExtraTimeDaily(value: Int)
    suspend fun setEmergencyDaily(value: Int)
    suspend fun markProfileChanged(at: Long)
    /** También cancela cualquier desactivación programada. */
    suspend fun setAntiSabotage(enabled: Boolean)
    suspend fun setAntiSabotageOffAt(at: Long?)
    suspend fun markBlockerSetUp()
    suspend fun setKindMode(enabled: Boolean)
    suspend fun setConsents(consents: Consents)
    suspend fun markConsentsSynced()

    /** Deja las preferencias como recién instalada la app, incluida la identidad del teléfono. */
    suspend fun eraseAll()
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

    override val profileChangedAt: Flow<Long?> = dataStore.data.map { it[PROFILE_CHANGED_AT] }.distinctUntilChanged()

    override val antiSabotage: Flow<Boolean> = dataStore.data.map { it[ANTI_SABOTAGE] ?: false }.distinctUntilChanged()

    override val antiSabotageOffAt: Flow<Long?> = dataStore.data.map { it[ANTI_SABOTAGE_OFF_AT] }.distinctUntilChanged()

    override val kindMode: Flow<Boolean> = dataStore.data.map { it[KIND_MODE] ?: false }.distinctUntilChanged()

    override val blockerSetUp: Flow<Boolean> = dataStore.data.map { it[BLOCKER_SET_UP] ?: false }.distinctUntilChanged()

    override val consents: Flow<Consents?> = dataStore.data.map { prefs ->
        prefs[CONSENT_VERSION]?.let { version ->
            Consents(
                excuses = prefs[CONSENT_EXCUSES] ?: false,
                phoneUsage = prefs[CONSENT_PHONE_USAGE] ?: false,
                crossBorder = prefs[CONSENT_CROSS_BORDER] ?: false,
                analytics = prefs[CONSENT_ANALYTICS] ?: false,
                version = version,
            )
        }
    }.distinctUntilChanged()

    override val consentsSynced: Flow<Boolean> = dataStore.data.map { it[CONSENTS_SYNCED] ?: false }.distinctUntilChanged()

    override suspend fun setExtraTimeDaily(value: Int) {
        dataStore.edit { it[EXTRA_TIME_DAILY] = value.coerceIn(0, UnlockLimits.MAX_EXTRA_TIME_DAILY) }
    }

    override suspend fun setEmergencyDaily(value: Int) {
        dataStore.edit { it[EMERGENCY_DAILY] = value.coerceIn(0, UnlockLimits.MAX_EMERGENCY_DAILY) }
    }

    override suspend fun markProfileChanged(at: Long) {
        dataStore.edit { it[PROFILE_CHANGED_AT] = at }
    }

    override suspend fun setAntiSabotage(enabled: Boolean) {
        dataStore.edit {
            it[ANTI_SABOTAGE] = enabled
            it.remove(ANTI_SABOTAGE_OFF_AT)
        }
    }

    override suspend fun setAntiSabotageOffAt(at: Long?) {
        dataStore.edit { prefs ->
            if (at == null) prefs.remove(ANTI_SABOTAGE_OFF_AT) else prefs[ANTI_SABOTAGE_OFF_AT] = at
        }
    }

    override suspend fun markBlockerSetUp() {
        dataStore.edit { it[BLOCKER_SET_UP] = true }
    }

    override suspend fun setKindMode(enabled: Boolean) {
        dataStore.edit { it[KIND_MODE] = enabled }
    }

    override suspend fun setConsents(consents: Consents) {
        dataStore.edit {
            it[CONSENT_EXCUSES] = consents.excuses
            it[CONSENT_PHONE_USAGE] = consents.phoneUsage
            it[CONSENT_CROSS_BORDER] = consents.crossBorder
            it[CONSENT_ANALYTICS] = consents.analytics
            it[CONSENT_VERSION] = consents.version
            it[CONSENTS_SYNCED] = false
        }
    }

    override suspend fun markConsentsSynced() {
        dataStore.edit { it[CONSENTS_SYNCED] = true }
    }

    override suspend fun eraseAll() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val EXTRA_TIME_DAILY = intPreferencesKey("extra_time_daily")
        val EMERGENCY_DAILY = intPreferencesKey("emergency_daily")
        val PROFILE_CHANGED_AT = longPreferencesKey("profile_changed_at")
        val ANTI_SABOTAGE = booleanPreferencesKey("anti_sabotage")
        val ANTI_SABOTAGE_OFF_AT = longPreferencesKey("anti_sabotage_off_at")
        val BLOCKER_SET_UP = booleanPreferencesKey("blocker_set_up")
        val KIND_MODE = booleanPreferencesKey("kind_mode")
        val CONSENT_EXCUSES = booleanPreferencesKey("consent_excuses")
        val CONSENT_PHONE_USAGE = booleanPreferencesKey("consent_phone_usage")
        val CONSENT_CROSS_BORDER = booleanPreferencesKey("consent_cross_border")
        val CONSENT_ANALYTICS = booleanPreferencesKey("consent_analytics")
        val CONSENT_VERSION = stringPreferencesKey("consent_version")
        val CONSENTS_SYNCED = booleanPreferencesKey("consents_synced")
    }
}
