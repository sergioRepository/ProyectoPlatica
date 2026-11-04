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
import com.lavidanoesunbanano.app.domain.setup.SetupStep
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
    /** Pasos cuyo ajuste ya se abrió y de los que ya se volvió: si siguen pendientes, se negó. */
    val opened: Set<SetupStep> = emptySet(),
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val source: SetupStatusSource,
    settings: SettingsStore,
) : ViewModel() {

    private val status = MutableStateFlow<SetupStatus?>(null)
    private val notificationsAsked = MutableStateFlow(false)
    private val opened = MutableStateFlow(emptySet<SetupStep>())

    /** Abierto y sin volver todavía: mientras tanto no se puede decir que se negó. */
    private var leaving: SetupStep? = null

    /** `null` hasta la primera lectura. */
    val state: StateFlow<SetupUiState?> = combine(
        status.filterNotNull(),
        notificationsAsked,
        settings.antiSabotage,
        opened,
    ) { status, asked, protectionOn, opened ->
        SetupUiState(
            items = SetupChecklist.items(status),
            service = status.service,
            manufacturer = source.manufacturer,
            notificationsAsked = asked,
            protectionOn = protectionOn,
            opened = opened,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Al volver de cada ajuste del sistema. */
    fun refresh() {
        status.value = source.current()
        leaving?.let { opened.value += it }
        leaving = null
    }

    fun onOpened(step: SetupStep) {
        leaving = step
    }

    fun onNotificationsAsked() {
        notificationsAsked.value = true
        refresh()
    }
}
