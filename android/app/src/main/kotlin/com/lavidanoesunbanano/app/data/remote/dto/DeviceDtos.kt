package com.lavidanoesunbanano.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConsentsDto(
    val excuses: Boolean,
    @SerialName("phone_usage") val phoneUsage: Boolean,
    @SerialName("cross_border") val crossBorder: Boolean,
    val analytics: Boolean,
    @SerialName("text_version") val textVersion: String,
)

/** Estado del dispositivo en el servidor: plan y llamadas de IA de hoy. */
@Serializable
data class DeviceStatusDto(
    val plan: String,
    @SerialName("would_pay") val wouldPay: String? = null,
    @SerialName("daily_limit") val dailyLimit: Int,
    @SerialName("remaining_today") val remainingToday: Int,
)

@Serializable
data class PlanRequestDto(
    val plan: String,
    @SerialName("would_pay") val wouldPay: String?,
)

@Serializable
data class RegisterRequestDto(
    val consents: ConsentsDto,
)

@Serializable
data class RegisterResponseDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String,
)
