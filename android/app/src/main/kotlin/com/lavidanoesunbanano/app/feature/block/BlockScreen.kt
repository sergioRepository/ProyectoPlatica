package com.lavidanoesunbanano.app.feature.block

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.Banana
import com.lavidanoesunbanano.app.core.designsystem.BananaMood
import com.lavidanoesunbanano.app.core.designsystem.BobbingBanana
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.IconNotice
import com.lavidanoesunbanano.app.core.designsystem.InfoPill
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTextField
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.Notice
import com.lavidanoesunbanano.app.core.designsystem.NoticeTone
import com.lavidanoesunbanano.app.core.designsystem.OptionButton
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.core.designsystem.StatusTag
import com.lavidanoesunbanano.app.core.designsystem.TextLink
import com.lavidanoesunbanano.app.core.designsystem.UiIcon
import com.lavidanoesunbanano.app.data.repository.QuotaToday
import com.lavidanoesunbanano.app.domain.excuse.CrisisLine
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.model.VerdictReason

/** Largo máximo de una excusa en la pantalla; el backend acepta hasta 1000. */
private const val MAX_REASON_CHARS = 300

class BlockActions(
    val onExit: () -> Unit,
    val onAskExcuse: (ExcuseType) -> Unit,
    val onAskHelp: () -> Unit,
    val onReasonChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onBack: () -> Unit,
    val onOpenApp: () -> Unit,
    val onReport: (String, String, ReportSource) -> Unit,
)

