package com.lavidanoesunbanano.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.GhostButton
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.domain.model.AttemptOutcome
import com.lavidanoesunbanano.app.domain.model.ExcuseRecord
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.stats.HistoryItem
import com.lavidanoesunbanano.app.feature.block.verdictColor
import com.lavidanoesunbanano.app.feature.block.verdictLabel
import com.lavidanoesunbanano.app.feature.block.verdictMessage
import java.time.LocalDate
import java.time.ZoneId

/** Hora en mono a la izquierda; las excusas se alinean con el nombre de la app. */
private val TimeColumn = 56.dp

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(state = state, onMarkReviewed = viewModel::markReviewed)
}

@Composable
private fun HistoryContent(state: HistoryUiState?, onMarkReviewed: (Long) -> Unit) {
    val colors = LaVidaTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
    ) {
        item {
            Spacer(Modifier.height(40.dp))
            Text(text = stringResource(R.string.history_title), style = LaVidaTheme.type.title, color = colors.textPrimary)
        }
        if (state != null && state.days.isEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(text = stringResource(R.string.history_empty), style = LaVidaTheme.type.small, color = colors.textSecondary)
            }
        }
        state?.days?.forEach { day ->
            item(key = "dia-${day.date}") {
                Spacer(Modifier.height(32.dp))
                MonoLabel(dayLabel(day.date, state.today))
                Spacer(Modifier.height(4.dp))
            }
            items(day.items, key = { it.attempt.id }) { item ->
                HistoryRow(item, state.zone, onMarkReviewed)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, zone: ZoneId, onMarkReviewed: (Long) -> Unit) {
    val colors = LaVidaTheme.colors
    val attempt = item.attempt
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ScheduleFormatter.time(attempt.startedAt.atZone(zone).toLocalTime()),
                modifier = Modifier.width(TimeColumn),
                style = LaVidaTheme.type.monoBody,
                color = colors.textSecondary,
            )
            Text(
                text = attempt.app.label,
                modifier = Modifier.weight(1f),
                style = LaVidaTheme.type.body,
                color = colors.textPrimary,
            )
            Text(text = outcomeLabel(attempt.outcome), style = LaVidaTheme.type.mono, color = colors.textSecondary)
        }
        item.excuses.forEach { excuse ->
            ExcuseLine(excuse, onMarkReviewed)
        }
    }
}

@Composable
private fun ExcuseLine(excuse: ExcuseRecord, onMarkReviewed: (Long) -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = TimeColumn, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = excuse.reason, style = LaVidaTheme.type.small, color = colors.textPrimary)
        Text(
            text = verdictMessage(excuse.verdict),
            style = LaVidaTheme.type.small,
            color = colors.textSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = verdictLabel(excuse.verdict), style = LaVidaTheme.type.mono, color = verdictColor(excuse.verdict))
        if (excuse.needsReview) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.history_review),
                    modifier = Modifier.weight(1f),
                    style = LaVidaTheme.type.mono,
                    color = colors.rejected,
                )
                GhostButton(text = stringResource(R.string.history_mark_reviewed), onClick = { onMarkReviewed(excuse.id) })
            }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.history_today)
    today.minusDays(1) -> stringResource(R.string.history_yesterday)
    else -> ScheduleFormatter.date(date)
}

@Composable
private fun outcomeLabel(outcome: AttemptOutcome?): String = stringResource(
    when (outcome) {
        AttemptOutcome.EXIT -> R.string.outcome_exit
        AttemptOutcome.EMERGENCY -> R.string.outcome_emergency
        AttemptOutcome.EXTRA_TIME -> R.string.outcome_extra_time
        null -> R.string.outcome_left
    },
)
