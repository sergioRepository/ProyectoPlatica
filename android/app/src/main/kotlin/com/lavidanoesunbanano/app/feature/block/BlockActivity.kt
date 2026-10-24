package com.lavidanoesunbanano.app.feature.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.BuildConfig
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.applyWindowTheme
import com.lavidanoesunbanano.app.service.blocker.BlockerBridge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Pantalla de bloqueo a pantalla completa. La lanza el servicio de accesibilidad en su propia
 * tarea (`singleTask`, fuera de recientes) encima de la app bloqueada.
 */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    @Inject lateinit var bridge: BlockerBridge

    private val viewModel: BlockViewModel by viewModels()

    /** Salir se pidió: la pantalla se cierra cuando el inicio ya la tapó. */
    private var exitRequested = false

    /** Momento del evento que la lanzó; 0 cuando ya se midió. */
    private var triggerUptime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWindowTheme()
        // Atrás no salta el bloqueo: solo los botones sacan de aquí.
        onBackPressedDispatcher.addCallback(this) {}
        // Si Android la recreó, el evento original ya no sirve para medir.
        handle(intent, measure = savedInstanceState == null)

        val actions = BlockActions(
            onExit = ::exit,
            onAskExcuse = viewModel::askExcuse,
            onReasonChange = viewModel::updateReason,
            onSubmit = viewModel::submit,
            onBack = viewModel::back,
            onOpenApp = ::openApp,
        )
        setContent {
            LaVidaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                BlockScreen(state = state, actions = actions, onShown = ::logLatency)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        // Cerrarla antes de que el inicio la tape reanudaría la app bloqueada.
        if (exitRequested) {
            exitRequested = false
            finish()
        }
    }

    private fun handle(intent: Intent, measure: Boolean = true) {
        val request = BlockRequest.from(intent) ?: return finish()
        exitRequested = false
        triggerUptime = if (measure) request.triggerUptime else 0L
        viewModel.open(request)
    }

    private fun exit() {
        viewModel.recordExit()
        exitRequested = true
        if (!bridge.exitToHome()) {
            // Sin servicio (por ejemplo si lo apagaron con la pantalla abierta), el launcher.
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Veredicto aprobado: el servicio ya sabe del desbloqueo y la app pasa al frente. */
    private fun openApp() {
        val unlock = viewModel.approvedUnlock() ?: return
        bridge.grantUnlock(unlock.packageName, unlock.until)
        packageManager.getLaunchIntentForPackage(unlock.packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun logLatency() {
        if (!BuildConfig.DEBUG || triggerUptime == 0L) return
        Log.d(LATENCY_TAG, "bloqueo en ${SystemClock.uptimeMillis() - triggerUptime} ms")
        triggerUptime = 0L
    }

    companion object {
        private const val LATENCY_TAG = "LaVidaBlock"

        fun intent(context: Context, request: BlockRequest): Intent = request.writeTo(
            Intent(context, BlockActivity::class.java)
                // Sin animación de ventana: aparece de una vez y el mensaje entra con un fundido.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }
}
