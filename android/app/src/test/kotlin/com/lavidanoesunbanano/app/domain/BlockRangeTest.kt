package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.isBlockedAt
import com.lavidanoesunbanano.app.domain.model.nextBlockChange
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BlockRangeTest {

    // 2026-10-12 es lunes.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test
    fun `daytime range covers only its days and hours`() {
        val range = BlockRange(setOf(MONDAY), LocalTime.of(8, 0), LocalTime.of(18, 0))

        assertTrue(range.covers(at(12, 8)))
        assertTrue(range.covers(at(12, 17, 59)))
        assertFalse(range.covers(at(12, 18)))
        assertFalse(range.covers(at(12, 7, 59)))
        assertFalse(range.covers(at(13, 10)))
    }

    @Test
    fun `overnight range continues into the next day`() {
        val friday = BlockRange(setOf(FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0))

        assertTrue(friday.covers(at(16, 23)))
        assertTrue(friday.covers(at(17, 5, 59)))
        assertFalse(friday.covers(at(17, 6)))
        assertFalse(friday.covers(at(16, 3)))
    }

    @Test
    fun `sunday overnight wraps into monday`() {
        val sunday = BlockRange(setOf(SUNDAY), LocalTime.of(23, 0), LocalTime.of(2, 0))

        assertTrue(sunday.covers(at(12, 1)))
        assertFalse(sunday.covers(at(13, 1)))
    }

    @Test
    fun `equal start and end blocks the whole day`() {
        val allDay = BlockRange(setOf(SATURDAY), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT)

        assertTrue(allDay.covers(at(17, 0)))
        assertTrue(allDay.covers(at(17, 23, 59)))
        assertFalse(allDay.covers(at(18, 12)))
    }

    @Test
    fun `a list blocks when any range covers`() {
        val ranges = listOf(
            BlockRange(setOf(MONDAY), LocalTime.of(8, 0), LocalTime.of(9, 0)),
            BlockRange(setOf(MONDAY), LocalTime.of(20, 0), LocalTime.of(21, 0)),
        )

        assertTrue(ranges.isBlockedAt(at(12, 20, 30)))
        assertFalse(ranges.isBlockedAt(at(12, 12)))
    }

    @Test
    fun `next change is the start when free and the end when blocked`() {
        val nights = listOf(BlockRange(DayOfWeek.entries.toSet(), LocalTime.of(22, 0), LocalTime.of(6, 0)))

        assertEquals(at(12, 22), nights.nextBlockChange(at(12, 15)))
        assertEquals(at(13, 6), nights.nextBlockChange(at(12, 23)))
        assertEquals(at(13, 6), nights.nextBlockChange(at(13, 1)))
    }

    @Test
    fun `touching ranges count as one block`() {
        val ranges = listOf(
            BlockRange(setOf(MONDAY), LocalTime.of(8, 0), LocalTime.of(12, 0)),
            BlockRange(setOf(MONDAY), LocalTime.of(12, 0), LocalTime.of(18, 0)),
        )

        assertEquals(at(12, 18), ranges.nextBlockChange(at(12, 9)))
    }

    @Test
    fun `the next change can be days away`() {
        val fridays = listOf(BlockRange(setOf(FRIDAY), LocalTime.of(8, 0), LocalTime.of(9, 0)))

        assertEquals(at(16, 8), fridays.nextBlockChange(at(12, 10)))
        assertEquals(at(23, 8), fridays.nextBlockChange(at(16, 9)))
    }

    @Test
    fun `no change without ranges or when always blocked`() {
        val always = listOf(BlockRange(DayOfWeek.entries.toSet(), LocalTime.of(0, 0), LocalTime.of(0, 0)))

        assertNull(emptyList<BlockRange>().nextBlockChange(at(12, 10)))
        assertNull(always.nextBlockChange(at(12, 10)))
    }
}
