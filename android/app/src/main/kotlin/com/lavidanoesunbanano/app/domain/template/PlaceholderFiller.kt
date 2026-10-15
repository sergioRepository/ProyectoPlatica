package com.lavidanoesunbanano.app.domain.template

import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class PlaceholderValues(
    val name: String,
    val goal: String,
    val app: String,
    val attemptsToday: Int,
    val minutesToday: Int,
    val time: LocalTime,
)

/** Rellena localmente los placeholders de la plantilla; no necesita red. */
object PlaceholderFiller {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val LEFTOVER = Regex("""\{[^{}]*\}""")
    private val REPEATED_WORD = Regex("""\b(\p{L}+)\s+\1\b""", RegexOption.IGNORE_CASE)

    fun fill(text: String, values: PlaceholderValues): String {
        val filled = text
            .replace("{nombre}", values.name)
            .replace("{meta}", values.goal)
            .replace("{app}", values.app)
            .replace("{intentos_hoy}", values.attemptsToday.toString())
            .replace("{minutos_hoy}", values.minutesToday.toString())
            .replace("{hora}", values.time.format(TIME))
            .replace(LEFTOVER, "")
        // "Así piensas ser {meta}" con la meta "ser el mejor..." deja "ser ser".
        val tidy = REPEATED_WORD.replace(filled) { it.groupValues[1] }
            .replace(Regex("""\s+"""), " ")
            .trim()
        return capitalizeFirstLetter(tidy)
    }

    private fun capitalizeFirstLetter(text: String): String {
        val index = text.indexOfFirst { it.isLetter() }
        if (index < 0 || text[index].isUpperCase()) return text
        return text.substring(0, index) + text[index].uppercaseChar() + text.substring(index + 1)
    }
}
