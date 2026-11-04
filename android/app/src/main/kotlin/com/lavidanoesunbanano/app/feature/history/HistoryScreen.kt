package com.lavidanoesunbanano.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FilterChip
import com.lavidanoesunbanano.app.core.designsystem.GroupedCard
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MainScaffold
import com.lavidanoesunbanano.app.core.designsystem.MainTab
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Notice
import com.lavidanoesunbanano.app.core.designsystem.NoticeTone
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.RowDivider
import com.lavidanoesunbanano.app.core.designsystem.SmallButton
import com.lavidanoesunbanano.app.core.designsystem.StatusTag
import com.lavidanoesunbanano.app.core.designsystem.UnderlineTabs
import com.lavidanoesunbanano.app.core.designsystem.dashed
import com.lavidanoesunbanano.app.domain.model.AttemptOutcome
import com.lavidanoesunbanano.app.domain.model.ExcuseRecord
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.stats.HistoryDay
import com.lavidanoesunbanano.app.domain.stats.HistoryFilter
import com.lavidanoesunbanano.app.domain.stats.HistoryItem
import com.lavidanoesunbanano.app.feature.block.verdictMessage
import com.lavidanoesunbanano.app.feature.stats.StatsTab
import java.time.LocalDate
import java.time.ZoneId

/** Hora en mono a la izquierda; las razones se alinean con el nombre de la app. */
private val TimeColumn = 56.dp

private const val TAB_SUMMARY = 0
private const val TAB_ATTEMPTS = 1

/** Historial con dos pestañas: el Resumen (las estadísticas) y cada Intento. */
@Composable
fun HistoryScreen(onTab: (MainTab) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_SUMMARY) }
    MainScaffold(MainTab.HISTORY, onTab) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.history_title),
                style = LaVidaTheme.type.screenTitle,
                color = LaVidaTheme.colors.textPrimary,
            )
            UnderlineTabs(
                labels = listOf(stringResource(R.string.history_tab_summary), stringResource(R.string.history_tab_attempts)),
                selected = tab,
                onSelect = { tab = it },
            )
        }
        if (tab == TAB_SUMMARY) {
            StatsTab(onReview = { tab = TAB_ATTEMPTS })
        } else {
            AttemptsTab(state, onFilter = viewModel::setFilter, onMarkReviewed = viewModel::markReviewed)
        }
    }
}

