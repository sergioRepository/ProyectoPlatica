package com.lavidanoesunbanano.app.domain.billing

import androidx.annotation.StringRes
import com.lavidanoesunbanano.app.R

/**
 * Fuente de verdad de los beneficios por plan. Debe coincidir con
 * `Settings.benefits` de backend/banano/config.py.
 */
enum class PlanTier(
    val wire: String,
    @StringRes val nameRes: Int,
    val dailyEvaluations: Int,
    val includedAccesses: Int,
    val autoReview: Boolean,
    val basePlanId: String?,
) {
    FREE("free", R.string.plan_free, 3, 0, false, null),
    MONTHLY("monthly", R.string.plan_monthly, 20, 3, true, "monthly"),
    SEMESTER("semester", R.string.plan_semester, 20, 3, true, "semester"),
    ;

    val isPaid: Boolean get() = basePlanId != null

    companion object {
        fun fromWire(value: String?): PlanTier = entries.firstOrNull { it.wire == value } ?: FREE
    }
}

/** Ids de producto de Google Play y constantes de compra. */
object PlanCatalog {
    const val SUBSCRIPTION_ID = "premium"
    const val CREDITS_PRODUCT_ID = "creditos_10"
    const val CREDITS_PER_PACK = 10

    /** Solo para builds de debug sin Google Play: en producción el precio sale siempre de ProductDetails. */
    object FallbackUsd {
        const val MONTHLY = 1.99
        const val SEMESTER = 8.39
        const val CREDITS = 0.99
    }
}
