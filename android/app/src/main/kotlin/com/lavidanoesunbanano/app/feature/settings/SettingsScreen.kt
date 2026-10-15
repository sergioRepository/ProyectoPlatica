package com.lavidanoesunbanano.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.InfoRow
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.LinkRow
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.core.designsystem.SelectableRow
import com.lavidanoesunbanano.app.core.designsystem.StepperRow
import com.lavidanoesunbanano.app.domain.model.ThemeMode
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingRules
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingStep

@Composable
fun SettingsScreen(
    onEditStep: (OnboardingStep) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSetup() }
    SettingsContent(
        state = state,
        actions = SettingsActions(
            onEditStep = onEditStep,
            onExtraTimeChange = viewModel::changeExtraTimeDaily,
            onEmergencyChange = viewModel::changeEmergencyDaily,
            onAntiSabotageChange = viewModel::setAntiSabotage,
            onOpenSetup = onOpenSetup,
            onThemeChange = viewModel::setTheme,
            onNextPreview = viewModel::nextPreview,
            onRegenerate = onRegenerate,
        ),
    )
}

private class SettingsActions(
    val onEditStep: (OnboardingStep) -> Unit,
    val onExtraTimeChange: (Int) -> Unit,
    val onEmergencyChange: (Int) -> Unit,
    val onAntiSabotageChange: (Boolean) -> Unit,
    val onOpenSetup: () -> Unit,
    val onThemeChange: (ThemeMode) -> Unit,
    val onNextPreview: () -> Unit,
    val onRegenerate: () -> Unit,
)

