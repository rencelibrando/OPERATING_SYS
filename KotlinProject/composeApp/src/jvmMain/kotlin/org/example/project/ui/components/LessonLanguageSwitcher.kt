package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LessonLanguage
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

/**
 * Language Switcher Component for Lessons Tab
 *
 * Allows users to switch their active learning language directly from the Lessons tab
 * without needing to go through onboarding or profile settings.
 */
@Composable
fun LessonLanguageSwitcher(
    selectedLanguage: LessonLanguage,
    availableLanguages: List<LessonLanguage> = LessonLanguage.entries.toList(),
    onLanguageSelected: (LessonLanguage) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> WordBridgeColors.SurfaceVariant
                isHovered || expanded -> WordBridgeColors.SurfaceVariant
                else -> WordBridgeColors.Surface
            },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor",
    )

    val borderColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> WordBridgeColors.BorderLight
                expanded -> WordBridgeColors.BorderDark
                isHovered -> WordBridgeColors.BorderDark
                else -> WordBridgeColors.BorderLight
            },
        animationSpec = tween(durationMillis = 200),
        label = "borderColor",
    )

    Box(modifier = modifier) {
        // Main Button
        Surface(
            modifier =
                Modifier
                    .hoverable(interactionSource)
                    .clickable(enabled = enabled) { expanded = !expanded },
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            shadowElevation = if (expanded) 2.dp else 0.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Language Flag
                Text(
                    text = getLanguageFlag(selectedLanguage),
                    style = MaterialTheme.typography.titleMedium,
                )

                // Language Name
                Column {
                    Text(
                        text = "Learning",
                        style = MaterialTheme.typography.labelSmall,
                        color = WordBridgeColors.TextMuted,
                    )
                    Text(
                        text = selectedLanguage.displayName,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Language",
                    tint = WordBridgeColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Dropdown Menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .width(280.dp)
                    .background(WordBridgeColors.Surface, RoundedCornerShape(12.dp)),
        ) {
            // Header
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = WordBridgeIcons.Language,
                        contentDescription = null,
                        tint = WordBridgeColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Switch Language",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose your learning language",
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondary,
                )
            }

            HorizontalDivider(color = WordBridgeColors.BorderLight)

            // Language Options
            availableLanguages.forEach { language ->
                LanguageDropdownItem(
                    language = language,
                    isSelected = language == selectedLanguage,
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageDropdownItem(
    language: LessonLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue =
            when {
                isSelected -> WordBridgeColors.SurfaceVariant
                isHovered -> WordBridgeColors.SurfaceVariant
                else -> Color.Transparent
            },
        animationSpec = tween(durationMillis = 150),
        label = "itemBackground",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Flag
            Text(
                text = getLanguageFlag(language),
                style = MaterialTheme.typography.titleMedium,
            )

            // Language info
            Column {
                Text(
                    text = language.displayName,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    color = if (isSelected) WordBridgeColors.Primary else WordBridgeColors.TextPrimary,
                )
                Text(
                    text = getLanguageNativeName(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondary,
                )
            }
        }

        // Selected checkmark without container
        if (isSelected) {
            Icon(
                imageVector = WordBridgeIcons.Check,
                contentDescription = "Selected",
                tint = WordBridgeColors.Primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Compact version of the language switcher for use in headers
 */
@Composable
fun LessonLanguageSwitcherCompact(
    selectedLanguage: LessonLanguage,
    availableLanguages: List<LessonLanguage> = LessonLanguage.entries.toList(),
    onLanguageSelected: (LessonLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(modifier = modifier) {
        Surface(
            modifier =
                Modifier
                    .hoverable(interactionSource)
                    .clickable { expanded = !expanded },
            shape = RoundedCornerShape(8.dp),
            color = if (isHovered || expanded) WordBridgeColors.SurfaceVariant else WordBridgeColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, WordBridgeColors.BorderLight),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = getLanguageFlag(selectedLanguage),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = selectedLanguage.displayName,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = WordBridgeColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(WordBridgeColors.Surface),
        ) {
            availableLanguages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(getLanguageFlag(language))
                            Text(
                                text = language.displayName,
                                fontWeight = if (language == selectedLanguage) FontWeight.SemiBold else FontWeight.Normal,
                                color = WordBridgeColors.TextPrimary,
                            )
                        }
                    },
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    },
                    trailingIcon =
                        if (language == selectedLanguage) {
                            {
                                Icon(
                                    imageVector = WordBridgeIcons.Check,
                                    contentDescription = null,
                                    tint = WordBridgeColors.Primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

/**
 * Get flag emoji for language
 */
private fun getLanguageFlag(language: LessonLanguage): String {
    return when (language) {
        LessonLanguage.KOREAN -> "🇰🇷"
        LessonLanguage.CHINESE -> "🇨🇳"
        LessonLanguage.FRENCH -> "🇫🇷"
        LessonLanguage.GERMAN -> "🇩🇪"
        LessonLanguage.SPANISH -> "🇪🇸"
    }
}

/**
 * Get native name for language
 */
private fun getLanguageNativeName(language: LessonLanguage): String {
    return when (language) {
        LessonLanguage.KOREAN -> "한국어"
        LessonLanguage.CHINESE -> "中文"
        LessonLanguage.FRENCH -> "Français"
        LessonLanguage.GERMAN -> "Deutsch"
        LessonLanguage.SPANISH -> "Español"
    }
}
