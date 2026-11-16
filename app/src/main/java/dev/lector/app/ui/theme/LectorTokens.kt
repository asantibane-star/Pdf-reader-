package dev.lector.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Regla de radios única de `docs/DESIGN.md` §2.3. */
object LectorShapes {
    /** Todo lo interactivo flotante: botones, barras, dock, chips. */
    val Pill = CircleShape
    val CardOuter = RoundedCornerShape(28.dp)
    val CardInner = RoundedCornerShape(22.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val SheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Tile = RoundedCornerShape(12.dp)
    val Field = RoundedCornerShape(12.dp)
    val Thumbnail = RoundedCornerShape(8.dp)
    val Page = RoundedCornerShape(4.dp)
    val Slide = RoundedCornerShape(6.dp)
    val SlideThumb = RoundedCornerShape(5.dp)
}

/** Espaciado en múltiplos de 4 dp y medidas fijas de la especificación. */
object LectorSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val screenMargin = 20.dp
    val group = 22.dp
    val xl = 24.dp
    val statePaneMargin = 28.dp
    val barMargin = 12.dp
    val barTopGap = 6.dp
    val barBottomGap = 22.dp
    val dockBottomGap = 22.dp
    val dockPadding = 6.dp
    val dockSpacing = 6.dp
    val listBottomForDock = 96.dp
    val pageGap = 12.dp
    val pageSideMargin = 14.dp
    val cardOuterPadding = 6.dp
    val cardInnerPadding = 14.dp
}

object LectorSizes {
    val rowMinHeight = 64.dp
    val touchTarget = 48.dp
    val iconButton = 44.dp
    val iconSize = 22.dp
    val iconSizeInButton = 20.dp
    val barHeight = 52.dp
    val buttonHeight = 52.dp
    val kindTile = 44.dp
    val thumbPortraitWidth = 66.dp
    val thumbPortraitHeight = 88.dp
    val thumbSlideWidth = 88.dp
    val thumbSlideHeight = 50.dp
    val maxContentWidth = 560.dp
    val scanCircle = 36.dp
    val progressLine = 3.dp
    val sliderTrack = 2.dp
    val sliderThumb = 16.dp
    val handleWidth = 32.dp
    val handleHeight = 4.dp
    val sheetRow = 56.dp
    val hairline = 1.dp
}
