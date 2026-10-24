package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.blocking.BlockPolicy
import com.lavidanoesunbanano.app.domain.blocking.BlockRules
import com.lavidanoesunbanano.app.domain.blocking.BlockVerdict
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class BlockPolicyTest {

    private val instagram = AppInfo("com.instagram.android", "Instagram")
    private val weekdays = BlockRange(DayOfWeek.entries.take(5).toSet(), LocalTime.of(8, 0), LocalTime.of(18, 0))
    private val rules = BlockRules(listOf(instagram), listOf(weekdays))

    // 2026-10-12 es lunes.
    private fun local(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)
    private fun instant(day: Int, hour: Int, minute: Int = 0): Instant = local(day, hour, minute).toInstant(ZoneOffset.UTC)

    private fun evaluate(packageName: String, at: Instant, unlocks: Map<String, Instant> = emptyMap()) =
        BlockPolicy.evaluate(packageName, at, ZoneOffset.UTC, rules, unlocks)

    @Test
    fun `apps outside the list are never blocked`() {
        assertEquals(BlockVerdict.NotListed, evaluate("com.whatsapp", instant(12, 10)))
    }

    @Test
    fun `a listed app is blocked inside its hours`() {
        assertEquals(BlockVerdict.Blocked(instagram), evaluate(instagram.packageName, instant(12, 10)))
    }

    @Test
    fun `outside its hours it is allowed until the next start`() {
        assertEquals(BlockVerdict.Allowed(instant(12, 8)), evaluate(instagram.packageName, instant(12, 7, 30)))
        // Viernes por la noche: el siguiente bloqueo es el lunes.
        assertEquals(BlockVerdict.Allowed(instant(19, 8)), evaluate(instagram.packageName, instant(16, 19)))
    }

    @Test
    fun `an active unlock wins over the schedule until it expires`() {
        val unlocks = mapOf(instagram.packageName to instant(12, 10, 10))

        assertEquals(BlockVerdict.Allowed(instant(12, 10, 10)), evaluate(instagram.packageName, instant(12, 10), unlocks))
        assertEquals(BlockVerdict.Blocked(instagram), evaluate(instagram.packageName, instant(12, 10, 10), unlocks))
    }

    @Test
    fun `a focus session blocks outside the schedule until it ends, and an unlock still wins`() {
        val focused = rules.copy(focus = mapOf(instagram.packageName to instant(12, 19, 20)))
        fun at(minute: Int, unlocks: Map<String, Instant> = emptyMap()) =
            BlockPolicy.evaluate(instagram.packageName, instant(12, 19, minute), ZoneOffset.UTC, focused, unlocks)

        assertEquals(BlockVerdict.Blocked(instagram), at(0))
        assertEquals(BlockVerdict.Allowed(instant(13, 8)), at(20))
        val unlock = mapOf(instagram.packageName to instant(12, 19, 10))
        assertEquals(BlockVerdict.Allowed(instant(12, 19, 10)), at(0, unlock))
    }

    @Test
    fun `without ranges nothing is ever blocked`() {
        val verdict = BlockPolicy.evaluate(instagram.packageName, instant(12, 10), ZoneOffset.UTC, BlockRules(listOf(instagram), emptyList()))

        assertEquals(BlockVerdict.Allowed(null), verdict)
    }

    @Test
    fun `next start handles overnight and all day ranges`() {
        val overnight = BlockRange(setOf(MONDAY), LocalTime.of(22, 0), LocalTime.of(6, 0))
        val saturday = BlockRange(setOf(SATURDAY), LocalTime.of(9, 0), LocalTime.of(9, 0))

        assertEquals(local(12, 22), BlockPolicy.nextBlockStart(listOf(overnight), local(12, 21)))
        // Después del inicio del lunes, el siguiente es el lunes de la otra semana.
        assertEquals(local(19, 22), BlockPolicy.nextBlockStart(listOf(overnight), local(13, 7)))
        // Un rango de día entero empieza a medianoche.
        assertEquals(local(17, 0), BlockPolicy.nextBlockStart(listOf(saturday), local(16, 15)))
        assertNull(BlockPolicy.nextBlockStart(emptyList(), local(12, 0)))
    }
}
