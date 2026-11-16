package dev.lector.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxHeight
import dev.lector.app.R
import dev.lector.app.domain.model.DocumentFamily
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.domain.model.RecentDocument
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

/** Mosaico con el monograma del tipo (`PDF`, `PPT`, `DOC`, `XLS`, `TXT`). Los tipos no se distinguen por color. */
@Composable
fun KindTile(kind: DocumentKind, modifier: Modifier = Modifier, width: Dp = LectorSizes.kindTile, height: Dp = LectorSizes.kindTile) {
    val c = LectorTheme.colors
    Box(
        modifier = modifier.size(width, height).clip(LectorShapes.Tile).background(c.sunken),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = kind.monogram, style = LectorTheme.type.monogram, color = c.textMuted, maxLines = 1)
    }
}

/** Tarjeta destacada con doble marco (DESIGN §4.2). */
@Composable
fun ContinueCard(
    recent: RecentDocument,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LectorTheme.colors
    val isSlide = recent.kind.family == DocumentFamily.Presentation
    val thumbWidth = if (isSlide) LectorSizes.thumbSlideWidth else LectorSizes.thumbPortraitWidth
    val thumbHeight = if (isSlide) LectorSizes.thumbSlideHeight else LectorSizes.thumbPortraitHeight
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .scaledBy(scale)
            .clip(LectorShapes.CardOuter)
            .background(c.sunken)
            .lineBorder(LectorShapes.CardOuter, c.line)
            .combinedClickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(LectorSpacing.cardOuterPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .floatingShadow(LectorShapes.CardInner, c.shadow)
                .clip(LectorShapes.CardInner)
                .background(c.surface)
                .padding(LectorSpacing.cardInnerPadding),
            horizontalArrangement = Arrangement.spacedBy(LectorSpacing.l),
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(thumbWidth, thumbHeight).clip(LectorShapes.Thumbnail),
                )
            } else {
                KindTile(recent.kind, width = thumbWidth, height = thumbHeight)
            }
            Column(modifier = Modifier.weight(1f).heightIn(min = thumbHeight), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = recent.displayName,
                        style = LectorTheme.type.itemPrimary,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (recent.pageCount > 0) {
                        Text(
                            text = stringResource(R.string.continue_page, recent.lastPage + 1, recent.pageCount),
                            style = LectorTheme.type.meta,
                            color = c.textMuted,
                            maxLines = 1,
                        )
                    }
                }
                if (recent.pageCount > 0) {
                    val fraction = ((recent.lastPage + 1).toFloat() / recent.pageCount).coerceIn(0f, 1f)
                    // Línea de progreso de 3 dp en accent, sin pista de fondo.
                    Spacer(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(LectorSizes.progressLine)
                            .clip(LectorShapes.Pill)
                            .background(c.accent),
                    )
                }
            }
        }
    }
}

/** Fila de la lista de recientes (DESIGN §4.3): mosaico, nombre sin extensión y "Hace 2 h · 8,3 MB". */
@Composable
fun RecentRow(
    recent: RecentDocument,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LectorTheme.colors
    val context = LocalContext.current
    val moreActions = stringResource(R.string.a11y_recent_actions)
    val meta = stringResource(R.string.meta_separator, relativeTime(recent.lastOpenedAt, now), fileSizeText(context, recent.sizeBytes))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LectorSizes.rowMinHeight)
            .clip(LectorShapes.Tile)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClickLabel = moreActions, onLongClick = onLongClick)
            .padding(vertical = LectorSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LectorSpacing.m),
    ) {
        KindTile(recent.kind)
        Column(Modifier.weight(1f)) {
            Text(text = recent.displayName, style = LectorTheme.type.item, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = meta, style = LectorTheme.type.meta, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Desliza a la izquierda para quitar la entrada (con "Deshacer" en el aviso). */
@Composable
fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove()
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = { Box(Modifier.fillMaxWidth().fillMaxHeight()) },
        content = { content() },
    )
}
