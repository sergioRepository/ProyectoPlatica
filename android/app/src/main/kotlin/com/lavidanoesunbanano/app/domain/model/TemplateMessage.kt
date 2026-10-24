package com.lavidanoesunbanano.app.domain.model

/** Contextos del spec. `wire` es el valor que manda el backend y guarda Room. */
enum class MessageContext(val wire: String) {
    GENERAL("general"),
    REPEAT("reincidente"),
    NIGHT("noche"),
    MORNING("mañana"),
    AFTER_REJECTION("tras_rechazo"),
    ;

    companion object {
        fun fromWire(value: String): MessageContext = entries.firstOrNull { it.wire == value } ?: GENERAL
    }
}

/** Mensaje de la plantilla; `text` trae placeholders sin rellenar. */
data class TemplateMessage(
    val id: String,
    val text: String,
    val context: MessageContext,
    val intensity: Int,
)
