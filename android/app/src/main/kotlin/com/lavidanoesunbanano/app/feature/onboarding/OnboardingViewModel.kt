package com.lavidanoesunbanano.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.apps.InstalledAppsSource
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val installedAppsSource: InstalledAppsSource,
) : ViewModel(), OnboardingActions {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        // Si ya hay respuestas guardadas (por ejemplo, al volver desde la generación), se precargan.
        viewModelScope.launch {
            val profile = profileRepository.getProfile() ?: return@launch
            val draft = OnboardingRules.fromSaved(
                profile,
                profileRepository.getBlockedApps(),
                profileRepository.getRanges(),
            )
            _state.update { it.copy(draft = draft) }
        }
        viewModelScope.launch {
            val apps = installedAppsSource.launchableApps()
            _state.update { it.copy(installedApps = apps) }
        }
    }

    override fun next() {
        val current = _state.value
        if (!current.canContinue) return
        if (current.isLastStep) finish() else _state.update { it.copy(stepIndex = it.stepIndex + 1) }
    }

    override fun back() {
        _state.update { if (it.stepIndex > 0) it.copy(stepIndex = it.stepIndex - 1) else it }
    }

    private fun updateDraft(transform: (OnboardingDraft) -> OnboardingDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    override fun onNameChange(value: String) = updateDraft { it.copy(name = value) }

    override fun onMainGoalChange(value: String) = updateDraft { it.copy(mainGoal = value) }

    override fun onSecondaryInputChange(value: String) = _state.update { it.copy(secondaryInput = value) }

    override fun addSecondaryGoal() {
        _state.update { current ->
            val goal = current.secondaryInput.trim()
            val goals = current.draft.secondaryGoals
            if (goal.isEmpty() || goals.size >= OnboardingRules.MAX_SECONDARY_GOALS) return@update current
            current.copy(draft = current.draft.copy(secondaryGoals = goals + goal), secondaryInput = "")
        }
    }

    override fun removeSecondaryGoal(index: Int) = updateDraft {
        it.copy(secondaryGoals = it.secondaryGoals.filterIndexed { i, _ -> i != index })
    }

    override fun onReasonChange(value: String) = updateDraft { it.copy(goalReason = value) }

    override fun onAppQueryChange(value: String) = _state.update { it.copy(appQuery = value) }

    override fun toggleApp(app: AppInfo) = updateDraft { draft ->
        val selected = draft.selectedApps.any { it.packageName == app.packageName }
        draft.copy(
            selectedApps = if (selected) {
                draft.selectedApps.filterNot { it.packageName == app.packageName }
            } else {
                draft.selectedApps + app
            },
        )
    }

    override fun addRange(range: BlockRange) = updateDraft {
        if (range in it.ranges) it else it.copy(ranges = it.ranges + range)
    }

    override fun removeRange(index: Int) = updateDraft {
        it.copy(ranges = it.ranges.filterIndexed { i, _ -> i != index })
    }

    override fun onExcusesChange(value: String) = updateDraft { it.copy(typicalExcuses = value) }

    override fun onSarcasmChange(level: Int) = updateDraft { it.copy(sarcasmLevel = level) }

    override fun onProfanityChange(allowed: Boolean) = updateDraft { it.copy(profanityAllowed = allowed) }

    override fun onCountryChange(country: String) = updateDraft { it.copy(country = country) }

    override fun onForbiddenChange(value: String) = updateDraft { it.copy(forbiddenTopics = value) }

    private fun finish() {
        val draft = _state.value.draft
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            profileRepository.saveOnboarding(OnboardingRules.toProfile(draft), draft.selectedApps, draft.ranges)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }
}
