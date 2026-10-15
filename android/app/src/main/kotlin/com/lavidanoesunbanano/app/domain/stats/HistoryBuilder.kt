package com.lavidanoesunbanano.app.domain.stats

import com.lavidanoesunbanano.app.domain.model.AttemptRecord
import com.lavidanoesunbanano.app.domain.model.ExcuseRecord
import java.time.LocalDate
import java.time.ZoneId

/** Un intento con las excusas que se pidieron en él, en orden. */
data class HistoryItem(val attempt: AttemptRecord, val excuses: List<ExcuseRecord>)

data class HistoryDay(val date: LocalDate, val items: List<HistoryItem>)

/** Arma el historial: días del más nuevo al más viejo y, dentro de cada uno, igual. */
object HistoryBuilder {

    fun build(attempts: List<AttemptRecord>, excuses: List<ExcuseRecord>, zone: ZoneId): List<HistoryDay> {
        val excusesByAttempt = excuses.groupBy { it.attemptId }
        return attempts
            .sortedByDescending { it.startedAt }
            .groupBy { it.startedAt.atZone(zone).toLocalDate() }
            .map { (date, items) ->
                HistoryDay(
                    date = date,
                    items = items.map { attempt ->
                        HistoryItem(attempt, excusesByAttempt[attempt.id].orEmpty().sortedBy { it.decidedAt })
                    },
                )
            }
    }
}
