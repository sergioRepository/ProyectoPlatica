package com.lavidanoesunbanano.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R

/** Lo que no se puede tocar se ve apagado, sin cambiar de color. */
private const val DISABLED_ALPHA = 0.5f

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

/** Borde punteado: lo apagado y lo que es solo una nota. */
fun Modifier.dashed(shape: Shape, color: Color): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline,
        color,
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

/** Al presionar, la pieza se hunde sobre su sombra, como un botón de cartón. */
@Composable
private fun ToonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    weight: FontWeight,
    subtitle: String? = null,
    minHeight: Dp = 56.dp,
    shape: Shape = MaterialTheme.shapes.small,
    sidePadding: Dp = 16.dp,
) {
    val colors = LaVidaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sunk = pressed && enabled
    val surface = if (enabled) {
        Modifier.toon(shape, fill, colors.border, shadow = if (sunk) 0.dp else 3.dp)
    } else {
        Modifier
            .background(colors.disabled, shape)
            .dashed(shape, colors.disabledBorder)
    }
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .offset(x = if (sunk) 3.dp else 0.dp, y = if (sunk) 3.dp else 0.dp)
            .then(surface)
            .clip(shape)
            .clickable(interaction, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = sidePadding, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = text,
                style = LaVidaTheme.type.body.copy(fontWeight = weight),
                color = if (enabled) colors.textPrimary else colors.textMuted,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = LaVidaTheme.type.mono,
                    color = if (enabled) colors.textSecondary else colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null,
) = ToonButton(text, onClick, modifier, enabled, LaVidaTheme.colors.accent, FontWeight.ExtraBold, subtitle)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ToonButton(text, onClick, modifier, enabled, LaVidaTheme.colors.surface, FontWeight.Bold)

/** Botón de las dos opciones del bloqueo: más alto, con su cupo debajo. */
@Composable
fun OptionButton(
    text: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ToonButton(
    text,
    onClick,
    modifier,
    enabled,
    LaVidaTheme.colors.surface,
    FontWeight.Bold,
    subtitle,
    minHeight = 64.dp,
    shape = RoundedCornerShape(18.dp),
    sidePadding = 6.dp,
)

/** Botón pequeño con borde, dentro de una tarjeta. */
@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .toon(shape, colors.surface, colors.border, shadow = 2.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LaVidaTheme.type.small.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Acción sin relleno: texto subrayado con la altura mínima de un blanco táctil. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Bold) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LaVidaTheme.type.small.copy(fontWeight = weight, textDecoration = TextDecoration.Underline),
            color = LaVidaTheme.colors.textPrimary,
        )
    }
}

/**
 * Campo con borde de tinta. Con varias líneas es una tarjeta con sombra y un pie con el
 * contador y, si hace falta, el error.
 */
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
    error: String? = null,
    textStyle: TextStyle = if (mono) LaVidaTheme.type.monoBody else LaVidaTheme.type.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = textStyle.copy(color = colors.textPrimary)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .toon(shape, colors.surface, colors.border, shadow = if (focused || showCounter) 3.dp else 0.dp)
            .clip(shape),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = style,
            cursorBrush = SolidColor(colors.textPrimary),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = style.copy(color = colors.textMuted))
                    }
                    inner()
                }
            },
        )
        if (showCounter) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.border))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = error.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.rejected,
                )
                Text(text = "${value.length} / $maxLength", style = type.mono, color = colors.textSecondary)
            }
        }
    }
}

/** Una barra por etapa, llena hasta la actual, y la etiqueta de la etapa en mono. */
@Composable
fun StageProgress(filled: Int, total: Int, label: String, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(if (index < filled) colors.accent else colors.surface, CircleShape)
                        .border(2.dp, colors.border, CircleShape),
                )
            }
        }
        Text(
            text = label,
            style = LaVidaTheme.type.mono.copy(fontSize = LaVidaTheme.type.caption.fontSize, fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
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
    detail: String? = null,
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.ExtraBold), color = colors.textPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = if (monoSubtitle) LaVidaTheme.type.mono else LaVidaTheme.type.small,
                    color = if (selected) colors.textBody else colors.textSecondary,
                )
            }
            if (detail != null) {
                Text(text = detail, style = LaVidaTheme.type.caption, color = colors.textBody)
            }
        }
        Spacer(Modifier.width(12.dp))
        SelectionMark(selected)
    }
}

/** Círculo de 30 dp: vacío, o negro con la marca amarilla. */
@Composable
private fun SelectionMark(selected: Boolean) {
    val colors = LaVidaTheme.colors
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(if (selected) colors.border else colors.surface, CircleShape)
            .border(2.dp, colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(text = "✓", style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.ExtraBold), color = colors.accent)
        }
    }
}

