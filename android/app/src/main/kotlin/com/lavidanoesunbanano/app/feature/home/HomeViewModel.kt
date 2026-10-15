package com.lavidanoesunbanano.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.repository.BackendRepository
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.MessagePreview
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.RegistrationResult
import com.lavidanoesunbanano.app.data.repository.TemplatePreviewer
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.feature.generation.CodeError
import com.lavidanoesunbanano.app.service.blocker.BlockerBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val blockedApps: Int = 0,
    val schedule: String = "",
    val messageCount: Int = 0,
    /** `null` mientras se verifica. */
    val backend: BackendStatus? = null,
    val preview: String? = null,
    /** Si el servicio de accesibilidad está activado; `null` antes de revisar. */
    val blockerEnabled: Boolean? = null,
    /** Sin token el juez no puede responder; se pide otra vez el código de registro. */
    val codeEntry: CodeEntry? = null,
)

data class CodeEntry(
    val code: String = "",
    val error: CodeError? = null,
    val submitting: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    templateRepository: TemplateRepository,
    private val backendRepository: BackendRepository,
    private val previewer: TemplatePreviewer,
    private val blockerBridge: BlockerBridge,
    private val deviceRepository: DeviceRepository,
    private val backendConfig: BackendConfig,
) : ViewModel() {

    private val backend = MutableStateFlow<BackendStatus?>(null)
    private val preview = MutableStateFlow<MessagePreview?>(null)
    private val blockerEnabled = MutableStateFlow<Boolean?>(null)
    private val codeEntry = MutableStateFlow<CodeEntry?>(null)

    private val summary = combine(
        profileRepository.observeBlockedApps(),
        profileRepository.observeRanges(),
        templateRepository.observeMessageCount(),
    ) { apps, ranges, count ->
        HomeUiState(blockedApps = apps.size, schedule = ScheduleFormatter.summary(ranges), messageCount = count)
    }

    val state: StateFlow<HomeUiState> = combine(
        summary,
        backend,
        preview,
        blockerEnabled,
        codeEntry,
    ) { summary, backend, preview, blocker, codeEntry ->
        summary.copy(backend = backend, preview = preview?.text, blockerEnabled = blocker, codeEntry = codeEntry)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshBackend()
        nextPreview()
    }

    fun refreshBackend() {
        viewModelScope.launch {
            backend.value = null
            backend.value = backendRepository.status()
        }
    }

    fun nextPreview() {
        viewModelScope.launch {
            preview.value = previewer.random(excludeRaw = preview.value?.raw)
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
}
