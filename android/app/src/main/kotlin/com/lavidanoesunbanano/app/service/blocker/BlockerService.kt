package com.lavidanoesunbanano.app.service.blocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.lavidanoesunbanano.app.BuildConfig
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.repository.AgentRepository
import com.lavidanoesunbanano.app.data.repository.ExcuseRepository
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.domain.blocking.BlockRules
import com.lavidanoesunbanano.app.domain.blocking.ForegroundTracker
import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard
import com.lavidanoesunbanano.app.domain.blocking.TrackerAction
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.feature.block.BlockActivity
import com.lavidanoesunbanano.app.feature.block.BlockRequest
import com.lavidanoesunbanano.app.service.health.HealthCheckWorker
import com.lavidanoesunbanano.app.service.notify.Notifier
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Detecta la app al frente con `TYPE_WINDOW_STATE_CHANGED` y lanza la pantalla de bloqueo.
 *
 * Las reglas viven en memoria para decidir sin tocar la base de datos; la decisión la toma
 * [ForegroundTracker] y aquí solo se ejecuta. Todo corre en el hilo principal.
 */
@AndroidEntryPoint
class BlockerService : AccessibilityService() {

    @Inject lateinit var profileRepository: ProfileRepository

    @Inject lateinit var excuseRepository: ExcuseRepository

    @Inject lateinit var agentRepository: AgentRepository

    @Inject lateinit var bridge: BlockerBridge

    @Inject lateinit var clock: Clock

    @Inject lateinit var settings: SettingsStore

    @Inject lateinit var notifier: Notifier

    private val scope = MainScope()
    private var tracker: ForegroundTracker? = null
    private var rules = BlockRules.EMPTY

    /** Hasta cuándo está desbloqueada cada app por una excusa aprobada. */
    private var unlocks: Map<String, Instant> = emptyMap()
    private var recheck: Job? = null

    /** Anti-autosabotaje activado en Ajustes. */
    private var antiSabotage = false

    /**
     * Al encender la pantalla se revisa la app al frente: la espera programada no avanza
     * mientras el teléfono duerme, y un bloqueo pudo empezar con la pantalla apagada.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tracker?.ignoredPackages = ignoredPackages()
            recheckNow()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tracker = ForegroundTracker(ownPackage = packageName, zone = clock.zone).apply {
            ignoredPackages = ignoredPackages()
        }
        bridge.attach(this)
        // Desde ahora, si el servicio se apaga, se avisa.
        notifier.cancelBlockerOff()
        scope.launch { settings.markBlockerSetUp() }
        scope.launch { settings.antiSabotage.collect { antiSabotage = it } }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            combine(
                profileRepository.observeBlockedApps(),
                profileRepository.observeRanges(),
                agentRepository.observeFocus(),
            ) { apps, ranges, focus ->
                BlockRules(apps, ranges, focus)
            }.collect {
                rules = it
                recheckNow()
            }
        }
        scope.launch {
            excuseRepository.observeUnlocks().collect {
                unlocks = it
                recheckNow()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val tracker = tracker ?: return
        val packageName = event.packageName?.toString() ?: return
        val texts = event.text.mapNotNull { it?.toString() } + listOfNotNull(event.contentDescription?.toString())
        val guarded = guardedWindow(packageName, texts)
        val effectiveRules = if (guarded != null) rules.guarding(guarded) else rules
        val action = tracker.onWindow(packageName, clock.instant(), effectiveRules, unlocks)
        if (BuildConfig.DEBUG) Log.d(TAG, "ventana $packageName ${event.className} $texts -> $action")
        perform(action, triggerUptime = event.eventTime)
    }

    override fun onInterrupt() = Unit

    /** Se apagó (el usuario o el sistema): en 30 s se revisa y, si sigue así, se avisa. */
    override fun onUnbind(intent: Intent?): Boolean {
        HealthCheckWorker.checkSoon(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        HealthCheckWorker.checkSoon(this)
        bridge.detach(this)
        if (tracker != null) unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    /** Botón Salir: al inicio, y volver a la app cuenta como intento nuevo. */
    fun exitToHome(): Boolean {
        tracker?.onExit()
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Desbloqueo recién aprobado. Room también avisa, pero la app se abre enseguida y no
     * debe bloquearse por llegar antes que ese aviso.
     */
    fun grantUnlock(packageName: String, until: Instant) {
        val current = unlocks[packageName]
        if (current == null || current < until) unlocks = unlocks + (packageName to until)
    }

    /**
     * Con el anti-autosabotaje encendido, la página del servicio en Accesibilidad y el diálogo
     * de desinstalar se tratan como una app bloqueada más; el horario decide si se tapan.
     */
    private fun guardedWindow(packageName: String, texts: List<String>): AppInfo? {
        if (!antiSabotage) return null
        if (!SabotageGuard.isSabotage(packageName, texts, getString(R.string.app_name))) return null
        return AppInfo(packageName, getString(R.string.sabotage_label))
    }

    private fun recheckNow() {
        val tracker = tracker ?: return
        perform(tracker.recheck(clock.instant(), rules, unlocks), triggerUptime = SystemClock.uptimeMillis())
    }

    private fun perform(action: TrackerAction, triggerUptime: Long) {
        when (action) {
            TrackerAction.Ignore -> Unit
            TrackerAction.Idle -> cancelRecheck()
            is TrackerAction.RecheckAt -> scheduleRecheck(action.at)
            is TrackerAction.ShowBlock -> {
                cancelRecheck()
                val request = BlockRequest(action.app, action.at, action.newAttempt, triggerUptime)
                startActivity(BlockActivity.intent(this, request))
            }
        }
    }

    private fun scheduleRecheck(at: Instant) {
        cancelRecheck()
        recheck = scope.launch {
            delay(Duration.between(clock.instant(), at).toMillis().coerceAtLeast(0))
            recheckNow()
        }
    }

    private fun cancelRecheck() {
        recheck?.cancel()
        recheck = null
    }

    /** Sistema y teclados: sus ventanas aparecen encima de cualquier app y no cuentan. */
    private fun ignoredPackages(): Set<String> {
        val keyboards = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            .orEmpty()
        return ForegroundTracker.SYSTEM_PACKAGES + keyboards
    }

    private companion object {
        /** El mismo que la medición de latencia: `adb logcat -s LaVidaBlock`. */
        const val TAG = "LaVidaBlock"
    }
}
