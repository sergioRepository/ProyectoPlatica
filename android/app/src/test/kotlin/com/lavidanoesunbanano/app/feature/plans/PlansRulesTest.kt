package com.lavidanoesunbanano.app.feature.plans

import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.data.billing.StorePrice
import com.lavidanoesunbanano.app.data.billing.StoreProduct
import com.lavidanoesunbanano.app.domain.billing.PlanTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlansRulesTest {

    private val price = StorePrice("US$ 1,99", 1_990_000, "USD", "P1M")
    private val active = "SUBSCRIPTION_STATE_ACTIVE"

    @Test
    fun `free user subscribes`() {
        assertEquals(
            PrimaryAction.Subscribe(StoreProduct.SEMESTER),
            primaryAction(PlanTier.FREE, null, PlanTier.SEMESTER, price, false),
        )
    }

    @Test
    fun `paid user changes plan`() {
        assertEquals(
            PrimaryAction.Change(StoreProduct.SEMESTER),
            primaryAction(PlanTier.MONTHLY, active, PlanTier.SEMESTER, price, false),
        )
    }

    @Test
    fun `selecting the validated plan is current`() {
        assertEquals(PrimaryAction.Current, primaryAction(PlanTier.MONTHLY, active, PlanTier.MONTHLY, price, false))
        assertEquals(PrimaryAction.Current, primaryAction(PlanTier.FREE, null, PlanTier.FREE, null, true))
    }

    @Test
    fun `free card with a paid plan goes to Google Play`() {
        assertEquals(PrimaryAction.Manage, primaryAction(PlanTier.SEMESTER, active, PlanTier.FREE, null, false))
    }

    @Test
    fun `payment on hold or paused goes to Google Play`() {
        listOf("SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED").forEach {
            assertEquals(PrimaryAction.Manage, primaryAction(PlanTier.MONTHLY, it, PlanTier.SEMESTER, price, false))
            assertEquals(PrimaryAction.Manage, primaryAction(PlanTier.MONTHLY, it, PlanTier.MONTHLY, price, false))
        }
    }

    @Test
    fun `no price or fallback prices cannot be bought`() {
        assertEquals(PrimaryAction.Unavailable, primaryAction(PlanTier.FREE, null, PlanTier.MONTHLY, null, false))
        assertEquals(PrimaryAction.Unavailable, primaryAction(PlanTier.FREE, null, PlanTier.MONTHLY, price, true))
    }

    @Test
    fun `period label`() {
        assertEquals(R.string.plans_period_month, periodLabel("P1M"))
        assertEquals(R.string.plans_period_6_months, periodLabel("P6M"))
        assertNull(periodLabel("P1Y"))
        assertNull(periodLabel(null))
    }

    @Test
    fun `manage url drops sku for free or expired`() {
        val generic = "https://play.google.com/store/account/subscriptions"
        assertEquals(generic, manageUrl(PlanTier.FREE, null))
        assertEquals(generic, manageUrl(PlanTier.MONTHLY, "SUBSCRIPTION_STATE_EXPIRED"))
        assertEquals(true, manageUrl(PlanTier.MONTHLY, active).contains("sku="))
    }
}
