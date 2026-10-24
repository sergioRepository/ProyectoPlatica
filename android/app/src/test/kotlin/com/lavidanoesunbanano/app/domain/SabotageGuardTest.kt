package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.blocking.BlockRules
import com.lavidanoesunbanano.app.domain.blocking.ForegroundTracker
import com.lavidanoesunbanano.app.domain.blocking.SabotageGuard
import com.lavidanoesunbanano.app.domain.blocking.TrackerAction
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SabotageGuardTest {

    private val label = "La vida no es un banano"
    private val evenings = BlockRange(DayOfWeek.entries.toSet(), LocalTime.of(20, 0), LocalTime.of(23, 0))

    @Test
    fun `system windows that name the app are sabotage`() {
        assertTrue(SabotageGuard.isSabotage("com.android.settings", listOf("La vida no es un banano"), label))
        assertTrue(SabotageGuard.isSabotage("com.samsung.accessibility", listOf("la vida no es un banano"), label))
        assertTrue(
            SabotageGuard.isSabotage(
                "com.google.android.packageinstaller",
                listOf("La vida no es un banano", "¿Quieres desinstalar esta app?"),
                label,
            ),
        )
    }

    @Test
    fun `other settings pages and other apps are not`() {
        assertFalse(SabotageGuard.isSabotage("com.android.settings", listOf("Accesibilidad"), label))
        assertFalse(SabotageGuard.isSabotage("com.android.settings", emptyList(), label))
        // Una app cualquiera que mencione el nombre no es Ajustes.
        assertFalse(SabotageGuard.isSabotage("com.whatsapp", listOf("La vida no es un banano"), label))
    }

    @Test
    fun `rules are locked only with protection on and inside the schedule`() {
        val inside = LocalDateTime.of(2026, 10, 12, 21, 0)
        val outside = LocalDateTime.of(2026, 10, 12, 12, 0)

        assertTrue(SabotageGuard.rulesLocked(protectionOn = true, listOf(evenings), inside))
        assertFalse(SabotageGuard.rulesLocked(protectionOn = true, listOf(evenings), outside))
        assertFalse(SabotageGuard.rulesLocked(protectionOn = false, listOf(evenings), inside))
    }

    @Test
    fun `a guarded settings window is blocked like an app, only inside the schedule`() {
        val settings = AppInfo("com.android.settings", "Ajustes")
        val rules = BlockRules(listOf(AppInfo("com.instagram.android", "Instagram")), listOf(evenings))
        val tracker = ForegroundTracker("com.lavidanoesunbanano.app", ZoneOffset.UTC)
        val inside = LocalDateTime.of(2026, 10, 12, 21, 0).toInstant(ZoneOffset.UTC)
        val outside = LocalDateTime.of(2026, 10, 12, 12, 0).toInstant(ZoneOffset.UTC)

        assertEquals(
            TrackerAction.ShowBlock(settings, newAttempt = true, at = inside),
            tracker.onWindow(settings.packageName, inside, rules.guarding(settings)),
        )
        // Las demás páginas de Ajustes no se tapan.
        assertEquals(TrackerAction.Idle, tracker.onWindow(settings.packageName, inside, rules))
        assertTrue(tracker.onWindow(settings.packageName, outside, rules.guarding(settings)) is TrackerAction.RecheckAt)
    }
}
