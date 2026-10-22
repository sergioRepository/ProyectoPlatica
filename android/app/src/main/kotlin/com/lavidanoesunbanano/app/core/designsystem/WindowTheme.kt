package com.lavidanoesunbanano.app.core.designsystem

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/** Barras del sistema claras sobre el papel. El fondo de la ventana ya viene de `Theme.LaVida`. */
fun ComponentActivity.applyWindowTheme() {
    val style = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
