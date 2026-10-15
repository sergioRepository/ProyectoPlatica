package com.lavidanoesunbanano.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lavidanoesunbanano.app.data.local.entity.TemplateMessageEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMetaEntity
import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TemplateDao {
    @Query("SELECT COUNT(*) FROM template_messages")
    abstract fun observeMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM template_messages")
    abstract suspend fun messageCount(): Int

    @Query("SELECT * FROM template_messages ORDER BY id")
    abstract suspend fun getMessages(): List<TemplateMessageEntity>

    @Query("SELECT * FROM verdict_lines WHERE kind = :kind ORDER BY id")
    abstract suspend fun getVerdictLines(kind: String): List<VerdictLineEntity>

    @Query("SELECT * FROM template_meta WHERE id = 0")
    abstract suspend fun getMeta(): TemplateMetaEntity?

    @Query("SELECT * FROM template_meta WHERE id = 0")
    abstract fun observeMeta(): Flow<TemplateMetaEntity?>

    /** Cambia la plantilla completa de una vez: nunca quedan mensajes viejos mezclados. */
    @Transaction
    open suspend fun replaceTemplate(
        messages: List<TemplateMessageEntity>,
        verdictLines: List<VerdictLineEntity>,
        meta: TemplateMetaEntity,
    ) {
        deleteMessages()
        deleteVerdictLines()
        insertMessages(messages)
        insertVerdictLines(verdictLines)
        upsertMeta(meta)
    }

    @Query("DELETE FROM template_messages")
    protected abstract suspend fun deleteMessages()

    @Query("DELETE FROM verdict_lines")
    protected abstract suspend fun deleteVerdictLines()

    @Insert
    protected abstract suspend fun insertMessages(messages: List<TemplateMessageEntity>)

    @Insert
    protected abstract suspend fun insertVerdictLines(lines: List<VerdictLineEntity>)

    @Upsert
    protected abstract suspend fun upsertMeta(meta: TemplateMetaEntity)
}
