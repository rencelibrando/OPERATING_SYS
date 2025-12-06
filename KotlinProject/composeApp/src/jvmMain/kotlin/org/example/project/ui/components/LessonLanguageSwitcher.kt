package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.example.project.domain.model.LessonLanguage
import org.example.project.ui.theme.WordBridgeColors

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
        targetValue = when {
            !enabled -> Color(0xFFF1F5F9) // slate-100
            isHovered || expanded -> Color(0xFFEDE9FE) // violet-100
            else -> Color.White
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFFE2E8F0) // slate-200
            expanded -> Color(0xFF8B5CF6) // violet-500
            isHovered -> Color(0xFFA78BFA) // violet-400
            else -> Color(0xFFE2E8F0) // slate-200
        },
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )

    Box(modifier = modifier) {
        // Main Button
        Surface(
            modifier = Modifier
                .hoverable(interactionSource)
                .clickable(enabled = enabled) { expanded = !expanded },
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            shadowElevation = if (expanded) 4.dp else 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
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
                        color = Color(0xFF64748B), // slate-500
                    )
                    Text(
                        text = selectedLanguage.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF1E293B), // slate-800
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Language",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Dropdown Menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(280.dp)
                .background(Color.White, RoundedCornerShape(16.dp)),
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "🌍 Switch Language",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF1E293B),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose your learning language",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))

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
        targetValue = when {
            isSelected -> Color(0xFFF5F3FF) // violet-50
            isHovered -> Color(0xFFF8FAFC) // slate-50
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "itemBackground"
    )

    Row(
        modifier = Modifier
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) Color(0xFF7C3AED) else Color(0xFF1E293B), // violet-600 or slate-800
                )
                Text(
                    text = getLanguageNativeName(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8), // slate-400
                )
            }
        }

        // Selected checkmark
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
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
            modifier = Modifier
                .hoverable(interactionSource)
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(8.dp),
            color = if (isHovered || expanded) Color(0xFFEDE9FE) else Color(0xFFF8FAFC),
        ) {
            Row(
                modifier = Modifier
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
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF475569),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
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
                            )
                        }
                    },
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    },
                    trailingIcon = if (language == selectedLanguage) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                            )
                        }
                    } else null,
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

