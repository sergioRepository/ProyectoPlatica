package com.lavidanoesunbanano.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.repository.MessagePreview
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.TemplatePreviewer
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import com.lavidanoesunbanano.app.data.system.SetupStatusSource
import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.Profile
import com.lavidanoesunbanano.app.domain.model.ThemeMode
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.setup.SetupChecklist
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateInfo(
    val messageCount: Int = 0,
    /** "15 oct 08:00"; `null` si no hay plantilla. */
    val generatedAt: String? = null,
    /** El perfil o el tono cambiaron después de generarla. */
    val stale: Boolean = false,
)

/** Anti-autosabotaje y si ahora, en horario de bloqueo, las reglas están bajo llave. */
data class Protection(val on: Boolean = false, val locked: Boolean = false)

data class SettingsUiState(
    val profile: Profile? = null,
    val apps: List<AppInfo> = emptyList(),
    val schedule: String = "",
    val limits: UnlockLimits = UnlockLimits(),
    val theme: ThemeMode = ThemeMode.DARK,
    val protection: Protection = Protection(),
    /** Pasos de la guía que faltan; `null` antes de revisar. */
    val setupPending: Int? = null,
    val template: TemplateInfo = TemplateInfo(),
    val preview: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    templateRepository: TemplateRepository,
    private val settings: SettingsStore,
    private val previewer: TemplatePreviewer,
    private val setupSource: SetupStatusSource,
    private val clock: Clock,
) : ViewModel() {

    private val preview = MutableStateFlow<MessagePreview?>(null)
    private val setupPending = MutableStateFlow<Int?>(null)

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
    ) { profile, apps, ranges -> Triple(profile, apps, ranges) }

    /** El candado depende de la hora: se revisa cada 30 s mientras la pantalla está abierta. */
    private val now: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(LOCK_REFRESH_MS)
        }
    }

    private val protection = combine(settings.antiSabotage, profileRepository.observeRanges(), now) { on, ranges, now ->
        Protection(on = on, locked = SabotageGuard.rulesLocked(on, ranges, now))
    }

    private val preferences = combine(settings.limits, settings.theme, protection, setupPending) { limits, theme, protection, pending ->
        Preferences(limits, theme, protection, pending)
    }

    val state: StateFlow<SettingsUiState> = combine(rules, preferences, template, preview) { rules, prefs, template, preview ->
        val (profile, apps, ranges) = rules
        SettingsUiState(
            profile = profile,
            apps = apps,
            schedule = ScheduleFormatter.summary(ranges),
            limits = prefs.limits,
            theme = prefs.theme,
            protection = prefs.protection,
            setupPending = prefs.setupPending,
            template = template,
            preview = preview?.text,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        nextPreview()
    }

    /** Al volver a la pantalla: los permisos se cambian fuera de la app. */
    fun refreshSetup() {
        setupPending.value = SetupChecklist.pending(SetupChecklist.items(setupSource.current()))
    }

    fun changeExtraTimeDaily(delta: Int) = unlessLocked {
        settings.setExtraTimeDaily(settings.limits.first().extraTimeDaily + delta)
    }

    fun changeEmergencyDaily(delta: Int) = unlessLocked {
        settings.setEmergencyDaily(settings.limits.first().emergencyDaily + delta)
    }

    /** Encenderla siempre se puede; apagarla, solo fuera del horario de bloqueo. */
    fun setAntiSabotage(enabled: Boolean) = unlessLocked {
        settings.setAntiSabotage(enabled)
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    fun nextPreview() {
        viewModelScope.launch { preview.value = previewer.random(excludeRaw = preview.value?.raw) }
    }

    private fun unlessLocked(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!state.value.protection.locked) block()
        }
    }

    private data class Preferences(
        val limits: UnlockLimits,
        val theme: ThemeMode,
        val protection: Protection,
        val setupPending: Int?,
    )

    private companion object {
        const val LOCK_REFRESH_MS = 30_000L
    }
}