@Composable
private fun AttemptsTab(state: HistoryUiState?, onFilter: (HistoryFilter) -> Unit, onMarkReviewed: (Long) -> Unit) {
    val colors = LaVidaTheme.colors
    LazyColumn(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(0.dp)) }
        if (state != null) {
            if (state.toReview.isNotEmpty()) {
                item(key = "revisar") { ReviewSection(state.toReview, state.today, state.zone, onMarkReviewed) }
            }
            if (state.justReviewed && state.toReview.isEmpty()) {
                item {
                    Notice(tone = NoticeTone.NOTE) {
                        Text(
                            text = stringResource(R.string.history_reviewed),
                            style = LaVidaTheme.type.small.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                    }
                }
            }
            item { Filters(state.filter, onFilter) }
            items(state.days, key = { "dia-${it.date}" }) { day -> DaySection(day, state.today, state.zone) }
            if (state.days.isEmpty()) {
                item {
                    val shape = MaterialTheme.shapes.medium
                    Text(
                        text = stringResource(
                            if (state.filter == HistoryFilter.ALL) R.string.history_empty else R.string.history_empty_filter,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dashed(shape, colors.border)
                            .padding(horizontal = 18.dp, vertical = 22.dp),
                        style = LaVidaTheme.type.body,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Filters(current: HistoryFilter, onFilter: (HistoryFilter) -> Unit) {
    val options = listOf(
        HistoryFilter.ALL to R.string.history_filter_all,
        HistoryFilter.APPROVED to R.string.history_filter_approved,
        HistoryFilter.REJECTED to R.string.history_filter_rejected,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (filter, label) ->
            FilterChip(label = stringResource(label), selected = filter == current, onClick = { onFilter(filter) })
        }
    }
}

/** Las emergencias que nadie leyó. Marcarlas solo quita el aviso: lo dice en vez de sugerir más. */
@Composable
private fun ReviewSection(excuses: List<ExcuseRecord>, today: LocalDate, zone: ZoneId, onMarkReviewed: (Long) -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Notice(tone = NoticeTone.INFO) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.history_to_review), style = type.subhead, color = colors.textPrimary)
            Text(text = stringResource(R.string.history_to_review_hint), style = type.caption, color = colors.textBody)
            excuses.forEach { excuse ->
                val at = excuse.decidedAt.atZone(zone)
                Panel(spacing = 4.dp, padding = 12.dp, shadow = 0.dp) {
                    Text(
                        text = stringResource(
                            R.string.history_to_review_meta,
                            dayLabel(at.toLocalDate(), today),
                            ScheduleFormatter.time(at.toLocalTime()),
                            excuse.app.label,
                        ),
                        style = type.mono,
                        color = colors.textSecondary,
                    )
                    Text(text = stringResource(R.string.history_quoted, excuse.reason), style = type.small, color = colors.textPrimary)
                }
                SmallButton(text = stringResource(R.string.history_mark_reviewed), onClick = { onMarkReviewed(excuse.id) })
            }
        }
    }
}

@Composable
private fun DaySection(day: HistoryDay, today: LocalDate, zone: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonoLabel(dayLabel(day.date, today))
        GroupedCard {
            day.items.forEachIndexed { index, item ->
                if (index > 0) RowDivider()
                HistoryRow(item, zone)
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, zone: ZoneId) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val attempt = item.attempt
    val last = item.excuses.lastOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = ScheduleFormatter.time(attempt.startedAt.atZone(zone).toLocalTime()),
                modifier = Modifier.width(46.dp),
                style = type.monoBody.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = attempt.app.label,
                    style = type.body.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
                Text(text = resultLabel(attempt.outcome, last), style = type.caption, color = colors.textSecondary)
            }
            last?.let {
                StatusTag(
                    text = stringResource(if (it.verdict.valid) R.string.history_approved else R.string.history_rejected),
                    approved = it.verdict.valid,
                    small = true,
                )
            }
        }
        item.excuses.forEach { excuse -> ExcuseBox(excuse) }
    }
}

@Composable
private fun ExcuseBox(excuse: ExcuseRecord) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Panel(
        modifier = Modifier.padding(start = TimeColumn),
        spacing = 6.dp,
        padding = 12.dp,
        shadow = 0.dp,
    ) {
        Text(text = stringResource(R.string.history_quoted, excuse.reason), style = type.small, color = colors.textPrimary)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.textPrimary)) {
                    append(stringResource(if (excuse.verdict.offline) R.string.history_judge_rule else R.string.history_judge_banana))
                }
                append(" ")
                append(verdictMessage(excuse.verdict))
            },
            style = type.caption,
            color = colors.textSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.history_today)
    today.minusDays(1) -> stringResource(R.string.history_yesterday)
    else -> ScheduleFormatter.date(date)
}

/** Qué se pidió; sin razones, cómo terminó el intento. */
@Composable
private fun resultLabel(outcome: AttemptOutcome?, last: ExcuseRecord?): String = stringResource(
    when {
        last?.type == ExcuseType.EXTRA_TIME -> R.string.outcome_extra_time
        last?.type == ExcuseType.EMERGENCY && last.verdict.offline -> R.string.outcome_emergency_offline
        last?.type == ExcuseType.EMERGENCY -> R.string.outcome_emergency
        outcome == AttemptOutcome.EXIT -> R.string.outcome_exit
        else -> R.string.outcome_left
    },
)