/** Casilla de 24 dp, marcada en tinta. */
@Composable
private fun CheckMark(checked: Boolean) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(if (checked) colors.border else colors.surface, shape)
            .border(2.dp, colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(text = "✓", style = LaVidaTheme.type.small.copy(fontWeight = FontWeight.ExtraBold), color = colors.surface)
        }
    }
}

/** Opción con casilla, sin tarjeta propia: se pone dentro de una. */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    extra: @Composable (ColumnScope.() -> Unit)? = null,
    trailingMark: Boolean = false,
) {
    val colors = LaVidaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!trailingMark) CheckMark(checked)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            if (description != null) {
                Text(text = description, style = LaVidaTheme.type.caption, color = colors.textSecondary)
            }
            extra?.invoke(this)
        }
        if (trailingMark) CheckMark(checked)
    }
}

/** Tarjeta con sombra y padding: agrupa datos o un formulario. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    padding: Dp = 16.dp,
    shadow: Dp = 3.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .toon(MaterialTheme.shapes.medium, colors.surface, colors.border, shadow = shadow)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Tarjeta sin padding: sus filas van pegadas, separadas con [RowDivider]. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, shadow: Dp = 3.dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = LaVidaTheme.colors
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .toon(shape, colors.surface, colors.border, shadow = shadow)
            .clip(shape),
        content = content,
    )
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(LaVidaTheme.colors.border))
}

/** Separador punteado dentro de una tarjeta. */
@Composable
fun DashedDivider() {
    val color = LaVidaTheme.colors.divider
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawLine(
                    color,
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            },
    )
}

/** Fila que abre otra pantalla, dentro de un [GroupedCard]: título, valor y flecha. */
@Composable
fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    note: String? = null,
    monoValue: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LaVidaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(if (enabled) Modifier else Modifier.background(colors.dimmed))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = if (monoValue) LaVidaTheme.type.mono else LaVidaTheme.type.caption,
                    color = if (enabled) colors.textSecondary else colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note != null) {
                Text(text = note, style = LaVidaTheme.type.caption, color = colors.textBody)
            }
        }
        if (enabled) UiIcon(R.drawable.ic_ui_chevron, size = 18.dp)
    }
}

/** Número que se ajusta con − y +, en mono. */
@Composable
fun StepperRow(
    title: String,
    hint: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    val colors = LaVidaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = LaVidaTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Text(text = hint, style = LaVidaTheme.type.caption, color = colors.textSecondary)
        }
        StepperButton(text = "−", label = decreaseLabel, fill = colors.surface, enabled = canDecrease, onClick = onDecrease)
        Text(
            text = value,
            modifier = Modifier.width(30.dp),
            style = LaVidaTheme.type.figure,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        StepperButton(text = "+", label = increaseLabel, fill = colors.accent, enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(text: String, label: String, fill: Color, enabled: Boolean, onClick: () -> Unit) {
    val colors = LaVidaTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toon(CircleShape, if (enabled) fill else colors.disabled, colors.border, shadow = if (enabled) 2.dp else 0.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = LaVidaTheme.type.figure, color = colors.textPrimary)
    }
}

enum class NoticeTone { INFO, SUCCESS, ERROR, NOTE, PLAIN }

/** Aviso con borde: amarillo informa, verde confirma, rojo falla, punteado es una nota. */
@Composable
fun Notice(
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.INFO,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LaVidaTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val surface = when (tone) {
        NoticeTone.INFO -> Modifier.background(colors.accentSoft, shape).border(2.dp, colors.border, shape)
        NoticeTone.SUCCESS -> Modifier.background(colors.approvedSoft, shape).border(2.dp, colors.approved, shape)
        NoticeTone.ERROR -> Modifier.background(colors.rejectedSoft, shape).border(2.dp, colors.rejected, shape)
        NoticeTone.NOTE -> Modifier.background(colors.surface, shape).dashed(shape, colors.border)
        NoticeTone.PLAIN -> Modifier.background(colors.surface, shape).border(2.dp, colors.border, shape)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** Aviso de una línea con ícono. */
@Composable
fun IconNotice(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.INFO,
) {
    Notice(modifier, tone) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UiIcon(icon, size = 20.dp)
            Text(text = text, style = LaVidaTheme.type.caption, color = LaVidaTheme.colors.textPrimary)
        }
    }
}

/** Ícono de línea en tinta. Siempre decorativo: lo acompaña un texto. */
@Composable
fun UiIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Image(painter = painterResource(icon), contentDescription = null, modifier = modifier.size(size))
}

enum class BananaMood(@param:DrawableRes val drawable: Int) {
    PLAIN(R.drawable.banana_plain),
    WAITING(R.drawable.banana_wait),
    FLAT(R.drawable.banana_flat),
    SMILE(R.drawable.banana_smile),
    SAD(R.drawable.banana_sad),
    DISAPPROVING(R.drawable.banana_block),
    HAPPY(R.drawable.banana_happy),
    READING(R.drawable.banana_reading),
}