/**
 * Fondo plano; arriba la app, la hora y el intento en mono. Según el paso: el mensaje con
 * sus opciones, la pregunta de una razón, el veredicto o la ayuda.
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
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (current != null) {
                when (val step = current.step) {
                    BlockStep.Message -> MessageStep(current, actions)
                    is BlockStep.Excuse -> ExcuseStep(current, step, actions)
                    is BlockStep.Decided -> DecidedStep(current, step, actions)
                    is BlockStep.Help -> HelpStep(current, step, actions)
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
    is BlockStep.Help -> "$attemptId:ayuda"
}

@Composable
private fun ColumnScope.Meta(state: BlockUiState) {
    InfoPill(
        text = stringResource(R.string.block_meta, state.appLabel, state.time, state.attemptNumber),
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

@Composable
private fun ColumnScope.MetaLine(text: String, strong: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.align(Alignment.CenterHorizontally),
        style = LaVidaTheme.type.monoBody.copy(fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal),
        color = if (strong) LaVidaTheme.colors.textPrimary else LaVidaTheme.colors.textBody,
    )
}

/** Contenido centrado en lo alto que sobra; con letra grande se desplaza sin tapar los botones. */
@Composable
private fun ColumnScope.CenteredBody(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun ColumnScope.MessageStep(state: BlockUiState, actions: BlockActions) {
    val quota = state.quota
    Meta(state)
    state.blockedUntil?.let { MetaLine(stringResource(R.string.block_until, it), strong = true) }
    CenteredBody {
        Banana(BananaMood.DISAPPROVING, 112.dp)
        Text(
            text = state.message ?: stringResource(R.string.block_fallback_message),
            style = LaVidaTheme.type.display,
            color = LaVidaTheme.colors.textPrimary,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(
            text = stringResource(R.string.block_exit),
            onClick = actions.onExit,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val extraTimeOut = quota.extraTimeLeft == 0
            OptionButton(
                text = stringResource(R.string.block_extra_time),
                subtitle = if (extraTimeOut) {
                    stringResource(R.string.block_extra_time_out, quota.extraTimeDaily)
                } else {
                    stringResource(R.string.block_extra_time_left, quota.extraTimeLeft, quota.extraTimeDaily)
                },
                onClick = { actions.onAskExcuse(ExcuseType.EXTRA_TIME) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                enabled = !extraTimeOut,
            )
            // Nunca se apaga: por aquí se escribe una crisis, aunque el cupo esté agotado.
            OptionButton(
                text = stringResource(R.string.block_emergency),
                subtitle = if (quota.judged) {
                    stringResource(R.string.block_emergency_left, quota.emergencyLeft, quota.emergencyDaily)
                } else {
                    stringResource(R.string.block_emergency_unlimited)
                },
                onClick = { actions.onAskExcuse(ExcuseType.EMERGENCY) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextLink(text = stringResource(R.string.block_help), onClick = actions.onAskHelp, weight = FontWeight.SemiBold)
            state.message?.let { ReportControl(it, ReportSource.MESSAGE, state.reported, actions) }
        }
    }
}

@Composable
private fun ColumnScope.ExcuseStep(state: BlockUiState, step: BlockStep.Excuse, actions: BlockActions) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val quota = state.quota
    val emergency = step.type == ExcuseType.EMERGENCY
    val focus = remember { FocusRequester() }
    Meta(state)
    MetaLine(
        when {
            !emergency -> stringResource(R.string.excuse_extra_time_meta, quota.extraTimeLeft, quota.extraTimeDaily)
            quota.judged -> stringResource(R.string.excuse_emergency_meta, quota.emergencyLeft, quota.emergencyDaily)
            else -> stringResource(R.string.excuse_emergency_meta_unlimited)
        },
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (emergency) R.string.excuse_emergency_title else R.string.excuse_extra_time_title),
            style = type.title,
            color = colors.textPrimary,
        )
        Text(text = excuseHint(emergency, state.blockedUntil), style = type.body, color = colors.textBody)
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
            textStyle = type.body.copy(fontSize = 17.sp, lineHeight = 26.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
    if (step.judging) {
        Notice(tone = NoticeTone.INFO) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BobbingBanana(BananaMood.READING, 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = stringResource(R.string.excuse_judging), style = type.subhead, color = colors.textPrimary)
                    Text(text = stringResource(R.string.excuse_judging_hint), style = type.caption, color = colors.textSecondary)
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = stringResource(R.string.excuse_submit),
                onClick = actions.onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = step.reason.isNotBlank(),
            )
            SecondaryButton(
                text = stringResource(R.string.excuse_cancel),
                onClick = actions.onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun excuseHint(emergency: Boolean, blockedUntil: String?): String = when {
    blockedUntil == null -> stringResource(
        if (emergency) R.string.excuse_emergency_hint_generic else R.string.excuse_extra_time_hint_generic,
    )
    emergency -> stringResource(R.string.excuse_emergency_hint, blockedUntil)
    else -> stringResource(R.string.excuse_extra_time_hint, blockedUntil)
}

@Composable
private fun ColumnScope.DecidedStep(state: BlockUiState, step: BlockStep.Decided, actions: BlockActions) {
    val verdict = step.verdict
    Meta(state)
    when {
        verdict.reason == VerdictReason.CRISIS -> CrisisVerdict(state, step, actions)
        verdict.valid && verdict.offline -> RuleApproved(state, verdict, actions)
        verdict.valid -> Approved(state, verdict, actions)
        else -> Rejected(state, step, actions)
    }
}

@Composable
private fun ColumnScope.Approved(state: BlockUiState, verdict: Verdict, actions: BlockActions) {
    val type = LaVidaTheme.type
    CenteredBody {
        Banana(BananaMood.HAPPY, 104.dp)
        StatusTag(verdictLabel(verdict), approved = true)
        Text(text = verdictMessage(verdict), style = type.headline, color = LaVidaTheme.colors.textPrimary)
        IconNotice(R.drawable.ic_ui_history, stringResource(R.string.verdict_clock), tone = NoticeTone.PLAIN)
    }
    PrimaryButton(
        text = stringResource(R.string.verdict_open_app, state.appLabel),
        onClick = actions.onOpenApp,
        modifier = Modifier.fillMaxWidth(),
    )
    verdict.message?.let { ReportControl(it, ReportSource.VERDICT, state.reported, actions, Modifier.align(Alignment.CenterHorizontally)) }
}

/** Decidió la regla, no el juez: se dice claro que nadie leyó la razón. */
@Composable
private fun ColumnScope.RuleApproved(state: BlockUiState, verdict: Verdict, actions: BlockActions) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val shared = state.quota.judged
    CenteredBody {
        StatusTag(verdictLabel(verdict, shared), approved = true)
        Text(
            text = stringResource(if (shared) R.string.verdict_offline_title else R.string.verdict_rule_title),
            style = type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = colors.textPrimary,
        )
        Text(text = stringResource(R.string.verdict_offline_note), style = type.body, color = colors.textBody)
        IconNotice(R.drawable.ic_ui_history, stringResource(R.string.verdict_clock_short), tone = NoticeTone.PLAIN)
    }
    PrimaryButton(
        text = stringResource(R.string.verdict_open_app, state.appLabel),
        onClick = actions.onOpenApp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.Rejected(state: BlockUiState, step: BlockStep.Decided, actions: BlockActions) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val verdict = step.verdict
    val shared = state.quota.judged
    CenteredBody {
        Banana(BananaMood.SAD, 104.dp)
        StatusTag(verdictLabel(verdict, shared), approved = false)
        Text(text = verdictMessage(verdict), style = type.headline, color = colors.textPrimary)
        Panel(spacing = 6.dp, shadow = 0.dp) {
            Text(
                text = stringResource(R.string.verdict_what_now),
                style = type.small.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.textPrimary,
            )
            Tip(verdictTip(verdict.reason, shared))
            state.blockedUntil?.let { Tip(stringResource(R.string.verdict_tip_opens, state.appLabel, it)) }
            if (step.type == ExcuseType.EXTRA_TIME) Tip(stringResource(R.string.verdict_tip_emergency))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(
            text = stringResource(R.string.block_exit),
            onClick = actions.onExit,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.verdict_back),
            onClick = actions.onBack,
            modifier = Modifier.fillMaxWidth(),
        )
        verdict.message?.let { ReportControl(it, ReportSource.VERDICT, state.reported, actions, Modifier.align(Alignment.CenterHorizontally)) }
    }
}

@Composable
private fun Tip(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "•", style = LaVidaTheme.type.small, color = LaVidaTheme.colors.textPrimary)
        Text(text = text, style = LaVidaTheme.type.small, color = LaVidaTheme.colors.textPrimary)
    }
}

/** Sin banano ni sarcasmo: un banano feliz no acompaña a alguien en crisis. */
@Composable
private fun ColumnScope.CrisisVerdict(state: BlockUiState, step: BlockStep.Decided, actions: BlockActions) {
    HelpBody(stringResource(R.string.crisis_title, step.verdict.grantedMinutes), step.helpLines)
    SecondaryButton(
        text = stringResource(R.string.verdict_open_app, state.appLabel),
        onClick = actions.onOpenApp,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** La misma ayuda sin escribir nada. No desbloquea: si diera minutos, sería una salida gratis. */
@Composable
private fun ColumnScope.HelpStep(state: BlockUiState, step: BlockStep.Help, actions: BlockActions) {
    Meta(state)
    HelpBody(stringResource(R.string.help_title), step.lines)
    SecondaryButton(
        text = stringResource(R.string.verdict_back),
        onClick = actions.onBack,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Líneas de ayuda: un toque abre el marcador, sin llamar por el usuario. */
@Composable
private fun ColumnScope.HelpBody(title: String, lines: List<CrisisLine>) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val uriHandler = LocalUriHandler.current
    CenteredBody {
        Text(text = title, style = type.display, color = colors.textPrimary)
        Text(text = stringResource(R.string.crisis_body), style = type.body.copy(fontSize = 17.sp, lineHeight = 26.sp), color = colors.textPrimary)
        Panel(spacing = 10.dp) {
            Text(text = stringResource(R.string.crisis_danger), style = type.body, color = colors.textPrimary)
            lines.forEach { line ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = line.name, style = type.caption, color = colors.textSecondary)
                    DialButton(line.number) { uriHandler.openUri("tel:${line.number}") }
                }
            }
            if (lines.isNotEmpty()) {
                Text(text = stringResource(R.string.crisis_dial_note), style = type.caption, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DialButton(number: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        SecondaryButton(
            text = stringResource(R.string.crisis_dial, number),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        UiIcon(R.drawable.ic_ui_phone, Modifier.align(Alignment.CenterStart).padding(start = 18.dp), size = 18.dp)
    }
}

private val REPORT_REASONS = listOf(
    "offensive" to R.string.report_reason_offensive,
    "harmful" to R.string.report_reason_harmful,
    "wrong" to R.string.report_reason_wrong,
    "other" to R.string.report_reason_other,
)

/** Reporta un mensaje de la IA dentro de la pantalla. Solo aparece si el mensaje vino de la IA. */
@Composable
private fun ReportControl(
    message: String,
    source: ReportSource,
    reported: Boolean?,
    actions: BlockActions,
    modifier: Modifier = Modifier,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    when (reported) {
        true -> Text(
            text = stringResource(R.string.report_sent),
            modifier = modifier,
            style = LaVidaTheme.type.small,
            color = LaVidaTheme.colors.textBody,
        )
        else -> TextLink(
            text = stringResource(if (reported == false) R.string.report_retry else R.string.report_action),
            onClick = { choosing = true },
            modifier = modifier,
            weight = FontWeight.SemiBold,
        )
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.report_title), style = LaVidaTheme.type.subhead) },
            text = {
                Column(horizontalAlignment = Alignment.Start) {
                    REPORT_REASONS.forEach { (reason, label) ->
                        TextLink(
                            text = stringResource(label),
                            onClick = {
                                choosing = false
                                actions.onReport(message, reason, source)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextLink(text = stringResource(R.string.action_cancel), onClick = { choosing = false })
            },
            shape = MaterialTheme.shapes.large,
            containerColor = LaVidaTheme.colors.surface,
        )
    }
}

private val PreviewActions = BlockActions({}, {}, {}, {}, {}, {}, {}, { _, _, _ -> })

private val PreviewState = BlockUiState(
    attemptId = 7,
    packageName = "com.instagram.android",
    appLabel = "Instagram",
    time = "23:40",
    attemptNumber = 7,
    message = "¿Instagram otra vez? Así piensas ser el mejor programador del mundo. Qué mierda.",
    quota = QuotaToday(extraTimeLeft = 2, extraTimeDaily = 3, emergencyLeft = 2, emergencyDaily = 2, judged = true),
    blockedUntil = "las 06:00",
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
