package com.lavidanoesunbanano.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.Banana
import com.lavidanoesunbanano.app.core.designsystem.BananaMood
import com.lavidanoesunbanano.app.core.designsystem.DashedDivider
import com.lavidanoesunbanano.app.core.designsystem.InfoRow
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MainScaffold
import com.lavidanoesunbanano.app.core.designsystem.MainTab
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Notice
import com.lavidanoesunbanano.app.core.designsystem.NoticeTone
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.SmallButton
import com.lavidanoesunbanano.app.core.designsystem.TextLink
import com.lavidanoesunbanano.app.core.designsystem.UiIcon
import com.lavidanoesunbanano.app.core.designsystem.toon
import com.lavidanoesunbanano.app.data.repository.AgentRun
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import com.lavidanoesunbanano.app.domain.setup.ServiceHealth
import com.lavidanoesunbanano.app.domain.setup.SetupChecklist
import com.lavidanoesunbanano.app.domain.setup.SetupItem
import com.lavidanoesunbanano.app.feature.setup.riskRes
import com.lavidanoesunbanano.app.feature.setup.titleRes

@Composable
fun HomeScreen(
    onTab: (MainTab) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenPlans: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnResume() }
    MainScaffold(MainTab.HOME, onTab) {
        HomeContent(state = state, onOpenSetup = onOpenSetup, onOpenPlans = onOpenPlans, onAnalyze = viewModel::analyzeNow)
    }
}

@Composable
private fun HomeContent(state: HomeUiState, onOpenSetup: () -> Unit, onOpenPlans: () -> Unit, onAnalyze: () -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val offline = state.backend == BackendStatus.Unreachable
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MonoLabel(stringResource(R.string.home_brand, state.schedule.date))
        Hero(state, onOpenSetup)
        if (offline || state.disconnected) {
            Notice(tone = NoticeTone.PLAIN) {
                Text(
                    text = stringResource(if (offline) R.string.home_offline_title else R.string.home_disconnected_title),
                    style = type.subhead,
                    color = colors.textPrimary,
                )
                Text(text = stringResource(R.string.home_offline_hint), style = type.caption, color = colors.textBody)
            }
        } else {
            state.setup?.let { RecommendedCard(SetupChecklist.items(it), onOpenSetup) }
        }
        TodayCard(state)
        AgentCard(state, onAnalyze, onOpenPlans)
    }
}

/** Lo primero que se lee: si ahora está libre o bloqueado, y hasta cuándo. */
@Composable
private fun Hero(state: HomeUiState, onOpenSetup: () -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val schedule = state.schedule
    val service = state.setup?.service
    val until = schedule.until
    val texts: HeroTexts = when {
        service == null -> HeroTexts(stringResource(R.string.home_checking), "", colors.surface)
        service == ServiceHealth.OFF -> HeroTexts(
            stringResource(R.string.home_off_title),
            stringResource(R.string.home_off_sub),
            colors.surface,
            stringResource(R.string.home_off_action),
        )
        service == ServiceHealth.STOPPED -> HeroTexts(
            stringResource(R.string.home_stopped_title),
            stringResource(R.string.home_stopped_sub),
            colors.surface,
            stringResource(R.string.home_stopped_action),
        )
        schedule.blocked -> HeroTexts(
            if (until != null) stringResource(R.string.home_blocked_until, until) else stringResource(R.string.home_blocked),
            stringResource(R.string.home_blocked_sub, schedule.apps),
            colors.accent,
        )
        else -> HeroTexts(
            until?.let { stringResource(R.string.home_free_until, it) }.orEmpty(),
            stringResource(R.string.home_free_sub, schedule.apps),
            colors.surface,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toon(MaterialTheme.shapes.large, texts.background, colors.border, shadow = 4.dp)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Banana(BananaMood.SMILE, 76.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = texts.title, style = type.headline, color = colors.textPrimary)
            if (texts.subtitle.isNotEmpty()) {
                Text(text = texts.subtitle, style = type.small, color = colors.textPrimary)
            }
            texts.action?.let { action -> SmallButton(text = action, onClick = onOpenSetup) }
        }
    }
}

private class HeroTexts(val title: String, val subtitle: String, val background: Color, val action: String? = null)

/** Solo cuando el bloqueo ya funciona: lo necesario lo reclama la tarjeta principal. */
@Composable
private fun RecommendedCard(items: List<SetupItem>, onOpenSetup: () -> Unit) {
    val colors = LaVidaTheme.colors
    val pending = items.filter { !it.done }
    if (!SetupChecklist.blockerReady(items) || pending.isEmpty()) return
    val first = pending.first().step
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toon(shape, colors.surface, colors.border, shadow = 0.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onOpenSetup)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = pluralStringResource(R.plurals.home_recommended_pending, pending.size, pending.size),
                style = LaVidaTheme.type.subhead,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.home_recommended_detail, stringResource(first.titleRes), stringResource(first.riskRes)),
                style = LaVidaTheme.type.caption,
                color = colors.textSecondary,
            )
        }
        UiIcon(R.drawable.ic_ui_chevron, size = 18.dp)
    }
}

