package com.lavidanoesunbanano.app.domain.template

import com.lavidanoesunbanano.app.domain.model.MessageContext
import com.lavidanoesunbanano.app.domain.model.TemplateMessage
import java.time.LocalTime
import kotlin.random.Random

/** Lo que importa para elegir el mensaje de un intento. */
data class PickSituation(
    /** Intentos de hoy con esta app, contando el actual. */
    val attemptsToday: Int,
    val minutesToday: Int,
    val time: LocalTime,
    val afterRejection: Boolean = false,
    /** Ids de los últimos mensajes mostrados, del más reciente al más viejo. */
    val recentIds: List<String> = emptyList(),
)

/**
 * Elige el mensaje de la pantalla de bloqueo según el contexto, sin repetir los últimos 10.
 *
 * Un mensaje de un contexto que no aplica (uno de noche al mediodía) solo sale si no queda otro.
 */
object MessagePicker {
    const val RECENT_WINDOW = 10
    const val REPEAT_FROM_ATTEMPT = 3

    /** Parte de las veces que sale un mensaje del contexto que aplica en vez de uno general. */
    private const val SPECIFIC_SHARE = 0.6
    private val NIGHT_START = LocalTime.of(21, 0)
    private val MORNING_START = LocalTime.of(5, 0)
    private val MORNING_END = LocalTime.of(11, 0)

    fun applicableContexts(situation: PickSituation): Set<MessageContext> = buildSet {
        if (situation.afterRejection) add(MessageContext.AFTER_REJECTION)
        if (situation.attemptsToday >= REPEAT_FROM_ATTEMPT) add(MessageContext.REPEAT)
        val time = situation.time
        when {
            time >= NIGHT_START || time < MORNING_START -> add(MessageContext.NIGHT)
            time < MORNING_END -> add(MessageContext.MORNING)
        }
    }

    fun pick(
        messages: List<TemplateMessage>,
        situation: PickSituation,
        random: Random = Random.Default,
    ): TemplateMessage? {
        val usable = messages.filter { it.fits(situation) }.ifEmpty { messages }
        if (usable.isEmpty()) return null
        val applicable = applicableContexts(situation)
        val specific = usable.filter { it.context in applicable }
        val general = usable.filter { it.context == MessageContext.GENERAL }
        val candidates = (specific + general).ifEmpty { usable }

        val recent = situation.recentIds.take(RECENT_WINDOW)
        val freshSpecific = specific.filterNot { it.id in recent }
        val freshGeneral = general.filterNot { it.id in recent }
        val freshAny = candidates.filterNot { it.id in recent }
        return when {
            freshSpecific.isNotEmpty() && (freshGeneral.isEmpty() || random.nextDouble() < SPECIFIC_SHARE) ->
                freshSpecific.random(random)
            freshGeneral.isNotEmpty() -> freshGeneral.random(random)
            freshAny.isNotEmpty() -> freshAny.random(random)
            // Todos salieron hace poco: el que salió hace más tiempo.
            else -> candidates.maxBy { recent.indexOf(it.id) }
        }
    }

    /**
     * Sin minutos concedidos, "{minutos_hoy}" diría "0 minutos"; en el primer intento,
     * "{intentos_hoy}" diría "van 1 intentos".
     */
    private fun TemplateMessage.fits(situation: PickSituation): Boolean = when {
        situation.minutesToday <= 0 && "{minutos_hoy}" in text -> false
        situation.attemptsToday < 2 && "{intentos_hoy}" in text -> false
        else -> true
    }
}
