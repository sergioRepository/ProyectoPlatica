package com.lavidanoesunbanano.app.service.blocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.lavidanoesunbanano.app.domain.setup.ServiceHealth

/** Estado del servicio de bloqueo, leído de Android. Sin inyección: también lo usa el worker. */
object AccessibilityState {

    fun health(context: Context): ServiceHealth =
        ServiceHealth.of(enabledInSettings = isEnabledInSettings(context), running = isRunning(context))

    /** El usuario lo activó en Ajustes > Accesibilidad. */
    fun isEnabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val own = component(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == own }
    }

    /** Android lo tiene conectado. Esta lista solo trae los servicios que están corriendo. */
    fun isRunning(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val own = component(context)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == own }
    }

    private fun component(context: Context) = ComponentName(context, BlockerService::class.java)
}
