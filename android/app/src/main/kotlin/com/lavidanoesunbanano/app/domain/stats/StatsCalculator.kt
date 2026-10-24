package com.lavidanoesunbanano.app.domain.stats

import com.lavidanoesunbanano.app.domain.model.AttemptRecord
import com.lavidanoesunbanano.app.domain.model.ExcuseRecord
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayCount(val date: LocalDate, val attempts: Int)

data class AppCount(val label: String, val attempts: Int)

data class ExcuseTally(val approved: Int = 0, val rejected: Int = 0)

data class Stats(
    val days: Int,
    val attempts: Int,
    /** Estimado: ver [StatsCalculator.MINUTES_PER_BLOCKED_ATTEMPT]. */
    val recoveredMinutes: Int,
    val grantedMinutes: Int,
    val approved: Int,
    val rejected: Int,
    val pendingReview: Int,
    /** Un elemento por día del periodo, del más viejo a hoy, con ceros incluidos. */
    val perDay: List<DayCount>,
    /** De la app con más intentos a la de menos; solo las que tuvieron alguno. */
    val perApp: List<AppCount>,
    val byType: Map<ExcuseType, ExcuseTally>,
)

/** Cifras de un periodo que termina hoy. Los días son locales, no UTC. */
object StatsCalculator {

    /**
     * Android no sabe cuánto habrías usado la app: cada intento que no terminó en un
     * desbloqueo cuenta como estos minutos recuperados.
     */
    const val MINUTES_PER_BLOCKED_ATTEMPT = 10

    fun periodStart(today: LocalDate, days: Int): LocalDate = today.minusDays(days - 1L)

    fun compute(
        attempts: List<AttemptRecord>,
        excuses: List<ExcuseRecord>,
        today: LocalDate,
        days: Int,
        zone: ZoneId,
    ): Stats {
        val from = periodStart(today, days)
        fun dateOf(at: Instant): LocalDate = at.atZone(zone).toLocalDate()
        fun inPeriod(at: Instant): Boolean = dateOf(at).let { it >= from && it <= today }

        val periodAttempts = attempts.filter { inPeriod(it.startedAt) }
        val periodExcuses = excuses.filter { inPeriod(it.decidedAt) }

        val byDate = periodAttempts.groupingBy { dateOf(it.startedAt) }.eachCount()
        val perDay = (0 until days).map { offset ->
            val date = from.plusDays(offset.toLong())
            DayCount(date, byDate[date] ?: 0)
        }
        val perApp = periodAttempts
            .groupBy { it.app.packageName }
            .map { (_, list) -> AppCount(list.maxBy { it.startedAt }.app.label, list.size) }
            .sortedWith(compareByDescending<AppCount> { it.attempts }.thenBy { it.label.lowercase() })

        val byType = ExcuseType.entries.associateWith { type ->
            val ofType = periodExcuses.filter { it.type == type }
            ExcuseTally(approved = ofType.count { it.verdict.valid }, rejected = ofType.count { !it.verdict.valid })
        }
        val blocked = periodAttempts.count { it.outcome?.unlocked != true }
        return Stats(
            days = days,
            attempts = periodAttempts.size,
            recoveredMinutes = blocked * MINUTES_PER_BLOCKED_ATTEMPT,
            grantedMinutes = periodExcuses.filter { it.verdict.valid }.sumOf { it.verdict.grantedMinutes },
            approved = periodExcuses.count { it.verdict.valid },
            rejected = periodExcuses.count { !it.verdict.valid },
            pendingReview = periodExcuses.count { it.needsReview },
            perDay = perDay,
            perApp = perApp,
            byType = byType,
        )
    }
}
