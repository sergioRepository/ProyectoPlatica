package com.lavidanoesunbanano.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un mensaje de la plantilla sarcástica. `text` trae placeholders que la app rellena localmente. */
@Entity(tableName = "template_messages")
data class TemplateMessageEntity(
    @PrimaryKey val id: String,
    val text: String,
    val context: String,
    val intensity: Int,
)
