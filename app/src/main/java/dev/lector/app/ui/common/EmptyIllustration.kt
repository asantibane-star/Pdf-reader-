package dev.lector.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorTheme

/** Dos hojas superpuestas dibujadas con formas (DESIGN §6.1): una en `sunken` girada 8°, otra en `surface` girada -4° con una raya `accent`. */
@Composable
fun EmptyIllustration(modifier: Modifier = Modifier) {
    val c = LectorTheme.colors
    Box(modifier.size(width = 148.dp, height = 140.dp)) {
        Box(
            Modifier
                .offset(x = 44.dp, y = 0.dp)
                .size(width = 96.dp, height = 124.dp)
                .rotate(8f)
                .clip(LectorShapes.Tile)
                .background(c.sunken)
                .lineBorder(LectorShapes.Tile, c.line),
        )
        Column(
            Modifier
                .offset(x = 8.dp, y = 14.dp)
                .size(width = 96.dp, height = 124.dp)
                .rotate(-4f)
                .floatingShadow(LectorShapes.Tile, c.shadow)
                .clip(LectorShapes.Tile)
                .background(c.surface)
                .lineBorder(LectorShapes.Tile, c.line)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(28.dp).height(4.dp).clip(LectorShapes.Pill).background(c.accent))
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(LectorShapes.Pill).background(c.sunken))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(LectorShapes.Pill).background(c.sunken))
            Box(Modifier.width(44.dp).height(4.dp).clip(LectorShapes.Pill).background(c.sunken))
        }
    }
}
