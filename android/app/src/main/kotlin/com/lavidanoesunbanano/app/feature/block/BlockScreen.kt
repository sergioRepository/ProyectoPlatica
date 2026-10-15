package com.lavidanoesunbanano.app.feature.block

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.GhostButton
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTextField
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.core.designsystem.StatusLine
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.model.VerdictReason

/** Largo máximo de una excusa en la pantalla; el backend acepta hasta 1000. */
private const val MAX_REASON_CHARS = 300

class BlockActions(
    val onExit: () -> Unit,
    val onAskExcuse: (ExcuseType) -> Unit,
    val onReasonChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onBack: () -> Unit,
    val onOpenApp: () -> Unit,
)

/**
 * Fondo plano; arriba la app, la hora y el intento en mono. Según el paso: el mensaje con
 * los tres botones, la pregunta de una excusa, o el veredicto del juez.
 *
 * `onShown` se llama en el primer cuadro con el mensaje, para medir la latencia.
 */
@Composable
fun BlockScreen(state: BlockUiState?, actions: BlockActions, onShown: () -> Unit) {
    // Atrás vuelve de la pregunta o del veredicto al mensaje; en el mensaje no hace nada.
    BackHandler(enabled = state != null && state.step !is BlockStep.Message) { actions.onBack() }
    AnimatedContent(
        targetState = state,
        modifier = Modifier
            .fillMaxSize()
            .background(LaVidaTheme.colors.background),
        // Escribir no anima; cambiar de paso, de intento o de mensaje sí.
        contentKey = { it?.contentKey() },
        transitionSpec = { FadeIn togetherWith FadeOut },
        label = "bloqueo",
    ) { current ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            if (current != null) {
                MonoLabel(stringResource(R.string.block_meta, current.appLabel, current.time, current.attemptNumber))
                when (val step = current.step) {
                    BlockStep.Message -> MessageStep(current, actions)
                    is BlockStep.Excuse -> ExcuseStep(step, actions)
                    is BlockStep.Decided -> DecidedStep(current, step, actions)
                }
            }
        }
    }
    LaunchedEffect(state != null) {
        if (state != null) {
            withFrameNanos { }
            onShown()
        }
    }
}

private fun BlockUiState.contentKey(): String = when (val step = step) {
    BlockStep.Message -> "$attemptId:mensaje:$message"
    is BlockStep.Excuse -> "$attemptId:excusa:${step.type}"
    is BlockStep.Decided -> "$attemptId:veredicto"
}

