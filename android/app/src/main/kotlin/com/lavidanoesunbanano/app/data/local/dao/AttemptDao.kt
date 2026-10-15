package com.lavidanoesunbanano.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lavidanoesunbanano.app.data.local.entity.AttemptEntity

@Dao
interface AttemptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempts WHERE package_name = :packageName AND started_at = :startedAt")
    suspend fun find(packageName: String, startedAt: Long): AttemptEntity?

    /** Intentos con esa app entre `from` y `to`, ambos incluidos. */
    @Query(
        "SELECT COUNT(*) FROM attempts " +
            "WHERE package_name = :packageName AND started_at >= :from AND started_at <= :to",
    )
    suspend fun countBetween(packageName: String, from: Long, to: Long): Int

    /** Mensajes mostrados más recientes, de cualquier app, del más nuevo al más viejo. */
    @Query("SELECT message_id FROM attempts WHERE message_id IS NOT NULL ORDER BY started_at DESC LIMIT :limit")
    suspend fun recentMessageIds(limit: Int): List<String>

    @Query("UPDATE attempts SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: String)

    @Query("UPDATE attempts SET message_id = :messageId WHERE id = :id")
    suspend fun setMessage(id: Long, messageId: String?)

    @Query("SELECT * FROM attempts WHERE id = :id")
    suspend fun get(id: Long): AttemptEntity?
}
