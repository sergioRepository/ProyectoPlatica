package com.lavidanoesunbanano.app.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Campos de texto: redondeados pero no en píldora, porque pueden tener varias líneas. */
private val FieldShape = RoundedCornerShape(16.dp)

/** Lo que no se puede tocar se ve apagado, sin cambiar de color. */
private const val DISABLED_ALPHA = 0.45f

// Animaciones mínimas: fundidos de 150 a 200 ms, nada que rebote.
const val FADE_IN_MS = 180
const val FADE_OUT_MS = 150
val FadeIn: EnterTransition = fadeIn(tween(FADE_IN_MS))
val FadeOut: ExitTransition = fadeOut(tween(FADE_OUT_MS))

/**
 * Borde de tinta de 2 dp y sombra dura desplazada, sin difuminar: el papel recortado del estilo
 * Warm Toon. La sombra se dibuja detrás, así que el padre no debe recortar el borde inferior derecho.
 */
fun Modifier.toon(shape: Shape, fill: Color, ink: Color, shadow: Dp = 3.dp): Modifier = this
    .drawBehind {
        if (shadow > 0.dp) {
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(shadow.toPx(), shadow.toPx()) { drawOutline(outline, ink) }
        }
    }
    .background(fill, shape)
    .border(2.dp, ink, shape)

/** Al presionar, la pieza se hunde sobre su sombra, como un botón de cartón. */
@Composable
private fun ToonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.small
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sunk = pressed && enabled
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .offset(x = if (sunk) 3.dp else 0.dp, y = if (sunk) 3.dp else 0.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toon(shape, fill, colors.border, shadow = if (sunk || !enabled) 0.dp else 3.dp)
            .clip(shape)
            .clickable(interaction, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ToonButton(text, onClick, modifier, enabled, fill = LaVidaTheme.colors.accent)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ToonButton(text, onClick, modifier, enabled, fill = LaVidaTheme.colors.surface)

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(contentColor = LaVidaTheme.colors.textSecondary),
    ) {
        Text(text = text, style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Bold))
    }
}

/** Campo con borde de tinta. Con el foco gana la sombra dura. */
@Composable
fun LaVidaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = false,
    showCounter: Boolean = !singleLine,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = (if (mono) type.monoBody else type.body).copy(color = colors.textPrimary)
    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.textPrimary),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toon(FieldShape, colors.surface, colors.border, shadow = if (focused) 3.dp else 0.dp)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = textStyle.copy(color = colors.textSecondary))
                    }
                    inner()
                }
            },
        )
        if (showCounter) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${value.length} / $maxLength",
                modifier = Modifier.fillMaxWidth(),
                style = type.mono,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Progreso del onboarding: "03 / 11" en mono y una barra amarilla con borde de tinta. */
@Composable
fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    val fraction by animateFloatAsState(
        targetValue = current.toFloat() / total,
        animationSpec = tween(FADE_IN_MS),
        label = "progreso",
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "%02d / %02d".format(current, total),
            style = LaVidaTheme.type.mono,
            color = colors.textSecondary,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .toon(CircleShape, colors.surface, colors.border, shadow = 0.dp)
                .clip(CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .background(colors.accent),
            )
        }
    }
}

/** Tarjeta que se elige; la elegida se pinta de amarillo banano. */
@Composable
fun SelectableRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    monoSubtitle: Boolean = false,
    multiple: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.medium
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toon(shape, if (selected) colors.accent else colors.surface, colors.border)
            .clip(shape)
            .then(interaction)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = if (monoSubtitle) LaVidaTheme.type.mono else LaVidaTheme.type.small,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SelectionMark(selected, multiple)
    }
}

/** Casilla (varias opciones) o círculo (una sola), de 20 dp con borde de tinta. */
@Composable
private fun SelectionMark(selected: Boolean, multiple: Boolean) {
    val colors = LaVidaTheme.colors
    val shape = if (multiple) MaterialTheme.shapes.extraSmall else CircleShape
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(colors.surface, shape)
            .border(2.dp, colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(colors.border, if (multiple) RoundedCornerShape(2.dp) else CircleShape),
            )
        }
    }
}

/** Tarjeta que abre otra pantalla: título y, debajo, el valor actual. */
@Composable
fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    monoValue: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toon(shape, colors.surface, colors.border)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = if (monoValue) LaVidaTheme.type.mono else LaVidaTheme.type.small,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Número que se ajusta con − y +, en mono. */
@Composable
fun StepperRow(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toon(shape, colors.surface, colors.border)
            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
        )
        StepperButton(text = "−", enabled = canDecrease, onClick = onDecrease)
        Text(
            text = value,
            modifier = Modifier.width(88.dp),
            style = LaVidaTheme.type.monoBody,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        StepperButton(text = "+", enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LaVidaTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toon(CircleShape, colors.accent, colors.border, shadow = 2.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LaVidaTheme.type.monoBody,
            color = colors.textPrimary,
        )
    }
}

/** Tarjeta blanca con borde de tinta y sombra dura para agrupar datos. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .toon(MaterialTheme.shapes.medium, colors.surface, colors.border, shadow = 4.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Fila "etiqueta  valor" en mono, para paneles de datos. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Row(modifier = modifier.fillMaxWidth()) {
        // 112 dp: caben etiquetas de 11 letras en mono ("recuperados") sin tocar el valor.
        Text(
            text = label,
            modifier = Modifier.width(112.dp),
            style = LaVidaTheme.type.monoBody,
            color = colors.textSecondary,
        )
        Text(text = value, style = LaVidaTheme.type.monoBody, color = colors.textPrimary)
    }
}

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = LaVidaTheme.type.mono,
        color = LaVidaTheme.colors.textSecondary,
    )
}

/** Estado mientras algo tarda: texto en mono y una barra en movimiento, sin spinner. */
@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = text, style = LaVidaTheme.type.mono, color = colors.textSecondary)
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .border(2.dp, colors.border, CircleShape)
                .clip(CircleShape),
            color = colors.accent,
            trackColor = colors.surface,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
    }
}

/** Día de la semana en mono, de una letra; el elegido va en amarillo. */
@Composable
fun DayToggle(label: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Box(
        modifier = modifier
            .size(36.dp)
            .toon(CircleShape, if (selected) colors.accent else colors.surface, colors.border, shadow = 0.dp)
            .clip(CircleShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LaVidaTheme.type.monoBody,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}
