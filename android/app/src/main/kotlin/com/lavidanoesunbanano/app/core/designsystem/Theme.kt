package com.lavidanoesunbanano.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens de color. La jerarquía va por tamaño y peso tipográfico, no por color:
 * el acento es el único color y se reserva para el botón principal y estados activos.
 */
@Immutable
data class LaVidaColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val approved: Color,
    val rejected: Color,
)

val DarkLaVidaColors = LaVidaColors(
    background = Color(0xFF0F1114),
    surface = Color(0xFF171A1E),
    textPrimary = Color(0xFFE3E6E9),
    textSecondary = Color(0xFF8A9199),
    border = Color(0xFF272B31),
    accent = Color(0xFF8499AE),
    onAccent = Color(0xFF0F1114),
    approved = Color(0xFF7FA087),
    rejected = Color(0xFFB37A7B),
)

val LightLaVidaColors = LaVidaColors(
    background = Color(0xFFF3F4F6),
    surface = Color(0xFFFBFBFC),
    textPrimary = Color(0xFF15181C),
    textSecondary = Color(0xFF5F666E),
    border = Color(0xFFDADDE2),
    accent = Color(0xFF4F6B85),
    onAccent = Color(0xFFFFFFFF),
    approved = Color(0xFF4E7A58),
    rejected = Color(0xFF9A4F50),
)

private val LocalLaVidaColors = staticCompositionLocalOf { DarkLaVidaColors }
private val LocalLaVidaType = staticCompositionLocalOf { LaVidaType() }
private val DefaultType = LaVidaType()

private val LaVidaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun LaVidaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkLaVidaColors else LightLaVidaColors
    CompositionLocalProvider(
        LocalLaVidaColors provides colors,
        LocalLaVidaType provides DefaultType,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            shapes = LaVidaShapes,
            typography = DefaultType.toMaterial(),
            content = content,
        )
    }
}

object LaVidaTheme {
    val colors: LaVidaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLaVidaColors.current

    val type: LaVidaType
        @Composable
        @ReadOnlyComposable
        get() = LocalLaVidaType.current
}

private fun LaVidaColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = textSecondary,
        onSecondary = background,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface,
        surfaceContainerLow = surface,
        surfaceContainerHigh = surface,
        outline = border,
        outlineVariant = border,
        error = rejected,
        onError = background,
    )
}
