package dev.lector.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import dev.lector.app.R
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

/** La barra superior se transforma en un campo con contador "3 de 17" y flechas (DESIGN §6.2). */
@Composable
fun SearchBar(
    query: String,
    current: Int,
    total: Int,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LectorTheme.colors
    var field by remember { mutableStateOf(TextFieldValue(query)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    FloatingPill(modifier = modifier.fillMaxWidth().height(LectorSizes.barHeight)) {
        IconCircleButton(R.drawable.ic_arrow_left, stringResource(R.string.viewer_search_close), onClose)
        Box(Modifier.weight(1f).padding(horizontal = LectorSpacing.xs), contentAlignment = Alignment.CenterStart) {
            if (field.text.isEmpty()) {
                Text(stringResource(R.string.viewer_search_hint), style = LectorTheme.type.body, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = field,
                onValueChange = { field = it; onQueryChange(it.text) },
                singleLine = true,
                textStyle = LectorTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        val status = when {
            query.isBlank() -> ""
            total > 0 -> stringResource(R.string.viewer_search_count, current + 1, total)
            searching -> ""
            else -> stringResource(R.string.viewer_search_none)
        }
        if (status.isNotEmpty()) {
            Text(status, style = LectorTheme.type.mono, color = c.textMuted, maxLines = 1, modifier = Modifier.padding(horizontal = LectorSpacing.xs))
        }
        IconCircleButton(R.drawable.ic_caret_up, stringResource(R.string.viewer_search_previous), onPrevious, enabled = total > 0)
        IconCircleButton(R.drawable.ic_caret_down, stringResource(R.string.viewer_search_next), onNext, enabled = total > 0)
    }
}
