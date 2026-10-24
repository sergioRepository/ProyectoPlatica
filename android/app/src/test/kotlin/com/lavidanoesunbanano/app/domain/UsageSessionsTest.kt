package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.usage.UsageEvent
import com.lavidanoesunbanano.app.domain.usage.UsageSession
import com.lavidanoesunbanano.app.domain.usage.UsageSessions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class UsageSessionsTest {

    private fun resumed(pkg: String, at: Long) = UsageEvent(pkg, at, resumed = true)
    private fun paused(pkg: String, at: Long) = UsageEvent(pkg, at, resumed = false)

    @Test
    fun `pairs resume with pause`() {
        val sessions = UsageSessions.fromEvents(listOf(resumed("tiktok", 0), paused("tiktok", 60_000)), now = 100_000)

        assertEquals(listOf(UsageSession("tiktok", 0, 60_000)), sessions)
        assertEquals(1.0, sessions.single().minutes)
    }

    @Test
    fun `another app resuming closes the open one and the last one ends now`() {
        val sessions = UsageSessions.fromEvents(
            listOf(resumed("tiktok", 0), resumed("chrome", 30_000), paused("tiktok", 31_000)),
            now = 90_000,
        )

        assertEquals(listOf(UsageSession("tiktok", 0, 30_000), UsageSession("chrome", 30_000, 90_000)), sessions)
    }

    @Test
    fun `the same app resumed twice is one session and a stray pause is ignored`() {
        val sessions = UsageSessions.fromEvents(
            listOf(paused("chrome", 0), resumed("tiktok", 10), resumed("tiktok", 20), paused("tiktok", 50)),
            now = 100,
        )

        assertEquals(listOf(UsageSession("tiktok", 10, 50)), sessions)
    }

    @Test
    fun `minimize keeps listed apps and the top others, newest first when capped`() {
        val sessions = listOf(
            UsageSession("tiktok", 0, 10),
            UsageSession("chrome", 10, 100),
            UsageSession("calc", 100, 101),
            UsageSession("tiktok", 200, 210),
        )

        val (kept, truncated) = UsageSessions.minimize(sessions, listed = setOf("tiktok"), otherApps = 1)
        assertEquals(listOf("tiktok", "chrome", "tiktok"), kept.map { it.packageName })
        assertFalse(truncated)

        val (capped, cut) = UsageSessions.minimize(sessions, listed = setOf("tiktok"), otherApps = 1, maxSessions = 2)
        assertEquals(listOf(10L, 200L), capped.map { it.startMillis })
        assertTrue(cut)
    }
}
