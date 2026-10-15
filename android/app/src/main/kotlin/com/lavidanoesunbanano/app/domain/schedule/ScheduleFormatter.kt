package com.lavidanoesunbanano.app.domain.schedule

import com.lavidanoesunbanano.app.domain.model.BlockRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Describe horarios en español corto ("lun a vie 08:00-18:00").
 *
 * Es formato de datos, no copy de interfaz: el mismo texto va al backend como
 * `schedule_summary` y se muestra en la app, que solo existe en español.
 */
object ScheduleFormatter {
    private val ABBREVIATIONS = mapOf(
        DayOfWeek.MONDAY to "lun",
        DayOfWeek.TUESDAY to "mar",
        DayOfWeek.WEDNESDAY to "mié",
        DayOfWeek.THURSDAY to "jue",
        DayOfWeek.FRIDAY to "vie",
        DayOfWeek.SATURDAY to "sáb",
        DayOfWeek.SUNDAY to "dom",
    )
    private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val WEEKDAYS = DayOfWeek.entries.take(5).toSet()
    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun days(days: Set<DayOfWeek>): String {
        val sorted = days.sorted()
        return when {
            days.size == 7 -> "todos los días"
            days == WEEKDAYS -> "lun a vie"
            days == WEEKEND -> "fines de semana"
            sorted.size >= 3 && sorted.zipWithNext().all { (a, b) -> b.value == a.value + 1 } ->
                "${ABBREVIATIONS.getValue(sorted.first())} a ${ABBREVIATIONS.getValue(sorted.last())}"
            else -> sorted.joinToString(", ") { ABBREVIATIONS.getValue(it) }
        }
    }

    fun time(time: LocalTime): String = time.format(TIME)

    /** "lun", "mar"... */
    fun day(day: DayOfWeek): String = ABBREVIATIONS.getValue(day)

    /** "15 oct". */
    fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

    /** "jue 15 oct": para encabezados del historial y fechas cortas. */
    fun date(date: LocalDate): String =
        "${ABBREVIATIONS.getValue(date.dayOfWeek)} ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

    /** "15 oct 08:00". */
    fun dateTime(at: LocalDateTime): String = "${at.dayOfMonth} ${MONTHS[at.monthValue - 1]} ${time(at.toLocalTime())}"

    fun range(range: BlockRange): String {
        val hours = if (range.isAllDay) "todo el día" else "${time(range.start)}-${time(range.end)}"
        return "${days(range.days)} $hours"
    }

    fun summary(ranges: List<BlockRange>): String = ranges.joinToString("; ") { range(it) }
}
