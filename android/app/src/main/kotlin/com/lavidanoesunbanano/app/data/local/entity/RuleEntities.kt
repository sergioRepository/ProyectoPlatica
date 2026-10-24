package com.lavidanoesunbanano.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    val label: String,
)

/** Rango de bloqueo. `days_mask`: bit 0 = lunes ... bit 6 = domingo. Minutos desde las 00:00. */
@Entity(tableName = "block_ranges")
data class BlockRangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int,
)
