package com.lavidanoesunbanano.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sesión de enfoque que pidió el agente. `id` es el de la intervención en el backend: si la
 * misma orden llega dos veces, no se duplica. `packages`: paquetes separados por coma.
 */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val packages: String,
    @ColumnInfo(name = "starts_at") val startsAt: Long,
    @ColumnInfo(name = "ends_at") val endsAt: Long,
    val reason: String,
)
