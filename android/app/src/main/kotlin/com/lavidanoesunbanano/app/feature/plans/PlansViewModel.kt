package com.lavidanoesunbanano.app.feature.plans

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.billing.BillingRepository
import com.lavidanoesunbanano.app.data.billing.BillingState
import com.lavidanoesunbanano.app.data.billing.PurchaseStatus
import com.lavidanoesunbanano.app.data.billing.StoreProduct
import com.lavidanoesunbanano.app.data.prefs.DeviceStore
import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.domain.billing.PlanTier
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlansUiState(
    val billing: BillingState = BillingState(),
    val device: DeviceStatusDto? = null,
    /** Plan que validó el servidor. Solo cambia con lo que él diga, nunca por tocar una tarjeta. */
    val validated: PlanTier = PlanTier.FREE,
    val selected: PlanTier = PlanTier.MONTHLY,
    val creditsOpen: Boolean = false,
)

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceStore: DeviceStore,
    savedState: SavedStateHandle,
) : ViewModel() {

    private data class Local(val device: DeviceStatusDto?, val validated: PlanTier, val selected: PlanTier, val creditsOpen: Boolean)

    // `showCredits` es el nombre del argumento de PlansRoute.
    private val local = MutableStateFlow(
        Local(null, PlanTier.FREE, PlanTier.MONTHLY, savedState.get<Boolean>("showCredits") ?: false),
    )
    private var selectedByUser = false
    private var lastAction: (suspend (Activity) -> Unit)? = null

    val state: StateFlow<PlansUiState> = combine(billing.state, local) { billing, local ->
        PlansUiState(billing, local.device, local.validated, local.selected, local.creditsOpen)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlansUiState(billing.state.value, creditsOpen = local.value.creditsOpen))

    init {
        viewModelScope.launch {
            // Sin red, el último plan que validó el servidor.
            applyValidated(PlanTier.fromWire(deviceStore.validatedPlan()))
        }
        viewModelScope.launch {
            billing.state.map { it.purchase }.distinctUntilChanged().collect {
                if (it is PurchaseStatus.Verified) loadStatus()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            billing.refreshPrices()
            loadStatus()
        }
    }

    fun select(tier: PlanTier) {
        selectedByUser = true
        local.update { it.copy(selected = tier) }
    }

    /** Subscribe y Change compran; Current y Manage no (Manage lo abre la pantalla). */
    fun onPrimary(activity: Activity) {
        val s = state.value
        val action = primaryAction(
            s.validated,
            s.device?.subscriptionState,
            s.selected,
            s.billing.prices[storeProduct(s.selected)],
            s.billing.fallback,
        )
        val product = when (action) {
            is PrimaryAction.Subscribe -> action.product
            is PrimaryAction.Change -> action.product
            else -> return
        }
        launchPurchase(activity, product)
    }

    fun buyCredits(activity: Activity) = launchPurchase(activity, StoreProduct.CREDITS)

    /** Repite la última compra que falló. */
    fun retry(activity: Activity) {
        val action = lastAction ?: return
        viewModelScope.launch { action(activity) }
    }

    /** Una compra pendiente no se descarta: el botón seguiría bloqueado hasta que Google la cobre. */
    fun dismissStatus() {
        if (billing.state.value.purchase != PurchaseStatus.Pending) billing.acknowledgeStatus()
    }

    fun toggleCredits() = local.update { it.copy(creditsOpen = !it.creditsOpen) }

    private fun launchPurchase(activity: Activity, product: StoreProduct) {
        val action: suspend (Activity) -> Unit = { billing.purchase(it, product) }
        lastAction = action
        viewModelScope.launch { action(activity) }
    }

    private suspend fun loadStatus() {
        val device = deviceRepository.status() ?: return
        applyValidated(PlanTier.fromWire(device.plan), device)
    }

    private fun applyValidated(validated: PlanTier, device: DeviceStatusDto? = null) {
        local.update {
            it.copy(
                device = device ?: it.device,
                validated = validated,
                // Al abrir: el plan de pago vigente, si no el mensual. Después, lo que el usuario toque.
                selected = if (selectedByUser) it.selected else if (validated.isPaid) validated else PlanTier.MONTHLY,
            )
        }
    }
}
