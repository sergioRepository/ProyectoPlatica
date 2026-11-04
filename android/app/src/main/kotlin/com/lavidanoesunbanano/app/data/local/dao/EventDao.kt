package com.lavidanoesunbanano.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lavidanoesunbanano.app.data.local.entity.PendingEventEntity

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: PendingEventEntity)

    /** Los que ya ocurrieron: el fin de una sesión de enfoque se anota por adelantado. */
    @Query("SELECT * FROM pending_events WHERE occurred_at <= :now ORDER BY occurred_at LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<PendingEventEntity>

    @Query("DELETE FROM pending_events WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM pending_events")
    suspend fun clear()
}
