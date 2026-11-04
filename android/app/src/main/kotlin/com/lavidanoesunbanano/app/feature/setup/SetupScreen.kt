package com.lavidanoesunbanano.app.feature.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.GroupedCard
import com.lavidanoesunbanano.app.core.designsystem.IconNotice
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.Notice
import com.lavidanoesunbanano.app.core.designsystem.NoticeTone
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.RowDivider
import com.lavidanoesunbanano.app.core.designsystem.ScreenColumn
import com.lavidanoesunbanano.app.core.designsystem.TextLink
import com.lavidanoesunbanano.app.data.system.Manufacturer
import com.lavidanoesunbanano.app.domain.setup.ServiceHealth
import com.lavidanoesunbanano.app.domain.setup.SetupItem
import com.lavidanoesunbanano.app.domain.setup.SetupStep

/** Primero lo necesario, una tarjeta a la vez; debajo, lo recomendado. Cada paso abre su ajuste. */
@Composable
fun SetupScreen(onContinue: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationsAsked()
    }
    SetupContent(state, onContinue) { step ->
        viewModel.onOpened(step)
        when (step) {
            SetupStep.RESTRICTED_SETTINGS -> SetupIntents.openAppDetails(context)
            SetupStep.ACCESSIBILITY -> SetupIntents.openAccessibility(context)
            SetupStep.NOTIFICATIONS ->
                // Android solo muestra el diálogo un par de veces; después hay que ir a Ajustes.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state?.notificationsAsked != true) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    SetupIntents.openNotificationSettings(context)
                }
            SetupStep.OVERLAY -> SetupIntents.openOverlay(context)
            SetupStep.BATTERY -> SetupIntents.requestBatteryExemption(context)
            SetupStep.USAGE_ACCESS -> SetupIntents.openUsageAccess(context)
        }
    }
}

@Composable
private fun SetupContent(state: SetupUiState?, onContinue: () -> Unit, onAction: (SetupStep) -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    ScreenColumn {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.setup_title), style = type.title, color = colors.textPrimary)
                if (state != null) {
                    val required = state.items.count { it.required }
                    val recommended = state.items.size - required
                    Text(
                        text = stringResource(
                            R.string.setup_intro,
                            pluralStringResource(R.plurals.setup_intro_required, required, required),
                            pluralStringResource(R.plurals.setup_intro_recommended, recommended, recommended),
                        ),
                        style = type.body,
                        color = colors.textSecondary,
                    )
                }
            }
            if (state == null) return@Column
            if (state.protectionOn) {
                IconNotice(R.drawable.ic_ui_info, stringResource(R.string.setup_protection_note), tone = NoticeTone.NOTE)
            }
            RequiredCard(state, onContinue, onAction)
            RecommendedList(state, onAction)
        }
    }
}

@Composable
private fun RequiredCard(state: SetupUiState, onContinue: () -> Unit, onAction: (SetupStep) -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val required = state.items.filter { it.required }
    val current = required.firstOrNull { !it.done } ?: required.last()
    val texts = requiredTexts(current.step, state.service)
    Panel(spacing = 14.dp, padding = 16.dp) {
        Text(
            text = stringResource(R.string.setup_required_badge, required.indexOf(current) + 1, required.size).uppercase(),
            modifier = Modifier
                .background(colors.border, CircleShape)
                .padding(horizontal = 9.dp, vertical = 3.dp),
            style = type.mono.copy(fontWeight = FontWeight.SemiBold),
            color = colors.background,
        )
        Text(text = stringResource(texts.title), style = type.section, color = colors.textPrimary)
        Text(text = stringResource(texts.description), style = type.small, color = colors.textBody)
        when {
            current.done -> {
                Notice(tone = NoticeTone.SUCCESS) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(26.dp).background(colors.approved, CircleShape), contentAlignment = Alignment.Center) {
                            Text(text = "✓", style = type.small.copy(fontWeight = FontWeight.ExtraBold), color = colors.surface)
                        }
                        Text(
                            text = stringResource(R.string.setup_ok),
                            style = type.small.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                        )
                    }
                }
                PrimaryButton(text = stringResource(R.string.setup_continue), onClick = onContinue, modifier = Modifier.fillMaxWidth())
            }
            current.step in state.opened -> {
                Notice(tone = NoticeTone.ERROR) {
                    Text(
                        text = stringResource(texts.denied),
                        style = type.small.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.rejected,
                    )
                    Text(text = stringResource(R.string.setup_denied_hint), style = type.caption, color = colors.textPrimary)
                }
                PrimaryButton(
                    text = stringResource(R.string.setup_retry),
                    onClick = { onAction(current.step) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                IconNotice(R.drawable.ic_ui_alert, stringResource(R.string.setup_warning))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.setup_what_to_do),
                        style = type.small.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.textPrimary,
                    )
                    texts.steps.forEachIndexed { index, step ->
                        Text(text = "${index + 1}. ${stringResource(step)}", style = type.small, color = colors.textPrimary)
                    }
                }
                PrimaryButton(
                    text = stringResource(texts.action),
                    onClick = { onAction(current.step) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (!current.done) HowTo()
    }
}

