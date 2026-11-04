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

@Serializable
data class RegisterRequestDto(
    val consents: ConsentsDto,
)

@Serializable
data class RegisterResponseDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String,
)
