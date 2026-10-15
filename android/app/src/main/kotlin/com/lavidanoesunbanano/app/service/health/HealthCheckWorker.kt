package com.lavidanoesunbanano.app.service.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.domain.setup.ServiceHealth
import com.lavidanoesunbanano.app.service.blocker.AccessibilityState
import com.lavidanoesunbanano.app.service.notify.Notifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Revisa que el servicio de bloqueo siga activo y, si no, avisa con una notificación.
 * WorkManager la mantiene programada aunque el teléfono se reinicie.
 */
class HealthCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun settings(): SettingsStore
        fun notifier(): Notifier
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        var health = AccessibilityState.health(applicationContext)
        if (health == ServiceHealth.STOPPED) {
            // Si el proceso acaba de arrancar, Android puede estar conectando el servicio todavía.
            delay(RECHECK_MS)
            health = AccessibilityState.health(applicationContext)
        }
        when {
            health == ServiceHealth.ACTIVE -> deps.notifier().cancelBlockerOff()
            // Antes de configurarlo por primera vez no hay nada que avisar.
            deps.settings().blockerSetUp.first() -> deps.notifier().showBlockerOff(health)
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "revision-bloqueo"
        private const val SOON = "revision-bloqueo-pronto"
        private const val RECHECK_MS = 5_000L

        /** Cada 15 minutos, el mínimo de WorkManager. Se llama al arrancar la app. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HealthCheckWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        /** El servicio se acaba de detener: se revisa en 30 s, cuando ya se sabe si fue el usuario. */
        fun checkSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                SOON,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HealthCheckWorker>().setInitialDelay(30, TimeUnit.SECONDS).build(),
            )
        }
    }
}
