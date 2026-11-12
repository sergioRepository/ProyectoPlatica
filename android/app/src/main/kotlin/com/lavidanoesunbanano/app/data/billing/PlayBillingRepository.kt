package com.lavidanoesunbanano.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.lavidanoesunbanano.app.BuildConfig
import com.lavidanoesunbanano.app.data.prefs.DeviceStore
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.VerifyResult
import com.lavidanoesunbanano.app.domain.billing.PlanCatalog
import com.lavidanoesunbanano.app.domain.billing.PlanTier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Play Billing Library directa. La app solo inicia compras y las manda al servidor: el
 * acknowledge, el consumo y la decisión del plan los hace el servidor tras validar con Google.
 */
@Singleton
class PlayBillingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val devices: DeviceRepository,
    private val deviceStore: DeviceStore,
) : BillingRepository, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(BillingState())
    override val state: StateFlow<BillingState> = _state.asStateFlow()

    /** Detalles de Google para lanzar la compra; nunca se guardan entre sesiones (lo pide Google). */
    private val details = mutableMapOf<StoreProduct, ProductDetails>()
    private val offerTokens = mutableMapOf<StoreProduct, String>()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) continuation.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    // Con la reconexión automática no hay que volver a llamar a startConnection.
                    override fun onBillingServiceDisconnected() = Unit
                },
            )
        }
    }

    override suspend fun refreshPrices() {
        _state.update { it.copy(loading = true) }
        val prices = if (connect()) {
            querySubscription() + queryCredits()
        } else {
            emptyMap()
        }
        val fallback = prices.isEmpty() && BuildConfig.DEBUG
        _state.update {
            it.copy(loading = false, prices = if (fallback) FALLBACK_PRICES else prices, fallback = fallback)
        }
    }

    private suspend fun queryDetails(productId: String, type: String): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(type).build()),
            )
            .build()
        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { result, found ->
                val list = if (result.responseCode == BillingResponseCode.OK) found.productDetailsList else emptyList()
                if (continuation.isActive) continuation.resume(list.firstOrNull())
            }
        }
    }

    private suspend fun querySubscription(): Map<StoreProduct, StorePrice> {
        val product = queryDetails(PlanCatalog.SUBSCRIPTION_ID, ProductType.SUBS) ?: return emptyMap()
        val prices = mutableMapOf<StoreProduct, StorePrice>()
        for (option in listOf(StoreProduct.MONTHLY, StoreProduct.SEMESTER)) {
            // Solo el plan base, sin ofertas: el precio que se muestra es el que se cobra al renovar.
            val offer = product.subscriptionOfferDetails
                ?.firstOrNull { it.basePlanId == option.tier?.basePlanId && it.offerId == null }
                ?: continue
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: continue
            details[option] = product
            offerTokens[option] = offer.offerToken
            prices[option] = StorePrice(phase.formattedPrice, phase.priceAmountMicros, phase.priceCurrencyCode, phase.billingPeriod)
        }
        return prices
    }

    private suspend fun queryCredits(): Map<StoreProduct, StorePrice> {
        val product = queryDetails(PlanCatalog.CREDITS_PRODUCT_ID, ProductType.INAPP) ?: return emptyMap()
        val offer = product.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return emptyMap()
        // En los productos únicos el token puede faltar: sin él no se puede lanzar la compra.
        val token = offer.offerToken ?: return emptyMap()
        details[StoreProduct.CREDITS] = product
        offerTokens[StoreProduct.CREDITS] = token
        return mapOf(
            StoreProduct.CREDITS to StorePrice(offer.formattedPrice, offer.priceAmountMicros, offer.priceCurrencyCode, null),
        )
    }

    /** Se toma antes de cualquier espera: un doble toque no abre dos compras (ni cobra dos paquetes). */
    private val purchasing = AtomicBoolean(false)

    override suspend fun purchase(activity: Activity, product: StoreProduct) {
        if (_state.value.busy || !purchasing.compareAndSet(false, true)) return
        try {
            launchPurchase(activity, product)
        } finally {
            purchasing.set(false)
        }
    }

    private suspend fun launchPurchase(activity: Activity, product: StoreProduct) {
        val productDetails = details[product]
        val offerToken = offerTokens[product]
        val account = devices.accountHash()
        if (productDetails == null || offerToken == null || account == null || !connect()) {
            _state.update { it.copy(purchase = PurchaseStatus.Failed(retryable = true)) }
            return
        }
        val productParams = ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
        val flow = BillingFlowParams.newBuilder().setObfuscatedAccountId(account)
        product.tier?.let { target ->
            val owned = queryOwned(ProductType.SUBS).firstOrNull {
                PlanCatalog.SUBSCRIPTION_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            val validated = PlanTier.fromWire(deviceStore.validatedPlan())
            when (val change = BillingRules.planChange(validated, target, owned != null)) {
                PlanChange.AlreadyActive -> return
                PlanChange.NewPurchase -> Unit
                is PlanChange.Replace -> {
                    productParams.setSubscriptionProductReplacementParams(
                        SubscriptionProductReplacementParams.newBuilder()
                            .setOldProductId(PlanCatalog.SUBSCRIPTION_ID)
                            .setReplacementMode(change.mode.toPlay())
                            .build(),
                    )
                    flow.setSubscriptionUpdateParams(
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(requireNotNull(owned).purchaseToken)
                            .build(),
                    )
                }
            }
        }
        flow.setProductDetailsParamsList(listOf(productParams.build()))
        _state.update { it.copy(purchase = PurchaseStatus.Launching(product)) }
        // Google exige lanzar el flujo desde el hilo principal.
        val result = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, flow.build()) }
        if (result.responseCode != BillingResponseCode.OK) onPurchasesUpdated(result, null)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK ->
                if (purchases.isNullOrEmpty()) {
                    _state.update { it.copy(purchase = PurchaseStatus.Idle) }
                } else {
                    scope.launch { purchases.forEach { handle(it, fromFlow = true) } }
                }
            BillingResponseCode.USER_CANCELED -> _state.update { it.copy(purchase = PurchaseStatus.Cancelled) }
            // Ya la tenía (otra instalación o una compra sin validar): se valida lo que Google tiene.
            BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch {
                restoreOwned(fromFlow = true)
                // Si no se encontró nada que validar, la pantalla no puede quedarse en «comprando».
                _state.update {
                    if (it.purchase is PurchaseStatus.Launching) it.copy(purchase = PurchaseStatus.Failed(retryable = true)) else it
                }
            }
            else -> _state.update { it.copy(purchase = PurchaseStatus.Failed(retryable = true)) }
        }
    }

    override suspend fun restore() = restoreOwned(fromFlow = false)

    private suspend fun restoreOwned(fromFlow: Boolean) {
        if (!connect()) return
        val owned = queryOwned(ProductType.SUBS) + queryOwned(ProductType.INAPP)
        // Una pendiente que Google canceló (no se pagó) desaparece: el aviso no puede quedar fijo.
        if (_state.value.purchase == PurchaseStatus.Pending && owned.none { it.purchaseState == Purchase.PurchaseState.PENDING }) {
            _state.update { it.copy(purchase = PurchaseStatus.Idle) }
        }
        owned.forEach { handle(it, fromFlow) }
    }

    /**
     * Solo una compra cobrada se manda a validar; la pendiente espera a que Google la cobre. Al
     * restaurar no se toca el estado de la pantalla salvo para avisar de una pendiente.
     */
    private suspend fun handle(purchase: Purchase, fromFlow: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> _state.update { it.copy(purchase = PurchaseStatus.Pending) }
            Purchase.PurchaseState.PURCHASED -> {
                val productId = purchase.products.firstOrNull() ?: return
                val status = when (val verified = devices.verifyPurchase(productId, purchase.purchaseToken)) {
                    is VerifyResult.Verified ->
                        if (verified.outcome == "pending") PurchaseStatus.Pending else PurchaseStatus.Verified(verified.outcome)
                    VerifyResult.Rejected -> PurchaseStatus.Failed(retryable = false)
                    VerifyResult.Unavailable, VerifyResult.NotRegistered -> PurchaseStatus.Failed(retryable = true)
                }
                val pendingBefore = _state.value.purchase == PurchaseStatus.Pending
                if (fromFlow || pendingBefore) _state.update { it.copy(purchase = status) }
            }
            else -> Unit
        }
    }

    private suspend fun queryOwned(type: String): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(params) { result, purchases ->
                val owned = if (result.responseCode == BillingResponseCode.OK) purchases else emptyList()
                if (continuation.isActive) continuation.resume(owned)
            }
        }
    }

    override fun acknowledgeStatus() {
        _state.update { it.copy(purchase = PurchaseStatus.Idle) }
    }

    private fun ReplacementKind.toPlay(): Int = when (this) {
        ReplacementKind.CHARGE_FULL_PRICE -> SubscriptionProductReplacementParams.ReplacementMode.CHARGE_FULL_PRICE
        ReplacementKind.WITHOUT_PRORATION -> SubscriptionProductReplacementParams.ReplacementMode.WITHOUT_PRORATION
    }

    private companion object {
        /** Solo para builds de debug sin Google Play; `fallback = true` impide comprar con ellos. */
        val FALLBACK_PRICES = mapOf(
            StoreProduct.MONTHLY to StorePrice("US$ ${PlanCatalog.FallbackUsd.MONTHLY}", 1_990_000, "USD", "P1M"),
            StoreProduct.SEMESTER to StorePrice("US$ ${PlanCatalog.FallbackUsd.SEMESTER}", 8_390_000, "USD", "P6M"),
            StoreProduct.CREDITS to StorePrice("US$ ${PlanCatalog.FallbackUsd.CREDITS}", 990_000, "USD", null),
        )
    }
}
