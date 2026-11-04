package com.lavidanoesunbanano.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens de color del estilo Warm Toon (`Desing/warm_toon_modern/DESIGN.md`): papel cálido,
 * tinta para bordes, texto y sombras, y el amarillo banano para acciones y estados activos.
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
    /** Texto de apoyo un punto más oscuro que `textSecondary`. */
    val textBody: Color,
    /** Etiquetas en mono. */
    val textMuted: Color,
    /** Fondo de avisos. */
    val accentSoft: Color,
    val approvedSoft: Color,
    val rejectedSoft: Color,
    /** Relleno y borde de lo que no se puede tocar. */
    val disabled: Color,
    val disabledBorder: Color,
    /** Fila bajo llave. */
    val dimmed: Color,
    /** Línea punteada dentro de una tarjeta. */
    val divider: Color,
)

val LaVidaPalette = LaVidaColors(
    background = Color(0xFFFFFDF7),
    surface = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1F1A14),
    textSecondary = Color(0xFF4A4136),
    border = Color(0xFF1F1A14),
    accent = Color(0xFFFACC15),
    onAccent = Color(0xFF1F1A14),
    approved = Color(0xFF1F7A3A),
    rejected = Color(0xFFBA1A1A),
    textBody = Color(0xFF3D352B),
    textMuted = Color(0xFF6B6255),
    accentSoft = Color(0xFFFEF6D3),
    approvedSoft = Color(0xFFE7F3EA),
    rejectedSoft = Color(0xFFFDEDEC),
    disabled = Color(0xFFF3EFE6),
    disabledBorder = Color(0xFFA89F91),
    dimmed = Color(0xFFF7F4EC),
    divider = Color(0xFFD8D1C3),
)

private val LocalLaVidaColors = staticCompositionLocalOf { LaVidaPalette }
private val LocalLaVidaType = staticCompositionLocalOf { LaVidaType() }
private val DefaultType = LaVidaType()

// Controles en píldora; tarjetas de 16 dp. `extraSmall` sirve para casillas y marcas.
private val LaVidaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(percent = 50),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun LaVidaTheme(content: @Composable () -> Unit) {
    val colors = LaVidaPalette
    CompositionLocalProvider(
        LocalLaVidaColors provides colors,
        LocalLaVidaType provides DefaultType,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
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

private fun LaVidaColors.toColorScheme(): ColorScheme =
    lightColorScheme().copy(
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
