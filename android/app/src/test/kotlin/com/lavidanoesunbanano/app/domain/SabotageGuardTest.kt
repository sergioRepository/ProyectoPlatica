package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard
import com.lavidanoesunbanano.app.domain.model.BlockRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SabotageGuardTest {

    private val evenings = BlockRange(DayOfWeek.entries.toSet(), LocalTime.of(20, 0), LocalTime.of(23, 0))
    private val phrase = "Soy consciente de que estoy apagando la protección"

    @Test
    fun `rules are locked only with protection on and inside the schedule`() {
        val inside = LocalDateTime.of(2026, 10, 12, 21, 0)
        val outside = LocalDateTime.of(2026, 10, 12, 12, 0)

        assertTrue(SabotageGuard.rulesLocked(protectionOn = true, listOf(evenings), inside))
        assertFalse(SabotageGuard.rulesLocked(protectionOn = true, listOf(evenings), outside))
        assertFalse(SabotageGuard.rulesLocked(protectionOn = false, listOf(evenings), inside))
    }

    @Test
    fun `a scheduled disable turns the protection off when its time comes`() {
        val now = Instant.parse("2026-11-04T20:00:00Z")

        assertTrue(SabotageGuard.protectionOn(enabled = true, offAt = null, now = now))
        assertTrue(SabotageGuard.protectionOn(true, now.plusSeconds(1), now))
        assertFalse(SabotageGuard.protectionOn(true, now, now))
        assertFalse(SabotageGuard.protectionOn(false, null, now))
    }

    @Test
    fun `the exact phrase disables, also in capitals, without accents and with extra spaces`() {
        assertTrue(SabotageGuard.canDisableNow(phrase, phrase))
        assertTrue(SabotageGuard.canDisableNow("SOY CONSCIENTE DE QUE ESTOY APAGANDO LA PROTECCIÓN", phrase))
        assertTrue(SabotageGuard.canDisableNow("soy consciente de que estoy apagando la proteccion", phrase))
        assertTrue(SabotageGuard.canDisableNow("  Soy   consciente de que  estoy apagando la protección  ", phrase))
    }

    @Test
    fun `another text, a partial one or an empty one does not disable`() {
        assertFalse(SabotageGuard.canDisableNow("porque si", phrase))
        assertFalse(SabotageGuard.canDisableNow("Soy consciente de que estoy apagando", phrase))
        assertFalse(SabotageGuard.canDisableNow("", phrase))
        assertFalse(SabotageGuard.canDisableNow("     ", phrase))
    }
}
