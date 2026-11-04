package com.lavidanoesunbanano.app.data.remote.dto

import kotlinx.serialization.Serializable

/** Cuerpo de POST /reports (banano/schemas/reports.py). */
@Serializable
data class ReportRequestDto(
    val message: String,
    val reason: String,
    val source: String,
)
