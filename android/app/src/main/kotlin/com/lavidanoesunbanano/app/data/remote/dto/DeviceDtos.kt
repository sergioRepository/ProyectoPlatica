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
    // Los campos de facturación tienen valor por defecto para aceptar servidores viejos.
    @SerialName("subscription_state") val subscriptionState: String? = null,
    @SerialName("expiry_time") val expiryTime: String? = null,
    @SerialName("auto_renewing") val autoRenewing: Boolean = false,
    val credits: Int = 0,
    @SerialName("included_daily") val includedDaily: Int = 0,
    @SerialName("included_remaining") val includedRemaining: Int = 0,
    @SerialName("agent_enabled") val agentEnabled: Boolean = false,
)

@Serializable
data class VerifyRequestDto(
    @SerialName("product_id") val productId: String,
    @SerialName("purchase_token") val purchaseToken: String,
)

@Serializable
data class VerifyResponseDto(
    val outcome: String,
    val device: DeviceStatusDto,
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
