package com.lavidanoesunbanano.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Cuerpo de POST /agent/analyze (banano/schemas/agent.py). */
@Serializable
data class AnalyzeRequestDto(
    @SerialName("request_id") val requestId: String,
    val trigger: String,
    @SerialName("local_time") val localTime: String,
    @SerialName("profile_summary") val profileSummary: String,
    @SerialName("main_goal") val mainGoal: String,
    @SerialName("blocked_apps") val blockedApps: List<BlockedAppDto>,
    @SerialName("focus_active_until_ms") val focusActiveUntilMs: Long?,
    val usage: UsageSnapshotDto,
)

@Serializable
data class BlockedAppDto(val `package`: String, val label: String)

@Serializable
data class UsageSnapshotDto(
    val status: String,
    @SerialName("collected_at_ms") val collectedAtMs: Long,
    val truncated: Boolean,
    val sessions: List<UsageSessionDto>,
)

@Serializable
data class UsageSessionDto(
    val `package`: String,
    val label: String,
    val category: String,
    @SerialName("start_ms") val startMs: Long,
    val minutes: Double,
)

@Serializable
data class AnalyzeResponseDto(
    val decision: String,
    val reason: String,
    val steps: List<AgentStepDto>,
    val intervention: InterventionDto? = null,
    val persisted: Boolean,
    val replayed: Boolean = false,
)

@Serializable
data class AgentStepDto(
    val tool: String,
    val args: JsonObject = JsonObject(emptyMap()),
    val status: String,
    val ms: Int = 0,
)

@Serializable
data class InterventionDto(
    val id: String? = null,
    val type: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("target_apps") val targetApps: List<String>,
    val status: String,
    @SerialName("policy_code") val policyCode: String? = null,
)

@Serializable
data class ConfirmRequestDto(
    val status: String,
    @SerialName("error_code") val errorCode: String? = null,
)

@Serializable
data class ConfirmResponseDto(val id: String, val status: String)
