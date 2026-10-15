package com.lavidanoesunbanano.app.service.blocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.domain.blocking.BlockRules
import com.lavidanoesunbanano.app.domain.blocking.ForegroundTracker
import com.lavidanoesunbanano.app.domain.blocking.TrackerAction
import com.lavidanoesunbanano.app.feature.block.BlockActivity
import com.lavidanoesunbanano.app.feature.block.BlockRequest
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

    @Inject lateinit var bridge: BlockerBridge

    @Inject lateinit var clock: Clock

    private val scope = MainScope()
    private var tracker: ForegroundTracker? = null
    private var rules = BlockRules.EMPTY
    private var recheck: Job? = null

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
            combine(profileRepository.observeBlockedApps(), profileRepository.observeRanges()) { apps, ranges ->
                BlockRules(apps, ranges)
            }.collect {
                rules = it
                recheckNow()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val tracker = tracker ?: return
        val packageName = event.packageName?.toString() ?: return
        perform(tracker.onWindow(packageName, clock.instant(), rules), triggerUptime = event.eventTime)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
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

    private fun recheckNow() {
        val tracker = tracker ?: return
        perform(tracker.recheck(clock.instant(), rules), triggerUptime = SystemClock.uptimeMillis())
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
}
