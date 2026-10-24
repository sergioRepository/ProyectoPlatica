package com.lavidanoesunbanano.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cada excusa con su veredicto. `decided_at` y `unlocked_until` en milisegundos desde 1970;
 * `unlocked_until` solo existe si se aprobó. `needs_review`: emergencia aprobada sin juez.
 */
@Entity(
    tableName = "excuses",
    indices = [Index(value = ["decided_at"]), Index(value = ["package_name"])],
)
data class ExcuseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "attempt_id") val attemptId: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    val type: String,
    val reason: String,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
    val valid: Boolean,
    @ColumnInfo(name = "granted_minutes") val grantedMinutes: Int,
    @ColumnInfo(name = "unlocked_until") val unlockedUntil: Long?,
    val message: String?,
    @ColumnInfo(name = "reason_code") val reasonCode: String,
    val confidence: Double?,
    val offline: Boolean,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
)

/** Último desbloqueo de cada app; puede estar vencido. */
data class UnlockRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "unlocked_until") val unlockedUntil: Long,
)
