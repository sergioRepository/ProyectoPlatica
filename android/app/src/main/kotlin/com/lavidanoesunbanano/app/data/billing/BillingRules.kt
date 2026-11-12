package com.lavidanoesunbanano.app.data.billing

import com.lavidanoesunbanano.app.domain.billing.PlanTier
import java.time.Period
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/** Qué hacer al pedir una suscripción, según el plan que el servidor ya validó. */
sealed interface PlanChange {
    data object NewPurchase : PlanChange

    /** Cambio entre base plans de la misma suscripción, con el modo de reemplazo de Google Play. */
    data class Replace(val mode: ReplacementKind) : PlanChange

    /** Ya es el plan vigente: no se ofrece comprarlo otra vez. */
    data object AlreadyActive : PlanChange
}

/**
 * Entre base plans de la misma suscripción Google solo acepta cobrar el precio completo ya o al
 * renovar; cualquier otro modo hace fallar la compra
 * (https://developer.android.com/google/play/billing/subscriptions).
 */
enum class ReplacementKind {
    /** Mensual → Semestral: el plan más largo empieza ya y el tiempo que quedaba se abona. */
    CHARGE_FULL_PRICE,

    /** Semestral → Mensual: se cobra al renovar; no hay doble cobro. */
    WITHOUT_PRORATION,
}

object BillingRules {

    fun planChange(validated: PlanTier, target: PlanTier, ownsSubscription: Boolean): PlanChange = when {
        validated == target -> PlanChange.AlreadyActive
        // Sin compra activa en Google (o en Gratis) se compra de cero.
        !ownsSubscription || !validated.isPaid -> PlanChange.NewPurchase
        target == PlanTier.SEMESTER -> PlanChange.Replace(ReplacementKind.CHARGE_FULL_PRICE)
        else -> PlanChange.Replace(ReplacementKind.WITHOUT_PRORATION)
    }

    /** Meses de un período ISO 8601 de Google Play (`P1M`, `P6M`, `P1Y`); `null` si no es en meses. */
    fun months(billingPeriod: String?): Int? {
        if (billingPeriod == null) return null
        return try {
            Period.parse(billingPeriod).toTotalMonths().toInt().takeIf { it > 0 }
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** Equivalente mensual en micros, calculado del precio real; `null` si no se puede. */
    fun monthlyMicros(price: StorePrice): Long? = months(price.billingPeriod)?.let { price.micros / it }

    /**
     * Porcentaje que ahorra `longer` frente a pagar `monthly` cada mes, redondeado. `null` si las
     * monedas no coinciden, no se puede calcular o no hay ahorro: entonces no se muestra.
     */
    fun savingsPercent(monthly: StorePrice, longer: StorePrice): Int? {
        if (monthly.currencyCode != longer.currencyCode) return null
        if (months(monthly.billingPeriod) != 1) return null
        val months = months(longer.billingPeriod) ?: return null
        val full = monthly.micros * months
        if (full <= 0 || longer.micros >= full) return null
        return ((full - longer.micros) * 100.0 / full).roundToInt().takeIf { it > 0 }
    }
}
