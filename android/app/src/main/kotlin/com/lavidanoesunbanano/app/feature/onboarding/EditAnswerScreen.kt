package com.lavidanoesunbanano.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.GhostButton
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton

/** Un paso del onboarding abierto desde Ajustes: la misma pregunta, con Guardar y Cancelar. */
@Composable
fun EditAnswerScreen(step: OnboardingStep, onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(step) { viewModel.editStep(step) }
    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }
    val ready = state.profileLoaded && state.editing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LaVidaTheme.colors.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        MonoLabel(stringResource(R.string.settings_label))
        Spacer(Modifier.height(32.dp))
        Text(text = stringResource(step.titleRes), style = LaVidaTheme.type.title, color = LaVidaTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(step.hintRes), style = LaVidaTheme.type.small, color = LaVidaTheme.colors.textSecondary)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f)) {
            if (ready) StepBody(step = step, state = state, actions = viewModel)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(text = stringResource(R.string.action_cancel), onClick = onDone)
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::saveEdit,
                enabled = ready && state.canContinue,
            )
        }
    }
}
