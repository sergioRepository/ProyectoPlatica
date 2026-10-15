package com.lavidanoesunbanano.app.feature.block

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.repository.BlockSession
import com.lavidanoesunbanano.app.data.repository.BlockSessionRepository
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que el servicio le pide a la pantalla. `triggerUptime` sirve para medir cuánto tardó. */
data class BlockRequest(
    val app: AppInfo,
    val startedAt: Instant,
    val newAttempt: Boolean,
    val triggerUptime: Long,
) {
    fun writeTo(intent: Intent): Intent = intent
        .putExtra(EXTRA_PACKAGE, app.packageName)
        .putExtra(EXTRA_LABEL, app.label)
        .putExtra(EXTRA_STARTED_AT, startedAt.toEpochMilli())
        .putExtra(EXTRA_NEW_ATTEMPT, newAttempt)
        .putExtra(EXTRA_TRIGGER_UPTIME, triggerUptime)

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_STARTED_AT = "started_at"
        private const val EXTRA_NEW_ATTEMPT = "new_attempt"
        private const val EXTRA_TRIGGER_UPTIME = "trigger_uptime"

        fun from(intent: Intent): BlockRequest? {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
            return BlockRequest(
                app = AppInfo(packageName, intent.getStringExtra(EXTRA_LABEL) ?: packageName),
                startedAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_STARTED_AT, 0)),
                newAttempt = intent.getBooleanExtra(EXTRA_NEW_ATTEMPT, true),
                triggerUptime = intent.getLongExtra(EXTRA_TRIGGER_UPTIME, 0),
            )
        }
    }
}

data class BlockUiState(
    val appLabel: String,
    val time: String,
    val attemptNumber: Int,
    /** `null`: no hay plantilla y se muestra la frase local. */
    val message: String?,
)

@HiltViewModel
class BlockViewModel @Inject constructor(
    private val sessions: BlockSessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<BlockUiState?>(null)

    /** `null` mientras se guarda el intento y se elige el mensaje. */
    val state: StateFlow<BlockUiState?> = _state.asStateFlow()

    private var session: BlockSession? = null
    private var opening: Job? = null

    fun open(request: BlockRequest) {
        val current = session
        // La misma pantalla otra vez: el servicio la volvió a traer o Android la recreó.
        if (current != null &&
            current.app.packageName == request.app.packageName &&
            (!request.newAttempt || current.startedAt == request.startedAt)
        ) {
            return
        }
        opening?.cancel()
        session = null
        _state.value = null
        opening = viewModelScope.launch {
            val opened = sessions.open(request.app, request.startedAt)
            session = opened
            _state.value = BlockUiState(
                appLabel = opened.app.label,
                time = ScheduleFormatter.time(opened.time),
                attemptNumber = opened.attemptNumber,
                message = opened.message,
            )
        }
    }

    fun recordExit() {
        val attemptId = session?.attemptId ?: return
        // La pantalla se cierra justo después: el registro termina aunque se limpie el ViewModel.
        viewModelScope.launch { withContext(NonCancellable) { sessions.recordExit(attemptId) } }
    }
}
