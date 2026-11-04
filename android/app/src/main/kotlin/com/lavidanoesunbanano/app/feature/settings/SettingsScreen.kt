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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.lavidanoesunbanano.app.core.designsystem.GhostButton
import com.lavidanoesunbanano.app.core.designsystem.InfoRow
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.LinkRow
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PolicyLink
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.core.designsystem.SelectableRow
import com.lavidanoesunbanano.app.core.designsystem.StepperRow
import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.feature.block.SabotageExitForm
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingRules
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingStep

@Composable
fun SettingsScreen(
    onEditStep: (OnboardingStep) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSetup: () -> Unit,
    onErased: () -> Unit,
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
            onSelectPlan = viewModel::selectPlan,
            onWouldPay = viewModel::answerWouldPay,
            onExcusesConsent = viewModel::setExcusesConsent,
            onAnalyticsConsent = viewModel::setAnalyticsConsent,
            onKindMode = viewModel::setKindMode,
            onErase = { viewModel.eraseEverything(onErased) },
            onDisableProtection = viewModel::disableProtectionNow,
            onScheduleProtectionOff = viewModel::scheduleProtectionOff,
            onCancelProtectionOff = viewModel::cancelProtectionOff,
            onOpenSetup = onOpenSetup,
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
    val onSelectPlan: (String) -> Unit,
    val onWouldPay: (String) -> Unit,
    val onExcusesConsent: (Boolean) -> Unit,
    val onAnalyticsConsent: (Boolean) -> Unit,
    val onKindMode: (Boolean) -> Unit,
    val onErase: () -> Unit,
    val onDisableProtection: (String) -> Unit,
    val onScheduleProtectionOff: () -> Unit,
    val onCancelProtectionOff: () -> Unit,
    val onOpenSetup: () -> Unit,
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

        Section(R.string.settings_section_plan) {
            PlanSection(state.device, actions)
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
            ProtectionRow(state.protection, actions)
        }

        Section(R.string.settings_section_tone) {
            SelectableRow(
                title = stringResource(R.string.settings_kind_mode),
                subtitle = stringResource(R.string.settings_kind_mode_desc),
                selected = state.kindMode,
                onClick = { actions.onKindMode(!state.kindMode) },
                multiple = true,
            )
            LinkRow(
                title = stringResource(R.string.settings_sarcasm),
                value = profile?.let { stringResource(sarcasmLabel(it.sarcasmLevel)) },
                onClick = { onEditStep(OnboardingStep.SARCASM) },
            )
            Text(
                text = stringResource(R.string.settings_sarcasm_hint),
                style = LaVidaTheme.type.small,
                color = colors.textSecondary,
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

        Section(R.string.settings_section_privacy) {
            PrivacySection(state, actions)
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

/** Encendida y en horario de bloqueo, apagarla pasa por la salida con fricción. */
@Composable
private fun ProtectionRow(protection: Protection, actions: SettingsActions) {
    var exiting by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    SelectableRow(
        title = stringResource(R.string.settings_anti_sabotage),
        subtitle = stringResource(R.string.settings_anti_sabotage_desc),
        selected = protection.on,
        onClick = { if (protection.locked) exiting = true else actions.onAntiSabotageChange(!protection.on) },
        multiple = true,
    )
    protection.offAt?.let { offAt ->
        Text(
            text = stringResource(R.string.sabotage_exit_scheduled, offAt),
            style = LaVidaTheme.type.small,
            color = LaVidaTheme.colors.textSecondary,
        )
        GhostButton(
            text = stringResource(R.string.sabotage_exit_cancel),
            onClick = actions.onCancelProtectionOff,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (exiting) {
        AlertDialog(
            onDismissRequest = { exiting = false },
            title = { Text(stringResource(R.string.sabotage_exit_title)) },
            text = {
                SabotageExitForm(
                    reason = reason,
                    scheduledAt = protection.offAt,
                    onReasonChange = { reason = it },
                    onDisableNow = {
                        actions.onDisableProtection(reason)
                        exiting = false
                        reason = ""
                    },
                    onSchedule = {
                        actions.onScheduleProtectionOff()
                        exiting = false
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { exiting = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PrivacySection(state: SettingsUiState, actions: SettingsActions) {
    val colors = LaVidaTheme.colors
    var confirming by rememberSaveable { mutableStateOf(false) }
    state.consents?.let { consents ->
        SelectableRow(
            title = stringResource(R.string.settings_consent_excuses),
            subtitle = stringResource(R.string.settings_consent_excuses_desc),
            selected = consents.excuses,
            onClick = { actions.onExcusesConsent(!consents.excuses) },
            multiple = true,
        )
        SelectableRow(
            title = stringResource(R.string.settings_consent_analytics),
            subtitle = stringResource(R.string.settings_consent_analytics_desc),
            selected = consents.analytics,
            onClick = { actions.onAnalyticsConsent(!consents.analytics) },
            multiple = true,
        )
    }
    PolicyLink()
    SecondaryButton(
        text = stringResource(R.string.settings_erase),
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.eraseFailed) {
        Text(text = stringResource(R.string.settings_erase_failed), style = LaVidaTheme.type.small, color = colors.rejected)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_erase_title)) },
            text = { Text(stringResource(R.string.settings_erase_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        actions.onErase()
                    },
                ) { Text(stringResource(R.string.settings_erase_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private val PLANS = listOf(
    "free" to R.string.plan_free,
    "monthly" to R.string.plan_monthly,
    "semester" to R.string.plan_semester,
)

private val WOULD_PAY = listOf(
    "yes" to R.string.would_pay_yes,
    "no" to R.string.would_pay_no,
    "maybe" to R.string.would_pay_maybe,
)

@Composable
private fun PlanSection(device: DeviceStatusDto?, actions: SettingsActions) {
    val colors = LaVidaTheme.colors
    if (device == null) {
        Text(text = stringResource(R.string.settings_plan_offline), style = LaVidaTheme.type.small, color = colors.textSecondary)
        return
    }
    Panel {
        InfoRow(
            label = stringResource(R.string.ai_calls_label),
            value = stringResource(R.string.ai_calls_value, device.remainingToday, device.dailyLimit),
        )
    }
    PLANS.forEach { (plan, label) ->
        SelectableRow(
            title = stringResource(label),
            selected = device.plan == plan,
            onClick = { actions.onSelectPlan(plan) },
        )
    }
    Text(text = stringResource(R.string.settings_plan_hint), style = LaVidaTheme.type.small, color = colors.textSecondary)
    Spacer(Modifier.height(8.dp))
    Text(text = stringResource(R.string.settings_would_pay), style = LaVidaTheme.type.body, color = colors.textPrimary)
    WOULD_PAY.forEach { (answer, label) ->
        SelectableRow(
            title = stringResource(label),
            selected = device.wouldPay == answer,
            onClick = { actions.onWouldPay(answer) },
        )
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
