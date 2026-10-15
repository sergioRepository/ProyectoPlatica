package com.lavidanoesunbanano.app.core.designsystem

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb

/**
 * Barras del sistema y fondo de la ventana según el tema. El fondo de la ventana es lo que se
 * ve antes del primer cuadro de Compose: si no coincide, hay un destello al abrir.
 */
fun ComponentActivity.applyWindowTheme(dark: Boolean) {
    val style = if (dark) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    val colors = if (dark) DarkLaVidaColors else LightLaVidaColors
    window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
}

val Context.isSystemDark: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
