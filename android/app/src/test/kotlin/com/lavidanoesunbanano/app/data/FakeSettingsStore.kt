package com.lavidanoesunbanano.app.data

import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.domain.model.ThemeMode
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsStore(
    limits: UnlockLimits = UnlockLimits(),
    theme: ThemeMode = ThemeMode.DARK,
) : SettingsStore {
    override val limits = MutableStateFlow(limits)
    override val theme = MutableStateFlow(theme)
    override val profileChangedAt = MutableStateFlow<Long?>(null)
    override val antiSabotage = MutableStateFlow(false)
    override val blockerSetUp = MutableStateFlow(false)

    override suspend fun setExtraTimeDaily(value: Int) {
        limits.value = limits.value.copy(extraTimeDaily = value)
    }

    override suspend fun setEmergencyDaily(value: Int) {
        limits.value = limits.value.copy(emergencyDaily = value)
    }

    override suspend fun setTheme(mode: ThemeMode) {
        theme.value = mode
    }

    override suspend fun markProfileChanged(at: Long) {
        profileChangedAt.value = at
    }

    override suspend fun setAntiSabotage(enabled: Boolean) {
        antiSabotage.value = enabled
    }

    override suspend fun markBlockerSetUp() {
        blockerSetUp.value = true
    }
}
