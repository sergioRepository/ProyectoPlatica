package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.billing.PlanTier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PlanCatalogTest {

    @Test
    fun `benefits per plan`() {
        assertEquals(listOf(3, 20, 20), PlanTier.entries.map { it.dailyEvaluations })
        assertEquals(listOf(0, 3, 3), PlanTier.entries.map { it.includedAccesses })
        assertEquals(listOf(false, true, true), PlanTier.entries.map { it.autoReview })
    }

    @Test
    fun `unknown or null wire falls back to free`() {
        assertEquals(PlanTier.FREE, PlanTier.fromWire("x"))
        assertEquals(PlanTier.FREE, PlanTier.fromWire(null))
        assertEquals(PlanTier.SEMESTER, PlanTier.fromWire("semester"))
    }

    @Test
    fun `only paid plans have a base plan`() {
        assertNull(PlanTier.FREE.basePlanId)
        assertFalse(PlanTier.FREE.isPaid)
        assertTrue(PlanTier.MONTHLY.isPaid)
        assertTrue(PlanTier.SEMESTER.isPaid)
        assertEquals("monthly", PlanTier.MONTHLY.basePlanId)
        assertEquals("semester", PlanTier.SEMESTER.basePlanId)
    }
}
