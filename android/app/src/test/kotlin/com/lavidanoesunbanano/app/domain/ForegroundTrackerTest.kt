package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.blocking.BlockRules
import com.lavidanoesunbanano.app.domain.blocking.ForegroundTracker
import com.lavidanoesunbanano.app.domain.blocking.TrackerAction
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.Test

class ForegroundTrackerTest {

    private val own = "com.lavidanoesunbanano.app"
    private val keyboard = "com.samsung.android.honeyboard"
    private val launcher = "com.sec.android.app.launcher"
    private val instagram = AppInfo("com.instagram.android", "Instagram")
    private val tiktok = AppInfo("com.zhiliaoapp.musically", "TikTok")

    private val evenings = BlockRange(DayOfWeek.entries.toSet(), LocalTime.of(20, 0), LocalTime.of(23, 0))
    private val rules = BlockRules(listOf(instagram, tiktok), listOf(evenings))

    // 2026-10-12, lunes, en pleno bloqueo.
    private fun at(hour: Int, minute: Int = 0, second: Int = 0): Instant =
        LocalDateTime.of(2026, 10, 12, hour, minute, second).toInstant(ZoneOffset.UTC)

    private val now = at(21)

    private fun tracker() = ForegroundTracker(own, ZoneOffset.UTC).apply {
        ignoredPackages = ForegroundTracker.SYSTEM_PACKAGES + keyboard
    }

    private fun show(app: AppInfo, newAttempt: Boolean, at: Instant = now) = TrackerAction.ShowBlock(app, newAttempt, at)

    @Test
    fun `system windows and keyboards are ignored`() {
        val tracker = tracker()

        assertEquals(TrackerAction.Ignore, tracker.onWindow(keyboard, now, rules))
        assertEquals(TrackerAction.Ignore, tracker.onWindow("com.android.systemui", now, rules))
        assertEquals(TrackerAction.Ignore, tracker.onWindow("android", now, rules))
        assertEquals(null, tracker.foreground)
    }

    @Test
    fun `free apps, the launcher and the app itself are idle`() {
        val tracker = tracker()

        assertEquals(TrackerAction.Idle, tracker.onWindow("com.whatsapp", now, rules))
        assertEquals(TrackerAction.Idle, tracker.onWindow(launcher, now, rules))
        assertEquals(TrackerAction.Idle, tracker.onWindow(own, now, rules))
    }

    @Test
    fun `coming back to the covered app does not add an attempt`() {
        val tracker = tracker()

        assertEquals(show(instagram, newAttempt = true), tracker.onWindow(instagram.packageName, now, rules))
        // La pantalla de bloqueo aparece, el usuario baja la barra y toca una notificación de Instagram.
        tracker.onWindow(own, now, rules)
        tracker.onWindow("com.android.systemui", now, rules)
        tracker.onWindow(keyboard, now, rules)
        assertEquals(show(instagram, newAttempt = false), tracker.onWindow(instagram.packageName, now, rules))
    }

    @Test
    fun `bursts from the same app always bring the screen back`() {
        val tracker = tracker()

        tracker.onWindow(instagram.packageName, now, rules)
        // Splash y actividad principal en menos de un segundo: las dos traen la pantalla.
        assertEquals(show(instagram, newAttempt = false), tracker.onWindow(instagram.packageName, now, rules))
    }

    @Test
    fun `leaving through the launcher or another app makes the next one a new attempt`() {
        val tracker = tracker()
        val later = at(21, 0, 15)

        tracker.onWindow(instagram.packageName, now, rules)
        tracker.onWindow(own, now, rules)
        tracker.onWindow(launcher, later, rules)
        assertEquals(show(instagram, newAttempt = true, at = later), tracker.onWindow(instagram.packageName, later, rules))

        assertEquals(show(tiktok, newAttempt = true, at = later), tracker.onWindow(tiktok.packageName, later, rules))
        assertEquals(show(instagram, newAttempt = true, at = later), tracker.onWindow(instagram.packageName, later, rules))
    }

    @Test
    fun `a window that sneaks in right after the block does not add an attempt`() {
        val tracker = tracker()

        tracker.onWindow(instagram.packageName, now, rules)
        // La animación del launcher llega tarde y la app vuelve a pedir el frente.
        assertEquals(TrackerAction.Idle, tracker.onWindow(launcher, at(21, 0, 1), rules))
        assertEquals(
            show(instagram, newAttempt = false, at = at(21, 0, 3)),
            tracker.onWindow(instagram.packageName, at(21, 0, 3), rules),
        )
        // Pasados los 10 s, volver a abrirla sí es otro intento.
        tracker.onWindow(launcher, at(21, 0, 20), rules)
        assertEquals(
            show(instagram, newAttempt = true, at = at(21, 0, 20)),
            tracker.onWindow(instagram.packageName, at(21, 0, 20), rules),
        )
    }

    @Test
    fun `exit makes the next open a new attempt even without a launcher event`() {
        val tracker = tracker()

        tracker.onWindow(instagram.packageName, now, rules)
        tracker.onWindow(own, now, rules)
        tracker.onExit()
        assertEquals(show(instagram, newAttempt = true), tracker.onWindow(instagram.packageName, now, rules))
    }

    @Test
    fun `an allowed app is checked again when the block starts`() {
        val tracker = tracker()
        val before = at(19, 59)

        assertEquals(TrackerAction.RecheckAt(at(20)), tracker.onWindow(instagram.packageName, before, rules))
        assertEquals(show(instagram, newAttempt = true, at = at(20)), tracker.recheck(at(20), rules))
    }

    @Test
    fun `an unlock is checked again when it expires`() {
        val tracker = tracker()
        val unlocks = mapOf(instagram.packageName to at(21, 10))

        assertEquals(TrackerAction.RecheckAt(at(21, 10)), tracker.onWindow(instagram.packageName, now, rules, unlocks))
        assertEquals(show(instagram, newAttempt = true, at = at(21, 10)), tracker.recheck(at(21, 10), rules, unlocks))
    }

    @Test
    fun `when an unlock granted from the block screen expires the screen returns as a new attempt`() {
        val tracker = tracker()
        val unlocks = mapOf(instagram.packageName to at(21, 10))

        tracker.onWindow(instagram.packageName, now, rules)
        tracker.onWindow(own, now, rules)
        // Veredicto aprobado: abre Instagram sin pasar por el launcher.
        assertEquals(TrackerAction.RecheckAt(at(21, 10)), tracker.onWindow(instagram.packageName, at(21, 1), rules, unlocks))
        assertEquals(show(instagram, newAttempt = true, at = at(21, 10)), tracker.recheck(at(21, 10), rules, unlocks))
    }

    @Test
    fun `new rules apply to the app already in front`() {
        val tracker = tracker()

        assertEquals(TrackerAction.Idle, tracker.onWindow(instagram.packageName, now, BlockRules.EMPTY))
        assertEquals(show(instagram, newAttempt = true), tracker.recheck(now, rules))
    }

    @Test
    fun `recheck does nothing while the app itself is in front`() {
        val tracker = tracker()

        assertEquals(TrackerAction.Idle, tracker.recheck(now, rules))
        tracker.onWindow(own, now, rules)
        assertEquals(TrackerAction.Idle, tracker.recheck(now, rules))
    }
}
