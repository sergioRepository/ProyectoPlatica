package com.lavidanoesunbanano.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val status: String,
    val version: String,
)
