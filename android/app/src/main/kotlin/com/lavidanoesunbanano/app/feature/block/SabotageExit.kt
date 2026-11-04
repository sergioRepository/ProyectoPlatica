package com.lavidanoesunbanano.app.feature.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTextField
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard

private const val MAX_SABOTAGE_REASON_CHARS = 300

/**
 * Las dos salidas del anti-autosabotaje, en la pantalla de bloqueo y en Ajustes. Nadie juzga
 * lo que se escribe. `scheduledAt`: cuándo se apaga, si ya está programado.
 */
@Composable
fun SabotageExitForm(
    reason: String,
    scheduledAt: String?,
    onReasonChange: (String) -> Unit,
    onDisableNow: () -> Unit,
    onSchedule: () -> Unit,
) {
    val colors = LaVidaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.sabotage_exit_hint),
            style = LaVidaTheme.type.small,
            color = colors.textSecondary,
        )
        LaVidaTextField(
            value = reason,
            onValueChange = onReasonChange,
            placeholder = stringResource(R.string.sabotage_exit_placeholder),
            maxLength = MAX_SABOTAGE_REASON_CHARS,
            singleLine = false,
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        PrimaryButton(
            text = stringResource(R.string.sabotage_exit_now),
            onClick = onDisableNow,
            modifier = Modifier.fillMaxWidth(),
            enabled = SabotageGuard.canDisableNow(reason),
        )
        if (scheduledAt != null) {
            Text(
                text = stringResource(R.string.sabotage_exit_scheduled, scheduledAt),
                style = LaVidaTheme.type.small,
                color = colors.textSecondary,
            )
        } else {
            SecondaryButton(
                text = stringResource(R.string.sabotage_exit_schedule),
                onClick = onSchedule,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
