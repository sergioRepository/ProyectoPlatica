package com.lavidanoesunbanano.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.GroupedCard
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTextField
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.RowDivider
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.core.designsystem.SelectableRow
import com.lavidanoesunbanano.app.core.designsystem.TextLink
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter

@Composable
internal fun ColumnScope.StepBody(step: OnboardingStep, state: OnboardingUiState, actions: OnboardingActions) {
    val draft = state.draft
    // Editar la meta exige un texto válido: el error se ve aquí, junto al contador.
    val goalError = stringResource(R.string.onboarding_goal_short).takeIf {
        draft.mainGoal.trim().length < OnboardingRules.MIN_GOAL && draft.mainGoal.isNotEmpty()
    }
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
            minLines = 3,
            error = goalError,
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
            if (draft.sarcasmLevel >= OnboardingRules.INSULTS_FROM_LEVEL) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_sarcasm_insults_warning),
                    style = LaVidaTheme.type.small,
                    color = LaVidaTheme.colors.rejected,
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
        OnboardingStep.SUMMARY -> SummaryStep(draft, actions)
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
private fun SummaryStep(draft: OnboardingDraft, actions: OnboardingActions) {
    val country = if (draft.country == OnboardingRules.NEUTRAL_COUNTRY) stringResource(R.string.country_neutral) else draft.country
    val profanity = stringResource(if (draft.profanityAllowed == true) R.string.summary_with_profanity else R.string.summary_without_profanity)
    // El margen deja ver la sombra de la tarjeta, que el desplazamiento recortaría.
    Column(Modifier.verticalScroll(rememberScrollState()).padding(end = 3.dp, bottom = 4.dp)) {
        GroupedCard {
            SummaryRow(R.string.summary_apps, draft.selectedApps.joinToString(", ") { it.label }) {
                actions.review(OnboardingStep.APPS)
            }
            RowDivider()
            SummaryRow(R.string.summary_schedule, ScheduleFormatter.summary(draft.ranges)) {
                actions.review(OnboardingStep.SCHEDULE)
            }
            RowDivider()
            SummaryRow(R.string.summary_goal, draft.mainGoal) { actions.review(OnboardingStep.MAIN_GOAL) }
            RowDivider()
            SummaryRow(R.string.summary_tone, stringResource(R.string.summary_tone_value, draft.sarcasmLevel, profanity, country)) {
                actions.review(OnboardingStep.SARCASM, until = OnboardingStep.COUNTRY)
            }
            RowDivider()
            SummaryRow(R.string.summary_forbidden, draft.forbiddenTopics.ifBlank { stringResource(R.string.settings_none) }) {
                actions.review(OnboardingStep.FORBIDDEN)
            }
        }
    }
}

@Composable
private fun SummaryRow(@StringRes label: Int, value: String, onChange: () -> Unit) {
    val name = stringResource(label)
    val changeLabel = stringResource(R.string.summary_change_label, name.lowercase())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MonoLabel(name)
            Text(
                text = value,
                style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = LaVidaTheme.colors.textPrimary,
            )
        }
        TextLink(
            text = stringResource(R.string.summary_change),
            onClick = onChange,
            modifier = Modifier.semantics { contentDescription = changeLabel },
        )
    }
}

@Composable
private fun TextStep(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    singleLine: Boolean = false,
    minLines: Int = 1,
    error: String? = null,
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
        error = error,
        textStyle = LaVidaTheme.type.body.copy(fontSize = LaVidaTheme.type.subhead.fontSize),
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        // Lugar para la sombra de la última tarjeta.
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SecondaryGoalsStep(state: OnboardingUiState, actions: OnboardingActions) {
    val goals = state.draft.secondaryGoals
    val canAdd = state.secondaryInput.isNotBlank() && goals.size < OnboardingRules.MAX_SECONDARY_GOALS
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                TextLink(text = stringResource(R.string.action_remove), onClick = { actions.removeSecondaryGoal(index) })
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
