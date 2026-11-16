package dev.lector.app.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lector.app.BuildConfig
import dev.lector.app.R
import dev.lector.app.domain.model.ThemeMode
import dev.lector.app.ui.common.DestructiveButton
import dev.lector.app.ui.common.IconCircleButton
import dev.lector.app.ui.common.LectorDialog
import dev.lector.app.ui.common.LectorSwitch
import dev.lector.app.ui.common.SegmentedPills
import dev.lector.app.ui.common.TertiaryButton
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLicenses: () -> Unit,
    onLastError: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val c = LectorTheme.colors
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = LectorSizes.maxContentWidth).fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = LectorSpacing.s, vertical = LectorSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircleButton(R.drawable.ic_arrow_left, stringResource(R.string.viewer_back), onBack)
                Text(stringResource(R.string.settings_title), style = LectorTheme.type.title, color = c.text, modifier = Modifier.padding(start = LectorSpacing.xs))
            }

            GroupLabel(stringResource(R.string.settings_group_appearance))
            Column(Modifier.padding(horizontal = LectorSpacing.screenMargin), verticalArrangement = Arrangement.spacedBy(LectorSpacing.s)) {
                Text(stringResource(R.string.settings_theme), style = LectorTheme.type.body, color = c.text)
                SegmentedPills(
                    options = listOf(
                        stringResource(R.string.settings_theme_system),
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_dark),
                    ),
                    selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                    onSelect = { index -> viewModel.update { it.copy(themeMode = ThemeMode.entries[index]) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(stringResource(R.string.settings_dynamic_color), settings.dynamicColor) { value ->
                    viewModel.update { it.copy(dynamicColor = value) }
                }
            }

            GroupLabel(stringResource(R.string.settings_group_reading))
            SwitchRow(stringResource(R.string.settings_keep_screen_on), settings.keepScreenOn) { value ->
                viewModel.update { it.copy(keepScreenOn = value) }
            }
            SwitchRow(stringResource(R.string.settings_hide_in_recents), settings.hideInRecents) { value ->
                viewModel.update { it.copy(hideInRecents = value) }
            }

            GroupLabel(stringResource(R.string.settings_group_data))
            ActionRow(stringResource(R.string.settings_clear_recents)) { confirmClear = true }

            GroupLabel(stringResource(R.string.settings_group_about))
            InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
            ActionRow(stringResource(R.string.settings_licenses), onClick = onLicenses)
            if (lastError != null) ActionRow(stringResource(R.string.settings_last_error), onClick = onLastError)
            Text(
                text = stringResource(R.string.settings_slides_note),
                style = LectorTheme.type.meta,
                color = c.textMuted,
                modifier = Modifier.padding(horizontal = LectorSpacing.screenMargin, vertical = LectorSpacing.s),
            )
            Text(
                text = stringResource(R.string.settings_privacy_note),
                style = LectorTheme.type.meta,
                color = c.textMuted,
                modifier = Modifier.padding(horizontal = LectorSpacing.screenMargin),
            )
            Spacer(Modifier.height(LectorSpacing.xl))
        }
    }

    if (confirmClear) {
        LectorDialog(
            onDismiss = { confirmClear = false },
            title = stringResource(R.string.settings_clear_recents_title),
            body = stringResource(R.string.settings_clear_recents_body),
            confirm = { DestructiveButton(stringResource(R.string.settings_clear_recents), onClick = { confirmClear = false; viewModel.clearRecents() }) },
            dismiss = { TertiaryButton(stringResource(R.string.action_cancel), onClick = { confirmClear = false }) },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = LectorTheme.type.metaGroup,
        color = LectorTheme.colors.textMuted,
        modifier = Modifier.padding(start = LectorSpacing.screenMargin, end = LectorSpacing.screenMargin, top = LectorSpacing.group, bottom = LectorSpacing.xs),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LectorSizes.rowMinHeight)
            .semantics(mergeDescendants = true) { role = Role.Switch; toggleableState = ToggleableState(checked) }
            .clickable { onChange(!checked) }
            .padding(horizontal = LectorSpacing.screenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LectorTheme.type.body, color = LectorTheme.colors.text, modifier = Modifier.weight(1f))
        LectorSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LectorSizes.rowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LectorSpacing.screenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LectorTheme.type.body, color = LectorTheme.colors.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = LectorSizes.rowMinHeight).padding(horizontal = LectorSpacing.screenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LectorTheme.type.body, color = LectorTheme.colors.text, modifier = Modifier.weight(1f))
        Text(value, style = LectorTheme.type.mono, color = LectorTheme.colors.textMuted)
    }
}
