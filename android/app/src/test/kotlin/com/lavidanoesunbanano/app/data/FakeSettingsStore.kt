package com.lavidanoesunbanano.app.data

import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.domain.model.Consents
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsStore(
    limits: UnlockLimits = UnlockLimits(),
) : SettingsStore {
    override val limits = MutableStateFlow(limits)
    override val profileChangedAt = MutableStateFlow<Long?>(null)
    override val antiSabotage = MutableStateFlow(false)
    override val antiSabotageOffAt = MutableStateFlow<Long?>(null)
    override val blockerSetUp = MutableStateFlow(false)
    override val consents = MutableStateFlow<Consents?>(
        Consents(excuses = true, phoneUsage = true, crossBorder = true, analytics = true),
    )
    override val consentsSynced = MutableStateFlow(false)

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
        antiSabotageOffAt.value = null
    }

    override suspend fun setAntiSabotageOffAt(at: Long?) {
        antiSabotageOffAt.value = at
    }

    override suspend fun markBlockerSetUp() {
        blockerSetUp.value = true
    }

    override suspend fun setConsents(consents: Consents) {
        this.consents.value = consents
        consentsSynced.value = false
    }

    override suspend fun markConsentsSynced() {
        consentsSynced.value = true
    }

    override suspend fun eraseAll() {
        consents.value = null
        consentsSynced.value = false
    }
}
