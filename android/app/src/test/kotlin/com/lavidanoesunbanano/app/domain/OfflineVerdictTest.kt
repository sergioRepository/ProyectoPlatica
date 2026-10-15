package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.excuse.OfflineVerdict
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.domain.model.VerdictReason
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class OfflineVerdictTest {

    private val limits = UnlockLimits()

    @Test
    fun `an emergency without the judge is approved and flagged for review`() {
        val verdict = OfflineVerdict.decide(ExcuseType.EMERGENCY, limits, listOf("No."))

        assertTrue(verdict.valid)
        assertEquals(15, verdict.grantedMinutes)
        assertEquals(VerdictReason.OFFLINE, verdict.reason)
        assertNull(verdict.message)
        assertTrue(OfflineVerdict.needsReview(ExcuseType.EMERGENCY, verdict))
    }

    @Test
    fun `extra time without the judge is rejected with a template line`() {
        val verdict = OfflineVerdict.decide(ExcuseType.EXTRA_TIME, limits, listOf("Ni lo sueñes."))

        assertFalse(verdict.valid)
        assertEquals(0, verdict.grantedMinutes)
        assertEquals("Ni lo sueñes.", verdict.message)
        assertFalse(OfflineVerdict.needsReview(ExcuseType.EXTRA_TIME, verdict))
    }

    @Test
    fun `without template lines the screen uses its own sentence`() {
        assertNull(OfflineVerdict.decide(ExcuseType.EXTRA_TIME, limits, emptyList()).message)
    }

    @Test
    fun `the emergency length follows the limits`() {
        val verdict = OfflineVerdict.decide(ExcuseType.EMERGENCY, limits.copy(emergencyMinutes = 20), emptyList())

        assertEquals(20, verdict.grantedMinutes)
    }
}
