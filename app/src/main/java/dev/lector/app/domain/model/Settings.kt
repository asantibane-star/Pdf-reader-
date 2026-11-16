package dev.lector.app.domain.model

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val keepScreenOn: Boolean = false,
    val hideInRecents: Boolean = false,
)
