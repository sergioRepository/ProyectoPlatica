package com.lavidanoesunbanano.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.ScreenColumn
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.core.designsystem.StageProgress
import com.lavidanoesunbanano.app.domain.apps.AppOrdering
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }
    BackHandler(enabled = state.canGoBack) { viewModel.back() }
    OnboardingContent(state = state, actions = viewModel)
}

@Composable
private fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions) {
    ScreenColumn(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        StageProgress(
            filled = (state.step.stage?.ordinal ?: 0) + 1,
            total = OnboardingStage.entries.size,
            label = stageLabel(state),
        )
        Spacer(Modifier.height(20.dp))
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
                    text = stepHint(step, state),
                    style = LaVidaTheme.type.body,
                    color = LaVidaTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(20.dp))
                StepBody(step = step, state = state, actions = actions)
            }
        }
        Footer(state, actions)
    }
}

@Composable
private fun Footer(state: OnboardingUiState, actions: OnboardingActions) {
    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.step == OnboardingStep.SCHEDULE && !state.canContinue) {
            Text(
                text = stringResource(R.string.onboarding_schedule_required),
                modifier = Modifier.fillMaxWidth(),
                style = LaVidaTheme.type.caption,
                color = LaVidaTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.canGoBack) {
                SecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = actions::back,
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                text = stringResource(if (state.isLastStep) R.string.onboarding_finish else R.string.action_next),
                onClick = actions::next,
                // En el resumen el botón principal es el doble de ancho.
                modifier = Modifier.weight(if (state.isLastStep) 2f else 1f),
                enabled = state.canContinue,
            )
        }
    }
}

@Composable
private fun stageLabel(state: OnboardingUiState): String = when (state.step.stage) {
    OnboardingStage.BLOCK -> stringResource(R.string.stage_block, state.stagePosition, state.stageSteps.size)
    OnboardingStage.MESSAGES -> stringResource(R.string.stage_messages, state.stagePosition, state.stageSteps.size)
    else -> stringResource(R.string.stage_summary)
}

/** La ayuda del horario nombra las apps que se acaban de elegir. */
@Composable
internal fun stepHint(step: OnboardingStep, state: OnboardingUiState): String {
    val apps = state.draft.selectedApps
    return when {
        step != OnboardingStep.SCHEDULE -> stringResource(step.hintRes)
        apps.isEmpty() -> stringResource(R.string.onboarding_schedule_hint_generic)
        else -> stringResource(R.string.onboarding_schedule_hint, AppOrdering.names(apps.map { it.label }))
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
        OnboardingStep.SUMMARY -> R.string.onboarding_summary_title
    }

@get:StringRes
private val OnboardingStep.hintRes: Int
    get() = when (this) {
        OnboardingStep.NAME -> R.string.onboarding_name_hint
        OnboardingStep.MAIN_GOAL -> R.string.onboarding_goal_hint
        OnboardingStep.SECONDARY_GOALS -> R.string.onboarding_secondary_hint
        OnboardingStep.GOAL_REASON -> R.string.onboarding_reason_hint
        OnboardingStep.APPS -> R.string.onboarding_apps_hint
        OnboardingStep.SCHEDULE -> R.string.onboarding_schedule_hint_generic
        OnboardingStep.EXCUSES -> R.string.onboarding_excuses_hint
        OnboardingStep.SARCASM -> R.string.onboarding_sarcasm_hint
        OnboardingStep.PROFANITY -> R.string.onboarding_profanity_hint
        OnboardingStep.COUNTRY -> R.string.onboarding_country_hint
        OnboardingStep.FORBIDDEN -> R.string.onboarding_forbidden_hint
        OnboardingStep.SUMMARY -> R.string.onboarding_summary_hint
    }

private object PreviewActions : OnboardingActions {
    override fun next() = Unit
    override fun back() = Unit
    override fun review(step: OnboardingStep, until: OnboardingStep) = Unit
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
                stepIndex = OnboardingStep.FLOW.indexOf(OnboardingStep.SCHEDULE),
                draft = OnboardingDraft(ranges = listOf(SchedulePresets.WORK, SchedulePresets.NIGHTS)),
            ),
            actions = PreviewActions,
        )
    }
}
