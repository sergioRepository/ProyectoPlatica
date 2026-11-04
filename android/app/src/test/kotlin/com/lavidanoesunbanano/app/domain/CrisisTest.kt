package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.excuse.CrisisDetector
import com.lavidanoesunbanano.app.domain.excuse.CrisisLines
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class CrisisTest {

    @Test
    fun `the same signals as the backend`() {
        listOf(
            "Ya no quiero seguir vivir así",
            "Me quiero morir",
            "Quiero hacerme daño",
            "Mi mamá se desmayó",
            "Voy para URGENCIAS",
            "Hubo un accidente en la casa",
            "¡Auxilio!",
            "Llamé al 123",
        ).forEach { assertTrue(CrisisDetector.isCrisis(it), it) }
    }

    @Test
    fun `ordinary excuses are not a crisis`() {
        listOf(
            "Tengo que responderle al cliente",
            "Solo cinco minutos para descansar",
            "Tengo 1234 mensajes sin leer",
        ).forEach { assertFalse(CrisisDetector.isCrisis(it), it) }
    }

    @Test
    fun `an unverified number is never shown`() {
        val file = """
            {"_todo": "x", "lines": [
              {"name": "Verificada", "number": "100", "verified": true},
              {"name": "Sin verificar", "number": "200", "verified": false},
              {"name": "Sin numero", "number": "", "verified": true},
              {"name": "Sin campo", "number": "300"}
            ]}
        """

        assertEquals(listOf("Verificada"), CrisisLines.visible(file).map { it.name })
    }

    @Test
    fun `the file in the apk can be read`() {
        CrisisLines.visible(File("src/main/assets/crisis_lines.json").readText())
    }
}
