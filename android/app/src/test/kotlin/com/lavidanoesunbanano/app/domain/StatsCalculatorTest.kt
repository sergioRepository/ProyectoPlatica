package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.AttemptOutcome
import com.lavidanoesunbanano.app.domain.model.AttemptRecord
import com.lavidanoesunbanano.app.domain.model.ExcuseRecord
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.model.VerdictReason
import com.lavidanoesunbanano.app.domain.stats.AppCount
import com.lavidanoesunbanano.app.domain.stats.ExcuseTally
import com.lavidanoesunbanano.app.domain.stats.HistoryBuilder
import com.lavidanoesunbanano.app.domain.stats.StatsCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

class StatsCalculatorTest {

    // Bogotá: UTC-5 todo el año. Un intento a las 23:00 locales cae en UTC al día siguiente.
    private val zone = ZoneId.of("America/Bogota")
    private val today = LocalDate.of(2026, 10, 15)
    private val instagram = AppInfo("com.instagram.android", "Instagram")
    private val tiktok = AppInfo("com.zhiliaoapp.musically", "TikTok")

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant()

    private var nextId = 1L

    private fun attempt(app: AppInfo, at: Instant, outcome: AttemptOutcome? = AttemptOutcome.EXIT) =
        AttemptRecord(id = nextId++, app = app, startedAt = at, outcome = outcome)

    private fun excuse(attempt: AttemptRecord, type: ExcuseType, valid: Boolean, needsReview: Boolean = false) = ExcuseRecord(
        id = nextId++,
        attemptId = attempt.id,
        app = attempt.app,
        type = type,
        reason = "Una razón cualquiera",
        decidedAt = attempt.startedAt.plusSeconds(30),
        verdict = Verdict(
            valid = valid,
            grantedMinutes = if (valid) (if (type == ExcuseType.EMERGENCY) 15 else 10) else 0,
            message = null,
            reason = if (valid) VerdictReason.LEGIT_WORK else VerdictReason.BOREDOM,
            confidence = null,
            offline = needsReview,
        ),
        unlockedUntil = null,
        needsReview = needsReview,
    )

    @Test
    fun `a period ends today and counts every day, empty ones included`() {
        val attempts = listOf(
            attempt(instagram, at(15, 9)),
            attempt(instagram, at(15, 23)),
            attempt(tiktok, at(13, 12)),
            // Fuera del periodo de 7 días.
            attempt(instagram, at(8, 12)),
        )

        val stats = StatsCalculator.compute(attempts, emptyList(), today, days = 7, zone = zone)

        assertEquals(3, stats.attempts)
        assertEquals((9..15).map { LocalDate.of(2026, 10, it) }, stats.perDay.map { it.date })
        assertEquals(listOf(0, 0, 0, 0, 1, 0, 2), stats.perDay.map { it.attempts })
    }

    @Test
    fun `apps are ranked by attempts`() {
        val attempts = listOf(
            attempt(tiktok, at(15, 9)),
            attempt(instagram, at(15, 10)),
            attempt(instagram, at(15, 11)),
        )

        val stats = StatsCalculator.compute(attempts, emptyList(), today, days = 1, zone = zone)

        assertEquals(listOf(AppCount("Instagram", 2), AppCount("TikTok", 1)), stats.perApp)
    }

    @Test
    fun `recovered minutes count only attempts that did not end in an unlock`() {
        val exit = attempt(instagram, at(15, 9), AttemptOutcome.EXIT)
        val left = attempt(instagram, at(15, 10), outcome = null)
        val unlocked = attempt(instagram, at(15, 11), AttemptOutcome.EXTRA_TIME)

        val stats = StatsCalculator.compute(listOf(exit, left, unlocked), emptyList(), today, days = 1, zone = zone)

        assertEquals(2 * StatsCalculator.MINUTES_PER_BLOCKED_ATTEMPT, stats.recoveredMinutes)
    }

    @Test
    fun `excuses are tallied by type and granted minutes add up`() {
        val first = attempt(instagram, at(15, 9))
        val second = attempt(tiktok, at(15, 10))
        val excuses = listOf(
            excuse(first, ExcuseType.EXTRA_TIME, valid = false),
            excuse(first, ExcuseType.EXTRA_TIME, valid = true),
            excuse(second, ExcuseType.EMERGENCY, valid = true, needsReview = true),
        )

        val stats = StatsCalculator.compute(listOf(first, second), excuses, today, days = 1, zone = zone)

        assertEquals(2, stats.approved)
        assertEquals(1, stats.rejected)
        assertEquals(25, stats.grantedMinutes)
        assertEquals(1, stats.pendingReview)
        assertEquals(ExcuseTally(approved = 1, rejected = 1), stats.byType[ExcuseType.EXTRA_TIME])
        assertEquals(ExcuseTally(approved = 1, rejected = 0), stats.byType[ExcuseType.EMERGENCY])
    }

    @Test
    fun `history groups by local day, newest first, with each attempt's excuses`() {
        val late = attempt(instagram, at(14, 23, 30))
        val morning = attempt(tiktok, at(15, 8))
        val noon = attempt(instagram, at(15, 12))
        val excuses = listOf(excuse(noon, ExcuseType.EXTRA_TIME, valid = false))

        val days = HistoryBuilder.build(listOf(late, noon, morning), excuses, zone)

        assertEquals(listOf(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 14)), days.map { it.date })
        assertEquals(listOf(noon.id, morning.id), days[0].items.map { it.attempt.id })
        assertEquals(1, days[0].items[0].excuses.size)
        assertEquals(emptyList(), days[0].items[1].excuses)
        assertEquals(listOf(late.id), days[1].items.map { it.attempt.id })
    }
}
