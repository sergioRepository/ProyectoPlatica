package com.lavidanoesunbanano.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.billing.BillingRepository
import com.lavidanoesunbanano.app.navigation.StartResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    startResolver: StartResolver,
    private val billing: BillingRepository,
) : ViewModel() {

    val startDestination: StateFlow<Any?> = flow { emit(startResolver.start()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Al abrir la app y al volver a ella: lo que Google Play dice que el usuario compró se manda
     * a validar. Así se restaura tras reinstalar y se recupera una compra que quedó sin validar.
     */
    fun restorePurchases() {
        viewModelScope.launch { billing.restore() }
    }
}
