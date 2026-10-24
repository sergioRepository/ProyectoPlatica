package com.lavidanoesunbanano.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cada vez que apareció la pantalla de bloqueo. `started_at` en milisegundos desde 1970;
 * con el paquete forma una llave única para no contar dos veces el mismo intento.
 * `outcome` queda vacío si el usuario se fue sin tocar ningún botón.
 */
@Entity(
    tableName = "attempts",
    indices = [
        Index(value = ["package_name", "started_at"], unique = true),
        Index(value = ["started_at"]),
    ],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "message_id") val messageId: String?,
    val outcome: String?,
) {
    companion object {
        const val OUTCOME_EXIT = "exit"
    }
}
