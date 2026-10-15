package com.lavidanoesunbanano.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.repository.BackendRepository
import com.lavidanoesunbanano.app.data.repository.MessagePreview
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.TemplatePreviewer
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val blockedApps: Int = 0,
    val schedule: String = "",
    val messageCount: Int = 0,
    /** `null` mientras se verifica. */
    val backend: BackendStatus? = null,
    val preview: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    templateRepository: TemplateRepository,
    private val backendRepository: BackendRepository,
    private val previewer: TemplatePreviewer,
) : ViewModel() {

    private val backend = MutableStateFlow<BackendStatus?>(null)
    private val preview = MutableStateFlow<MessagePreview?>(null)

    val state: StateFlow<HomeUiState> = combine(
        profileRepository.observeBlockedApps(),
        profileRepository.observeRanges(),
        templateRepository.observeMessageCount(),
        backend,
        preview,
    ) { apps, ranges, count, backend, preview ->
        HomeUiState(
            blockedApps = apps.size,
            schedule = ScheduleFormatter.summary(ranges),
            messageCount = count,
            backend = backend,
            preview = preview?.text,
        )
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
}
