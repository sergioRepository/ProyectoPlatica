package com.lavidanoesunbanano.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.GhostButton
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTextField
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.core.designsystem.SelectableRow

@Composable
internal fun ColumnScope.StepBody(step: OnboardingStep, state: OnboardingUiState, actions: OnboardingActions) {
    val draft = state.draft
    when (step) {
        OnboardingStep.NAME -> TextStep(
            value = draft.name,
            onValueChange = actions::onNameChange,
            placeholder = stringResource(R.string.onboarding_name_placeholder),
            maxLength = OnboardingRules.MAX_NAME,
            singleLine = true,
            onDone = actions::next,
        )
        OnboardingStep.MAIN_GOAL -> TextStep(
            value = draft.mainGoal,
            onValueChange = actions::onMainGoalChange,
            placeholder = stringResource(R.string.onboarding_goal_placeholder),
            maxLength = OnboardingRules.MAX_GOAL,
            minLines = 2,
        )
        OnboardingStep.SECONDARY_GOALS -> SecondaryGoalsStep(state, actions)
        OnboardingStep.GOAL_REASON -> TextStep(
            value = draft.goalReason,
            onValueChange = actions::onReasonChange,
            placeholder = stringResource(R.string.onboarding_reason_placeholder),
            maxLength = OnboardingRules.MAX_REASON,
            minLines = 3,
        )
        OnboardingStep.APPS -> AppsStep(state, actions)
        OnboardingStep.SCHEDULE -> ScheduleStep(draft.ranges, onAdd = actions::addRange, onRemove = actions::removeRange)
        OnboardingStep.EXCUSES -> TextStep(
            value = draft.typicalExcuses,
            onValueChange = actions::onExcusesChange,
            placeholder = stringResource(R.string.onboarding_excuses_placeholder),
            maxLength = OnboardingRules.MAX_EXCUSES,
            minLines = 3,
        )
        OnboardingStep.SARCASM -> ChoiceList {
            SARCASM_LEVELS.forEach { level ->
                SelectableRow(
                    title = stringResource(level.title),
                    subtitle = stringResource(level.description),
                    selected = draft.sarcasmLevel == level.value,
                    onClick = { actions.onSarcasmChange(level.value) },
                )
            }
        }
        OnboardingStep.PROFANITY -> ChoiceList {
            SelectableRow(
                title = stringResource(R.string.profanity_yes),
                subtitle = stringResource(R.string.profanity_yes_desc),
                selected = draft.profanityAllowed == true,
                onClick = { actions.onProfanityChange(true) },
            )
            SelectableRow(
                title = stringResource(R.string.profanity_no),
                subtitle = stringResource(R.string.profanity_no_desc),
                selected = draft.profanityAllowed == false,
                onClick = { actions.onProfanityChange(false) },
            )
        }
        OnboardingStep.COUNTRY -> ChoiceList {
            OnboardingRules.COUNTRIES.forEach { country ->
                SelectableRow(
                    title = country,
                    selected = draft.country == country,
                    onClick = { actions.onCountryChange(country) },
                )
            }
            SelectableRow(
                title = stringResource(R.string.country_neutral),
                selected = draft.country == OnboardingRules.NEUTRAL_COUNTRY,
                onClick = { actions.onCountryChange(OnboardingRules.NEUTRAL_COUNTRY) },
            )
        }
        OnboardingStep.FORBIDDEN -> TextStep(
            value = draft.forbiddenTopics,
            onValueChange = actions::onForbiddenChange,
            placeholder = stringResource(R.string.onboarding_forbidden_placeholder),
            maxLength = OnboardingRules.MAX_FORBIDDEN,
            minLines = 3,
        )
    }
}

private class SarcasmLevel(val value: Int, @param:StringRes val title: Int, @param:StringRes val description: Int)

private val SARCASM_LEVELS = listOf(
    SarcasmLevel(1, R.string.sarcasm_1, R.string.sarcasm_1_desc),
    SarcasmLevel(2, R.string.sarcasm_2, R.string.sarcasm_2_desc),
    SarcasmLevel(3, R.string.sarcasm_3, R.string.sarcasm_3_desc),
    SarcasmLevel(4, R.string.sarcasm_4, R.string.sarcasm_4_desc),
    SarcasmLevel(5, R.string.sarcasm_5, R.string.sarcasm_5_desc),
)

@Composable
private fun TextStep(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onDone: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaVidaTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        maxLength = maxLength,
        modifier = Modifier.focusRequester(focusRequester),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onNext = { onDone() }),
    )
}

@Composable
private fun ChoiceList(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun SecondaryGoalsStep(state: OnboardingUiState, actions: OnboardingActions) {
    val goals = state.draft.secondaryGoals
    val canAdd = state.secondaryInput.isNotBlank() && goals.size < OnboardingRules.MAX_SECONDARY_GOALS
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.Top) {
            LaVidaTextField(
                value = state.secondaryInput,
                onValueChange = actions::onSecondaryInputChange,
                placeholder = stringResource(R.string.onboarding_secondary_placeholder),
                maxLength = OnboardingRules.MAX_SECONDARY_GOAL,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canAdd) actions.addSecondaryGoal() }),
            )
            Spacer(Modifier.width(8.dp))
            SecondaryButton(
                text = stringResource(R.string.action_add),
                onClick = actions::addSecondaryGoal,
                enabled = canAdd,
            )
        }
        Spacer(Modifier.height(16.dp))
        goals.forEachIndexed { index, goal ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal,
                    modifier = Modifier.weight(1f),
                    style = LaVidaTheme.type.body,
                    color = LaVidaTheme.colors.textPrimary,
                )
                GhostButton(text = stringResource(R.string.action_remove), onClick = { actions.removeSecondaryGoal(index) })
            }
        }
        Spacer(Modifier.height(8.dp))
        MonoLabel("${goals.size} / ${OnboardingRules.MAX_SECONDARY_GOALS}")
    }
}

@Composable
private fun ColumnScope.AppsStep(state: OnboardingUiState, actions: OnboardingActions) {
    LaVidaTextField(
        value = state.appQuery,
        onValueChange = actions::onAppQueryChange,
        placeholder = stringResource(R.string.onboarding_apps_search),
        maxLength = 40,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
    Spacer(Modifier.height(12.dp))
    val selectedCount = state.draft.selectedApps.size
    MonoLabel(pluralStringResource(R.plurals.onboarding_apps_selected, selectedCount, selectedCount))
    Spacer(Modifier.height(12.dp))
    val apps = state.visibleApps
    when {
        apps == null -> MonoLabel(stringResource(R.string.onboarding_apps_loading))
        apps.isEmpty() -> MonoLabel(stringResource(R.string.onboarding_apps_empty))
        else -> LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                SelectableRow(
                    title = app.label,
                    subtitle = app.packageName,
                    monoSubtitle = true,
                    selected = app.packageName in state.selectedPackages,
                    onClick = { actions.toggleApp(app) },
                    multiple = true,
                )
            }
        }
    }
}
