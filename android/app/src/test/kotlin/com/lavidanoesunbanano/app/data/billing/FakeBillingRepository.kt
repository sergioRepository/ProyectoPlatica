package com.lavidanoesunbanano.app.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Doble de Google Play para los ViewModels: registra lo que se pidió y no compra nada. */
class FakeBillingRepository(initial: BillingState = BillingState(loading = false)) : BillingRepository {
    override val state = MutableStateFlow(initial)
    val purchased = mutableListOf<StoreProduct>()
    var restores = 0

    override suspend fun refreshPrices() = Unit

    override suspend fun purchase(activity: Activity, product: StoreProduct) {
        if (state.value.busy) return
        purchased += product
        state.update { it.copy(purchase = PurchaseStatus.Launching(product)) }
    }

    override suspend fun restore() {
        restores++
    }

    override fun acknowledgeStatus() {
        state.update { it.copy(purchase = PurchaseStatus.Idle) }
    }
}
