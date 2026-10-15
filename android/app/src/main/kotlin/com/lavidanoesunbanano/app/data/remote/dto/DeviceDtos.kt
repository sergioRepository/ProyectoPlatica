package com.lavidanoesunbanano.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("enrollment_code") val enrollmentCode: String,
)

@Serializable
data class RegisterResponseDto(
    @SerialName("device_token") val deviceToken: String,
)
