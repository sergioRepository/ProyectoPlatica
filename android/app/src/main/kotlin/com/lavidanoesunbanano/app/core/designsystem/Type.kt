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

val JakartaFamily = FontFamily(jakarta(500), jakarta(700), jakarta(800))
val GeistMonoFamily = FontFamily(geistMono(400), geistMono(500))

/**
 * Cinco tamaños: 30, 22, 16, 14 y 12. Pesos del estilo Warm Toon: 800 en títulos, 700 en
 * etiquetas interactivas y 500 en el cuerpo (nada más delgado).
 * Mono para datos: contadores, horas, paquetes, etiquetas e historial.
 */
@Immutable
data class LaVidaType(
    /** Mensaje de la pantalla de bloqueo: grande y grueso, como una viñeta. */
    val display: TextStyle = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
    ),
    /** Pregunta del onboarding y títulos de pantalla. */
    val title: TextStyle = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    /** Ayudas y texto secundario. */
    val small: TextStyle = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    /** Cifras de las estadísticas: en mono, como pide el spec, y del tamaño de un título. */
    val figure: TextStyle = TextStyle(
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    /** Valores técnicos legibles: horas, paquetes, códigos. */
    val monoBody: TextStyle = TextStyle(
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    /** Etiquetas, contadores y progreso. */
    val mono: TextStyle = TextStyle(
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
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