/** La explicación larga queda a un toque: casi nadie la necesita. */
@Composable
private fun ColumnScope.HowTo() {
    var open by rememberSaveable { mutableStateOf(false) }
    if (open) {
        Text(
            text = stringResource(R.string.setup_how_detail),
            style = LaVidaTheme.type.caption,
            color = LaVidaTheme.colors.textBody,
        )
    } else {
        TextLink(
            text = stringResource(R.string.setup_how),
            onClick = { open = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun RecommendedList(state: SetupUiState, onAction: (SetupStep) -> Unit) {
    val colors = LaVidaTheme.colors
    val recommended = state.items.filterNot { it.required }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = stringResource(R.string.setup_later), style = LaVidaTheme.type.subhead, color = colors.textPrimary)
        GroupedCard(shadow = 0.dp) {
            recommended.forEachIndexed { index, item ->
                if (index > 0) RowDivider()
                RecommendedRow(item, state.manufacturer, onAction)
            }
        }
        Text(text = stringResource(R.string.setup_later_hint), style = LaVidaTheme.type.caption, color = colors.textSecondary)
    }
}

@Composable
private fun RecommendedRow(item: SetupItem, manufacturer: Manufacturer, onAction: (SetupStep) -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.done, role = Role.Button) { onAction(item.step) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(item.step.titleRes),
                modifier = Modifier.weight(1f),
                style = type.small.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            if (item.done) {
                Text(text = stringResource(R.string.setup_done), style = type.mono, color = colors.approved)
            }
        }
        if (!item.done) {
            Text(
                text = stringResource(R.string.setup_if_skipped, stringResource(item.step.riskRes)),
                style = type.caption,
                color = colors.textSecondary,
            )
            manufacturerHint(item.step, manufacturer)?.let { hint ->
                Text(text = stringResource(hint), style = type.caption, color = colors.textSecondary)
            }
        }
    }
}

private class RequiredTexts(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val action: Int,
    @StringRes val denied: Int,
    val steps: List<Int>,
)

private fun requiredTexts(step: SetupStep, service: ServiceHealth): RequiredTexts =
    if (step == SetupStep.RESTRICTED_SETTINGS) {
        RequiredTexts(
            R.string.setup_restricted_title,
            R.string.setup_restricted_desc,
            R.string.setup_restricted_action,
            R.string.setup_restricted_denied,
            listOf(R.string.setup_restricted_step_1, R.string.setup_restricted_step_2, R.string.setup_restricted_step_3),
        )
    } else {
        RequiredTexts(
            R.string.setup_accessibility_title,
            if (service == ServiceHealth.STOPPED) R.string.setup_accessibility_stopped_desc else R.string.setup_accessibility_desc,
            R.string.setup_accessibility_action,
            R.string.setup_accessibility_denied,
            listOf(R.string.setup_accessibility_step_1, R.string.setup_accessibility_step_2, R.string.setup_accessibility_step_3),
        )
    }

/** Nombre de un paso recomendado; también lo usa el aviso de Inicio. */
@get:StringRes
internal val SetupStep.titleRes: Int
    get() = when (this) {
        SetupStep.RESTRICTED_SETTINGS -> R.string.setup_restricted_title
        SetupStep.ACCESSIBILITY -> R.string.setup_accessibility_title
        SetupStep.NOTIFICATIONS -> R.string.setup_notifications_title
        SetupStep.OVERLAY -> R.string.setup_overlay_title
        SetupStep.BATTERY -> R.string.setup_battery_title
        SetupStep.USAGE_ACCESS -> R.string.setup_usage_title
    }

/** Qué se pierde sin ese paso. Los necesarios no tienen riesgo: sin ellos no hay bloqueo. */
@get:StringRes
internal val SetupStep.riskRes: Int
    get() = when (this) {
        SetupStep.NOTIFICATIONS -> R.string.setup_notifications_risk
        SetupStep.OVERLAY -> R.string.setup_overlay_risk
        SetupStep.USAGE_ACCESS -> R.string.setup_usage_risk
        SetupStep.RESTRICTED_SETTINGS, SetupStep.ACCESSIBILITY, SetupStep.BATTERY -> R.string.setup_battery_risk
    }

/** El ahorro de batería de Samsung y Xiaomi tiene un paso propio que Android no deja abrir. */
@StringRes
private fun manufacturerHint(step: SetupStep, manufacturer: Manufacturer): Int? = when {
    step != SetupStep.BATTERY -> null
    manufacturer == Manufacturer.SAMSUNG -> R.string.setup_battery_samsung
    manufacturer == Manufacturer.XIAOMI -> R.string.setup_battery_xiaomi
    else -> null
}
