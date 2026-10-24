package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.model.MessageContext
import com.lavidanoesunbanano.app.domain.model.MessageContext.GENERAL
import com.lavidanoesunbanano.app.domain.model.MessageContext.MORNING
import com.lavidanoesunbanano.app.domain.model.MessageContext.NIGHT
import com.lavidanoesunbanano.app.domain.model.MessageContext.REPEAT
import com.lavidanoesunbanano.app.domain.model.TemplateMessage
import com.lavidanoesunbanano.app.domain.template.MessagePicker
import com.lavidanoesunbanano.app.domain.template.PickSituation
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class MessagePickerTest {

    private fun message(id: String, context: MessageContext = GENERAL, text: String = "Mensaje $id") =
        TemplateMessage(id = id, text = text, context = context, intensity = 3)

    private fun situation(
        attempts: Int = 1,
        time: LocalTime = LocalTime.NOON,
        minutes: Int = 0,
        recent: List<String> = emptyList(),
    ) = PickSituation(attemptsToday = attempts, minutesToday = minutes, time = time, recentIds = recent)

    /** Siempre devuelve el mínimo: prefiere el contexto específico y el primer elemento. */
    private val lowest = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    private fun picks(messages: List<TemplateMessage>, situation: PickSituation, times: Int = 300): Set<String> =
        (1..times).mapNotNull { MessagePicker.pick(messages, situation, Random(it))?.id }.toSet()

    @Test
    fun `contexts follow the hour and the attempts`() {
        assertEquals(setOf(NIGHT), MessagePicker.applicableContexts(situation(time = LocalTime.of(23, 40))))
        assertEquals(setOf(NIGHT), MessagePicker.applicableContexts(situation(time = LocalTime.of(4, 59))))
        assertEquals(setOf(MORNING), MessagePicker.applicableContexts(situation(time = LocalTime.of(5, 0))))
        assertEquals(emptySet(), MessagePicker.applicableContexts(situation(time = LocalTime.of(11, 0))))
        assertEquals(setOf(REPEAT), MessagePicker.applicableContexts(situation(attempts = 3)))
        assertEquals(
            setOf(MessageContext.AFTER_REJECTION),
            MessagePicker.applicableContexts(situation().copy(afterRejection = true)),
        )
    }

    @Test
    fun `the specific context is preferred but general still shows up`() {
        val messages = listOf(message("g1"), message("r1", REPEAT))

        assertEquals("r1", MessagePicker.pick(messages, situation(attempts = 3), lowest)?.id)
        assertEquals(setOf("g1", "r1"), picks(messages, situation(attempts = 3)))
    }

    @Test
    fun `contexts that do not apply stay out`() {
        val messages = listOf(message("g1"), message("n1", NIGHT), message("r1", REPEAT))

        assertEquals(setOf("g1"), picks(messages, situation(attempts = 1, time = LocalTime.NOON)))
    }

    @Test
    fun `a context that does not apply is used only when nothing else is left`() {
        val messages = listOf(message("n1", NIGHT))

        assertEquals("n1", MessagePicker.pick(messages, situation(time = LocalTime.NOON))?.id)
    }

    @Test
    fun `the last ten shown are not repeated`() {
        val messages = (1..12).map { message("m%02d".format(it)) }
        val recent = messages.take(10).map { it.id }

        assertEquals(setOf("m11", "m12"), picks(messages, situation(recent = recent)))
    }

    @Test
    fun `when all were shown recently the oldest one comes back`() {
        val messages = (1..3).map { message("m$it") }

        assertEquals("m1", MessagePicker.pick(messages, situation(recent = listOf("m3", "m2", "m1")))?.id)
    }

    @Test
    fun `placeholders without a real value are skipped`() {
        val minutes = message("min", text = "Llevas {minutos_hoy} minutos hoy.")
        val attempts = message("int", text = "Van {intentos_hoy} intentos.")
        val plain = message("plain")
        val messages = listOf(minutes, attempts, plain)

        assertEquals(setOf("plain"), picks(messages, situation(attempts = 1, minutes = 0)))
        assertTrue("int" in picks(messages, situation(attempts = 2, minutes = 0)))
        assertTrue("min" in picks(messages, situation(attempts = 1, minutes = 10)))
        // Si no queda otro, se usa igual.
        assertEquals("min", MessagePicker.pick(listOf(minutes), situation())?.id)
    }

    @Test
    fun `an empty template gives nothing`() {
        assertNull(MessagePicker.pick(emptyList(), situation()))
    }
}
