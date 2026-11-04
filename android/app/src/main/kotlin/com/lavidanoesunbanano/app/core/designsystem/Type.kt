package com.lavidanoesunbanano.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lavidanoesunbanano.app.R

// Plus Jakarta Sans y Geist Mono van empaquetadas (no descargables): la pantalla de bloqueo
// debe verse igual en modo avión. Son fuentes variables; cada peso fija el eje "wght".
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun geistMono(weight: Int) = Font(
    R.font.geist_mono,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val JakartaFamily = FontFamily(jakarta(500), jakarta(600), jakarta(700), jakarta(800))
val GeistMonoFamily = FontFamily(geistMono(400), geistMono(500), geistMono(600))

private fun heading(size: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = JakartaFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

private fun monoStyle(size: Double, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) = TextStyle(
    fontFamily = GeistMonoFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

private fun text(size: Double, line: Int) = TextStyle(
    fontFamily = JakartaFamily,
    fontWeight = FontWeight.Medium,
    fontSize = size.sp,
    lineHeight = line.sp,
)

/**
 * Escala del diseño: títulos con peso 800, etiquetas interactivas con 700 y cuerpo con 500.
 * Mono para datos: contadores, horas, paquetes, etiquetas e historial.
 */
@Immutable
data class LaVidaType(
    /** Primera pantalla. */
    val hero: TextStyle = heading(34, 36, -0.68),
    /** Título de Inicio, Historial y Ajustes. */
    val screenTitle: TextStyle = heading(32, 34, -0.64),
    /** Pregunta del onboarding y títulos de pantalla. */
    val title: TextStyle = heading(30, 33, -0.6),
    /** Mensaje de la pantalla de bloqueo: grande y grueso, como una viñeta. */
    val display: TextStyle = heading(28, 34, -0.56),
    /** Título de la tarjeta principal y del veredicto. */
    val headline: TextStyle = heading(26, 31, -0.52),
    /** Secciones de Ajustes. */
    val section: TextStyle = heading(20, 26),
    /** Título dentro de una tarjeta. */
    val subhead: TextStyle = heading(16, 22),
    val body: TextStyle = text(16.0, 24),
    /** Ayudas y texto secundario. */
    val small: TextStyle = text(14.0, 20),
    val caption: TextStyle = text(13.0, 18),
    /** Cifra principal. */
    val bigFigure: TextStyle = monoStyle(40.0, 44, FontWeight.SemiBold),
    /** Cifras de las estadísticas. */
    val figure: TextStyle = monoStyle(20.0, 26, FontWeight.SemiBold),
    /** Valores técnicos legibles: horas, paquetes, códigos. */
    val monoBody: TextStyle = monoStyle(14.0, 20),
    /** Etiquetas, contadores y progreso. */
    val mono: TextStyle = monoStyle(12.5, 16),
    /** Etiqueta en mayúsculas sobre una sección. */
    val monoLabel: TextStyle = monoStyle(12.0, 16, spacing = 0.96),
)

internal fun LaVidaType.toMaterial() = Typography(
    displaySmall = display,
    headlineSmall = title,
    titleMedium = body.copy(fontWeight = FontWeight.Bold),
    bodyLarge = body,
    bodyMedium = small,
    bodySmall = small,
    labelLarge = body.copy(fontWeight = FontWeight.Bold),
    labelMedium = mono,
    labelSmall = mono,
)
