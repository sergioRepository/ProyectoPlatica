package com.lavidanoesunbanano.app.service.blocker

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Une el servicio de accesibilidad con las pantallas: salida al inicio y desbloqueos. */
@Singleton
class BlockerBridge @Inject constructor() {
    @Volatile
    private var service: BlockerService? = null

    fun attach(service: BlockerService) {
        this.service = service
    }

    fun detach(service: BlockerService) {
        if (this.service === service) this.service = null
    }

    /** Manda al inicio con `GLOBAL_ACTION_HOME`. `false` si el servicio no está conectado. */
    fun exitToHome(): Boolean = service?.exitToHome() ?: false

    /** Avisa al servicio de un desbloqueo antes de abrir la app. */
    fun grantUnlock(packageName: String, until: Instant) {
        service?.grantUnlock(packageName, until)
    }
}
