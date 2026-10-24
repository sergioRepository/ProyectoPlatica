package com.lavidanoesunbanano.app.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.toon
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.stats.AppCount
import com.lavidanoesunbanano.app.domain.stats.DayCount
import com.lavidanoesunbanano.app.domain.stats.Stats
import com.lavidanoesunbanano.app.domain.stats.StatsCalculator
import kotlin.math.min

// Marcas finas: columnas de 24 dp como máximo con la punta redondeada y la base recta,
// barras de 10 dp, una línea base y nada de cuadrícula. Todas con contorno de tinta: el
// amarillo solo sobre papel no llega al contraste mínimo.
private val MaxColumnWidth = 24.dp
private val BarThickness = 10.dp
private val MarkRadius = 4.dp
private val ChartHeight = 120.dp


@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(state = state, onPeriodChange = viewModel::setPeriod, onSelectDay = viewModel::selectDay)
}

@Composable
private fun StatsContent(state: StatsUiState?, onPeriodChange: (Int) -> Unit, onSelectDay: (Int) -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.stats_title), style = LaVidaTheme.type.title, color = colors.textPrimary)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsViewModel.PERIODS.forEach { days ->
                PeriodChip(
                    label = stringResource(if (days == 7) R.string.stats_period_7 else R.string.stats_period_30),
                    selected = state?.days == days,
                    onClick = { onPeriodChange(days) },
                )
            }
        }
        if (state == null) return@Column
        val stats = state.stats

        Spacer(Modifier.height(24.dp))
        // Dos columnas: con tres, las etiquetas en mono no caben en el ancho del teléfono.
        Panel {
            Row(Modifier.fillMaxWidth()) {
                Figure(stringResource(R.string.stats_attempts), stats.attempts.toString(), Modifier.weight(1f))
                Figure(
                    stringResource(R.string.stats_recovered),
                    stringResource(R.string.stats_recovered_value, stats.recoveredMinutes),
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Figure(stringResource(R.string.stats_granted), stats.grantedMinutes.toString(), Modifier.weight(1f))
                Figure(
                    stringResource(R.string.stats_excuses),
                    (stats.approved + stats.rejected).toString(),
                    Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.stats_recovered_note, StatsCalculator.MINUTES_PER_BLOCKED_ATTEMPT),
            style = LaVidaTheme.type.small,
            color = colors.textSecondary,
        )

        if (stats.attempts == 0) {
            Spacer(Modifier.height(32.dp))
            Text(text = stringResource(R.string.stats_empty), style = LaVidaTheme.type.small, color = colors.textSecondary)
        } else {
            Spacer(Modifier.height(32.dp))
            MonoLabel(stringResource(R.string.stats_per_day))
            Spacer(Modifier.height(12.dp))
            val day = stats.perDay[state.selectedDay]
            Text(
                text = pluralStringResource(
                    R.plurals.stats_day_readout,
                    day.attempts,
                    ScheduleFormatter.date(day.date),
                    day.attempts,
                ),
                style = LaVidaTheme.type.monoBody,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            DayColumns(stats.perDay, state.selectedDay, onSelectDay)
            Spacer(Modifier.height(6.dp))
            DayAxis(stats.perDay)

            Spacer(Modifier.height(32.dp))
            MonoLabel(stringResource(R.string.stats_per_app))
            Spacer(Modifier.height(12.dp))
            AppBars(stats.perApp)
        }

        Spacer(Modifier.height(32.dp))
        MonoLabel(stringResource(R.string.stats_excuses))
        Spacer(Modifier.height(12.dp))
        ExcuseFigures(stats)
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .toon(shape, if (selected) colors.accent else colors.surface, colors.border, shadow = if (selected) 2.dp else 0.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = LaVidaTheme.type.mono,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MonoLabel(label)
        Text(text = value, style = LaVidaTheme.type.figure, color = LaVidaTheme.colors.textPrimary, maxLines = 1)
    }
}

/** Una columna por día. Tocar cualquier punto de su franja la elige y muestra su cifra arriba. */
@Composable
private fun DayColumns(perDay: List<DayCount>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = LaVidaTheme.colors.accent
    val baseline = LaVidaTheme.colors.border
    val paper = LaVidaTheme.colors.surface
    val description = perDay.joinToString("; ") { "${ScheduleFormatter.date(it.date)}: ${it.attempts}" }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description }
            .pointerInput(perDay.size) {
                detectTapGestures { offset ->
                    val index = (offset.x / (size.width.toFloat() / perDay.size)).toInt()
                    onSelect(index.coerceIn(0, perDay.lastIndex))
                }
            },
    ) {
        val slot = size.width / perDay.size
        val gap = 2.dp.toPx()
        val barWidth = min(MaxColumnWidth.toPx(), slot - gap).coerceAtLeast(1f)
        val radius = CornerRadius(min(MarkRadius.toPx(), barWidth / 2))
        val max = perDay.maxOf { it.attempts }.coerceAtLeast(1)
        perDay.forEachIndexed { index, day ->
            if (day.attempts == 0) return@forEachIndexed
            val left = index * slot + (slot - barWidth) / 2
            val top = size.height * (1f - day.attempts.toFloat() / max)
            val column = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = left + barWidth,
                        bottom = size.height,
                        topLeftCornerRadius = radius,
                        topRightCornerRadius = radius,
                        bottomRightCornerRadius = CornerRadius.Zero,
                        bottomLeftCornerRadius = CornerRadius.Zero,
                    ),
                )
            }
            // El día que se lee va en amarillo; los demás, en papel con el mismo contorno.
            drawPath(column, color = if (index == selected) accent else paper)
            drawPath(column, color = baseline, style = Stroke(width = 2.dp.toPx()))
        }
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
    }
}

