package com.lavidanoesunbanano.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.CheckRow
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.GroupedCard
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.LinkRow
import com.lavidanoesunbanano.app.core.designsystem.MainScaffold
import com.lavidanoesunbanano.app.core.designsystem.MainTab
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Notice
import com.lavidanoesunbanano.app.core.designsystem.NoticeTone
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PolicyLink
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.RowDivider
import com.lavidanoesunbanano.app.core.designsystem.SelectableRow
import com.lavidanoesunbanano.app.core.designsystem.SmallButton
import com.lavidanoesunbanano.app.core.designsystem.StepperRow
import com.lavidanoesunbanano.app.core.designsystem.TextLink
import com.lavidanoesunbanano.app.core.designsystem.UiIcon
import com.lavidanoesunbanano.app.core.designsystem.dashed
import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.feature.block.SabotageExitForm
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingRules
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingStep

@Composable
fun SettingsScreen(
    onTab: (MainTab) -> Unit,
    onEditStep: (OnboardingStep) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSetup: () -> Unit,
    onErased: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDevice() }
    MainScaffold(MainTab.SETTINGS, onTab) {
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
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = LaVidaTheme.type.screenTitle,
            color = LaVidaTheme.colors.textPrimary,
        )
        BlockSection(state, actions)
        MessagesSection(state, actions)
        ProfileSection(state, actions)
        AccountSection(state, actions)
    }
}

@Composable
private fun Section(@StringRes label: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = stringResource(label), style = LaVidaTheme.type.section, color = LaVidaTheme.colors.textPrimary)
        content()
    }
}

@Composable
private fun BlockSection(state: SettingsUiState, actions: SettingsActions) {
    val locked = state.protection.locked
    val unlocksAt = state.protection.unlocksAt
    val limits = state.limits
    Section(R.string.settings_section_block) {
        if (locked && unlocksAt != null) {
            Notice(tone = NoticeTone.INFO) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiIcon(R.drawable.ic_ui_lock, size = 16.dp, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        text = stringResource(R.string.settings_locked, unlocksAt),
                        style = LaVidaTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = LaVidaTheme.colors.textPrimary,
                    )
                }
            }
        }
        val note = unlocksAt?.takeIf { locked }?.let { stringResource(R.string.settings_locked_row, it) }
        GroupedCard {
            LinkRow(
                title = stringResource(R.string.settings_apps),
                value = state.apps.joinToString(", ") { it.label },
                note = note,
                onClick = { actions.onEditStep(OnboardingStep.APPS) },
                enabled = !locked,
            )
            RowDivider()
            LinkRow(
                title = stringResource(R.string.settings_schedule),
                value = state.schedule,
                note = note,
                onClick = { actions.onEditStep(OnboardingStep.SCHEDULE) },
                enabled = !locked,
            )
            RowDivider()
            val extraTime = stringResource(R.string.settings_extra_time)
            StepperRow(
                title = extraTime,
                hint = stringResource(R.string.settings_extra_time_hint, UnlockLimits.MAX_EXTRA_TIME_DAILY),
                value = limits.extraTimeDaily.toString(),
                onDecrease = { actions.onExtraTimeChange(-1) },
                onIncrease = { actions.onExtraTimeChange(1) },
                decreaseLabel = stringResource(R.string.settings_decrease, extraTime),
                increaseLabel = stringResource(R.string.settings_increase, extraTime),
                canDecrease = !locked && limits.extraTimeDaily > 0,
                canIncrease = !locked && limits.extraTimeDaily < UnlockLimits.MAX_EXTRA_TIME_DAILY,
            )
            RowDivider()
            val emergency = stringResource(R.string.settings_emergency)
            StepperRow(
                title = emergency,
                hint = stringResource(R.string.settings_emergency_hint, UnlockLimits.MAX_EMERGENCY_DAILY),
                value = limits.emergencyDaily.toString(),
                onDecrease = { actions.onEmergencyChange(-1) },
                onIncrease = { actions.onEmergencyChange(1) },
                decreaseLabel = stringResource(R.string.settings_decrease, emergency),
                increaseLabel = stringResource(R.string.settings_increase, emergency),
                canDecrease = !locked && limits.emergencyDaily > 0,
                canIncrease = !locked && limits.emergencyDaily < UnlockLimits.MAX_EMERGENCY_DAILY,
            )
        }
        ProtectionCard(state.protection, actions)
    }
}

