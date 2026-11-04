package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.domain.model.Consents
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Lo que el usuario decide sobre sus datos: revocar autorizaciones y borrarlo todo. */
class PrivacyRepository @Inject constructor(
    private val database: LaVidaDatabase,
    private val settings: SettingsStore,
    private val deviceRepository: DeviceRepository,
    private val events: EventRepository,
) {
    suspend fun update(change: (Consents) -> Consents) {
        val current = settings.consents.first() ?: return
        val updated = change(current)
        settings.setConsents(updated)
        if (!updated.analytics) events.clear()
        // Sin red queda pendiente: Inicio lo reintenta.
        deviceRepository.sync()
    }

    /**
     * Borra primero el servidor: si falla, el teléfono conserva el token para reintentar. Sin
     * token no hay nada que autenticar, y solo queda lo local. `false` si no se pudo.
     */
    suspend fun eraseEverything(): Boolean {
        if (deviceRepository.authorization() != null && !deviceRepository.deleteRemoteData()) return false
        withContext(Dispatchers.IO) { database.clearAllTables() }
        settings.eraseAll()
        return true
    }
}
