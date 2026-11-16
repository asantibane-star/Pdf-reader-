package dev.lector.app.ui.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.lector.app.R
import dev.lector.app.ui.common.DestructiveButton
import dev.lector.app.ui.common.KindTile
import dev.lector.app.ui.common.LectorDialog
import dev.lector.app.ui.common.LectorSheet
import dev.lector.app.ui.common.LectorTextField
import dev.lector.app.ui.common.PrimaryButton
import dev.lector.app.ui.common.SecondaryButton
import dev.lector.app.ui.common.TertiaryButton
import dev.lector.app.ui.common.messageRes
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

/** Hoja "Nombrar" (DESIGN §6.6): miniatura, "3 páginas", campo "Nombre" prellenado y seleccionado, Guardar / Descartar. */
@Composable
fun NameScanSheet(state: ScanState.Naming, onSave: (String) -> Unit, onDiscard: () -> Unit) {
    val c = LectorTheme.colors
    var name by rememberSaveable(state.pending.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.suggestedName, selection = TextRange(0, state.suggestedName.length)))
    }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // No se cierra con un gesto: el pendiente solo se guarda o se descarta de forma explícita.
    LectorSheet(onDismiss = {}, dismissible = false) {
        Column(Modifier.padding(horizontal = LectorSpacing.screenMargin), verticalArrangement = Arrangement.spacedBy(LectorSpacing.l)) {
            Text(stringResource(R.string.scan_name_title), style = LectorTheme.type.title, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(LectorSpacing.l), verticalAlignment = Alignment.CenterVertically) {
                val thumbnail = state.thumbnail
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(LectorSizes.thumbPortraitWidth, LectorSizes.thumbPortraitHeight).clip(LectorShapes.Thumbnail),
                    )
                } else {
                    KindTile(dev.lector.app.domain.model.DocumentKind.Pdf, width = LectorSizes.thumbPortraitWidth, height = LectorSizes.thumbPortraitHeight)
                }
                Text(
                    pluralStringResource(R.plurals.scan_pages, state.pending.pageCount, state.pending.pageCount),
                    style = LectorTheme.type.meta,
                    color = c.textMuted,
                )
            }
            LectorTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.scan_name_label),
                modifier = Modifier.fillMaxWidth(),
                requestFocus = true,
            )
            Column(verticalArrangement = Arrangement.spacedBy(LectorSpacing.xs)) {
                PrimaryButton(stringResource(R.string.scan_save), onClick = { onSave(name.text) }, modifier = Modifier.fillMaxWidth())
                DestructiveButton(stringResource(R.string.scan_discard), onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(LectorSpacing.s))
        }
    }
    if (confirmDiscard) {
        LectorDialog(
            onDismiss = { confirmDiscard = false },
            title = stringResource(R.string.scan_discard_title),
            body = stringResource(R.string.scan_discard_body),
            confirm = { DestructiveButton(stringResource(R.string.scan_discard), onClick = { confirmDiscard = false; onDiscard() }) },
            dismiss = { TertiaryButton(stringResource(R.string.action_cancel), onClick = { confirmDiscard = false }) },
        )
    }
}

/** El guardado falló: se conserva el pendiente; Reintentar o Guardar en otra ubicación. */
@Composable
fun SaveFailedDialog(state: ScanState.SaveFailed, onRetry: () -> Unit, onElsewhere: () -> Unit, onDiscard: () -> Unit) {
    LectorDialog(
        onDismiss = {},
        title = stringResource(R.string.err_save),
        body = stringResource(state.error.messageRes()),
        confirm = { PrimaryButton(stringResource(R.string.action_retry), onClick = onRetry) },
        dismiss = {
            Row(horizontalArrangement = Arrangement.spacedBy(LectorSpacing.xs)) {
                TertiaryButton(stringResource(R.string.scan_discard), onClick = onDiscard)
                SecondaryButton(stringResource(R.string.scan_save_elsewhere), onClick = onElsewhere)
            }
        },
    )
}

/** Escaneo huérfano de una sesión anterior: guardarlo o descartarlo. */
@Composable
fun OrphanScanDialog(onSave: () -> Unit, onDiscard: () -> Unit) {
    LectorDialog(
        onDismiss = {},
        title = stringResource(R.string.scan_pending_title),
        body = stringResource(R.string.scan_pending_body),
        confirm = { PrimaryButton(stringResource(R.string.scan_save), onClick = onSave) },
        dismiss = { DestructiveButton(stringResource(R.string.scan_discard), onClick = onDiscard) },
    )
}
