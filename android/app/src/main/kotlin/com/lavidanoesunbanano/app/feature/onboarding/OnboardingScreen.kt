package com.lavidanoesunbanano.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.GhostButton
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.StepProgress
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }
    BackHandler(enabled = state.stepIndex > 0) { viewModel.back() }
    OnboardingContent(state = state, actions = viewModel)
}

@Composable
private fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LaVidaTheme.colors.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        StepProgress(current = state.stepIndex + 1, total = state.totalSteps)
        Spacer(Modifier.height(32.dp))
        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            transitionSpec = { FadeIn togetherWith FadeOut },
            label = "paso",
        ) { step ->
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(step.titleRes),
                    style = LaVidaTheme.type.title,
                    color = LaVidaTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(step.hintRes),
                    style = LaVidaTheme.type.small,
                    color = LaVidaTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(24.dp))
                StepBody(step = step, state = state, actions = actions)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.stepIndex > 0) {
                GhostButton(text = stringResource(R.string.action_back), onClick = actions::back)
            } else {
                Spacer(Modifier.height(1.dp))
            }
            PrimaryButton(
                text = stringResource(if (state.isLastStep) R.string.onboarding_finish else R.string.action_next),
                onClick = actions::next,
                enabled = state.canContinue,
            )
        }
    }
}

@get:StringRes
internal val OnboardingStep.titleRes: Int
    get() = when (this) {
        OnboardingStep.NAME -> R.string.onboarding_name_title
        OnboardingStep.MAIN_GOAL -> R.string.onboarding_goal_title
        OnboardingStep.SECONDARY_GOALS -> R.string.onboarding_secondary_title
        OnboardingStep.GOAL_REASON -> R.string.onboarding_reason_title
        OnboardingStep.APPS -> R.string.onboarding_apps_title
        OnboardingStep.SCHEDULE -> R.string.onboarding_schedule_title
        OnboardingStep.EXCUSES -> R.string.onboarding_excuses_title
        OnboardingStep.SARCASM -> R.string.onboarding_sarcasm_title
        OnboardingStep.PROFANITY -> R.string.onboarding_profanity_title
        OnboardingStep.COUNTRY -> R.string.onboarding_country_title
        OnboardingStep.FORBIDDEN -> R.string.onboarding_forbidden_title
    }

@get:StringRes
internal val OnboardingStep.hintRes: Int
    get() = when (this) {
        OnboardingStep.NAME -> R.string.onboarding_name_hint
        OnboardingStep.MAIN_GOAL -> R.string.onboarding_goal_hint
        OnboardingStep.SECONDARY_GOALS -> R.string.onboarding_secondary_hint
        OnboardingStep.GOAL_REASON -> R.string.onboarding_reason_hint
        OnboardingStep.APPS -> R.string.onboarding_apps_hint
        OnboardingStep.SCHEDULE -> R.string.onboarding_schedule_hint
        OnboardingStep.EXCUSES -> R.string.onboarding_excuses_hint
        OnboardingStep.SARCASM -> R.string.onboarding_sarcasm_hint
        OnboardingStep.PROFANITY -> R.string.onboarding_profanity_hint
        OnboardingStep.COUNTRY -> R.string.onboarding_country_hint
        OnboardingStep.FORBIDDEN -> R.string.onboarding_forbidden_hint
    }

private object PreviewActions : OnboardingActions {
    override fun next() = Unit
    override fun back() = Unit
    override fun onNameChange(value: String) = Unit
    override fun onMainGoalChange(value: String) = Unit
    override fun onSecondaryInputChange(value: String) = Unit
    override fun addSecondaryGoal() = Unit
    override fun removeSecondaryGoal(index: Int) = Unit
    override fun onReasonChange(value: String) = Unit
    override fun onAppQueryChange(value: String) = Unit
    override fun toggleApp(app: AppInfo) = Unit
    override fun addRange(range: BlockRange) = Unit
    override fun removeRange(index: Int) = Unit
    override fun onExcusesChange(value: String) = Unit
    override fun onSarcasmChange(level: Int) = Unit
    override fun onProfanityChange(allowed: Boolean) = Unit
    override fun onCountryChange(country: String) = Unit
    override fun onForbiddenChange(value: String) = Unit
}

@Preview
@Composable
private fun OnboardingSchedulePreview() {
    LaVidaTheme {
        OnboardingContent(
            state = OnboardingUiState(
                stepIndex = OnboardingStep.SCHEDULE.ordinal,
                draft = OnboardingDraft(ranges = listOf(SchedulePresets.WORK, SchedulePresets.NIGHTS)),
            ),
            actions = PreviewActions,
        )
    }
}
