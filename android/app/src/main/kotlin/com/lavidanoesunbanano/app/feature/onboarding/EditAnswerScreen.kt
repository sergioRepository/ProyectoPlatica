package com.lavidanoesunbanano.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.IconNotice
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.NoticeTone
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.ScreenColumn
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton

/** Un paso del onboarding abierto desde Ajustes: la misma pregunta, con Guardar y Cancelar. */
@Composable
fun EditAnswerScreen(step: OnboardingStep, onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(step) { viewModel.editStep(step) }
    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }
    val ready = state.profileLoaded && state.editing
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    ScreenColumn(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        Text(text = stringResource(step.pathRes), style = type.mono, color = colors.textBody)
        Spacer(Modifier.height(18.dp))
        Text(text = stringResource(step.titleRes), style = type.title, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(text = stepHint(step, state), style = type.body, color = colors.textSecondary)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.weight(1f)) {
            if (ready) StepBody(step = step, state = state, actions = viewModel)
        }
        if (step.keepsOldMessages) {
            Spacer(Modifier.height(12.dp))
            IconNotice(R.drawable.ic_ui_info, stringResource(R.string.edit_note), tone = NoticeTone.NOTE)
        }
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDone,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.edit_save),
                onClick = viewModel::saveEdit,
                modifier = Modifier.weight(1f),
                enabled = ready && state.canContinue && state.hasChanges,
            )
        }
    }
}

@get:StringRes
private val OnboardingStep.pathRes: Int
    get() = when (this) {
        OnboardingStep.APPS, OnboardingStep.SCHEDULE -> R.string.edit_path_block
        OnboardingStep.SARCASM, OnboardingStep.PROFANITY, OnboardingStep.COUNTRY, OnboardingStep.FORBIDDEN ->
            R.string.edit_path_messages
        else -> R.string.edit_path_profile
    }

/** Apps, horario y sarcasmo aplican de inmediato; lo demás va escrito en los mensajes. */
private val OnboardingStep.keepsOldMessages: Boolean
    get() = this != OnboardingStep.APPS && this != OnboardingStep.SCHEDULE && this != OnboardingStep.SARCASM
