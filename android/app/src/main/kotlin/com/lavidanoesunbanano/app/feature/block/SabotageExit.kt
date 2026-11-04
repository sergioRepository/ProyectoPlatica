package com.lavidanoesunbanano.app.feature.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.IconNotice
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTextField
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard

private const val MAX_SABOTAGE_REASON_CHARS = 300

/**
 * Las dos salidas del anti-autosabotaje, en la pantalla de bloqueo y en Ajustes. Nadie juzga
 * lo que se escribe. `scheduledAt`: cuándo se apaga, si ya está programado. `scheduleAt`:
 * cuándo se apagaría si se programa ahora.
 */
@Composable
fun SabotageExitForm(
    reason: String,
    scheduledAt: String?,
    scheduleAt: String,
    onReasonChange: (String) -> Unit,
    onDisableNow: () -> Unit,
    onSchedule: () -> Unit,
) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val length = reason.trim().length
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (scheduledAt != null) {
            IconNotice(R.drawable.ic_ui_history, stringResource(R.string.sabotage_exit_scheduled, scheduledAt))
        } else {
            PrimaryButton(
                text = stringResource(R.string.sabotage_exit_schedule),
                subtitle = stringResource(R.string.sabotage_exit_schedule_at, scheduleAt),
                onClick = onSchedule,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Panel(spacing = 10.dp) {
            Text(text = stringResource(R.string.sabotage_exit_now_title), style = type.subhead, color = colors.textPrimary)
            Text(
                text = stringResource(R.string.sabotage_exit_now_hint, SabotageGuard.MIN_REASON_CHARS),
                style = type.caption,
                color = colors.textSecondary,
            )
            LaVidaTextField(
                value = reason,
                onValueChange = onReasonChange,
                placeholder = stringResource(R.string.sabotage_exit_placeholder),
                maxLength = MAX_SABOTAGE_REASON_CHARS,
                singleLine = false,
                minLines = 3,
                showCounter = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text(
                text = if (SabotageGuard.canDisableNow(reason)) {
                    stringResource(R.string.sabotage_exit_ready, length)
                } else {
                    stringResource(R.string.sabotage_exit_count, length, SabotageGuard.MIN_REASON_CHARS)
                },
                style = type.mono.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
            )
            SecondaryButton(
                text = stringResource(R.string.sabotage_exit_now),
                onClick = onDisableNow,
                modifier = Modifier.fillMaxWidth(),
                enabled = SabotageGuard.canDisableNow(reason),
            )
        }
    }
}