/** Con 7 días, cada día abreviado; con 30 no caben: solo el primero y el último. */
@Composable
private fun DayAxis(perDay: List<DayCount>) {
    val style = LaVidaTheme.type.mono
    val color = LaVidaTheme.colors.textSecondary
    if (perDay.size <= 7) {
        Row(Modifier.fillMaxWidth()) {
            perDay.forEach { day ->
                Text(
                    text = ScheduleFormatter.day(day.date.dayOfWeek),
                    modifier = Modifier.weight(1f),
                    style = style,
                    color = color,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = ScheduleFormatter.shortDate(perDay.first().date), style = style, color = color)
            Text(text = ScheduleFormatter.shortDate(perDay.last().date), style = style, color = color)
        }
    }
}

/** Barras horizontales con la app a la izquierda y la cifra en la punta de la barra. */
@Composable
private fun AppBars(perApp: List<AppCount>) {
    val colors = LaVidaTheme.colors
    val max = perApp.maxOfOrNull { it.attempts }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        perApp.forEach { app ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    modifier = Modifier.width(96.dp),
                    style = LaVidaTheme.type.small,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BoxWithConstraints(Modifier.weight(1f)) {
                    // Se reserva lugar para la cifra: la barra más larga no la empuja fuera.
                    val track = maxWidth - 40.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(track * (app.attempts.toFloat() / max))
                                .height(BarThickness)
                                .toon(RoundedCornerShape(topEnd = MarkRadius, bottomEnd = MarkRadius), colors.accent, colors.border, shadow = 0.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = app.attempts.toString(), style = LaVidaTheme.type.monoBody, color = colors.textPrimary)
                    }
                }
            }
        }
    }
}

/**
 * Aprobadas y rechazadas por tipo. Son cifras con su palabra al lado: los colores de estado
 * del spec son apagados y no se distinguen con daltonismo, así que el color solo acompaña.
 */
@Composable
private fun ExcuseFigures(stats: Stats) {
    val colors = LaVidaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(ExcuseType.EMERGENCY to R.string.stats_emergency, ExcuseType.EXTRA_TIME to R.string.stats_extra_time)
            .forEach { (type, label) ->
                val tally = stats.byType.getValue(type)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(label),
                        modifier = Modifier.width(96.dp),
                        style = LaVidaTheme.type.small,
                        color = colors.textPrimary,
                    )
                    StateCount(stringResource(R.string.stats_approved_count, tally.approved), colors.approved, Modifier.weight(1f))
                    StateCount(stringResource(R.string.stats_rejected_count, tally.rejected), colors.rejected, Modifier.weight(1f))
                }
            }
        if (stats.pendingReview > 0) {
            Text(
                text = pluralStringResource(R.plurals.stats_pending_review, stats.pendingReview, stats.pendingReview),
                style = LaVidaTheme.type.small,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StateCount(text: String, mark: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(mark, MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = LaVidaTheme.type.mono, color = LaVidaTheme.colors.textSecondary)
    }
}
