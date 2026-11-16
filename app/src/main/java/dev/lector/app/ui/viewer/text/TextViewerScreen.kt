package dev.lector.app.ui.viewer.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lector.app.R
import dev.lector.app.ui.common.FloatingTopBar
import dev.lector.app.ui.common.IconCircleButton
import dev.lector.app.ui.common.LectorSheet
import dev.lector.app.ui.common.SheetRow
import dev.lector.app.ui.common.SkeletonBox
import dev.lector.app.ui.common.ErrorIllustration
import dev.lector.app.ui.common.StatePane
import dev.lector.app.ui.common.messageRes
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

@Composable
fun TextViewerScreen(viewModel: TextViewerViewModel, onBack: () -> Unit, onOpenWithOther: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val step by viewModel.sizeStep.collectAsStateWithLifecycle()
    val c = LectorTheme.colors
    var showSize by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        when (val current = state) {
            TextUiState.Loading -> SkeletonBox(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = LectorSizes.barHeight + LectorSpacing.xl, start = LectorSpacing.screenMargin, end = LectorSpacing.screenMargin, bottom = LectorSpacing.xl),
            )
            is TextUiState.Content -> TextLines(current.lines, step)
            is TextUiState.Error -> StatePane(
                title = stringResource(R.string.err_title),
                illustration = { ErrorIllustration() },
                body = stringResource(current.error.messageRes()),
                primaryLabel = stringResource(R.string.action_retry),
                onPrimary = viewModel::load,
                secondaryLabel = stringResource(R.string.action_open_with),
                onSecondary = onOpenWithOther,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
        FloatingTopBar(
            title = viewModel.title,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = LectorSpacing.barTopGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin),
        ) {
            if (state is TextUiState.Content) {
                IconCircleButton(R.drawable.ic_text_aa, stringResource(R.string.viewer_text_size), { showSize = true })
            }
        }
    }

    if (showSize) {
        LectorSheet(onDismiss = { showSize = false }) {
            SheetRow(R.drawable.ic_plus, stringResource(R.string.viewer_text_larger), viewModel::larger)
            SheetRow(R.drawable.ic_minus, stringResource(R.string.viewer_text_smaller), viewModel::smaller)
        }
    }
}

@Composable
private fun TextLines(lines: List<String>, step: Int) {
    val c = LectorTheme.colors
    val sizeSp = TextViewerViewModel.SIZES_SP[step.coerceIn(0, TextViewerViewModel.SIZES_SP.lastIndex)]
    val reading = LectorTheme.type.reading
    val style: TextStyle = reading.copy(
        color = c.text,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * READING_LINE_RATIO).sp,
    )
    // Ancho máximo de 68 caracteres.
    val maxWidth = with(LocalDensity.current) { (sizeSp.sp.toPx() * CHAR_WIDTH_EM * MAX_CHARS).toDp() }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        SelectionContainer(Modifier.widthIn(max = maxWidth)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = LectorSizes.barHeight + LectorSpacing.barTopGap + LectorSpacing.xl,
                    bottom = LectorSpacing.xl,
                    start = LectorSpacing.screenMargin,
                    end = LectorSpacing.screenMargin,
                ),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(text = line.ifEmpty { " " }, style = style)
                }
            }
        }
    }
}

private const val READING_LINE_RATIO = 26f / 16f
private const val CHAR_WIDTH_EM = 0.56f
private const val MAX_CHARS = 68
