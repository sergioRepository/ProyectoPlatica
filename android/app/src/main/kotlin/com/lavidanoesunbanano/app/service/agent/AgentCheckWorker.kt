package com.lavidanoesunbanano.app.service.agent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lavidanoesunbanano.app.data.prefs.DeviceStore
import com.lavidanoesunbanano.app.data.repository.AgentRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Cada 15 minutos revisa el uso; si las reglas locales lo justifican, le pide un análisis al
 * agente. Sin red no corre: el bloqueo local no depende de esto.
 */
class AgentCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun agent(): AgentRepository
        fun deviceStore(): DeviceStore
        fun agentSchedule(): AgentSchedule
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        // Un worker heredado de una versión anterior se apaga solo si el plan no es de pago.
        if (!deps.deviceStore().agentEnabled()) {
            deps.agentSchedule().apply(false)
            return Result.success()
        }
        deps.agent().analyzeIfNeeded()
        return Result.success()
    }
}
