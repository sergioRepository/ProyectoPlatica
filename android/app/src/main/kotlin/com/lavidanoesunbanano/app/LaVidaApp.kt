package com.lavidanoesunbanano.app

import android.app.Application
import com.lavidanoesunbanano.app.service.events.EventSyncWorker
import com.lavidanoesunbanano.app.service.health.HealthCheckWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LaVidaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HealthCheckWorker.schedulePeriodic(this)
        // La revisión del agente se programa cuando el servidor confirma un plan de pago.
        EventSyncWorker.schedulePeriodic(this)
    }
}
