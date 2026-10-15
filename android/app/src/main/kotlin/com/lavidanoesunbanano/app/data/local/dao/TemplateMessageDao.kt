package com.lavidanoesunbanano.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lavidanoesunbanano.app.data.local.entity.TemplateMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateMessageDao {
    @Query("SELECT * FROM template_messages")
    fun observeAll(): Flow<List<TemplateMessageEntity>>

    @Query("SELECT COUNT(*) FROM template_messages")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(messages: List<TemplateMessageEntity>)

    @Query("DELETE FROM template_messages")
    suspend fun clear()
}
