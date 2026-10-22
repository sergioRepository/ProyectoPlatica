package com.lavidanoesunbanano.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.DayToggle
import com.lavidanoesunbanano.app.core.designsystem.GhostButton
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.toon
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import java.time.DayOfWeek
import java.time.LocalTime

// Iniciales de lunes a domingo; la X evita confundir martes y miércoles.
private val DAY_INITIALS = listOf(
    DayOfWeek.MONDAY to "L",
    DayOfWeek.TUESDAY to "M",
    DayOfWeek.WEDNESDAY to "X",
    DayOfWeek.THURSDAY to "J",
    DayOfWeek.FRIDAY to "V",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "D",
)

@Composable
internal fun ScheduleStep(ranges: List<BlockRange>, onAdd: (BlockRange) -> Unit, onRemove: (Int) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ranges.isEmpty()) {
            MonoLabel(stringResource(R.string.onboarding_schedule_empty))
        } else {
            ranges.forEachIndexed { index, range -> RangeRow(range, onRemove = { onRemove(index) }) }
        }
        Spacer(Modifier.height(4.dp))
        MonoLabel(stringResource(R.string.onboarding_schedule_presets))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.onboarding_schedule_preset_work), onClick = { onAdd(SchedulePresets.WORK) })
            SecondaryButton(stringResource(R.string.onboarding_schedule_preset_nights), onClick = { onAdd(SchedulePresets.NIGHTS) })
            SecondaryButton(stringResource(R.string.onboarding_schedule_preset_mornings), onClick = { onAdd(SchedulePresets.MORNINGS) })
        }
        Spacer(Modifier.height(4.dp))
        MonoLabel(stringResource(R.string.onboarding_schedule_custom))
        RangeEditor(onAdd = onAdd)
    }
}

@Composable
private fun RangeRow(range: BlockRange, onRemove: () -> Unit) {
    val colors = LaVidaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toon(MaterialTheme.shapes.medium, colors.surface, colors.border)
            .padding(start = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(text = ScheduleFormatter.range(range), style = LaVidaTheme.type.monoBody, color = colors.textPrimary)
            if (range.crossesMidnight) {
                MonoLabel(stringResource(R.string.onboarding_schedule_overnight))
            }
        }
        GhostButton(text = stringResource(R.string.action_remove), onClick = onRemove)
    }
}

@Composable
private fun RangeEditor(onAdd: (BlockRange) -> Unit) {
    var days by rememberSaveable { mutableStateOf(DayOfWeek.entries.take(5).map { it.value }.toSet()) }
    var startMinute by rememberSaveable { mutableStateOf(8 * 60) }
    var endMinute by rememberSaveable { mutableStateOf(18 * 60) }
    var editing by remember { mutableStateOf<TimeField?>(null) }
    val start = LocalTime.of(startMinute / 60, startMinute % 60)
    val end = LocalTime.of(endMinute / 60, endMinute % 60)

    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_INITIALS.forEach { (day, initial) ->
                val selected = day.value in days
                DayToggle(
                    label = initial,
                    selected = selected,
                    onToggle = { days = if (selected) days - day.value else days + day.value },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TimeBox(stringResource(R.string.onboarding_schedule_from), start, onClick = { editing = TimeField.START })
            TimeBox(stringResource(R.string.onboarding_schedule_to), end, onClick = { editing = TimeField.END })
        }
        when {
            start == end -> MonoLabel(stringResource(R.string.onboarding_schedule_all_day))
            end < start -> MonoLabel(stringResource(R.string.onboarding_schedule_overnight))
        }
        SecondaryButton(
            text = stringResource(R.string.action_add),
            enabled = days.isNotEmpty(),
            onClick = {
                onAdd(BlockRange(days.map { DayOfWeek.of(it) }.toSet(), start, end))
            },
        )
    }

    editing?.let { field ->
        TimeDialog(
            initial = if (field == TimeField.START) start else end,
            onDismiss = { editing = null },
            onConfirm = { time ->
                val minutes = time.hour * 60 + time.minute
                if (field == TimeField.START) startMinute = minutes else endMinute = minutes
                editing = null
            },
        )
    }
}

private enum class TimeField { START, END }

@Composable
private fun TimeBox(label: String, time: LocalTime, onClick: () -> Unit) {
    val colors = LaVidaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MonoLabel(label)
        Text(
            text = ScheduleFormatter.time(time),
            modifier = Modifier
                .toon(MaterialTheme.shapes.small, colors.surface, colors.border, shadow = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .width(56.dp),
            style = LaVidaTheme.type.monoBody,
            color = colors.textPrimary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val colors = LaVidaTheme.colors
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            GhostButton(
                text = stringResource(R.string.action_accept),
                onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
            )
        },
        dismissButton = { GhostButton(text = stringResource(R.string.action_cancel), onClick = onDismiss) },
        title = {
            Text(
                text = stringResource(R.string.onboarding_schedule_pick_time),
                style = LaVidaTheme.type.body,
                color = colors.textPrimary,
            )
        },
        text = {
            TimeInput(
                state = state,
                colors = TimePickerDefaults.colors(
                    timeSelectorSelectedContainerColor = colors.background,
                    timeSelectorUnselectedContainerColor = colors.background,
                    timeSelectorSelectedContentColor = colors.accent,
                    timeSelectorUnselectedContentColor = colors.textPrimary,
                ),
            )
        },
        shape = MaterialTheme.shapes.large,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    )
}
