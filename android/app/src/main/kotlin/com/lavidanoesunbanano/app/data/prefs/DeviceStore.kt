package com.lavidanoesunbanano.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Identidad del teléfono frente al backend: id generado en el primer arranque y su token. */
interface DeviceStore {
    suspend fun deviceId(): String?
    suspend fun setDeviceId(id: String)
    suspend fun token(): String?
    suspend fun setToken(token: String?)
}

@Singleton
class DataStoreDeviceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DeviceStore {

    override suspend fun deviceId(): String? = dataStore.data.first()[DEVICE_ID]

    override suspend fun setDeviceId(id: String) {
        dataStore.edit { it[DEVICE_ID] = id }
    }

    override suspend fun token(): String? = dataStore.data.first()[DEVICE_TOKEN]

    override suspend fun setToken(token: String?) {
        dataStore.edit { prefs ->
            if (token == null) prefs.remove(DEVICE_TOKEN) else prefs[DEVICE_TOKEN] = token
        }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_TOKEN = stringPreferencesKey("device_token")
    }
}
