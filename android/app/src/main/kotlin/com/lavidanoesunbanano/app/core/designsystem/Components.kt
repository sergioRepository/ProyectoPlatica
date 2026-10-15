package com.lavidanoesunbanano.app.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Animaciones mínimas: fundidos de 150 a 200 ms, nada que rebote.
const val FADE_IN_MS = 180
const val FADE_OUT_MS = 150
val FadeIn: EnterTransition = fadeIn(tween(FADE_IN_MS))
val FadeOut: ExitTransition = fadeOut(tween(FADE_OUT_MS))

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LaVidaTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.textSecondary,
        ),
        border = if (enabled) null else BorderStroke(1.dp, colors.border),
        elevation = null,
    ) {
        Text(text = text, style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LaVidaTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textSecondary,
        ),
    ) {
        Text(text = text, style = LaVidaTheme.type.body)
    }
}

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
        Text(text = text, style = LaVidaTheme.type.body)
    }
}

/** Campo con borde fino. El borde pasa al color de acento con el foco. */
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
            cursorBrush = SolidColor(colors.accent),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, MaterialTheme.shapes.medium)
                        .border(1.dp, if (focused) colors.accent else colors.border, MaterialTheme.shapes.medium)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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

/** Progreso del onboarding: "03 / 11" en mono y una línea de 1 dp. */
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
                .height(1.dp)
                .background(colors.border),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(1.dp)
                    .background(colors.accent),
            )
        }
    }
}

/** Fila con borde fino que se elige; el acento marca la selección. */
@Composable
fun SelectableRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    monoSubtitle: Boolean = false,
    multiple: Boolean = false,
) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.medium
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .then(interaction)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = LaVidaTheme.type.body, color = colors.textPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = if (monoSubtitle) LaVidaTheme.type.mono else LaVidaTheme.type.small,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SelectionMark(selected)
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    val colors = LaVidaTheme.colors
    Box(
        modifier = Modifier
            .size(16.dp)
            .border(1.dp, if (selected) colors.accent else colors.border, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(colors.accent, MaterialTheme.shapes.extraSmall),
            )
        }
    }
}

/** Fila con borde fino que abre otra pantalla: título y, debajo, el valor actual en gris. */
@Composable
fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    monoValue: Boolean = false,
) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, style = LaVidaTheme.type.body, color = colors.textPrimary)
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
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = LaVidaTheme.type.body, color = colors.textPrimary)
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
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LaVidaTheme.type.monoBody,
            color = if (enabled) colors.textPrimary else colors.border,
        )
    }
}

/** Superficie con borde fino para agrupar datos. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, MaterialTheme.shapes.medium)
            .border(1.dp, colors.border, MaterialTheme.shapes.medium)
            .padding(16.dp),
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

/** Estado discreto mientras algo tarda: texto en mono y un indicador lineal fino, sin spinner. */
@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = text, style = LaVidaTheme.type.mono, color = colors.textSecondary)
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp),
            color = colors.accent,
            trackColor = colors.border,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
    }
}

/** Día de la semana en mono, de una letra. */
@Composable
fun DayToggle(label: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(shape)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LaVidaTheme.type.monoBody,
            color = if (selected) colors.accent else colors.textSecondary,
        )
    }
}
