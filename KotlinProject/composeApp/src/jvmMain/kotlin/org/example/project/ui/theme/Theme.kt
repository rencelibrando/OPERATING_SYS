package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
    lightColorScheme(
        primary = WordBridgeColors.PrimaryPurple,
        onPrimary = WordBridgeColors.BackgroundWhite,
        primaryContainer = WordBridgeColors.PrimaryPurpleLight,
        onPrimaryContainer = WordBridgeColors.TextPrimary,
        secondary = WordBridgeColors.AccentBlue,
        onSecondary = WordBridgeColors.BackgroundWhite,
        tertiary = WordBridgeColors.AccentGreen,
        onTertiary = WordBridgeColors.BackgroundWhite,
        background = WordBridgeColors.BackgroundLight,
        onBackground = WordBridgeColors.TextPrimary,
        surface = WordBridgeColors.BackgroundWhite,
        onSurface = WordBridgeColors.TextPrimary,
        surfaceVariant = WordBridgeColors.BackgroundLight,
        onSurfaceVariant = WordBridgeColors.TextSecondary,
        outline = WordBridgeColors.TextMuted,
        error = WordBridgeColors.AccentRed,
        onError = WordBridgeColors.BackgroundWhite,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = WordBridgeColors.PrimaryPurpleLight,
        onPrimary = WordBridgeColors.SidebarBackgroundDark,
        primaryContainer = WordBridgeColors.PrimaryPurpleDark,
        onPrimaryContainer = WordBridgeColors.SidebarText,
        secondary = WordBridgeColors.AccentBlue,
        onSecondary = WordBridgeColors.SidebarBackgroundDark,
        tertiary = WordBridgeColors.AccentGreen,
        onTertiary = WordBridgeColors.SidebarBackgroundDark,
        background = WordBridgeColors.SidebarBackgroundDark,
        onBackground = WordBridgeColors.SidebarText,
        surface = WordBridgeColors.SidebarBackground,
        onSurface = WordBridgeColors.SidebarText,
        surfaceVariant = WordBridgeColors.SidebarBackground,
        onSurfaceVariant = WordBridgeColors.SidebarTextSecondary,
        outline = WordBridgeColors.TextMuted,
        error = WordBridgeColors.AccentRed,
        onError = WordBridgeColors.BackgroundWhite,
    )

@Composable
fun WordBridgeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (darkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WordBridgeTypography,
        content = content,
    )
}
