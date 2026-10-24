package com.lavidanoesunbanano.app.domain.setup

/** Estado del servicio de accesibilidad. */
enum class ServiceHealth {
    /** Activado en Ajustes y conectado. */
    ACTIVE,

    /** Apagado en Ajustes. */
    OFF,

    /** Activado en Ajustes, pero Android no lo tiene corriendo (lo detuvo el sistema). */
    STOPPED,
    ;

    companion object {
        fun of(enabledInSettings: Boolean, running: Boolean): ServiceHealth = when {
            !enabledInSettings -> OFF
            running -> ACTIVE
            else -> STOPPED
        }
    }
}

enum class SetupStep {
    /** Android 13+ con la app instalada desde un archivo: hay que permitir ajustes restringidos. */
    RESTRICTED_SETTINGS,
    ACCESSIBILITY,
    NOTIFICATIONS,
    OVERLAY,
    BATTERY,

    /** Acceso a datos de uso: la evidencia que revisa el agente. */
    USAGE_ACCESS,
}

/** Lo que se sabe del teléfono para armar la guía. */
data class SetupStatus(
    val sdk: Int,
    val installedFromFile: Boolean,
    val service: ServiceHealth,
    val notificationsAllowed: Boolean,
    val overlayAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val usageAccessAllowed: Boolean,
)

/** `required`: sin ese paso no hay bloqueo. Los demás lo hacen más confiable. */
data class SetupItem(val step: SetupStep, val done: Boolean, val required: Boolean)

object SetupChecklist {
    private const val TIRAMISU = 33

    fun items(status: SetupStatus): List<SetupItem> = buildList {
        if (status.sdk >= TIRAMISU && status.installedFromFile) {
            // No hay forma pública de leerlo: si Accesibilidad ya se pudo activar, estaba permitido.
            add(SetupItem(SetupStep.RESTRICTED_SETTINGS, done = status.service != ServiceHealth.OFF, required = true))
        }
        add(SetupItem(SetupStep.ACCESSIBILITY, done = status.service == ServiceHealth.ACTIVE, required = true))
        // Antes de Android 13 las notificaciones no piden permiso.
        if (status.sdk >= TIRAMISU) {
            add(SetupItem(SetupStep.NOTIFICATIONS, done = status.notificationsAllowed, required = false))
        }
        add(SetupItem(SetupStep.OVERLAY, done = status.overlayAllowed, required = false))
        add(SetupItem(SetupStep.BATTERY, done = status.batteryUnrestricted, required = false))
        add(SetupItem(SetupStep.USAGE_ACCESS, done = status.usageAccessAllowed, required = false))
    }

    /** El bloqueo funciona: están todos los pasos obligatorios. */
    fun blockerReady(items: List<SetupItem>): Boolean = items.filter { it.required }.all { it.done }

    fun pending(items: List<SetupItem>): Int = items.count { !it.done }
}
