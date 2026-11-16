package dev.lector.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

@Composable
fun LectorIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LectorTheme.colors.text,
    size: Dp = LectorSizes.iconSize,
) {
    Icon(painter = painterResource(id), contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}

/** Base de todos los botones de píldora (DESIGN §4.5). Etiquetas de una línea. */
@Composable
private fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier,
    container: Color,
    content: Color,
    border: BorderStroke?,
    enabled: Boolean,
    padding: PaddingValues,
    row: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = LectorSizes.buttonHeight)
            .scaledBy(scale)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        shape = LectorShapes.Pill,
        color = container,
        contentColor = content,
        border = border,
        interactionSource = source,
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = LectorSizes.buttonHeight).padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LectorSpacing.s, Alignment.CenterHorizontally),
        ) {
            row()
        }
    }
}

@Composable
private fun Label(text: String, color: Color) {
    Text(text = text, style = LectorTheme.type.button, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val c = LectorTheme.colors
    PillButton(onClick, modifier, c.accent, c.onAccent, null, enabled, PaddingValues(horizontal = LectorSpacing.xl)) {
        if (icon != null) LectorIcon(icon, null, tint = c.onAccent, size = LectorSizes.iconSizeInButton)
        Label(text, c.onAccent)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val c = LectorTheme.colors
    PillButton(
        onClick, modifier, Color.Transparent, c.text, BorderStroke(LectorSizes.hairline, c.line), enabled,
        PaddingValues(horizontal = LectorSpacing.xl),
    ) {
        if (icon != null) LectorIcon(icon, null, tint = c.text, size = LectorSizes.iconSizeInButton)
        Label(text, c.text)
    }
}

/** Terciario: solo texto `accent`. */
@Composable
fun TertiaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LectorTheme.colors
    PillButton(onClick, modifier, Color.Transparent, c.accent, null, enabled, PaddingValues(horizontal = LectorSpacing.l)) {
        Label(text, c.accent)
    }
}

/** Destructivo: texto `danger`. */
@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LectorTheme.colors
    PillButton(onClick, modifier, Color.Transparent, c.danger, null, enabled, PaddingValues(horizontal = LectorSpacing.l)) {
        Label(text, c.danger)
    }
}

/** Botón de texto con icono, sin fondo (el "Abrir" del dock). */
@Composable
fun TextIconButton(text: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LectorTheme.colors
    PillButton(onClick, modifier, Color.Transparent, c.text, null, true, PaddingValues(horizontal = LectorSpacing.l)) {
        LectorIcon(icon, null, tint = c.text)
        Label(text, c.text)
    }
}

/**
 * Botón de icono redondo: dibuja 44 dp y amplía su área táctil a 48 dp.
 * Con [outlined] lleva fondo `surface` y contorno `line` (ajustes en Inicio).
 */
@Composable
fun IconCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    tint: Color = LectorTheme.colors.text,
    enabled: Boolean = true,
) {
    val c = LectorTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Box(
        modifier = modifier
            .size(LectorSizes.touchTarget)
            .scaledBy(scale)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { this.contentDescription = contentDescription; role = Role.Button }
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LectorSizes.iconButton)
                .let { if (outlined) it.drawBehind { drawCircle(c.surface) }.lineBorder(CircleShape, c.line) else it },
            contentAlignment = Alignment.Center,
        ) {
            LectorIcon(icon, null, tint = tint)
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