@Composable
private fun SettingsContent(state: SettingsUiState, actions: SettingsActions) {
    val colors = LaVidaTheme.colors
    val profile = state.profile
    val locked = state.protection.locked
    val onEditStep = actions.onEditStep
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.settings_title), style = LaVidaTheme.type.title, color = colors.textPrimary)

        Section(R.string.settings_section_rules) {
            if (locked) {
                Text(text = stringResource(R.string.settings_locked), style = LaVidaTheme.type.small, color = colors.rejected)
            }
            LinkRow(
                title = stringResource(R.string.settings_apps),
                value = state.apps.joinToString(", ") { it.label },
                onClick = { onEditStep(OnboardingStep.APPS) },
                enabled = !locked,
            )
            LinkRow(
                title = stringResource(R.string.settings_schedule),
                value = state.schedule,
                monoValue = true,
                onClick = { onEditStep(OnboardingStep.SCHEDULE) },
                enabled = !locked,
            )
        }

        Section(R.string.settings_section_quotas) {
            val limits = state.limits
            StepperRow(
                title = stringResource(R.string.settings_extra_time),
                value = stringResource(R.string.settings_quota_value, limits.extraTimeDaily),
                onDecrease = { actions.onExtraTimeChange(-1) },
                onIncrease = { actions.onExtraTimeChange(1) },
                canDecrease = !locked && limits.extraTimeDaily > 0,
                canIncrease = !locked && limits.extraTimeDaily < UnlockLimits.MAX_EXTRA_TIME_DAILY,
            )
            StepperRow(
                title = stringResource(R.string.settings_emergency),
                value = stringResource(R.string.settings_quota_value, limits.emergencyDaily),
                onDecrease = { actions.onEmergencyChange(-1) },
                onIncrease = { actions.onEmergencyChange(1) },
                canDecrease = !locked && limits.emergencyDaily > 0,
                canIncrease = !locked && limits.emergencyDaily < UnlockLimits.MAX_EMERGENCY_DAILY,
            )
            Text(
                text = stringResource(R.string.settings_quotas_hint),
                style = LaVidaTheme.type.small,
                color = colors.textSecondary,
            )
        }

        Section(R.string.settings_section_protection) {
            LinkRow(
                title = stringResource(R.string.setup_link),
                value = state.setupPending?.let { pending ->
                    if (pending == 0) {
                        stringResource(R.string.setup_all_done)
                    } else {
                        pluralStringResource(R.plurals.setup_pending_count, pending, pending)
                    }
                },
                onClick = actions.onOpenSetup,
            )
            SelectableRow(
                title = stringResource(R.string.settings_anti_sabotage),
                subtitle = stringResource(R.string.settings_anti_sabotage_desc),
                selected = state.protection.on,
                onClick = { actions.onAntiSabotageChange(!state.protection.on) },
                multiple = true,
                enabled = !locked,
            )
        }

        Section(R.string.settings_section_tone) {
            LinkRow(
                title = stringResource(R.string.settings_sarcasm),
                value = profile?.let { stringResource(sarcasmLabel(it.sarcasmLevel)) },
                onClick = { onEditStep(OnboardingStep.SARCASM) },
            )
            LinkRow(
                title = stringResource(R.string.settings_profanity),
                value = profile?.let { stringResource(if (it.profanityAllowed) R.string.profanity_yes else R.string.profanity_no) },
                onClick = { onEditStep(OnboardingStep.PROFANITY) },
            )
            LinkRow(
                title = stringResource(R.string.settings_country),
                value = profile?.country?.let {
                    if (it == OnboardingRules.NEUTRAL_COUNTRY) stringResource(R.string.country_neutral) else it
                },
                onClick = { onEditStep(OnboardingStep.COUNTRY) },
            )
            LinkRow(
                title = stringResource(R.string.settings_forbidden),
                value = profile?.forbiddenTopics?.ifBlank { stringResource(R.string.settings_none) },
                onClick = { onEditStep(OnboardingStep.FORBIDDEN) },
            )
        }

        Section(R.string.settings_section_profile) {
            LinkRow(
                title = stringResource(R.string.settings_name),
                value = profile?.name,
                onClick = { onEditStep(OnboardingStep.NAME) },
            )
            LinkRow(
                title = stringResource(R.string.settings_goal),
                value = profile?.mainGoal,
                onClick = { onEditStep(OnboardingStep.MAIN_GOAL) },
            )
            LinkRow(
                title = stringResource(R.string.settings_secondary),
                value = profile?.secondaryGoals?.joinToString("; ")?.ifBlank { stringResource(R.string.settings_none) },
                onClick = { onEditStep(OnboardingStep.SECONDARY_GOALS) },
            )
            LinkRow(
                title = stringResource(R.string.settings_reason),
                value = profile?.goalReason,
                onClick = { onEditStep(OnboardingStep.GOAL_REASON) },
            )
            LinkRow(
                title = stringResource(R.string.settings_excuses),
                value = profile?.typicalExcuses,
                onClick = { onEditStep(OnboardingStep.EXCUSES) },
            )
        }

        Section(R.string.settings_section_theme) {
            ThemeMode.entries.forEach { mode ->
                SelectableRow(
                    title = stringResource(themeLabel(mode)),
                    selected = state.theme == mode,
                    onClick = { actions.onThemeChange(mode) },
                )
            }
        }

        Section(R.string.settings_section_template) {
            Panel {
                InfoRow(label = stringResource(R.string.home_messages_label), value = state.template.messageCount.toString())
                InfoRow(
                    label = stringResource(R.string.settings_template_generated),
                    value = state.template.generatedAt ?: "-",
                )
            }
            if (state.template.stale) {
                Text(
                    text = stringResource(R.string.settings_template_stale),
                    style = LaVidaTheme.type.small,
                    color = colors.rejected,
                )
            }
            state.preview?.let { preview ->
                Panel {
                    MonoLabel(stringResource(R.string.home_preview_label))
                    AnimatedContent(
                        targetState = preview,
                        transitionSpec = { FadeIn togetherWith FadeOut },
                        label = "vista previa",
                    ) { text ->
                        Text(text = text, style = LaVidaTheme.type.title, color = colors.textPrimary)
                    }
                }
                SecondaryButton(text = stringResource(R.string.home_preview_next), onClick = actions.onNextPreview)
            }
            Text(
                text = stringResource(R.string.settings_regenerate_hint),
                style = LaVidaTheme.type.small,
                color = colors.textSecondary,
            )
            PrimaryButton(
                text = stringResource(R.string.settings_regenerate),
                onClick = actions.onRegenerate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Section(@StringRes label: Int, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(32.dp))
    MonoLabel(stringResource(label))
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@StringRes
private fun sarcasmLabel(level: Int): Int = when (level) {
    1 -> R.string.sarcasm_1
    2 -> R.string.sarcasm_2
    3 -> R.string.sarcasm_3
    4 -> R.string.sarcasm_4
    else -> R.string.sarcasm_5
}

@StringRes
private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.SYSTEM -> R.string.theme_system
}
