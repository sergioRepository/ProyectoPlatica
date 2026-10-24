package com.lavidanoesunbanano.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import com.lavidanoesunbanano.app.navigation.GenerationRoute
import com.lavidanoesunbanano.app.navigation.HomeRoute
import com.lavidanoesunbanano.app.navigation.OnboardingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    deviceRepository: DeviceRepository,
    profileRepository: ProfileRepository,
    templateRepository: TemplateRepository,
) : ViewModel() {

    /**
     * Pantalla inicial: sin perfil, onboarding; con perfil pero sin plantilla (por ejemplo si
     * la generación falló o se cerró la app), generación; con plantilla, inicio.
     */
    val startDestination: StateFlow<Any?> = flow {
        // El id del dispositivo se crea en el primer arranque, como pide el spec.
        deviceRepository.ensureDeviceId()
        emit(
            when {
                templateRepository.messageCount() > 0 -> HomeRoute
                profileRepository.getProfile() != null -> GenerationRoute()
                else -> OnboardingRoute
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
