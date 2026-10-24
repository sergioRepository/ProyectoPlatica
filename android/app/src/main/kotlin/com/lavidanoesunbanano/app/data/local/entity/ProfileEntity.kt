package com.lavidanoesunbanano.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Una sola fila: el perfil del onboarding. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val name: String,
    @ColumnInfo(name = "main_goal") val mainGoal: String,
    @ColumnInfo(name = "secondary_goals") val secondaryGoals: List<String>,
    @ColumnInfo(name = "goal_reason") val goalReason: String,
    @ColumnInfo(name = "typical_excuses") val typicalExcuses: String,
    @ColumnInfo(name = "sarcasm_level") val sarcasmLevel: Int,
    @ColumnInfo(name = "profanity_allowed") val profanityAllowed: Boolean,
    val country: String,
    @ColumnInfo(name = "forbidden_topics") val forbiddenTopics: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
