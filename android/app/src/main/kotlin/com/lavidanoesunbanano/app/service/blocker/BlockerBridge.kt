package com.lavidanoesunbanano.app.service.blocker

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Une el servicio de accesibilidad con las pantallas: estado y salida al inicio. */
@Singleton
class BlockerBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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

    /** Si el servicio está activado en Ajustes > Accesibilidad. */
    fun isEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val own = ComponentName(context, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == own }
    }
}
