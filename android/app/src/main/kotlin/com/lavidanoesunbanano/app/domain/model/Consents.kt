package com.lavidanoesunbanano.app.domain.model

/**
 * Lo que autorizó el usuario. `excuses` y `analytics` son opcionales: el texto de las excusas
 * puede traer datos sensibles, y sin esa autorización decide la regla sin conexión.
 */
data class Consents(
    val excuses: Boolean,
    val phoneUsage: Boolean,
    val crossBorder: Boolean,
    val analytics: Boolean,
    val version: String = CURRENT_VERSION,
) {
    /** Si cambia el texto de la pantalla de autorización, hay que volver a pedirlas. */
    val isCurrent: Boolean get() = version == CURRENT_VERSION && phoneUsage && crossBorder

    companion object {
        const val CURRENT_VERSION = "2026-11-v2"
    }
}
