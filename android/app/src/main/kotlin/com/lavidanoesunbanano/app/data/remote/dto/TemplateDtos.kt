package com.lavidanoesunbanano.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cuerpo de POST /templates/generate. Los límites de largo los valida el backend. */
@Serializable
data class ProfileDto(
    val name: String,
    @SerialName("main_goal") val mainGoal: String,
    @SerialName("secondary_goals") val secondaryGoals: List<String>,
    @SerialName("goal_reason") val goalReason: String,
    @SerialName("blocked_apps") val blockedApps: List<String>,
    @SerialName("schedule_summary") val scheduleSummary: String,
    @SerialName("typical_excuses") val typicalExcuses: String,
    @SerialName("sarcasm_level") val sarcasmLevel: Int,
    @SerialName("profanity_allowed") val profanityAllowed: Boolean,
    val country: String,
    @SerialName("forbidden_topics") val forbiddenTopics: String,
)

@Serializable
data class TemplateResponseDto(
    val messages: List<TemplateMessageDto>,
    @SerialName("verdict_lines") val verdictLines: VerdictLinesDto,
    @SerialName("profile_summary") val profileSummary: String,
)

@Serializable
data class TemplateMessageDto(
    val id: String,
    val text: String,
    val context: String,
    val intensity: Int,
)

@Serializable
data class VerdictLinesDto(
    val approved: List<String>,
    val rejected: List<String>,
)
