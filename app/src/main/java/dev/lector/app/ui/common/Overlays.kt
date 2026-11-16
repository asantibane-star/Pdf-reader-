package dev.lector.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

/** Diálogo (DESIGN §4.5): `surface`, radio 28 dp, relleno 24 dp, acciones a la derecha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectorDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    confirm: @Composable () -> Unit,
    dismiss: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val c = LectorTheme.colors
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(modifier = modifier, shape = LectorShapes.Dialog, color = c.surface, tonalElevation = 0.dp) {
            Column(Modifier.padding(LectorSpacing.xl)) {
                Text(text = title, style = LectorTheme.type.title, color = c.text)
                if (body != null) {
                    Spacer(Modifier.height(LectorSpacing.m))
                    Text(text = body, style = LectorTheme.type.body, color = c.textMuted)
                }
                if (content != null) {
                    Spacer(Modifier.height(LectorSpacing.l))
                    content()
                }
                Spacer(Modifier.height(LectorSpacing.xl))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LectorSpacing.s, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismiss?.invoke()
                    confirm()
                }
            }
        }
    }
}

/** Hoja inferior (DESIGN §4.5): radio superior 28 dp, asa de 32 × 4 dp. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectorSheet(onDismiss: () -> Unit, dismissible: Boolean = true, content: @Composable () -> Unit) {
    val c = LectorTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target -> dismissible || target != SheetValue.Hidden },
        ),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
        shape = LectorShapes.SheetTop,
        containerColor = c.surface,
        contentColor = c.text,
        tonalElevation = 0.dp,
        scrimColor = c.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = LectorSpacing.m)
                    .width(LectorSizes.handleWidth)
                    .height(LectorSizes.handleHeight)
                    .clip(LectorShapes.Pill)
                    .background(c.textMuted.copy(alpha = HANDLE_ALPHA)),
            )
        },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = LectorSpacing.m)) { content() }
    }
}

@Composable
fun SheetRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LectorTheme.colors
    val tint = if (destructive) c.danger else c.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LectorSizes.sheetRow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LectorSpacing.screenMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LectorSpacing.l),
    ) {
        LectorIcon(icon, null, tint = tint)
        Text(text = label, style = LectorTheme.type.body, color = tint, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

/** Campo de texto (DESIGN §4.5): `sunken`, radio 12 dp, etiqueta encima, error debajo en `danger`. */
@Composable
fun LectorTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    requestFocus: Boolean = false,
) {
    val c = LectorTheme.colors
    val focus = remember { FocusRequester() }
    Column(modifier) {
        Text(text = label, style = LectorTheme.type.meta, color = c.textMuted)
        Spacer(Modifier.height(LectorSpacing.xs))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = LectorSizes.touchTarget)
                .clip(LectorShapes.Field)
                .background(c.sunken)
                .padding(horizontal = LectorSpacing.l),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LectorTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (error != null) {
            Spacer(Modifier.height(LectorSpacing.xs))
            Text(text = error, style = LectorTheme.type.meta, color = c.danger)
        }
    }
    if (requestFocus) {
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

@Composable
fun LectorSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LectorTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = c.textMuted,
            uncheckedTrackColor = c.sunken,
            uncheckedBorderColor = c.line,
        ),
    )
}

/** Control segmentado de píldoras (Tema: Sistema / Claro / Oscuro). */
@Composable
fun SegmentedPills(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LectorTheme.colors
    Row(
        modifier = modifier
            .clip(LectorShapes.Pill)
            .background(c.sunken)
            .padding(LectorSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LectorSpacing.xs),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = LectorSizes.touchTarget - LectorSpacing.s)
                    .clip(LectorShapes.Pill)
                    .background(if (selected) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .semantics { role = Role.RadioButton; this.selected = selected }
                    .clickable(onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = LectorTheme.type.button,
                    color = if (selected) c.onAccentSoft else c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = LectorSpacing.m),
                )
            }
        }
    }
}

/** Avisos temporales: píldora `text` sobre `bg` invertido (DESIGN §4.5). */
@Composable
fun LectorSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state, modifier = modifier) { data -> LectorSnackbar(data) }
}

@Composable
private fun LectorSnackbar(data: SnackbarData) {
    val c = LectorTheme.colors
    Surface(
        modifier = Modifier.padding(horizontal = LectorSpacing.barMargin),
        shape = LectorShapes.Pill,
        color = c.text,
        contentColor = c.bg,
    ) {
        Row(
            modifier = Modifier.padding(start = LectorSpacing.xl, end = LectorSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LectorSpacing.s),
        ) {
            Text(
                text = data.visuals.message,
                style = LectorTheme.type.body,
                color = c.bg,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = LectorSpacing.m),
            )
            val action = data.visuals.actionLabel
            if (action != null) {
                Box(
                    Modifier
                        .heightIn(min = LectorSizes.touchTarget)
                        .clip(LectorShapes.Pill)
                        .clickable(role = Role.Button) { data.performAction() }
                        .padding(horizontal = LectorSpacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = action, style = LectorTheme.type.button, color = c.accentSoft)
                }
            }
        }
    }
}

private const val HANDLE_ALPHA = 0.4f
