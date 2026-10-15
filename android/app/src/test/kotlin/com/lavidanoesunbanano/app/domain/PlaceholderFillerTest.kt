package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.template.PlaceholderFiller
import com.lavidanoesunbanano.app.domain.template.PlaceholderValues
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.Test

class PlaceholderFillerTest {

    private val values = PlaceholderValues(
        name = "Alex",
        goal = "ser el mejor programador del mundo",
        app = "Instagram",
        attemptsToday = 7,
        minutesToday = 42,
        time = LocalTime.of(9, 5),
    )

    @Test
    fun `fills every placeholder`() {
        assertEquals(
            "Alex, van 7 intentos y 42 minutos en Instagram. Son las 09:05.",
            PlaceholderFiller.fill("{nombre}, van {intentos_hoy} intentos y {minutos_hoy} minutos en {app}. Son las {hora}.", values),
        )
    }

    @Test
    fun `avoids repeating the verb the goal already has`() {
        assertEquals(
            "¿Instagram otra vez, Alex? Así piensas ser el mejor programador del mundo. Qué mierda.",
            PlaceholderFiller.fill("¿{app} otra vez, {nombre}? Así piensas ser {meta}. Qué mierda.", values),
        )
    }

    @Test
    fun `capitalizes when a placeholder starts the sentence`() {
        assertEquals(
            "Ser el mejor programador del mundo no se construye en Instagram.",
            PlaceholderFiller.fill("{meta} no se construye en {app}.", values),
        )
        assertEquals("¿Ser el mejor programador del mundo?", PlaceholderFiller.fill("¿{meta}?", values))
    }

    @Test
    fun `a sentence that starts with a number keeps its words in lowercase`() {
        assertEquals("7 veces ya. ¿Cuántas más?", PlaceholderFiller.fill("{intentos_hoy} veces ya. ¿Cuántas más?", values))
    }

    @Test
    fun `drops placeholders it does not know`() {
        assertEquals("Hola Alex.", PlaceholderFiller.fill("Hola {edad} {nombre}.", values))
    }
}
