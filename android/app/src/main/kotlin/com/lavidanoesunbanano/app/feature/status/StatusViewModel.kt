package com.lavidanoesunbanano.app.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.repository.BackendRepository
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatusUiState(
    /** `null` mientras se verifica. */
    val backend: BackendStatus? = null,
    val baseUrl: String = "",
    val templateMessages: Int = 0,
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val backendRepository: BackendRepository,
    templateRepository: TemplateRepository,
    config: BackendConfig,
) : ViewModel() {

    private val backend = MutableStateFlow<BackendStatus?>(null)

    val uiState: StateFlow<StatusUiState> =
        combine(backend, templateRepository.observeMessageCount()) { backend, count ->
            StatusUiState(backend = backend, baseUrl = config.baseUrl, templateMessages = count)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatusUiState(baseUrl = config.baseUrl),
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            backend.value = null
            backend.value = backendRepository.status()
        }
    }
}
