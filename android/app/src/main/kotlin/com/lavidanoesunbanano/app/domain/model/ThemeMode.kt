package com.lavidanoesunbanano.app.domain.model

/** Apariencia elegida en Ajustes. Oscuro por defecto, como pide el spec. */
enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> systemDark
    }
}
