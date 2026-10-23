package org.example.project.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class WordBridgePalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val onPrimary: Color,
    val cardBackground: Color,
    val cardShadow: Color,
    val sidebarBackground: Color,
    val sidebarBorder: Color,
    val sidebarText: Color,
    val sidebarTextSecondary: Color,
    val sidebarActiveItem: Color,
    val sidebarItemHover: Color,
    val accentBlue: Color,
    val accentGreen: Color,
    val accentOrange: Color,
    val accentRed: Color,
    val progressBackground: Color,
    val progressFill: Color,
    val streakFire: Color,
    val hoverOverlay: Color,
    val pressedOverlay: Color,
    val focusedBorder: Color,
)

// Standard white & slight white light mode palette (shadcn zinc light)
val LightWordBridgePalette =
    WordBridgePalette(
        isDark = false,
        background = Color(0xFFF8F9FA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF4F4F5),
        border = Color(0xFFE4E4E7),
        borderSubtle = Color(0xFFF4F4F5),
        textPrimary = Color(0xFF09090B),
        textSecondary = Color(0xFF71717A),
        textMuted = Color(0xFFA1A1AA),
        primary = Color(0xFF24B47E),
        primaryLight = Color(0xFF3ECF8E),
        primaryDark = Color(0xFF16A34A),
        onPrimary = Color(0xFFFFFFFF),
        cardBackground = Color(0xFFFFFFFF),
        cardShadow = Color(0x08000000),
        sidebarBackground = Color(0xFFFFFFFF),
        sidebarBorder = Color(0xFFE4E4E7),
        sidebarText = Color(0xFF09090B),
        sidebarTextSecondary = Color(0xFF71717A),
        sidebarActiveItem = Color(0xFFF4F4F5),
        sidebarItemHover = Color(0xFFFAFAFA),
        accentBlue = Color(0xFF2563EB),
        accentGreen = Color(0xFF24B47E),
        accentOrange = Color(0xFFD97706),
        accentRed = Color(0xFFDC2626),
        progressBackground = Color(0xFFE4E4E7),
        progressFill = Color(0xFF24B47E),
        streakFire = Color(0xFFEA580C),
        hoverOverlay = Color(0x0A000000),
        pressedOverlay = Color(0x14000000),
        focusedBorder = Color(0xFF24B47E),
    )

// Black and grey dark mode palette (shadcn zinc dark with Supabase green brand)
val DarkWordBridgePalette =
    WordBridgePalette(
        isDark = true,
        background = Color(0xFF09090B),
        surface = Color(0xFF18181B),
        surfaceVariant = Color(0xFF27272A),
        border = Color(0xFF27272A),
        borderSubtle = Color(0xFF1F1F23),
        textPrimary = Color(0xFFFAFAFA),
        textSecondary = Color(0xFFA1A1AA),
        textMuted = Color(0xFF71717A),
        primary = Color(0xFF3ECF8E),
        primaryLight = Color(0xFF70E3AD),
        primaryDark = Color(0xFF24B47E),
        onPrimary = Color(0xFF09090B),
        cardBackground = Color(0xFF18181B),
        cardShadow = Color(0x00000000),
        sidebarBackground = Color(0xFF09090B),
        sidebarBorder = Color(0xFF27272A),
        sidebarText = Color(0xFFFAFAFA),
        sidebarTextSecondary = Color(0xFFA1A1AA),
        sidebarActiveItem = Color(0xFF27272A),
        sidebarItemHover = Color(0xFF18181B),
        accentBlue = Color(0xFF60A5FA),
        accentGreen = Color(0xFF3ECF8E),
        accentOrange = Color(0xFFFBBF24),
        accentRed = Color(0xFFF87171),
        progressBackground = Color(0xFF27272A),
        progressFill = Color(0xFF3ECF8E),
        streakFire = Color(0xFFFB923C),
        hoverOverlay = Color(0x14FFFFFF),
        pressedOverlay = Color(0x28FFFFFF),
        focusedBorder = Color(0xFF3ECF8E),
    )

val LocalWordBridgeColors = staticCompositionLocalOf { LightWordBridgePalette }

object WordBridgeColors {
    var currentPalette: WordBridgePalette by mutableStateOf(LightWordBridgePalette)
        internal set

    val Surface: Color get() = currentPalette.surface
    val SurfaceVariant: Color get() = currentPalette.surfaceVariant
    val Background: Color get() = currentPalette.background
    val Border: Color get() = currentPalette.border
    val BorderLight: Color get() = currentPalette.border
    val BorderDark: Color get() = if (currentPalette.isDark) Color(0xFF52525B) else Color(0xFFA1A1AA)
    val Primary: Color get() = currentPalette.primary
    val OnPrimary: Color get() = currentPalette.onPrimary
    val PrimaryForeground: Color get() = currentPalette.onPrimary
    val Success: Color get() = currentPalette.accentGreen
    val Warning: Color get() = currentPalette.accentOrange
    val Error: Color get() = currentPalette.accentRed
    val ErrorForeground: Color get() = Color.White

    val BackgroundLight: Color get() = currentPalette.background
    val BackgroundWhite: Color get() = currentPalette.surface
    val CardBackground: Color get() = currentPalette.cardBackground
    val CardShadow: Color get() = currentPalette.cardShadow
    val BorderSubtle: Color get() = currentPalette.border
    val TextPrimary: Color get() = currentPalette.textPrimary
    val TextSecondary: Color get() = currentPalette.textSecondary
    val TextMuted: Color get() = currentPalette.textMuted
    val PrimaryGreen: Color get() = currentPalette.primary
    val PrimaryGreenLight: Color get() = currentPalette.primaryLight
    val PrimaryGreenDark: Color get() = currentPalette.primaryDark
    val PrimaryPurple: Color get() = currentPalette.primary
    val PrimaryPurpleLight: Color get() = currentPalette.primaryLight
    val PrimaryPurpleDark: Color get() = currentPalette.primaryDark
    val SidebarBackground: Color get() = currentPalette.sidebarBackground
    val SidebarBackgroundDark: Color get() = DarkWordBridgePalette.sidebarBackground
    val SidebarBorder: Color get() = currentPalette.sidebarBorder
    val SidebarText: Color get() = currentPalette.sidebarText
    val SidebarTextSecondary: Color get() = currentPalette.sidebarTextSecondary
    val SidebarActiveItem: Color get() = currentPalette.sidebarActiveItem
    val AccentBlue: Color get() = currentPalette.accentBlue
    val AccentGreen: Color get() = currentPalette.accentGreen
    val AccentOrange: Color get() = currentPalette.accentOrange
    val AccentRed: Color get() = currentPalette.accentRed
    val ProgressBackground: Color get() = currentPalette.progressBackground
    val ProgressFill: Color get() = currentPalette.progressFill
    val StreakFire: Color get() = currentPalette.streakFire
    val HoverOverlay: Color get() = currentPalette.hoverOverlay
    val PressedOverlay: Color get() = currentPalette.pressedOverlay
    val FocusedBorder: Color get() = currentPalette.focusedBorder
}