/** Encendida y en horario de bloqueo, apagarla pasa por la salida con fricción. */
@Composable
private fun ProtectionCard(protection: Protection, actions: SettingsActions) {
    val colors = LaVidaTheme.colors
    var exiting by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    GroupedCard {
        CheckRow(
            title = stringResource(R.string.settings_anti_sabotage),
            description = stringResource(R.string.settings_anti_sabotage_desc),
            checked = protection.on,
            onToggle = { if (protection.locked) exiting = true else actions.onAntiSabotageChange(!protection.on) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            trailingMark = true,
        )
        protection.offAt?.let { offAt ->
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accentSoft)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.sabotage_exit_scheduled, offAt),
                    modifier = Modifier.weight(1f),
                    style = LaVidaTheme.type.caption,
                    color = colors.textPrimary,
                )
                SmallButton(text = stringResource(R.string.sabotage_exit_cancel), onClick = actions.onCancelProtectionOff)
            }
        }
        RowDivider()
        LinkRow(title = stringResource(R.string.settings_permissions), onClick = actions.onOpenSetup)
    }
    if (exiting) {
        AlertDialog(
            onDismissRequest = { exiting = false },
            title = { Text(stringResource(R.string.sabotage_exit_title), style = LaVidaTheme.type.subhead) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SabotageExitForm(
                        reason = reason,
                        scheduledAt = protection.offAt,
                        scheduleAt = protection.scheduleAt,
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
                }
            },
            confirmButton = {
                TextLink(text = stringResource(R.string.sabotage_exit_keep), onClick = { exiting = false })
            },
            shape = MaterialTheme.shapes.large,
            containerColor = colors.surface,
        )
    }
}

@Composable
private fun MessagesSection(state: SettingsUiState, actions: SettingsActions) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val profile = state.profile
    val kind = state.kindMode
    Section(R.string.settings_section_messages) {
        GroupedCard {
            CheckRow(
                title = stringResource(R.string.settings_kind_mode),
                description = stringResource(R.string.settings_kind_mode_desc),
                checked = kind,
                onToggle = { actions.onKindMode(!kind) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                trailingMark = true,
            )
            RowDivider()
            LinkRow(
                title = stringResource(R.string.settings_sarcasm),
                value = when {
                    kind -> stringResource(R.string.settings_sarcasm_off)
                    profile == null -> null
                    else -> stringResource(R.string.settings_sarcasm_value, profile.sarcasmLevel, sarcasmName(profile.sarcasmLevel))
                },
                onClick = { actions.onEditStep(OnboardingStep.SARCASM) },
            )
            RowDivider()
            LinkRow(
                title = stringResource(R.string.settings_profanity),
                value = profile?.let {
                    stringResource(if (it.profanityAllowed && !kind) R.string.profanity_yes else R.string.profanity_no)
                },
                onClick = { actions.onEditStep(OnboardingStep.PROFANITY) },
            )
            RowDivider()
            LinkRow(
                title = stringResource(R.string.settings_country),
                value = profile?.country?.let {
                    if (it == OnboardingRules.NEUTRAL_COUNTRY) stringResource(R.string.country_neutral) else it
                },
                onClick = { actions.onEditStep(OnboardingStep.COUNTRY) },
            )
            RowDivider()
            LinkRow(
                title = stringResource(R.string.settings_forbidden),
                value = profile?.forbiddenTopics?.ifBlank { stringResource(R.string.settings_none) },
                onClick = { actions.onEditStep(OnboardingStep.FORBIDDEN) },
            )
        }
        Panel {
            state.template.generatedAt?.let { generatedAt ->
                Text(
                    text = stringResource(R.string.settings_template_info, state.template.messageCount, generatedAt),
                    style = type.small,
                    color = colors.textPrimary,
                )
            }
            if (state.template.stale) {
                Notice(tone = NoticeTone.INFO) {
                    Text(
                        text = stringResource(R.string.settings_template_stale),
                        style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                }
            }
            state.preview?.let { preview ->
                Notice(tone = NoticeTone.PLAIN) {
                    AnimatedContent(
                        targetState = preview,
                        transitionSpec = { FadeIn togetherWith FadeOut },
                        label = "vista previa",
                    ) { text ->
                        Text(
                            text = text,
                            style = type.body.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                        )
                    }
                }
                TextLink(text = stringResource(R.string.home_preview_next), onClick = actions.onNextPreview)
            }
            PrimaryButton(
                text = stringResource(R.string.settings_regenerate),
                onClick = actions.onRegenerate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProfileSection(state: SettingsUiState, actions: SettingsActions) {
    val profile = state.profile
    val none = stringResource(R.string.settings_none)
    Section(R.string.settings_section_profile) {
        GroupedCard {
            LinkRow(
                title = stringResource(R.string.settings_name),
                value = profile?.name,
                onClick = { actions.onEditStep(OnboardingStep.NAME) },
            )
            RowDivider()
            LinkRow(
                title = stringResource(R.string.settings_goal),
                value = profile?.mainGoal,
                onClick = { actions.onEditStep(OnboardingStep.MAIN_GOAL) },
            )
            RowDivider()
            val goals = profile?.secondaryGoals.orEmpty().size
            LinkRow(
                title = stringResource(R.string.settings_secondary),
                value = if (goals == 0) none else pluralStringResource(R.plurals.settings_secondary_count, goals, goals),
                onClick = { actions.onEditStep(OnboardingStep.SECONDARY_GOALS) },
            )
            RowDivider()
            LinkRow(
                title = stringResource(R.string.settings_reason),
                value = profile?.goalReason?.ifBlank { none },
                onClick = { actions.onEditStep(OnboardingStep.GOAL_REASON) },
            )
            RowDivider()
            LinkRow(
                title = stringResource(R.string.settings_excuses),
                value = profile?.typicalExcuses?.ifBlank { none },
                onClick = { actions.onEditStep(OnboardingStep.EXCUSES) },
            )
        }
    }
}

@Composable
private fun AccountSection(state: SettingsUiState, actions: SettingsActions) {
    Section(R.string.settings_section_account) {
        PlanSection(state.device, actions)
        PrivacySection(state, actions)
    }
}

@Composable
private fun PrivacySection(state: SettingsUiState, actions: SettingsActions) {
    val colors = LaVidaTheme.colors
    var confirming by rememberSaveable { mutableStateOf(false) }
    Panel(spacing = 14.dp) {
        state.consents?.let { consents ->
            CheckRow(
                title = stringResource(R.string.settings_consent_excuses),
                description = stringResource(R.string.settings_consent_excuses_desc),
                checked = consents.excuses,
                onToggle = { actions.onExcusesConsent(!consents.excuses) },
            )
            CheckRow(
                title = stringResource(R.string.settings_consent_analytics),
                description = stringResource(R.string.settings_consent_analytics_desc),
                checked = consents.analytics,
                onToggle = { actions.onAnalyticsConsent(!consents.analytics) },
            )
        }
        PolicyLink()
        EraseButton(onClick = { confirming = true })
        if (state.eraseFailed) {
            Text(text = stringResource(R.string.settings_erase_failed), style = LaVidaTheme.type.small, color = colors.rejected)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_erase_title), style = LaVidaTheme.type.subhead) },
            text = { Text(stringResource(R.string.settings_erase_warning), style = LaVidaTheme.type.small) },
            confirmButton = {
                TextLink(
                    text = stringResource(R.string.settings_erase_confirm),
                    onClick = {
                        confirming = false
                        actions.onErase()
                    },
                )
            },
            dismissButton = {
                TextLink(text = stringResource(R.string.action_cancel), onClick = { confirming = false })
            },
            shape = MaterialTheme.shapes.large,
            containerColor = colors.surface,
        )
    }
}

/** El único botón rojo de la app: borra todo y no se puede deshacer. */
@Composable
private fun EraseButton(onClick: () -> Unit) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .border(2.dp, colors.rejected, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_erase),
            style = LaVidaTheme.type.small.copy(fontWeight = FontWeight.Bold),
            color = colors.rejected,
        )
    }
}

