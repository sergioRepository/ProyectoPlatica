package com.lavidanoesunbanano.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cuerpo de POST /excuses/judge: los campos del spec y las extensiones del backend. */
@Serializable
data class JudgeRequestDto(
    val type: String,
    val reason: String,
    val app: String,
    @SerialName("profile_summary") val profileSummary: String,
    @SerialName("attempts_today") val attemptsToday: Int,
    @SerialName("extra_time_granted_today") val extraTimeGrantedToday: Int,
    @SerialName("recent_excuses") val recentExcuses: List<String>,
    @SerialName("local_time") val localTime: String,
    @SerialName("emergencies_granted_today") val emergenciesGrantedToday: Int,
    @SerialName("extra_time_daily_limit") val extraTimeDailyLimit: Int,
    @SerialName("emergency_daily_limit") val emergencyDailyLimit: Int,
    @SerialName("sarcasm_level") val sarcasmLevel: Int,
    @SerialName("profanity_allowed") val profanityAllowed: Boolean,
    val country: String,
)

@Serializable
data class JudgeResponseDto(
    val valid: Boolean,
    val confidence: Double,
    @SerialName("granted_minutes") val grantedMinutes: Int,
    val message: String,
    @SerialName("reason_code") val reasonCode: String,
)
