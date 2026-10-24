package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.agent.AgentRules
import com.lavidanoesunbanano.app.domain.usage.UsageEvidence
import com.lavidanoesunbanano.app.domain.usage.UsageSession
import com.lavidanoesunbanano.app.domain.usage.UsageStatus
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AgentRulesTest {

    private val now = Instant.parse("2026-10-23T16:00:00Z")
    private val listed = setOf("tiktok")
    private fun minutesAgo(m: Long) = now.minusSeconds(m * 60).toEpochMilli()
    private fun evidence(vararg sessions: UsageSession, status: UsageStatus = UsageStatus.OK) =
        UsageEvidence(status, now.toEpochMilli(), sessions.toList())

    private val heavy = evidence(
        UsageSession("tiktok", minutesAgo(50), minutesAgo(38)),
        UsageSession("tiktok", minutesAgo(20), minutesAgo(10)),
    )

    @Test
    fun `listed apps over the threshold in the last hour trigger an analysis`() {
        assertTrue(AgentRules.shouldAnalyze(heavy, listed, lastRunAt = null, focusUntil = null, now = now))
    }

    @Test
    fun `other apps, old sessions and missing permission do not count`() {
        val chrome = evidence(UsageSession("chrome", minutesAgo(50), minutesAgo(0)))
        // Empezó hace 2 h: solo cuentan los 10 minutos que caen dentro de la hora.
        val old = evidence(UsageSession("tiktok", minutesAgo(120), minutesAgo(50)))

        assertFalse(AgentRules.shouldAnalyze(chrome, listed, null, null, now))
        assertFalse(AgentRules.shouldAnalyze(old, listed, null, null, now))
        assertFalse(AgentRules.shouldAnalyze(evidence(status = UsageStatus.PERMISSION_MISSING), listed, null, null, now))
    }

    @Test
    fun `cooldown and an active focus session skip the analysis`() {
        assertFalse(AgentRules.shouldAnalyze(heavy, listed, lastRunAt = now.minusSeconds(600), focusUntil = null, now = now))
        assertFalse(AgentRules.shouldAnalyze(heavy, listed, lastRunAt = null, focusUntil = now.plusSeconds(60), now = now))
        assertTrue(AgentRules.shouldAnalyze(heavy, listed, lastRunAt = now.minusSeconds(3600), focusUntil = now, now = now))
    }

    @Test
    fun `orders outside the list or with odd durations are refused`() {
        assertTrue(AgentRules.acceptsOrder(10, listOf("tiktok"), listed))
        assertFalse(AgentRules.acceptsOrder(45, listOf("tiktok"), listed))
        assertFalse(AgentRules.acceptsOrder(10, listOf("whatsapp"), listed))
        assertFalse(AgentRules.acceptsOrder(10, emptyList(), listed))
    }
}
