package com.lavidanoesunbanano.app.feature.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.data.system.Manufacturer
import com.lavidanoesunbanano.app.domain.setup.ServiceHealth
import com.lavidanoesunbanano.app.domain.setup.SetupItem
import com.lavidanoesunbanano.app.domain.setup.SetupStep

/** Guía paso a paso: cada paso dice si está listo y abre el ajuste exacto del sistema. */
@Composable
fun SetupScreen(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationsAsked()
    }
    SetupContent(state) { step ->
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
private fun SetupContent(state: SetupUiState?, onAction: (SetupStep) -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.setup_title), style = LaVidaTheme.type.title, color = colors.textPrimary)
        if (state == null) return@Column
        Spacer(Modifier.height(6.dp))
        MonoLabel(stringResource(R.string.setup_progress, state.doneCount, state.items.size))
        if (state.protectionOn) {
            Spacer(Modifier.height(16.dp))
            Text(text = stringResource(R.string.setup_protection_note), style = LaVidaTheme.type.small, color = colors.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.items.forEach { item -> StepCard(item, state, onAction) }
        }
    }
}

@Composable
private fun StepCard(item: SetupItem, state: SetupUiState, onAction: (SetupStep) -> Unit) {
    val colors = LaVidaTheme.colors
    val texts = textsFor(item.step, state.service)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(texts.title),
                modifier = Modifier.weight(1f),
                style = LaVidaTheme.type.body,
                color = colors.textPrimary,
            )
            // "listo" lleva el color de aprobado, pero siempre con la palabra al lado.
            Text(
                text = stringResource(if (item.done) R.string.setup_done else R.string.setup_pending),
                style = LaVidaTheme.type.mono,
                color = if (item.done) colors.approved else colors.textSecondary,
            )
        }
        MonoLabel(stringResource(if (item.required) R.string.setup_required else R.string.setup_recommended))
        Text(text = stringResource(texts.description), style = LaVidaTheme.type.small, color = colors.textSecondary)
        manufacturerHint(item.step, state.manufacturer)?.let { hint ->
            Text(text = stringResource(hint), style = LaVidaTheme.type.small, color = colors.textSecondary)
        }
        if (!item.done) {
            SecondaryButton(
                text = stringResource(texts.action),
                onClick = { onAction(item.step) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private class StepTexts(@StringRes val title: Int, @StringRes val description: Int, @StringRes val action: Int)

private fun textsFor(step: SetupStep, service: ServiceHealth): StepTexts = when (step) {
    SetupStep.RESTRICTED_SETTINGS -> StepTexts(
        R.string.setup_restricted_title,
        R.string.setup_restricted_desc,
        R.string.setup_restricted_action,
    )
    SetupStep.ACCESSIBILITY -> StepTexts(
        R.string.setup_accessibility_title,
        if (service == ServiceHealth.STOPPED) R.string.setup_accessibility_stopped_desc else R.string.setup_accessibility_desc,
        R.string.setup_accessibility_action,
    )
    SetupStep.NOTIFICATIONS -> StepTexts(
        R.string.setup_notifications_title,
        R.string.setup_notifications_desc,
        R.string.setup_notifications_action,
    )
    SetupStep.OVERLAY -> StepTexts(R.string.setup_overlay_title, R.string.setup_overlay_desc, R.string.setup_overlay_action)
    SetupStep.BATTERY -> StepTexts(R.string.setup_battery_title, R.string.setup_battery_desc, R.string.setup_battery_action)
    SetupStep.USAGE_ACCESS -> StepTexts(R.string.setup_usage_title, R.string.setup_usage_desc, R.string.setup_usage_action)
}

/** El ahorro de batería de Samsung y Xiaomi tiene un paso propio que Android no deja abrir. */
@StringRes
private fun manufacturerHint(step: SetupStep, manufacturer: Manufacturer): Int? = when {
    step != SetupStep.BATTERY -> null
    manufacturer == Manufacturer.SAMSUNG -> R.string.setup_battery_samsung
    manufacturer == Manufacturer.XIAOMI -> R.string.setup_battery_xiaomi
    else -> null
}
