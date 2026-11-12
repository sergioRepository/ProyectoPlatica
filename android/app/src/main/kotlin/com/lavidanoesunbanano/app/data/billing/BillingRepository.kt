package com.lavidanoesunbanano.app.data.billing

import android.app.Activity
import com.lavidanoesunbanano.app.domain.billing.PlanTier
import kotlinx.coroutines.flow.StateFlow

/** Lo que se vende en Google Play. */
enum class StoreProduct(val tier: PlanTier?) {
    MONTHLY(PlanTier.MONTHLY),
    SEMESTER(PlanTier.SEMESTER),
    CREDITS(null),
    ;

    val isSubscription: Boolean get() = tier != null
}

/**
 * Precio tal como lo da Google Play para el país del usuario. `billingPeriod` es ISO 8601
 * (`P1M`, `P6M`) y `null` en los créditos.
 */
data class StorePrice(
    val formatted: String,
    val micros: Long,
    val currencyCode: String,
    val billingPeriod: String?,
)

/** En qué va una compra iniciada desde la app. Ningún estado da beneficios por sí solo. */
sealed interface PurchaseStatus {
    data object Idle : PurchaseStatus

    data class Launching(val product: StoreProduct) : PurchaseStatus

    /** Google la tiene pendiente (por ejemplo, pago en efectivo): sin beneficios hasta que se cobre. */
    data object Pending : PurchaseStatus

    data object Cancelled : PurchaseStatus

    /** `retryable`: se puede intentar otra vez (sin red, Google o el servidor caídos). */
    data class Failed(val retryable: Boolean) : PurchaseStatus

    /** El servidor la validó con Google. `outcome` es el del servidor (`subscription`, `granted`...). */
    data class Verified(val outcome: String) : PurchaseStatus
}

data class BillingState(
    val loading: Boolean = true,
    /** Precios de Google Play; vacío si la tienda no respondió. */
    val prices: Map<StoreProduct, StorePrice> = emptyMap(),
    /** Precios de respaldo en USD: solo en builds de debug sin Google Play. Nunca se puede comprar. */
    val fallback: Boolean = false,
    val purchase: PurchaseStatus = PurchaseStatus.Idle,
) {
    /** Hay un flujo en curso: el botón de compra se desactiva para no comprar dos veces. */
    val busy: Boolean
        get() = purchase is PurchaseStatus.Launching || purchase is PurchaseStatus.Pending
}

/**
 * Compras con Google Play. Los beneficios nunca salen de aquí: toda compra se manda a validar al
 * servidor, que es quien decide el plan y los créditos.
 */
interface BillingRepository {
    val state: StateFlow<BillingState>

    suspend fun refreshPrices()

    /** Inicia la compra de `product`. No hace nada si ya hay un flujo en curso o es el plan actual. */
    suspend fun purchase(activity: Activity, product: StoreProduct)

    /** Manda a validar todo lo que Google Play dice que el usuario tiene. Al abrir la app y al volver. */
    suspend fun restore()

    /** Tras mostrar el resultado de una compra, vuelve a `Idle`. */
    fun acknowledgeStatus()
}
