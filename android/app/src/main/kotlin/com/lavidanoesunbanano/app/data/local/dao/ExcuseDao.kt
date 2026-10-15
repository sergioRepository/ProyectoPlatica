package com.lavidanoesunbanano.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lavidanoesunbanano.app.data.local.entity.ExcuseEntity
import com.lavidanoesunbanano.app.data.local.entity.UnlockRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcuseDao {
    @Insert
    suspend fun insert(excuse: ExcuseEntity): Long

    /** Permisos aprobados de un tipo desde `since`, de cualquier app: el cupo es por día. */
    @Query("SELECT COUNT(*) FROM excuses WHERE type = :type AND valid = 1 AND decided_at >= :since")
    suspend fun countGrantedSince(type: String, since: Long): Int

    @Query(
        "SELECT COALESCE(SUM(granted_minutes), 0) FROM excuses " +
            "WHERE package_name = :packageName AND valid = 1 AND decided_at >= :since",
    )
    suspend fun grantedMinutesSince(packageName: String, since: Long): Int

    /** Textos de las últimas excusas, de la más nueva a la más vieja, para detectar repetidas. */
    @Query("SELECT reason FROM excuses ORDER BY decided_at DESC, id DESC LIMIT :limit")
    suspend fun recentReasons(limit: Int): List<String>

    @Query(
        "SELECT package_name, MAX(unlocked_until) AS unlocked_until FROM excuses " +
            "WHERE unlocked_until IS NOT NULL GROUP BY package_name",
    )
    fun observeUnlocks(): Flow<List<UnlockRow>>

    @Query("SELECT * FROM excuses WHERE attempt_id = :attemptId ORDER BY id")
    suspend fun forAttempt(attemptId: Long): List<ExcuseEntity>
}
