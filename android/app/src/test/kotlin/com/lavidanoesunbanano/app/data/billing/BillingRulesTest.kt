package com.lavidanoesunbanano.app.data.billing

import com.lavidanoesunbanano.app.domain.billing.PlanTier
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class BillingRulesTest {

    private fun price(micros: Long, period: String?, currency: String = "USD") =
        StorePrice("x", micros, currency, period)

    @Test
    fun `the current plan is never offered again`() {
        assertEquals(PlanChange.AlreadyActive, BillingRules.planChange(PlanTier.MONTHLY, PlanTier.MONTHLY, true))
        assertEquals(PlanChange.AlreadyActive, BillingRules.planChange(PlanTier.SEMESTER, PlanTier.SEMESTER, false))
    }

    @Test
    fun `free or without a purchase in Google buys from scratch`() {
        assertEquals(PlanChange.NewPurchase, BillingRules.planChange(PlanTier.FREE, PlanTier.MONTHLY, false))
        assertEquals(PlanChange.NewPurchase, BillingRules.planChange(PlanTier.FREE, PlanTier.SEMESTER, true))
        assertEquals(PlanChange.NewPurchase, BillingRules.planChange(PlanTier.MONTHLY, PlanTier.SEMESTER, false))
    }

    @Test
    fun `switching plans uses the only two modes Google accepts`() {
        assertEquals(
            PlanChange.Replace(ReplacementKind.CHARGE_FULL_PRICE),
            BillingRules.planChange(PlanTier.MONTHLY, PlanTier.SEMESTER, true),
        )
        assertEquals(
            PlanChange.Replace(ReplacementKind.WITHOUT_PRORATION),
            BillingRules.planChange(PlanTier.SEMESTER, PlanTier.MONTHLY, true),
        )
    }

    @Test
    fun `the monthly equivalent comes from the store price`() {
        assertEquals(1_398_333L, BillingRules.monthlyMicros(price(8_390_000, "P6M")))
        assertEquals(1_990_000L, BillingRules.monthlyMicros(price(1_990_000, "P1M")))
        assertNull(BillingRules.monthlyMicros(price(990_000, null)))
        assertNull(BillingRules.monthlyMicros(price(990_000, "P1W")))
        assertNull(BillingRules.monthlyMicros(price(990_000, "basura")))
    }

    @Test
    fun `savings are computed from real prices and hidden when they make no sense`() {
        val monthly = price(1_990_000, "P1M")
        assertEquals(30, BillingRules.savingsPercent(monthly, price(8_390_000, "P6M")))
        assertNull(BillingRules.savingsPercent(monthly, price(8_390_000, "P6M", "COP")))
        assertNull(BillingRules.savingsPercent(monthly, price(12_000_000, "P6M")))
        assertNull(BillingRules.savingsPercent(price(1_990_000, "P1W"), price(8_390_000, "P6M")))
    }

    @Test
    fun `a flow in progress makes the state busy`() {
        assertEquals(true, BillingState(purchase = PurchaseStatus.Launching(StoreProduct.MONTHLY)).busy)
        assertEquals(true, BillingState(purchase = PurchaseStatus.Pending).busy)
        assertEquals(false, BillingState(purchase = PurchaseStatus.Failed(retryable = true)).busy)
    }
}
