package com.lavidanoesunbanano.app.feature.block

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.repository.AppEvents
import com.lavidanoesunbanano.app.data.repository.BlockSession
import com.lavidanoesunbanano.app.data.repository.BlockSessionRepository
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.EventRepository
import com.lavidanoesunbanano.app.data.repository.ExcuseRepository
import com.lavidanoesunbanano.app.data.repository.ProtectionRepository
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.service.notify.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/** Lo que el servicio le pide a la pantalla. `triggerUptime` sirve para medir cuánto tardó. */
data class BlockRequest(
    val app: AppInfo,
    val startedAt: Instant,
    val newAttempt: Boolean,
    val triggerUptime: Long,
    /** Tapa una ventana desde donde se apaga el bloqueo: se ofrece la salida con fricción. */
    val sabotage: Boolean = false,
) {
    fun writeTo(intent: Intent): Intent = intent
        .putExtra(EXTRA_PACKAGE, app.packageName)
        .putExtra(EXTRA_LABEL, app.label)
        .putExtra(EXTRA_STARTED_AT, startedAt.toEpochMilli())
        .putExtra(EXTRA_NEW_ATTEMPT, newAttempt)
        .putExtra(EXTRA_TRIGGER_UPTIME, triggerUptime)
        .putExtra(EXTRA_SABOTAGE, sabotage)

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_STARTED_AT = "started_at"
        private const val EXTRA_NEW_ATTEMPT = "new_attempt"
        private const val EXTRA_TRIGGER_UPTIME = "trigger_uptime"
        private const val EXTRA_SABOTAGE = "sabotage"

        fun from(intent: Intent): BlockRequest? {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
            return BlockRequest(
                app = AppInfo(packageName, intent.getStringExtra(EXTRA_LABEL) ?: packageName),
                startedAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_STARTED_AT, 0)),
                newAttempt = intent.getBooleanExtra(EXTRA_NEW_ATTEMPT, true),
                triggerUptime = intent.getLongExtra(EXTRA_TRIGGER_UPTIME, 0),
                sabotage = intent.getBooleanExtra(EXTRA_SABOTAGE, false),
            )
        }
    }
}

/** Qué muestra la pantalla: el mensaje, la pregunta de una excusa o el veredicto. */
sealed interface BlockStep {
    data object Message : BlockStep

    data class Excuse(
        val type: ExcuseType,
        val reason: String = "",
        val judging: Boolean = false,
    ) : BlockStep

    /** Salida con fricción. `scheduledAt`: cuándo se apaga, si ya está programado. */
    data class Sabotage(val reason: String = "", val scheduledAt: String? = null) : BlockStep

    data class Decided(
        val type: ExcuseType,
        val verdict: Verdict,
        val unlockedUntil: Instant?,
    ) : BlockStep
}

data class BlockUiState(
    val attemptId: Long,
    val packageName: String,
    val appLabel: String,
    val time: String,
    val attemptNumber: Int,
    /** `null`: no hay plantilla y se muestra la frase local. */
    val message: String?,
    val step: BlockStep = BlockStep.Message,
    /** Resultado del último reporte; `null` si no se ha reportado nada en este paso. */
    val reported: Boolean? = null,
)

enum class ReportSource(val wire: String) { MESSAGE("block_message"), VERDICT("verdict") }

/** App y hora hasta la que queda desbloqueada, para abrirla después de un veredicto aprobado. */
data class Unlock(val packageName: String, val until: Instant)

