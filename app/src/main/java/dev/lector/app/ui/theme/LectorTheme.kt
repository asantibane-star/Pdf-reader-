package dev.lector.app.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import dev.lector.app.domain.model.ThemeMode

/** Si el sistema tiene las animaciones desactivadas, no hay movimiento (DESIGN §3). */
val LocalMotionEnabled = compositionLocalOf { true }

object LectorTheme {
    val colors: LectorColors @Composable get() = LocalLectorColors.current
    val type: LectorType @Composable get() = LocalLectorType.current
}

/**
 * Tema propio completo sobre Material 3. El color dinámico (opcional, apagado por defecto)
 * solo sustituye los tokens `accent*`.
 */
@Composable
fun LectorTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = forceDark || when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val base = if (dark) DarkLectorColors else LightLectorColors
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dyn = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        LectorColors(
            isDark = base.isDark, bg = base.bg, surface = base.surface, sunken = base.sunken, canvas = base.canvas,
            text = base.text, textMuted = base.textMuted, line = base.line,
            accent = dyn.primary, onAccent = dyn.onPrimary, accentSoft = dyn.primaryContainer,
            onAccentSoft = dyn.onPrimaryContainer, danger = base.danger, scrim = base.scrim, shadow = base.shadow,
        )
    } else {
        base
    }

    val motionEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, dark) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val previousStatus = controller?.isAppearanceLightStatusBars
            val previousNavigation = controller?.isAppearanceLightNavigationBars
            controller?.isAppearanceLightStatusBars = !dark
            controller?.isAppearanceLightNavigationBars = !dark
            onDispose {
                // Una pantalla con tema forzado devuelve las barras del sistema a como estaban.
                if (forceDark && controller != null) {
                    previousStatus?.let { controller.isAppearanceLightStatusBars = it }
                    previousNavigation?.let { controller.isAppearanceLightNavigationBars = it }
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalLectorColors provides colors,
        LocalLectorType provides DefaultLectorType,
        LocalMotionEnabled provides motionEnabled,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = DefaultLectorType.toMaterial(),
            content = content,
        )
    }
}

private fun LectorColors.toMaterial(): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accentSoft,
    onPrimaryContainer = onAccentSoft,
    inversePrimary = accent,
    // secondary y tertiary se igualan a textMuted/accent: ningún componente saca un color ajeno.
    secondary = textMuted,
    onSecondary = bg,
    secondaryContainer = accentSoft,
    onSecondaryContainer = onAccentSoft,
    tertiary = accent,
    onTertiary = onAccent,
    tertiaryContainer = accentSoft,
    onTertiaryContainer = onAccentSoft,
    background = bg,
    onBackground = text,
    surface = bg,
    onSurface = text,
    surfaceVariant = sunken,
    onSurfaceVariant = textMuted,
    surfaceTint = Color.Transparent, // sin tonalElevation: las superficies nunca se tiñen
    inverseSurface = text,
    inverseOnSurface = bg,
    error = danger,
    onError = bg,
    errorContainer = sunken,
    onErrorContainer = danger,
    outline = textMuted,
    outlineVariant = line,
    scrim = scrim,
    surfaceBright = bg,
    surfaceDim = bg,
    surfaceContainerLowest = surface,
    surfaceContainerLow = surface,
    surfaceContainer = surface,
    surfaceContainerHigh = sunken,
    surfaceContainerHighest = sunken,
)

private fun LectorType.toMaterial(): Typography = Typography(
    displayLarge = display, displayMedium = display, displaySmall = displayReduced,
    headlineLarge = title, headlineMedium = title, headlineSmall = title,
    titleLarge = title, titleMedium = item, titleSmall = item,
    bodyLarge = body, bodyMedium = body, bodySmall = meta,
    labelLarge = button, labelMedium = meta, labelSmall = meta,
)
