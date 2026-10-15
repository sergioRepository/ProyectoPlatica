package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.domain.template.PlaceholderFiller
import com.lavidanoesunbanano.app.domain.template.PlaceholderValues
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

data class MessagePreview(val raw: String, val text: String)

/**
 * Un mensaje al azar de la plantilla con los placeholders rellenados, para mostrar el tono.
 * Los contadores son de ejemplo; la selección por contexto llega con la pantalla de bloqueo.
 */
class TemplatePreviewer @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val profileRepository: ProfileRepository,
    private val clock: Clock,
) {
    suspend fun random(excludeRaw: String? = null): MessagePreview? {
        val messages = templateRepository.messages()
        if (messages.isEmpty()) return null
        val profile = profileRepository.getProfile() ?: return null
        val app = profileRepository.getBlockedApps().firstOrNull()?.label ?: return null
        val raw = messages.filter { it != excludeRaw }.ifEmpty { messages }.random()
        val values = PlaceholderValues(
            name = profile.name,
            goal = profile.mainGoal,
            app = app,
            attemptsToday = SAMPLE_ATTEMPTS,
            minutesToday = SAMPLE_MINUTES,
            time = LocalTime.now(clock),
        )
        return MessagePreview(raw = raw, text = PlaceholderFiller.fill(raw, values))
    }

    private companion object {
        const val SAMPLE_ATTEMPTS = 3
        const val SAMPLE_MINUTES = 27
    }
}
