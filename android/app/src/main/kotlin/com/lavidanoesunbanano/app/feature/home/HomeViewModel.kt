package com.lavidanoesunbanano.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto
import com.lavidanoesunbanano.app.data.repository.AgentRepository
import com.lavidanoesunbanano.app.data.repository.AgentRun
import com.lavidanoesunbanano.app.data.repository.BackendRepository
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.ExcuseRepository
import com.lavidanoesunbanano.app.data.repository.HistoryRepository
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.system.SetupStatusSource
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.setup.SetupStatus
import com.lavidanoesunbanano.app.domain.stats.Stats
import com.lavidanoesunbanano.app.domain.stats.StatsCalculator
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
import kotlinx.coroutines.launch

/** Una app desbloqueada ahora mismo y la hora a la que se vuelve a bloquear. */
data class ActiveUnlock(val label: String, val until: String)

data class HomeUiState(
    /** `null` mientras se verifica. */
    val backend: BackendStatus? = null,
    /** Estado del teléfono para el bloqueo; `null` antes de revisar. */
    val setup: SetupStatus? = null,
    /** El servidor no conoce este teléfono: sin juez ni agente hasta que se reconecte. */
    val disconnected: Boolean = false,
    /** Plan y llamadas de IA de hoy; `null` sin conexión. */
    val device: DeviceStatusDto? = null,
    val today: Stats? = null,
    val unlocks: List<ActiveUnlock> = emptyList(),
    /** Último análisis del agente en esta sesión; `null` si no ha corrido. */
    val agent: AgentRun? = null,
    val analyzing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    excuseRepository: ExcuseRepository,
    history: HistoryRepository,
    private val backendRepository: BackendRepository,
    private val setupSource: SetupStatusSource,
    private val deviceRepository: DeviceRepository,
    private val agentRepository: AgentRepository,
    private val backendConfig: BackendConfig,
    private val clock: Clock,
) : ViewModel() {

    private val backend = MutableStateFlow<BackendStatus?>(null)
    private val setup = MutableStateFlow<SetupStatus?>(null)
    private val disconnected = MutableStateFlow(false)
    private val device = MutableStateFlow<DeviceStatusDto?>(null)

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

    private val analyzing = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        combine(backend, setup, disconnected, today, unlocks) { backend, setup, disconnected, today, unlocks ->
            HomeUiState(backend, setup, disconnected, today = today, unlocks = unlocks)
        },
        agentRepository.lastRun,
        analyzing,
        device,
    ) { state, agent, busy, device -> state.copy(agent = agent, analyzing = busy, device = device) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun analyzeNow() {
        if (analyzing.value) return
        analyzing.value = true
        viewModelScope.launch {
            try {
                agentRepository.analyzeNow()
            } finally {
                analyzing.value = false
            }
            device.value = deviceRepository.status()
        }
    }

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
     * Se llama al volver a la app: el usuario pudo cambiar permisos o el bloqueo en Ajustes, y
     * el juez pudo haber rechazado el token (por ejemplo si cambió el secreto del servidor).
     */
    fun refreshOnResume() {
        setup.value = setupSource.current()
        viewModelScope.launch {
            disconnected.value = backendConfig.isConfigured && !deviceRepository.sync()
            device.value = deviceRepository.status()
        }
    }

    private companion object {
        const val UNLOCK_REFRESH_MS = 30_000L
    }
}
