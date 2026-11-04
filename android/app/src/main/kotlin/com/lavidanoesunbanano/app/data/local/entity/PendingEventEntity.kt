package com.lavidanoesunbanano.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Evento de analítica esperando red. `props`: objeto JSON. Nunca lleva texto de excusas. */
@Entity(tableName = "pending_events", indices = [Index("occurred_at")])
data class PendingEventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val app: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val props: String,
)
