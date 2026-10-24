package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.local.dao.AttemptDao
import com.lavidanoesunbanano.app.data.local.dao.ExcuseDao
import com.lavidanoesunbanano.app.data.mapper.toRecord
import com.lavidanoesunbanano.app.domain.model.AttemptRecord
import com.lavidanoesunbanano.app.domain.model.ExcuseRecord
import com.lavidanoesunbanano.app.domain.stats.StatsCalculator
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class HistorySnapshot(
    val attempts: List<AttemptRecord>,
    val excuses: List<ExcuseRecord>,
    val today: LocalDate,
    val zone: ZoneId,
)

/** Intentos y excusas guardados, para el historial y las estadísticas. */
@Singleton
class HistoryRepository @Inject constructor(
    private val attemptDao: AttemptDao,
    private val excuseDao: ExcuseDao,
    private val clock: Clock,
) {
    /** Los últimos `days` días, hoy incluido. */
    fun observe(days: Int = MAX_DAYS): Flow<HistorySnapshot> {
        val today = LocalDate.now(clock)
        val since = StatsCalculator.periodStart(today, days).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return combine(attemptDao.observeSince(since), excuseDao.observeSince(since)) { attempts, excuses ->
            HistorySnapshot(
                attempts = attempts.map { it.toRecord() },
                excuses = excuses.map { it.toRecord() },
                today = LocalDate.now(clock),
                zone = clock.zone,
            )
        }
    }

    suspend fun markReviewed(excuseId: Long) = excuseDao.markReviewed(excuseId)

    companion object {
        /** El historial y las estadísticas llegan hasta 30 días atrás. */
        const val MAX_DAYS = 30
    }
}
