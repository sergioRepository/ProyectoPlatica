package com.lavidanoesunbanano.app.domain.excuse

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CrisisLine(
    val name: String,
    val number: String,
    val verified: Boolean = false,
)

@Serializable
private data class CrisisLinesFile(val lines: List<CrisisLine>)

object CrisisLines {
    private val json = Json { ignoreUnknownKeys = true }

    /** Un número sin verificar nunca se le muestra a alguien en crisis. */
    fun visible(fileContent: String): List<CrisisLine> =
        json.decodeFromString<CrisisLinesFile>(fileContent).lines.filter { it.verified && it.number.isNotBlank() }
}
