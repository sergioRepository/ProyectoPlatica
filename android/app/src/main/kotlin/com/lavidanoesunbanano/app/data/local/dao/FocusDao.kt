package com.lavidanoesunbanano.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lavidanoesunbanano.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    /** `false` si la orden ya se había aplicado (mismo id). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: FocusSessionEntity): Long

    /** Las vigentes y las que vencieron hace poco; las vencidas las descarta `BlockPolicy`. */
    @Query("SELECT * FROM focus_sessions WHERE ends_at > :since ORDER BY ends_at DESC")
    fun observeSince(since: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT MAX(ends_at) FROM focus_sessions")
    suspend fun latestEnd(): Long?
}