@Composable
private fun TodayCard(state: HomeUiState) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val today = state.today
    Panel {
        MonoLabel(stringResource(R.string.home_today))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = today?.let { stringResource(R.string.minutes_approx, it.recoveredMinutes) } ?: "-",
                style = type.bigFigure,
                color = colors.textPrimary,
            )
            Text(text = stringResource(R.string.home_not_used), style = type.small, color = colors.textBody)
        }
        DashedDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Figure(today?.attempts?.toString() ?: "-", stringResource(R.string.home_attempts), colors.textPrimary, Modifier.weight(1f))
            Figure(
                today?.let { stringResource(R.string.count_approved, it.approved) } ?: "-",
                stringResource(R.string.home_approved),
                colors.approved,
                Modifier.weight(1f),
            )
            Figure(
                today?.let { stringResource(R.string.count_rejected, it.rejected) } ?: "-",
                stringResource(R.string.home_rejected),
                colors.rejected,
                Modifier.weight(1f),
            )
        }
        state.device?.let { device ->
            Text(
                text = pluralStringResource(R.plurals.home_evaluations_left, device.remainingToday, device.remainingToday),
                style = type.caption,
                color = colors.textSecondary,
            )
        }
        state.unlocks.forEach { unlock ->
            Text(
                text = stringResource(R.string.home_unlocked_value, unlock.label, unlock.until),
                style = type.mono,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun Figure(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = value, style = LaVidaTheme.type.figure, color = color)
        Text(text = label, style = LaVidaTheme.type.caption, color = LaVidaTheme.colors.textSecondary)
    }
}

/**
 * Lo que hizo el agente en una frase. El detalle (herramientas, estados, lo que confirmó el
 * teléfono) sigue a un toque: "enviada" no es "aplicada" y se muestran por separado.
 */
@Composable
private fun AgentCard(state: HomeUiState, onAnalyze: () -> Unit, onOpenPlans: () -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val run = state.agent
    var details by rememberSaveable { mutableStateOf(false) }
    // Sin estado del servidor (sin red) la tarjeta queda como siempre.
    val free = state.device?.agentEnabled == false
    Panel(spacing = 10.dp, shadow = 0.dp) {
        MonoLabel(stringResource(R.string.agent_title))
        if (free) {
            Text(text = stringResource(R.string.agent_free), style = type.body, color = colors.textPrimary)
            SmallButton(text = stringResource(R.string.block_see_plans), onClick = onOpenPlans)
            return@Panel
        }
        Text(text = agentSummary(state), style = type.body, color = colors.textPrimary)
        if (details && run is AgentRun.Done) AgentDetails(run)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.analyzing) {
                Text(text = stringResource(R.string.agent_analyzing), style = type.mono, color = colors.textSecondary)
            } else {
                SmallButton(text = stringResource(R.string.agent_action), onClick = onAnalyze)
            }
            if (run is AgentRun.Done) {
                TextLink(
                    text = stringResource(if (details) R.string.agent_details_hide else R.string.agent_details),
                    onClick = { details = !details },
                )
            }
        }
    }
}

@Composable
private fun agentSummary(state: HomeUiState): String = when (val run = state.agent) {
    null -> stringResource(R.string.agent_never)
    AgentRun.Unavailable -> stringResource(R.string.agent_unavailable)
    is AgentRun.Done -> {
        val response = run.response
        val order = response.intervention
        when {
            order == null -> response.reason.ifBlank { decisionText(response.decision) }
            else -> {
                val apps = order.targetApps.joinToString(", ") { state.schedule.labels[it] ?: it }
                if (response.reason.isBlank()) {
                    stringResource(R.string.agent_blocked, apps, order.durationMinutes)
                } else {
                    stringResource(R.string.agent_blocked_reason, apps, order.durationMinutes, response.reason)
                }
            }
        }
    }
}

@Composable
private fun AgentDetails(run: AgentRun.Done) {
    val response = run.response
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InfoRow(label = stringResource(R.string.agent_decision_label), value = decisionText(response.decision))
        response.steps.forEach { step -> InfoRow(label = step.tool, value = step.status) }
        response.intervention?.let { order ->
            val applied = run.applied ?: order.status
            InfoRow(
                label = stringResource(R.string.agent_focus_label, order.durationMinutes),
                value = order.policyCode?.let { "${order.status} ($it)" } ?: "${order.status} / $applied",
            )
        }
    }
}

@Composable
private fun decisionText(decision: String): String = stringResource(
    when (decision) {
        "intervene" -> R.string.agent_decision_intervene
        "no_action" -> R.string.agent_decision_no_action
        "insufficient_evidence" -> R.string.agent_decision_insufficient
        "plan_quota" -> R.string.agent_decision_plan_quota
        else -> R.string.agent_decision_unavailable
    },
)

@Preview
@Composable
private fun HomePreview() {
    LaVidaTheme {
        MainScaffold(MainTab.HOME, {}) {
            HomeContent(
                state = HomeUiState(
                    backend = BackendStatus.Online("0.1.0"),
                    unlocks = listOf(ActiveUnlock("Instagram", "21:55")),
                    schedule = ScheduleNow("miércoles 4 nov", blocked = false, until = "las 22:00", apps = "Instagram y TikTok"),
                ),
                onOpenSetup = {},
                onAnalyze = {},
                onOpenPlans = {},
            )
        }
    }
}