private val PLANS = listOf(
    "free" to R.string.plan_free,
    "monthly" to R.string.plan_monthly,
    "semester" to R.string.plan_semester,
)

@Composable
private fun PlanSection(device: DeviceStatusDto?, actions: SettingsActions) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    if (device == null) {
        Text(text = stringResource(R.string.settings_plan_offline), style = type.small, color = colors.textSecondary)
        return
    }
    var showPlans by rememberSaveable { mutableStateOf(false) }
    var surveyClosed by rememberSaveable { mutableStateOf(false) }
    Panel(spacing = 10.dp) {
        MonoLabel(stringResource(R.string.settings_plan_label))
        val name = PLANS.firstOrNull { it.first == device.plan }?.second ?: R.string.plan_free
        Text(text = stringResource(name), style = type.section, color = colors.textPrimary)
        Text(
            text = pluralStringResource(R.plurals.settings_plan_limit, device.dailyLimit, device.dailyLimit, device.remainingToday),
            style = type.small,
            color = colors.textPrimary,
        )
        if (showPlans) {
            PLANS.forEach { (plan, label) ->
                SelectableRow(
                    title = stringResource(label),
                    selected = device.plan == plan,
                    onClick = { actions.onSelectPlan(plan) },
                )
            }
            Text(text = stringResource(R.string.settings_plan_hint), style = type.caption, color = colors.textSecondary)
        } else {
            SmallButton(text = stringResource(R.string.settings_plans), onClick = { showPlans = true })
        }
    }
    if (!surveyClosed && device.wouldPay == null) {
        PaySurvey(
            onAnswer = { answer ->
                actions.onWouldPay(answer)
                surveyClosed = true
            },
            onClose = { surveyClosed = true },
        )
    }
}

/** Se contesta una vez o se cierra: no es un cartel fijo en Ajustes. */
@Composable
private fun PaySurvey(onAnswer: (String) -> Unit, onClose: () -> Unit) {
    val colors = LaVidaTheme.colors
    val closeLabel = stringResource(R.string.settings_survey_close)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashed(shape, colors.border)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row {
            Text(
                text = stringResource(R.string.settings_would_pay),
                modifier = Modifier.weight(1f),
                style = LaVidaTheme.type.small.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "×", style = LaVidaTheme.type.section, color = colors.textPrimary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(text = stringResource(R.string.would_pay_yes), onClick = { onAnswer("yes") })
            SmallButton(text = stringResource(R.string.would_pay_no), onClick = { onAnswer("no") })
        }
    }
}

@Composable
private fun sarcasmName(level: Int): String {
    val full = stringResource(
        when (level) {
            1 -> R.string.sarcasm_1
            2 -> R.string.sarcasm_2
            3 -> R.string.sarcasm_3
            4 -> R.string.sarcasm_4
            else -> R.string.sarcasm_5
        },
    )
    return full.substringAfter("· ")
}
