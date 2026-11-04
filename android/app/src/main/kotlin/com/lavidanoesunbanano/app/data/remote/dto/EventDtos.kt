package com.lavidanoesunbanano.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Cuerpo de POST /events (banano/schemas/events.py). */
@Serializable
data class EventBatchDto(
    val events: List<AppEventDto>,
)

@Serializable
data class AppEventDto(
    val id: String,
    val name: String,
    val app: String?,
    @SerialName("occurred_at") val occurredAt: String,
    val props: JsonObject,
)

@Serializable
data class EventsAcceptedDto(
    val accepted: Int,
)
