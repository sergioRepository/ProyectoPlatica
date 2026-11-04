package com.lavidanoesunbanano.app.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.DashedDivider
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.Notice
import com.lavidanoesunbanano.app.core.designsystem.NoticeTone
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.SegmentedControl
import com.lavidanoesunbanano.app.core.designsystem.TextLink
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.stats.AppCount
import com.lavidanoesunbanano.app.domain.stats.DayCount
import com.lavidanoesunbanano.app.domain.stats.ExcuseTally
import com.lavidanoesunbanano.app.domain.stats.Stats
import com.lavidanoesunbanano.app.domain.stats.StatsCalculator
import java.time.DayOfWeek
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val ChartHeight = 160.dp
private val MaxBarHeight = 120.dp
private val BarThickness = 22.dp
private val AppLabelWidth = 84.dp
private const val WEEK_DAYS = 7

/** La pestaña Resumen del Historial. */
@Composable
fun StatsTab(onReview: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(state = state, onPeriodChange = viewModel::setPeriod, onSelectDay = viewModel::selectDay, onReview = onReview)
}

@Composable
private fun StatsContent(
    state: StatsUiState?,
    onPeriodChange: (Int) -> Unit,
    onSelectDay: (Int) -> Unit,
    onReview: () -> Unit,
) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state == null) return@Column
        val stats = state.stats
        if (stats.pendingReview > 0) PendingCard(stats.pendingReview, onReview)
        SegmentedControl(
            labels = listOf(stringResource(R.string.stats_period_7), stringResource(R.string.stats_period_30)),
            selected = StatsViewModel.PERIODS.indexOf(state.days),
            onSelect = { onPeriodChange(StatsViewModel.PERIODS[it]) },
        )
        Headline(stats, state.days)
        if (stats.attempts == 0) {
            Text(text = stringResource(R.string.stats_empty), style = type.small, color = colors.textSecondary)
        } else {
            DaysCard(stats.perDay, state.selectedDay, onSelectDay)
            Panel {
                Text(text = stringResource(R.string.stats_per_app), style = type.subhead, color = colors.textPrimary)
                AppBars(stats.perApp)
            }
        }
        Panel(spacing = 14.dp) {
            Text(text = stringResource(R.string.stats_excuses), style = type.subhead, color = colors.textPrimary)
            ReasonRow(stringResource(R.string.stats_emergency), stats.byType.getValue(ExcuseType.EMERGENCY))
            ReasonRow(stringResource(R.string.stats_extra_time), stats.byType.getValue(ExcuseType.EXTRA_TIME))
        }
    }
}

@Composable
private fun PendingCard(pending: Int, onReview: () -> Unit) {
    val colors = LaVidaTheme.colors
    Notice(tone = NoticeTone.INFO) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.stats_pending_review, pending, pending),
                    style = LaVidaTheme.type.subhead,
                    color = colors.textPrimary,
                )
                Text(text = stringResource(R.string.stats_pending_hint), style = LaVidaTheme.type.caption, color = colors.textBody)
            }
            TextLink(text = stringResource(R.string.stats_review), onClick = onReview)
        }
    }
}

@Composable
private fun Headline(stats: Stats, days: Int) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = recovered(stats.recoveredMinutes), style = type.bigFigure, color = colors.textPrimary)
            Text(text = stringResource(R.string.stats_not_used), style = type.small, color = colors.textBody)
        }
        val attempts = pluralStringResource(R.plurals.stats_attempts, stats.attempts, stats.attempts)
        Text(
            text = stats.previousAttempts?.let { previous ->
                stringResource(R.string.stats_attempts_delta, attempts, comparison(stats.attempts - previous, days))
            } ?: attempts,
            style = type.body,
            color = colors.textPrimary,
        )
        DashedDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Figure(stringResource(R.string.stats_granted_value, stats.grantedMinutes), stringResource(R.string.stats_granted), Modifier.weight(1f))
            Figure((stats.approved + stats.rejected).toString(), stringResource(R.string.stats_reasons), Modifier.weight(1f))
        }
        Text(
            text = stringResource(R.string.stats_recovered_note, StatsCalculator.MINUTES_PER_BLOCKED_ATTEMPT),
            style = type.caption,
            color = colors.textSecondary,
        )
    }
}

/** Minutos mientras caben; después, horas redondeadas. */
@Composable
private fun recovered(minutes: Int): String =
    if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.minutes_approx, minutes)
    } else {
        stringResource(R.string.hours_approx, (minutes / MINUTES_PER_HOUR.toFloat()).roundToInt())
    }

private const val MINUTES_PER_HOUR = 60

@Composable
private fun comparison(delta: Int, days: Int): String {
    val week = days == WEEK_DAYS
    return when {
        delta == 0 -> stringResource(if (week) R.string.stats_same_week else R.string.stats_same_month)
        delta < 0 -> stringResource(if (week) R.string.stats_fewer_week else R.string.stats_fewer_month, delta.absoluteValue)
        else -> stringResource(if (week) R.string.stats_more_week else R.string.stats_more_month, delta)
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = value, style = LaVidaTheme.type.figure, color = LaVidaTheme.colors.textPrimary, maxLines = 1)
        Text(text = label, style = LaVidaTheme.type.caption, color = LaVidaTheme.colors.textSecondary)
    }
}

