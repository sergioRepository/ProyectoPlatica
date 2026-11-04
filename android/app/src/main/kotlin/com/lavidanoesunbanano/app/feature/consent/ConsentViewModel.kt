package com.lavidanoesunbanano.app.feature.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.domain.model.Consents
import com.lavidanoesunbanano.app.navigation.StartResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConsentUiState(
    val excuses: Boolean = false,
    val phoneUsage: Boolean = false,
    val crossBorder: Boolean = false,
    val analytics: Boolean = false,
    val saving: Boolean = false,
) {
    val canContinue: Boolean get() = phoneUsage && crossBorder && !saving
}

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val deviceRepository: DeviceRepository,
    private val startResolver: StartResolver,
) : ViewModel() {

    // Todo arranca sin marcar: la autorización tiene que ser explícita.
    private val _state = MutableStateFlow(ConsentUiState())
    val state: StateFlow<ConsentUiState> = _state.asStateFlow()

    fun toggleExcuses() = _state.update { it.copy(excuses = !it.excuses) }

    fun togglePhoneUsage() = _state.update { it.copy(phoneUsage = !it.phoneUsage) }

    fun toggleCrossBorder() = _state.update { it.copy(crossBorder = !it.crossBorder) }

    fun toggleAnalytics() = _state.update { it.copy(analytics = !it.analytics) }

    fun accept(onDone: (Any) -> Unit) {
        val current = _state.value
        if (!current.canContinue) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            settings.setConsents(Consents(current.excuses, current.phoneUsage, current.crossBorder, current.analytics))
            // Sin red queda pendiente: Inicio lo reintenta y mientras tanto decide la regla local.
            deviceRepository.sync()
            onDone(startResolver.afterConsent())
        }
    }
}
