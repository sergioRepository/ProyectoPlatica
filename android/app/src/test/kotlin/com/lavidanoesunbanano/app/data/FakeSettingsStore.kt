package com.lavidanoesunbanano.app.data

import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsStore(
    limits: UnlockLimits = UnlockLimits(),
) : SettingsStore {
    override val limits = MutableStateFlow(limits)
    override val profileChangedAt = MutableStateFlow<Long?>(null)
    override val antiSabotage = MutableStateFlow(false)
    override val blockerSetUp = MutableStateFlow(false)

    override suspend fun setExtraTimeDaily(value: Int) {
        limits.value = limits.value.copy(extraTimeDaily = value)
    }

    override suspend fun setEmergencyDaily(value: Int) {
        limits.value = limits.value.copy(emergencyDaily = value)
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