@HiltViewModel
class BlockViewModel @Inject constructor(
    private val sessions: BlockSessionRepository,
    private val excuses: ExcuseRepository,
    private val notifier: Notifier,
    private val events: EventRepository,
    private val deviceRepository: DeviceRepository,
    private val protection: ProtectionRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<BlockUiState?>(null)

    /** `null` mientras se guarda el intento y se elige el mensaje. */
    val state: StateFlow<BlockUiState?> = _state.asStateFlow()

    private var session: BlockSession? = null
    private var opening: Job? = null

    /** Lo escrito en cada pregunta, por si el usuario vuelve al mensaje y luego regresa. */
    private val drafts = mutableMapOf<ExcuseType, String>()

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
        drafts.clear()
        _state.value = null
        opening = viewModelScope.launch {
            val opened = sessions.open(request.app, request.startedAt)
            session = opened
            val step = if (request.sabotage) BlockStep.Sabotage(scheduledAt = scheduledOff()) else BlockStep.Message
            _state.value = opened.toUiState().copy(step = step)
            when {
                !request.newAttempt -> Unit
                request.sabotage -> protection.recordAttempt(opened.app.packageName)
                else -> events.track(AppEvents.BLOCK_SHOWN, opened.app.packageName, "attempt" to JsonPrimitive(opened.attemptNumber))
            }
        }
    }

    fun askExcuse(type: ExcuseType) = updateStep { step ->
        if (step is BlockStep.Message) BlockStep.Excuse(type, drafts[type].orEmpty()) else step
    }

    fun updateReason(reason: String) = updateStep { step ->
        when {
            step is BlockStep.Excuse && !step.judging -> step.copy(reason = reason)
            step is BlockStep.Sabotage -> step.copy(reason = reason)
            else -> step
        }
    }

    /** Apaga la protección. `onDone`: la pantalla se quita y deja ver lo que tapaba. */
    fun disableProtection(onDone: () -> Unit) {
        val step = _state.value?.step as? BlockStep.Sabotage ?: return
        viewModelScope.launch {
            if (protection.disableNow(step.reason, session?.app?.packageName)) onDone()
        }
    }

    fun scheduleProtectionOff() {
        viewModelScope.launch {
            protection.scheduleDisable(session?.app?.packageName)
            val at = scheduledOff()
            updateStep { step -> if (step is BlockStep.Sabotage) step.copy(scheduledAt = at) else step }
        }
    }

    private suspend fun scheduledOff(): String? = settings.antiSabotageOffAt.first()?.let {
        ScheduleFormatter.dateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), clock.zone))
    }

    fun back() {
        when (val step = _state.value?.step) {
            is BlockStep.Excuse -> if (!step.judging) {
                drafts[step.type] = step.reason
                updateStep { BlockStep.Message }
            }
            is BlockStep.Decided -> if (!step.verdict.valid) dismissRejection()
            is BlockStep.Sabotage, BlockStep.Message, null -> Unit
        }
    }

    fun submit() {
        val current = session ?: return
        val step = _state.value?.step as? BlockStep.Excuse ?: return
        if (step.judging || step.reason.isBlank()) return
        updateStep { step.copy(judging = true) }
        viewModelScope.launch {
            val result = excuses.judge(current, step.type, step.reason)
            drafts.remove(step.type)
            // El tiempo corre desde el veredicto, se abra o no la app enseguida.
            result.unlockedUntil?.let { notifier.showUnlock(current.app, it) }
            // Si mientras tanto llegó otro intento, el veredicto solo queda en el historial.
            if (session?.attemptId == current.attemptId) {
                updateStep { BlockStep.Decided(step.type, result.verdict, result.unlockedUntil) }
            }
        }
    }

    /** Volver tras un rechazo: otro mensaje, ahora del contexto `tras_rechazo`. */
    fun dismissRejection() {
        val current = session ?: return
        viewModelScope.launch {
            val updated = sessions.afterRejection(current)
            if (session?.attemptId != current.attemptId) return@launch
            session = updated
            _state.update { it?.copy(message = updated.message, step = BlockStep.Message, reported = null) }
        }
    }

    /** El desbloqueo del veredicto aprobado que se muestra, si hay uno. */
    fun approvedUnlock(): Unlock? {
        val state = _state.value ?: return null
        val step = state.step as? BlockStep.Decided ?: return null
        return step.unlockedUntil?.let { Unlock(state.packageName, it) }
    }

    fun recordExit() {
        val current = session ?: return
        // La pantalla se cierra justo después: el registro termina aunque se limpie el ViewModel.
        viewModelScope.launch {
            withContext(NonCancellable) {
                sessions.recordExit(current.attemptId)
                events.track(AppEvents.BLOCK_EXIT, current.app.packageName)
            }
        }
    }

    /** Reportar un mensaje de la IA, sin salir de la pantalla. */
    fun report(message: String, reason: String, source: ReportSource) {
        viewModelScope.launch {
            val sent = deviceRepository.report(message, reason, source.wire)
            _state.update { it?.copy(reported = sent) }
        }
    }

    private fun updateStep(transform: (BlockStep) -> BlockStep) {
        _state.update { it?.copy(step = transform(it.step), reported = null) }
    }

    private fun BlockSession.toUiState() = BlockUiState(
        attemptId = attemptId,
        packageName = app.packageName,
        appLabel = app.label,
        time = ScheduleFormatter.time(time),
        attemptNumber = attemptNumber,
        message = message,
    )
}
