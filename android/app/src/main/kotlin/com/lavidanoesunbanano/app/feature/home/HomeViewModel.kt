package com.lavidanoesunbanano.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.repository.BackendRepository
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.ExcuseRepository
import com.lavidanoesunbanano.app.data.repository.HistoryRepository
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.RegistrationResult
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.stats.Stats
import com.lavidanoesunbanano.app.domain.stats.StatsCalculator
import com.lavidanoesunbanano.app.feature.generation.CodeError
import com.lavidanoesunbanano.app.service.blocker.BlockerBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una app desbloqueada ahora mismo y la hora a la que se vuelve a bloquear. */
data class ActiveUnlock(val label: String, val until: String)

data class HomeUiState(
    /** `null` mientras se verifica. */
    val backend: BackendStatus? = null,
    /** Si el servicio de accesibilidad está activado; `null` antes de revisar. */
    val blockerEnabled: Boolean? = null,
    /** Sin token el juez no puede responder; se pide otra vez el código de registro. */
    val codeEntry: CodeEntry? = null,
    val today: Stats? = null,
    val unlocks: List<ActiveUnlock> = emptyList(),
)

data class CodeEntry(
    val code: String = "",
    val error: CodeError? = null,
    val submitting: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    excuseRepository: ExcuseRepository,
    history: HistoryRepository,
    private val backendRepository: BackendRepository,
    private val blockerBridge: BlockerBridge,
    private val deviceRepository: DeviceRepository,
    private val backendConfig: BackendConfig,
    private val clock: Clock,
) : ViewModel() {

    private val backend = MutableStateFlow<BackendStatus?>(null)
    private val blockerEnabled = MutableStateFlow<Boolean?>(null)
    private val codeEntry = MutableStateFlow<CodeEntry?>(null)

    private val today: Flow<Stats> = history.observe(days = 1).map {
        StatsCalculator.compute(it.attempts, it.excuses, it.today, days = 1, zone = it.zone)
    }

    /** Los desbloqueos vencen solos: se revisan cada 30 s para quitarlos de la lista. */
    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(UNLOCK_REFRESH_MS)
        }
    }

    private val unlocks: Flow<List<ActiveUnlock>> = combine(
        excuseRepository.observeUnlocks(),
        profileRepository.observeBlockedApps(),
        ticker,
    ) { unlocks, apps, now ->
        val labels = apps.associate { it.packageName to it.label }
        unlocks.filterValues { it > now }.map { (packageName, until) ->
            ActiveUnlock(
                label = labels[packageName] ?: packageName,
                until = ScheduleFormatter.time(LocalTime.ofInstant(until, clock.zone)),
            )
        }.sortedBy { it.until }
    }

    val state: StateFlow<HomeUiState> = combine(
        backend,
        blockerEnabled,
        codeEntry,
        today,
        unlocks,
    ) { backend, blocker, codeEntry, today, unlocks ->
        HomeUiState(backend, blocker, codeEntry, today, unlocks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshBackend()
    }

    fun refreshBackend() {
        viewModelScope.launch {
            backend.value = null
            backend.value = backendRepository.status()
        }
    }

    /**
     * Se llama al volver a la app: el usuario pudo activar o apagar el bloqueo en Ajustes, y
     * el juez pudo haber rechazado el token (por ejemplo si cambió el secreto del servidor).
     */
    fun refreshOnResume() {
        blockerEnabled.value = blockerBridge.isEnabled()
        viewModelScope.launch {
            val missing = backendConfig.isConfigured && deviceRepository.authorization() == null
            codeEntry.update { current -> if (missing) current ?: CodeEntry() else null }
        }
    }

    fun onCodeChange(code: String) {
        codeEntry.update { it?.copy(code = code, error = null) }
    }

    fun submitCode() {
        val current = codeEntry.value ?: return
        if (current.code.isBlank() || current.submitting) return
        codeEntry.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            codeEntry.value = when (deviceRepository.register(current.code)) {
                RegistrationResult.Success -> null
                RegistrationResult.WrongCode -> current.copy(error = CodeError.WRONG)
                RegistrationResult.Unreachable -> current.copy(error = CodeError.UNREACHABLE)
                RegistrationResult.NotConfigured, is RegistrationResult.ServerError -> current.copy(error = CodeError.SERVER)
            }
        }
    }

    private companion object {
        const val UNLOCK_REFRESH_MS = 30_000L
    }
}
