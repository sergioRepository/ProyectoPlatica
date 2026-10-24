package com.lavidanoesunbanano.app.domain.blocking

import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.isBlockedAt
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reglas del onboarding listas para decidir rápido: apps por paquete y horarios. `focus`:
 * sesiones de enfoque del agente, hasta cuándo bloquean cada app aunque esté fuera de horario.
 */
data class BlockRules(
    val apps: Map<String, AppInfo>,
    val ranges: List<BlockRange>,
    val focus: Map<String, Instant> = emptyMap(),
) {
    constructor(apps: List<AppInfo>, ranges: List<BlockRange>, focus: Map<String, Instant> = emptyMap()) :
        this(apps.associateBy { it.packageName }, ranges, focus)

    /** Las mismas reglas, con una app más protegida (por ejemplo, Ajustes en la página del bloqueo). */
    fun guarding(app: AppInfo): BlockRules = copy(apps = apps + (app.packageName to app))

    companion object {
        val EMPTY = BlockRules(emptyMap(), emptyList())
    }
}

sealed interface BlockVerdict {
    /** La app no está en la lista. */
    data object NotListed : BlockVerdict

    data class Blocked(val app: AppInfo) : BlockVerdict

    /** Está en la lista pero ahora se permite. `until`: cuándo puede cambiar; `null` si nunca. */
    data class Allowed(val until: Instant?) : BlockVerdict
}

object BlockPolicy {

    /** Días que se revisan para encontrar el próximo bloqueo: una semana y un día de margen. */
    private const val LOOKAHEAD_DAYS = 8L

    fun evaluate(
        packageName: String,
        now: Instant,
        zone: ZoneId,
        rules: BlockRules,
        unlockedUntil: Map<String, Instant> = emptyMap(),
    ): BlockVerdict {
        val app = rules.apps[packageName] ?: return BlockVerdict.NotListed
        // Un desbloqueo temporal manda sobre el horario hasta que expira.
        // Las excusas y sus cupos siguen siendo la salida durante una sesión de enfoque.
        unlockedUntil[packageName]?.takeIf { it > now }?.let { return BlockVerdict.Allowed(it) }
        if (rules.focus[packageName]?.let { it > now } == true) return BlockVerdict.Blocked(app)
        val local = LocalDateTime.ofInstant(now, zone)
        if (rules.ranges.isBlockedAt(local)) return BlockVerdict.Blocked(app)
        return BlockVerdict.Allowed(nextBlockStart(rules.ranges, local)?.atZone(zone)?.toInstant())
    }

    /** Primer momento después de `after` en que empieza algún rango. */
    fun nextBlockStart(ranges: List<BlockRange>, after: LocalDateTime): LocalDateTime? =
        ranges.flatMap { range ->
            (0..LOOKAHEAD_DAYS).mapNotNull { offset ->
                val date = after.toLocalDate().plusDays(offset)
                // Un rango de día entero empieza a medianoche, no en su hora nominal.
                if (date.dayOfWeek in range.days) date.atTime(if (range.isAllDay) LocalTime.MIDNIGHT else range.start) else null
            }
        }.filter { it > after }.minOrNull()
}