@Composable
private fun DaysCard(perDay: List<DayCount>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LaVidaTheme.colors
    val day = perDay[selected]
    val gap = if (perDay.size <= WEEK_DAYS) 10.dp else 2.dp
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_per_day),
                modifier = Modifier.weight(1f),
                style = LaVidaTheme.type.subhead,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.stats_day_readout, dayName(perDay, selected), day.attempts),
                style = LaVidaTheme.type.mono,
                color = colors.textPrimary,
            )
        }
        DayColumns(perDay, selected, gap, onSelect)
        DayAxis(perDay, gap)
    }
}

@Composable
private fun dayName(perDay: List<DayCount>, index: Int): String {
    val ago = perDay.lastIndex - index
    return when {
        ago == 0 -> stringResource(R.string.history_today)
        perDay.size <= WEEK_DAYS -> ScheduleFormatter.day(perDay[index].date.dayOfWeek)
        else -> stringResource(R.string.stats_days_ago, ago)
    }
}

/** Una columna por día. Tocarla la elige; la elegida va en amarillo y con su cifra encima. */
@Composable
private fun DayColumns(perDay: List<DayCount>, selected: Int, gap: Dp, onSelect: (Int) -> Unit) {
    val colors = LaVidaTheme.colors
    val max = perDay.maxOf { it.attempts }.coerceAtLeast(1)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            perDay.forEachIndexed { index, day ->
                val on = index == selected
                val name = dayName(perDay, index)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button) { onSelect(index) }
                        .semantics { contentDescription = "$name: ${day.attempts}" },
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (on) {
                        Text(
                            text = day.attempts.toString(),
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                            style = LaVidaTheme.type.mono.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                    if (day.attempts > 0) {
                        val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(MaxBarHeight * (day.attempts.toFloat() / max))
                                .background(if (on) colors.accent else colors.surface, shape)
                                .border(2.dp, colors.border, shape),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.border),
        )
    }
}

/** Con 7 días, cada día; con 30 no caben: solo los lunes. */
@Composable
private fun DayAxis(perDay: List<DayCount>, gap: Dp) {
    val week = perDay.size <= WEEK_DAYS
    val style = LaVidaTheme.type.mono.copy(fontSize = LaVidaTheme.type.monoLabel.fontSize)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        perDay.forEachIndexed { index, day ->
            val tick = when {
                week -> dayName(perDay, index)
                day.date.dayOfWeek == DayOfWeek.MONDAY -> ScheduleFormatter.shortDate(day.date)
                else -> ""
            }
            Text(
                text = tick,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentWidth(unbounded = true),
                style = style,
                color = LaVidaTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Barras con la app a la izquierda y la cifra en la punta; la que más tiene, en amarillo. */
@Composable
private fun AppBars(perApp: List<AppCount>) {
    val colors = LaVidaTheme.colors
    val max = perApp.maxOfOrNull { it.attempts }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        perApp.forEachIndexed { index, app ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = app.label,
                    modifier = Modifier.width(AppLabelWidth),
                    style = LaVidaTheme.type.small.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BoxWithConstraints(Modifier.weight(1f)) {
                    // Se reserva lugar para la cifra: la barra más larga no la empuja fuera.
                    val track = maxWidth - 40.dp
                    val shape = RoundedCornerShape(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .width(track * (app.attempts.toFloat() / max))
                                .height(BarThickness)
                                .background(if (index == 0) colors.accent else colors.surface, shape)
                                .border(2.dp, colors.border, shape),
                        )
                        Text(
                            text = app.attempts.toString(),
                            style = LaVidaTheme.type.mono.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Aprobadas y rechazadas de un tipo. Son cifras con su palabra al lado y las rechazadas van
 * rayadas: el rojo y el verde no se distinguen con daltonismo, así que el color solo acompaña.
 */
@Composable
private fun ReasonRow(name: String, tally: ExcuseTally) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = name, style = type.small.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val mono = type.mono.copy(fontWeight = FontWeight.SemiBold)
            Text(text = stringResource(R.string.stats_approved_count, tally.approved), style = mono, color = colors.approved)
            Text(text = stringResource(R.string.stats_rejected_count, tally.rejected), style = mono, color = colors.rejected)
        }
        if (tally.approved + tally.rejected > 0) StackedBar(tally)
    }
}

@Composable
private fun StackedBar(tally: ExcuseTally) {
    val colors = LaVidaTheme.colors
    val stripe = colors.rejected.copy(alpha = 0.25f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .border(2.dp, colors.border, CircleShape),
    ) {
        if (tally.approved > 0) {
            Box(
                Modifier
                    .weight(tally.approved.toFloat())
                    .fillMaxHeight()
                    .background(colors.approved),
            )
        }
        if (tally.rejected > 0) {
            Canvas(
                Modifier
                    .weight(tally.rejected.toFloat())
                    .fillMaxHeight(),
            ) {
                val step = 8.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(
                        stripe,
                        Offset(x, size.height),
                        Offset(x + size.height, 0f),
                        strokeWidth = 4.dp.toPx(),
                    )
                    x += step
                }
            }
        }
    }
}
