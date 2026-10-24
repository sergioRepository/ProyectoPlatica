package com.lavidanoesunbanano.app.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.system.Manufacturer
import com.lavidanoesunbanano.app.data.system.SetupStatusSource
import com.lavidanoesunbanano.app.domain.setup.ServiceHealth
import com.lavidanoesunbanano.app.domain.setup.SetupChecklist
import com.lavidanoesunbanano.app.domain.setup.SetupItem
import com.lavidanoesunbanano.app.domain.setup.SetupStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

data class SetupUiState(
    val items: List<SetupItem>,
    val service: ServiceHealth,
    val manufacturer: Manufacturer,
    /** Ya se pidió el permiso de notificaciones: si sigue negado, se abren sus ajustes. */
    val notificationsAsked: Boolean,
    val protectionOn: Boolean,
) {
    val doneCount: Int get() = items.count { it.done }
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val source: SetupStatusSource,
    settings: SettingsStore,
) : ViewModel() {

    private val status = MutableStateFlow<SetupStatus?>(null)
    private val notificationsAsked = MutableStateFlow(false)

    /** `null` hasta la primera lectura. */
    val state: StateFlow<SetupUiState?> = combine(
        status.filterNotNull(),
        notificationsAsked,
        settings.antiSabotage,
    ) { status, asked, protectionOn ->
        SetupUiState(
            items = SetupChecklist.items(status),
            service = status.service,
            manufacturer = source.manufacturer,
            notificationsAsked = asked,
            protectionOn = protectionOn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Al volver de cada ajuste del sistema. */
    fun refresh() {
        status.value = source.current()
    }

    fun onNotificationsAsked() {
        notificationsAsked.value = true
        refresh()
    }
}
