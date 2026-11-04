package com.lavidanoesunbanano.app.navigation

import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** A qué pantalla se entra al abrir la app. */
class StartResolver @Inject constructor(
    private val settings: SettingsStore,
    private val profileRepository: ProfileRepository,
    private val templateRepository: TemplateRepository,
) {
    /** Las autorizaciones van primero, también para quien ya usaba la app antes de pedirlas. */
    suspend fun start(): Any = if (settings.consents.first()?.isCurrent == true) afterConsent() else ConsentRoute

    /**
     * Sin perfil, onboarding; con perfil pero sin plantilla (por ejemplo si la generación
     * falló o se cerró la app), generación; con plantilla, inicio.
     */
    suspend fun afterConsent(): Any = when {
        templateRepository.messageCount() > 0 -> HomeRoute
        profileRepository.getProfile() != null -> GenerationRoute()
        else -> OnboardingRoute
    }
}
