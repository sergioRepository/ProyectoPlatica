package com.lavidanoesunbanano.app.domain.model

/** Respuestas del onboarding que describen a la persona. Las reglas van aparte. */
data class Profile(
    val name: String,
    val mainGoal: String,
    val secondaryGoals: List<String>,
    val goalReason: String,
    val typicalExcuses: String,
    val sarcasmLevel: Int,
    val profanityAllowed: Boolean,
    val country: String,
    val forbiddenTopics: String,
)
