package com.lavidanoesunbanano.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.repository.MessagePreview
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.TemplatePreviewer
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.Profile
import com.lavidanoesunbanano.app.domain.model.ThemeMode
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateInfo(
    val messageCount: Int = 0,
    /** "15 oct 08:00"; `null` si no hay plantilla. */
    val generatedAt: String? = null,
    /** El perfil o el tono cambiaron después de generarla. */
    val stale: Boolean = false,
)

data class SettingsUiState(
    val profile: Profile? = null,
    val apps: List<AppInfo> = emptyList(),
    val schedule: String = "",
    val limits: UnlockLimits = UnlockLimits(),
    val theme: ThemeMode = ThemeMode.DARK,
    val template: TemplateInfo = TemplateInfo(),
    val preview: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    templateRepository: TemplateRepository,
    private val settings: SettingsStore,
    private val previewer: TemplatePreviewer,
    private val clock: Clock,
) : ViewModel() {

    private val preview = MutableStateFlow<MessagePreview?>(null)

    private val template = combine(
        templateRepository.observeMessageCount(),
        templateRepository.observeGeneratedAt(),
        settings.profileChangedAt,
    ) { count, generatedAt, changedAt ->
        TemplateInfo(
            messageCount = count,
            generatedAt = generatedAt?.let { ScheduleFormatter.dateTime(LocalDateTime.ofInstant(it, clock.zone)) },
            stale = generatedAt != null && changedAt != null && changedAt > generatedAt.toEpochMilli(),
        )
    }

    private val rules = combine(
        profileRepository.observeProfile(),
        profileRepository.observeBlockedApps(),
        profileRepository.observeRanges(),
    ) { profile, apps, ranges -> Triple(profile, apps, ScheduleFormatter.summary(ranges)) }

    val state: StateFlow<SettingsUiState> = combine(
        rules,
        settings.limits,
        settings.theme,
        template,
        preview,
    ) { (profile, apps, schedule), limits, theme, template, preview ->
        SettingsUiState(
            profile = profile,
            apps = apps,
            schedule = schedule,
            limits = limits,
            theme = theme,
            template = template,
            preview = preview?.text,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        nextPreview()
    }

    fun changeExtraTimeDaily(delta: Int) {
        viewModelScope.launch { settings.setExtraTimeDaily(settings.limits.first().extraTimeDaily + delta) }
    }

    fun changeEmergencyDaily(delta: Int) {
        viewModelScope.launch { settings.setEmergencyDaily(settings.limits.first().emergencyDaily + delta) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    fun nextPreview() {
        viewModelScope.launch { preview.value = previewer.random(excludeRaw = preview.value?.raw) }
    }
}