/** La mascota. Decorativa: lo que dice siempre está en el texto de al lado. */
@Composable
fun Banana(mood: BananaMood, size: Dp, modifier: Modifier = Modifier) {
    Image(painter = painterResource(mood.drawable), contentDescription = null, modifier = modifier.size(size))
}

/** El banano sube y baja mientras lee. */
@Composable
fun BobbingBanana(mood: BananaMood, size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "banano")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(BOB_MS), RepeatMode.Reverse),
        label = "banano",
    )
    Banana(mood, size, modifier.offset(y = lift.dp))
}

private const val BOB_MS = 600

/** Etiqueta del resultado de un veredicto: siempre con la palabra, no solo con el color. */
@Composable
fun StatusTag(text: String, approved: Boolean, modifier: Modifier = Modifier, small: Boolean = false) {
    val colors = LaVidaTheme.colors
    Text(
        text = text,
        modifier = modifier
            .background(if (approved) colors.approved else colors.rejected, CircleShape)
            .border(if (small) 1.5.dp else 2.dp, colors.border, CircleShape)
            .padding(horizontal = if (small) 9.dp else 12.dp, vertical = if (small) 3.dp else 6.dp),
        style = LaVidaTheme.type.mono.copy(fontWeight = FontWeight.SemiBold),
        color = colors.surface,
    )
}

/** Pastilla con borde: quién, cuándo y cuántos en el bloqueo. */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Text(
        text = text,
        modifier = modifier
            .background(colors.surface, CircleShape)
            .border(2.dp, colors.border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = LaVidaTheme.type.mono.copy(fontWeight = FontWeight.Medium),
        color = colors.textPrimary,
    )
}

/** Dos pestañas con subrayado amarillo. */
@Composable
fun UnderlineTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(colors.border))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = LaVidaTheme.type.body.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    if (on) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(colors.accent))
                    }
                }
            }
        }
    }
}

/** Dos opciones en una píldora. */
@Composable
fun SegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, CircleShape)
            .border(2.dp, colors.border, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .then(if (on) Modifier.background(colors.accent, CircleShape).border(2.dp, colors.border, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = LaVidaTheme.type.body.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(if (selected) colors.accent else colors.surface, CircleShape)
            .border(2.dp, colors.border, CircleShape)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LaVidaTheme.type.small.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = colors.textPrimary,
        )
    }
}

enum class MainTab { HOME, HISTORY, SETTINGS }

/** Inicio, Historial y Ajustes: el contenido arriba, la barra siempre visible abajo. */
@Composable
fun MainScaffold(tab: MainTab, onTab: (MainTab) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NavItem(R.drawable.ic_ui_home, stringResource(R.string.nav_home), tab == MainTab.HOME, { onTab(MainTab.HOME) })
            NavItem(R.drawable.ic_ui_history, stringResource(R.string.nav_history), tab == MainTab.HISTORY, { onTab(MainTab.HISTORY) })
            NavItem(R.drawable.ic_ui_settings, stringResource(R.string.nav_settings), tab == MainTab.SETTINGS, { onTab(MainTab.SETTINGS) })
        }
    }
}

@Composable
private fun RowScope.NavItem(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .then(if (selected) Modifier.background(colors.accent, CircleShape).border(2.dp, colors.border, CircleShape) else Modifier)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        UiIcon(icon)
        Text(
            text = label,
            style = LaVidaTheme.type.caption.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = colors.textPrimary,
        )
    }
}

/** Fondo y márgenes seguros de una pantalla sin barra inferior. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LaVidaTheme.colors.background)
            .safeDrawingPadding(),
        content = content,
    )
}

/** Fila "etiqueta  valor" en mono, para paneles de datos. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = LaVidaTheme.type.monoBody,
            color = colors.textSecondary,
        )
        Text(text = value, style = LaVidaTheme.type.monoBody, color = colors.textPrimary)
    }
}

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = LaVidaTheme.type.monoLabel,
        color = LaVidaTheme.colors.textMuted,
    )
}

/** Estado mientras algo tarda: una barra en movimiento, sin spinner. */
@Composable
fun ProgressBar(modifier: Modifier = Modifier) {
    val colors = LaVidaTheme.colors
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .border(2.dp, colors.border, CircleShape)
            .clip(CircleShape),
        color = colors.accent,
        trackColor = colors.surface,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
    )
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

/** Abre la política de tratamiento de datos en el navegador. */
@Composable
fun PolicyLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.policy_url)
    TextLink(
        text = stringResource(R.string.policy_link),
        onClick = { uriHandler.openUri(url) },
        modifier = modifier,
    )
}
