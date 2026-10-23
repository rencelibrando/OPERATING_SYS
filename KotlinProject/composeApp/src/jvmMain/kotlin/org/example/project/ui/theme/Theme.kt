package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeController {
    var isDarkTheme by mutableStateOf(false)

    fun toggle() {
        isDarkTheme = !isDarkTheme
        WordBridgeColors.currentPalette = if (isDarkTheme) DarkWordBridgePalette else LightWordBridgePalette
    }

    fun setDarkMode(dark: Boolean) {
        isDarkTheme = dark
        WordBridgeColors.currentPalette = if (dark) DarkWordBridgePalette else LightWordBridgePalette
    }
}

private val LightColorScheme =
    lightColorScheme(
        primary = LightWordBridgePalette.primary,
        onPrimary = LightWordBridgePalette.onPrimary,
        primaryContainer = LightWordBridgePalette.primaryLight,
        onPrimaryContainer = LightWordBridgePalette.textPrimary,
        secondary = LightWordBridgePalette.accentBlue,
        onSecondary = LightWordBridgePalette.surface,
        tertiary = LightWordBridgePalette.accentGreen,
        onTertiary = LightWordBridgePalette.surface,
        background = LightWordBridgePalette.background,
        onBackground = LightWordBridgePalette.textPrimary,
        surface = LightWordBridgePalette.surface,
        onSurface = LightWordBridgePalette.textPrimary,
        surfaceVariant = LightWordBridgePalette.surfaceVariant,
        onSurfaceVariant = LightWordBridgePalette.textSecondary,
        outline = LightWordBridgePalette.border,
        outlineVariant = LightWordBridgePalette.borderSubtle,
        error = LightWordBridgePalette.accentRed,
        onError = LightWordBridgePalette.surface,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkWordBridgePalette.primary,
        onPrimary = DarkWordBridgePalette.onPrimary,
        primaryContainer = DarkWordBridgePalette.primaryDark,
        onPrimaryContainer = DarkWordBridgePalette.textPrimary,
        secondary = DarkWordBridgePalette.accentBlue,
        onSecondary = DarkWordBridgePalette.surface,
        tertiary = DarkWordBridgePalette.accentGreen,
        onTertiary = DarkWordBridgePalette.surface,
        background = DarkWordBridgePalette.background,
        onBackground = DarkWordBridgePalette.textPrimary,
        surface = DarkWordBridgePalette.surface,
        onSurface = DarkWordBridgePalette.textPrimary,
        surfaceVariant = DarkWordBridgePalette.surfaceVariant,
        onSurfaceVariant = DarkWordBridgePalette.textSecondary,
        outline = DarkWordBridgePalette.border,
        outlineVariant = DarkWordBridgePalette.borderSubtle,
        error = DarkWordBridgePalette.accentRed,
        onError = DarkWordBridgePalette.onPrimary,
    )

@Composable
fun WordBridgeTheme(
    darkTheme: Boolean = ThemeController.isDarkTheme,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkWordBridgePalette else LightWordBridgePalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    SideEffect {
        if (WordBridgeColors.currentPalette != palette) {
            WordBridgeColors.currentPalette = palette
        }
    }

    CompositionLocalProvider(
        LocalWordBridgeColors provides palette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WordBridgeTypography,
            content = content,
        )
    }
}
