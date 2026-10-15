package com.lavidanoesunbanano.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Rango de bloqueo para ciertos días.
 *
 * Si `end` es menor que `start`, el rango cruza la medianoche: empieza el día marcado y
 * termina al día siguiente. Si son iguales, bloquea el día entero.
 */
data class BlockRange(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(days.isNotEmpty()) { "Un rango necesita al menos un día" }
    }

    val isAllDay: Boolean get() = start == end
    val crossesMidnight: Boolean get() = end < start

    fun covers(at: LocalDateTime): Boolean {
        val day = at.dayOfWeek
        val time = at.toLocalTime()
        return when {
            isAllDay -> day in days
            crossesMidnight -> (day in days && time >= start) || (day.minus(1) in days && time < end)
            else -> day in days && time >= start && time < end
        }
    }
}

fun List<BlockRange>.isBlockedAt(at: LocalDateTime): Boolean = any { it.covers(at) }
