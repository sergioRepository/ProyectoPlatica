package com.lavidanoesunbanano.app.domain.blocking

import com.lavidanoesunbanano.app.domain.model.AppInfo
import java.time.Instant
import java.time.ZoneId

sealed interface TrackerAction {
    /** Evento sin importancia (teclado, barra de notificaciones): no cambia nada. */
    data object Ignore : TrackerAction

    /** Al frente hay algo que no se bloquea: se cancela cualquier revisión pendiente. */
    data object Idle : TrackerAction

    /**
     * Hay que mostrar (o volver a traer) la pantalla de bloqueo. `newAttempt` es falso
     * cuando el usuario no ha salido de esa app desde la última vez: no suma intento.
     */
    data class ShowBlock(val app: AppInfo, val newAttempt: Boolean, val at: Instant) : TrackerAction

    /** La app al frente está permitida por ahora; hay que revisar de nuevo en `at`. */
    data class RecheckAt(val at: Instant) : TrackerAction
}

/**
 * Decide qué hacer con cada cambio de ventana. No guarda nada en disco y no toca Android,
 * así que se prueba en JVM; el servicio de accesibilidad solo ejecuta lo que devuelve.
 *
 * Nunca descarta un evento de una app bloqueada: si la pantalla ya está encima, traerla
 * otra vez no cuesta nada, y descartarlo dejaría pasar apps que encadenan dos actividades
 * al abrir (splash y principal).
 */
class ForegroundTracker(
    private val ownPackage: String,
    private val zone: ZoneId,
) {
    /** Paquetes cuyas ventanas no cuentan como "la app al frente": sistema y teclados. */
    var ignoredPackages: Set<String> = SYSTEM_PACKAGES

    /** Última app al frente, sin contar las ignoradas. */
    var foreground: String? = null
        private set

    /** App que tapa la pantalla de bloqueo ahora; volver a ella no es un intento nuevo. */
    private var covered: String? = null

    fun onWindow(
        packageName: String,
        now: Instant,
        rules: BlockRules,
        unlockedUntil: Map<String, Instant> = emptyMap(),
    ): TrackerAction {
        if (packageName in ignoredPackages) return TrackerAction.Ignore
        foreground = packageName
        // La propia app: la pantalla de bloqueo o el inicio. No cambia qué app está tapada.
        if (packageName == ownPackage) return TrackerAction.Idle
        // Pasó por otra app o por el launcher: la próxima vez es un intento nuevo.
        if (packageName != covered) covered = null
        return evaluate(packageName, now, rules, unlockedUntil)
    }

    /** Vuelve a decidir con la app que ya está al frente: revisión programada o reglas nuevas. */
    fun recheck(
        now: Instant,
        rules: BlockRules,
        unlockedUntil: Map<String, Instant> = emptyMap(),
    ): TrackerAction {
        val packageName = foreground ?: return TrackerAction.Idle
        if (packageName == ownPackage) return TrackerAction.Idle
        return evaluate(packageName, now, rules, unlockedUntil)
    }

    /** El usuario salió con el botón Salir: volver a la app es un intento nuevo. */
    fun onExit() {
        covered = null
    }

    private fun evaluate(
        packageName: String,
        now: Instant,
        rules: BlockRules,
        unlockedUntil: Map<String, Instant>,
    ): TrackerAction = when (val verdict = BlockPolicy.evaluate(packageName, now, zone, rules, unlockedUntil)) {
        BlockVerdict.NotListed -> TrackerAction.Idle
        is BlockVerdict.Allowed -> verdict.until?.let { TrackerAction.RecheckAt(it) } ?: TrackerAction.Idle
        is BlockVerdict.Blocked -> {
            val newAttempt = covered != packageName
            covered = packageName
            TrackerAction.ShowBlock(verdict.app, newAttempt, now)
        }
    }

    companion object {
        /** Barra de estado, notificaciones y diálogos del sistema. */
        val SYSTEM_PACKAGES = setOf("com.android.systemui", "android")
    }
}
