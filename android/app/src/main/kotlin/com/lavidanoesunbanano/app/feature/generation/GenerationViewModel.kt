package com.lavidanoesunbanano.app.feature.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.GenerationResult
import com.lavidanoesunbanano.app.data.repository.RegistrationResult
import com.lavidanoesunbanano.app.data.repository.TemplatePreviewer
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CodeError { WRONG, UNREACHABLE, SERVER }

enum class GenerationFailure { UNREACHABLE, SERVER, NOT_CONFIGURED, MISSING_PROFILE }

sealed interface GenerationUiState {
    data class NeedsCode(
        val code: String = "",
        val error: CodeError? = null,
        val submitting: Boolean = false,
    ) : GenerationUiState

    /** `status` rota entre frases mientras el backend trabaja. */
    data class Working(val status: Int = 0) : GenerationUiState

    data class Done(val count: Int, val sample: String?) : GenerationUiState

    data class Failed(val reason: GenerationFailure) : GenerationUiState
}

@HiltViewModel
class GenerationViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val deviceRepository: DeviceRepository,
    private val previewer: TemplatePreviewer,
) : ViewModel() {

    private val _state = MutableStateFlow<GenerationUiState>(GenerationUiState.Working())
    val state: StateFlow<GenerationUiState> = _state.asStateFlow()

    private var statusJob: Job? = null

    init {
        generate()
    }

    fun generate() {
        _state.value = GenerationUiState.Working()
        startStatusRotation()
        viewModelScope.launch {
            val next = when (val result = templateRepository.generate()) {
                is GenerationResult.Success -> GenerationUiState.Done(result.messageCount, previewer.random()?.text)
                GenerationResult.NeedsRegistration -> GenerationUiState.NeedsCode()
                GenerationResult.MissingProfile -> GenerationUiState.Failed(GenerationFailure.MISSING_PROFILE)
                GenerationResult.NotConfigured -> GenerationUiState.Failed(GenerationFailure.NOT_CONFIGURED)
                GenerationResult.Unreachable -> GenerationUiState.Failed(GenerationFailure.UNREACHABLE)
                is GenerationResult.ServerError -> GenerationUiState.Failed(GenerationFailure.SERVER)
            }
            statusJob?.cancel()
            _state.value = next
        }
    }

    fun onCodeChange(code: String) {
        _state.update { if (it is GenerationUiState.NeedsCode) it.copy(code = code, error = null) else it }
    }

    fun submitCode() {
        val current = _state.value as? GenerationUiState.NeedsCode ?: return
        if (current.code.isBlank() || current.submitting) return
        _state.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            val error = when (deviceRepository.register(current.code)) {
                RegistrationResult.Success -> null
                RegistrationResult.WrongCode -> CodeError.WRONG
                RegistrationResult.Unreachable -> CodeError.UNREACHABLE
                RegistrationResult.NotConfigured, is RegistrationResult.ServerError -> CodeError.SERVER
            }
            if (error == null) generate() else _state.value = current.copy(submitting = false, error = error)
        }
    }

    private fun startStatusRotation() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            var status = 0
            while (true) {
                delay(STATUS_INTERVAL_MS)
                status = (status + 1) % STATUS_COUNT
                _state.update { if (it is GenerationUiState.Working) it.copy(status = status) else it }
            }
        }
    }

    companion object {
        const val STATUS_COUNT = 5
        private const val STATUS_INTERVAL_MS = 3_500L
    }
}
