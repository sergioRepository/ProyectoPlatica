package com.lavidanoesunbanano.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Frase corta para veredictos sin conexión. `kind`: approved o rejected. */
@Entity(tableName = "verdict_lines")
data class VerdictLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val text: String,
) {
    companion object {
        const val APPROVED = "approved"
        const val REJECTED = "rejected"
    }
}

/** Una sola fila: cuándo se generó la plantilla y el resumen del perfil para el juez. */
@Entity(tableName = "template_meta")
data class TemplateMetaEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    @ColumnInfo(name = "profile_summary") val profileSummary: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
