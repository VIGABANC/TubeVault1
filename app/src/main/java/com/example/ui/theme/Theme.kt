package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightThemeColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = LightSurface,
    onPrimaryContainer = PrimaryBlue,
    secondary = TealAccent,
    onSecondary = Color.White,
    secondaryContainer = LightSurface,
    onSecondaryContainer = PrimaryText,
    tertiary = TealAccent,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = PrimaryText,
    surface = LightSurface,
    onSurface = PrimaryText,
    surfaceVariant = LightSurface,
    onSurfaceVariant = SecondaryText,
    outline = DividerLight,
    outlineVariant = DividerLight
)

private val DarkThemeColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = DarkPrimaryText,
    primaryContainer = DarkSurface,
    onPrimaryContainer = TealAccent,
    secondary = TealAccent,
    onSecondary = DarkBg,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkPrimaryText,
    tertiary = TealAccent,
    onTertiary = DarkBg,
    background = DarkBg,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkSecondaryText,
    outline = DividerDark,
    outlineVariant = DividerDark
)

private val OledThemeColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = DarkPrimaryText,
    primaryContainer = DarkSurface,
    onPrimaryContainer = TealAccent,
    secondary = TealAccent,
    onSecondary = OledBg,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkPrimaryText,
    tertiary = TealAccent,
    onTertiary = OledBg,
    background = OledBg,
    onBackground = DarkPrimaryText,
    surface = OledBg,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkSecondaryText,
    outline = DividerDark,
    outlineVariant = DividerDark
)

@Composable
fun TubeVaultTheme(
    themeMode: String = "System",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        "Light" -> LightThemeColorScheme
        "Dark" -> DarkThemeColorScheme
        "OLED" -> OledThemeColorScheme
        else -> if (isSystemDark) DarkThemeColorScheme else LightThemeColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