@Composable
private fun ColumnScope.MessageStep(state: BlockUiState, actions: BlockActions) {
    CenteredText(state.message ?: stringResource(R.string.block_fallback_message))
    Spacer(Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(
            text = stringResource(R.string.block_emergency),
            onClick = { actions.onAskExcuse(ExcuseType.EMERGENCY) },
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.block_extra_time),
            onClick = { actions.onAskExcuse(ExcuseType.EXTRA_TIME) },
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = stringResource(R.string.block_exit),
            onClick = actions.onExit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.ExcuseStep(step: BlockStep.Excuse, actions: BlockActions) {
    val colors = LaVidaTheme.colors
    val emergency = step.type == ExcuseType.EMERGENCY
    val focus = remember { FocusRequester() }
    Spacer(Modifier.height(32.dp))
    Text(
        text = stringResource(if (emergency) R.string.excuse_emergency_title else R.string.excuse_extra_time_title),
        style = LaVidaTheme.type.title,
        color = colors.textPrimary,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(if (emergency) R.string.excuse_emergency_hint else R.string.excuse_extra_time_hint),
        style = LaVidaTheme.type.small,
        color = colors.textSecondary,
    )
    Spacer(Modifier.height(24.dp))
    LaVidaTextField(
        value = step.reason,
        onValueChange = actions.onReasonChange,
        placeholder = stringResource(
            if (emergency) R.string.excuse_emergency_placeholder else R.string.excuse_extra_time_placeholder,
        ),
        maxLength = MAX_REASON_CHARS,
        modifier = Modifier.focusRequester(focus),
        singleLine = false,
        minLines = 5,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Spacer(Modifier.weight(1f))
    Spacer(Modifier.height(24.dp))
    if (step.judging) {
        StatusLine(stringResource(R.string.excuse_judging))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                text = stringResource(R.string.excuse_submit),
                onClick = actions.onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = step.reason.isNotBlank(),
            )
            GhostButton(
                text = stringResource(R.string.action_cancel),
                onClick = actions.onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun ColumnScope.DecidedStep(state: BlockUiState, step: BlockStep.Decided, actions: BlockActions) {
    val verdict = step.verdict
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = verdict.message ?: offlineMessage(verdict),
                style = LaVidaTheme.type.display,
                color = LaVidaTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = verdictLabel(verdict),
                style = LaVidaTheme.type.mono,
                color = if (verdict.valid) LaVidaTheme.colors.approved else LaVidaTheme.colors.rejected,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (verdict.valid) {
            PrimaryButton(
                text = stringResource(R.string.verdict_open_app, state.appLabel),
                onClick = actions.onOpenApp,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SecondaryButton(
                text = stringResource(R.string.verdict_back),
                onClick = actions.onBack,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.block_exit),
                onClick = actions.onExit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Mensaje centrado verticalmente; con letra grande se desplaza en vez de tapar los botones. */
@Composable
private fun ColumnScope.CenteredText(text: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            style = LaVidaTheme.type.display,
            color = LaVidaTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun offlineMessage(verdict: Verdict): String =
    if (verdict.valid) {
        stringResource(R.string.verdict_offline_emergency, verdict.grantedMinutes)
    } else {
        stringResource(R.string.verdict_offline_rejected)
    }

/** `APROBADO · 10 min` o `RECHAZADO · excusa repetida`. */
@Composable
private fun verdictLabel(verdict: Verdict): String = when {
    verdict.valid && verdict.offline -> stringResource(R.string.verdict_approved_offline, verdict.grantedMinutes)
    verdict.valid -> stringResource(R.string.verdict_approved, verdict.grantedMinutes)
    else -> stringResource(
        R.string.verdict_rejected,
        stringResource(
            when (verdict.reason) {
                VerdictReason.REPEATED -> R.string.verdict_reason_repeated
                VerdictReason.OVER_QUOTA -> R.string.verdict_reason_over_quota
                VerdictReason.TOO_SHORT -> R.string.verdict_reason_too_short
                VerdictReason.OFFLINE -> R.string.verdict_reason_offline
                else -> R.string.verdict_reason_boredom
            },
        ),
    )
}

private val PreviewActions = BlockActions({}, {}, {}, {}, {}, {})

private val PreviewState = BlockUiState(
    attemptId = 7,
    packageName = "com.instagram.android",
    appLabel = "Instagram",
    time = "23:40",
    attemptNumber = 7,
    message = "¿Instagram otra vez? Así piensas ser el mejor programador del mundo. Qué mierda.",
)

@Preview
@Composable
private fun BlockPreview() {
    LaVidaTheme { BlockScreen(state = PreviewState, actions = PreviewActions, onShown = {}) }
}

@Preview
@Composable
private fun ExcusePreview() {
    LaVidaTheme {
        BlockScreen(
            state = PreviewState.copy(step = BlockStep.Excuse(ExcuseType.EXTRA_TIME, "Tengo que responder", judging = true)),
            actions = PreviewActions,
            onShown = {},
        )
    }
}

@Preview
@Composable
private fun VerdictPreview() {
    LaVidaTheme {
        BlockScreen(
            state = PreviewState.copy(
                step = BlockStep.Decided(
                    type = ExcuseType.EXTRA_TIME,
                    verdict = Verdict(
                        valid = false,
                        grantedMinutes = 0,
                        message = "Esa excusa ya la usaste. Ni la excusa es nueva.",
                        reason = VerdictReason.REPEATED,
                        confidence = 1.0,
                        offline = false,
                    ),
                    unlockedUntil = null,
                ),
            ),
            actions = PreviewActions,
            onShown = {},
        )
    }
}
