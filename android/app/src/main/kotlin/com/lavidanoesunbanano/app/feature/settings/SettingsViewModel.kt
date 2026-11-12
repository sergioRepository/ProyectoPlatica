package com.lavidanoesunbanano.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.MessagePreview
import com.lavidanoesunbanano.app.data.repository.PrivacyRepository
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.ProtectionRepository
import com.lavidanoesunbanano.app.data.repository.TemplatePreviewer
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.Consents
import com.lavidanoesunbanano.app.domain.model.Profile
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.domain.model.nextBlockChange
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
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
    /** "15 oct"; `null` si no hay plantilla. */
    val generatedAt: String? = null,
    /** El perfil o el tono cambiaron después de generarla. */
    val stale: Boolean = false,
)

/**
 * Anti-autosabotaje y si ahora, en horario de bloqueo, las reglas están bajo llave.
 * `offAt`: cuándo se apaga, si hay una desactivación programada. `scheduleAt`: cuándo se
 * apagaría si se programa ahora. `unlocksAt`: "las 06:00", cuando termina el horario.
 */
data class Protection(
    val on: Boolean = false,
    val locked: Boolean = false,
    val offAt: String? = null,
    val scheduleAt: String = "",
    val unlocksAt: String? = null,
)

data class SettingsUiState(
    val profile: Profile? = null,
    val apps: List<AppInfo> = emptyList(),
    val schedule: String = "",
    val limits: UnlockLimits = UnlockLimits(),
    val protection: Protection = Protection(),
    /** Plan y llamadas de IA de hoy; `null` sin conexión. */
    val device: DeviceStatusDto? = null,
    val consents: Consents? = null,
    val kindMode: Boolean = false,
    val eraseFailed: Boolean = false,
    val template: TemplateInfo = TemplateInfo(),
    val preview: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    templateRepository: TemplateRepository,
    private val settings: SettingsStore,
    private val previewer: TemplatePreviewer,
    private val deviceRepository: DeviceRepository,
    private val privacy: PrivacyRepository,
    private val protectionRepository: ProtectionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val preview = MutableStateFlow<MessagePreview?>(null)
    private val device = MutableStateFlow<DeviceStatusDto?>(null)
    private val eraseFailed = MutableStateFlow(false)

    private val template = combine(
        templateRepository.observeMessageCount(),
        templateRepository.observeGeneratedAt(),
        settings.profileChangedAt,
    ) { count, generatedAt, changedAt ->
        TemplateInfo(
            messageCount = count,
            generatedAt = generatedAt?.let { ScheduleFormatter.shortDate(LocalDateTime.ofInstant(it, clock.zone).toLocalDate()) },
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

    private val protection = combine(
        settings.antiSabotage,
        settings.antiSabotageOffAt,
        profileRepository.observeRanges(),
        now,
    ) { enabled, offAt, ranges, now ->
        val off = offAt?.let(Instant::ofEpochMilli)
        val on = SabotageGuard.protectionOn(enabled, off, clock.instant())
        Protection(
            on = on,
            locked = SabotageGuard.rulesLocked(on, ranges, now),
            offAt = off?.takeIf { on }?.let { ScheduleFormatter.dayTime(LocalDateTime.ofInstant(it, clock.zone)) },
            scheduleAt = ScheduleFormatter.dayTime(now.plus(SabotageGuard.DISABLE_DELAY)),
            unlocksAt = ranges.nextBlockChange(now)?.let { ScheduleFormatter.until(it, now) },
        )
    }

    private val preferences = combine(settings.limits, protection, device) { limits, protection, device ->
        Preferences(limits, protection, device)
    }

    private val privacyState = combine(settings.consents, eraseFailed, settings.kindMode, ::Triple)

    val state: StateFlow<SettingsUiState> = combine(rules, preferences, template, preview, privacyState) { rules, prefs, template, preview, privacyState ->
        val (profile, apps, ranges) = rules
        SettingsUiState(
            profile = profile,
            apps = apps,
            schedule = ScheduleFormatter.summary(ranges),
            limits = prefs.limits,
            protection = prefs.protection,
            device = prefs.device,
            consents = privacyState.first,
            eraseFailed = privacyState.second,
            kindMode = privacyState.third,
            template = template,
            // Los mensajes de la plantilla son sarcásticos: el modo amable no los muestra.
            preview = preview?.text?.takeUnless { privacyState.third },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        nextPreview()
    }

    /** Al volver a la pantalla: las evaluaciones de hoy cambian con cada juicio. */
    fun refreshDevice() {
        viewModelScope.launch { device.value = deviceRepository.status() }
    }

    fun setKindMode(enabled: Boolean) {
        viewModelScope.launch { settings.setKindMode(enabled) }
    }

    fun setExcusesConsent(granted: Boolean) {
        viewModelScope.launch { privacy.update { it.copy(excuses = granted) } }
    }

    fun setAnalyticsConsent(granted: Boolean) {
        viewModelScope.launch { privacy.update { it.copy(analytics = granted) } }
    }

    fun eraseEverything(onDone: () -> Unit) {
        viewModelScope.launch {
            val erased = privacy.eraseEverything()
            eraseFailed.value = !erased
            if (erased) onDone()
        }
    }

    fun changeExtraTimeDaily(delta: Int) = unlessLocked {
        settings.setExtraTimeDaily(settings.limits.first().extraTimeDaily + delta)
    }

    fun changeEmergencyDaily(delta: Int) = unlessLocked {
        settings.setEmergencyDaily(settings.limits.first().emergencyDaily + delta)
    }

    /** Encenderla siempre se puede; apagarla en horario de bloqueo pasa por la salida con fricción. */
    fun setAntiSabotage(enabled: Boolean) = unlessLocked {
        settings.setAntiSabotage(enabled)
    }

    fun disableProtectionNow(typed: String, phrase: String) {
        viewModelScope.launch { protectionRepository.disableNow(typed, phrase) }
    }

    fun scheduleProtectionOff() {
        viewModelScope.launch { protectionRepository.scheduleDisable() }
    }

    fun cancelProtectionOff() {
        viewModelScope.launch { protectionRepository.cancelScheduled() }
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
        val protection: Protection,
        val device: DeviceStatusDto?,
    )

    private companion object {
        const val LOCK_REFRESH_MS = 30_000L
    }
}
