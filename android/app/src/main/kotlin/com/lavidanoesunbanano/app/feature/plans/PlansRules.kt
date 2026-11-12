package com.lavidanoesunbanano.app.feature.plans

import androidx.annotation.StringRes
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.data.billing.StorePrice
import com.lavidanoesunbanano.app.data.billing.StoreProduct
import com.lavidanoesunbanano.app.domain.billing.PlanCatalog
import com.lavidanoesunbanano.app.domain.billing.PlanTier

/** Qué hace el botón principal de la pantalla de planes. */
sealed interface PrimaryAction {
    data class Subscribe(val product: StoreProduct) : PrimaryAction

    data class Change(val product: StoreProduct) : PrimaryAction

    /** Es el plan vigente. */
    data object Current : PrimaryAction

    /** Se resuelve en Google Play (pago por arreglar o cancelar). */
    data object Manage : PrimaryAction

    data object Unavailable : PrimaryAction
}

private val BROKEN_PAYMENT_STATES = setOf("SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED")

/** `validated` es el plan que dio el servidor; `selected`, la tarjeta que el usuario tocó. */
fun primaryAction(
    validated: PlanTier,
    subscriptionState: String?,
    selected: PlanTier,
    price: StorePrice?,
    fallback: Boolean,
): PrimaryAction = when {
    subscriptionState in BROKEN_PAYMENT_STATES -> PrimaryAction.Manage
    selected == validated -> PrimaryAction.Current
    selected == PlanTier.FREE -> PrimaryAction.Manage
    price == null || fallback -> PrimaryAction.Unavailable
    else -> storeProduct(selected).let { if (validated.isPaid) PrimaryAction.Change(it) else PrimaryAction.Subscribe(it) }
}

fun storeProduct(tier: PlanTier): StoreProduct = if (tier == PlanTier.SEMESTER) StoreProduct.SEMESTER else StoreProduct.MONTHLY

@StringRes
fun periodLabel(billingPeriod: String?): Int? = when (billingPeriod) {
    "P1M" -> R.string.plans_period_month
    "P6M" -> R.string.plans_period_6_months
    else -> null
}

private const val MANAGE_URL_GENERIC = "https://play.google.com/store/account/subscriptions"
private const val MANAGE_URL_SKU =
    "$MANAGE_URL_GENERIC?sku=${PlanCatalog.SUBSCRIPTION_ID}&package=com.lavidanoesunbanano.app"

/** Sin suscripción vigente no hay `sku` que abrir: se va a la lista general de suscripciones. */
fun manageUrl(validated: PlanTier, subscriptionState: String?): String =
    if (validated == PlanTier.FREE || subscriptionState == "SUBSCRIPTION_STATE_EXPIRED") MANAGE_URL_GENERIC else MANAGE_URL_SKU
