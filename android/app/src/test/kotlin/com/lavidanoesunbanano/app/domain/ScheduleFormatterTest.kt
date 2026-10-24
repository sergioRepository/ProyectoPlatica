package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.Test

class ScheduleFormatterTest {

    @Test
    fun `describes groups of days`() {
        assertEquals("todos los días", ScheduleFormatter.days(DayOfWeek.entries.toSet()))
        assertEquals("lun a vie", ScheduleFormatter.days(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)))
        assertEquals("fines de semana", ScheduleFormatter.days(setOf(SATURDAY, SUNDAY)))
        assertEquals("mar a jue", ScheduleFormatter.days(setOf(TUESDAY, WEDNESDAY, THURSDAY)))
        assertEquals("lun, mié, vie", ScheduleFormatter.days(setOf(FRIDAY, MONDAY, WEDNESDAY)))
        assertEquals("sáb", ScheduleFormatter.days(setOf(SATURDAY)))
    }

    @Test
    fun `describes ranges and summaries`() {
        val work = BlockRange(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), LocalTime.of(8, 0), LocalTime.of(18, 0))
        val nights = BlockRange(DayOfWeek.entries.toSet(), LocalTime.of(22, 0), LocalTime.of(6, 0))
        val allDay = BlockRange(setOf(SUNDAY), LocalTime.of(9, 0), LocalTime.of(9, 0))

        assertEquals("lun a vie 08:00-18:00", ScheduleFormatter.range(work))
        assertEquals("dom todo el día", ScheduleFormatter.range(allDay))
        assertEquals(
            "lun a vie 08:00-18:00; todos los días 22:00-06:00",
            ScheduleFormatter.summary(listOf(work, nights)),
        )
    }

    @Test
    fun `describes dates in short spanish`() {
        assertEquals("jue 15 oct", ScheduleFormatter.date(LocalDate.of(2026, 10, 15)))
        assertEquals("1 ene", ScheduleFormatter.shortDate(LocalDate.of(2027, 1, 1)))
        assertEquals("15 oct 08:05", ScheduleFormatter.dateTime(LocalDateTime.of(2026, 10, 15, 8, 5)))
    }
}
