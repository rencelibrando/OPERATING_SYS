package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LessonCategoryInfo
import org.example.project.domain.model.LessonDifficulty
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun LessonCategoryCard(
    categoryInfo: LessonCategoryInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val iconVector =
        when (categoryInfo.difficulty) {
            LessonDifficulty.BEGINNER -> WordBridgeIcons.School
            LessonDifficulty.INTERMEDIATE -> WordBridgeIcons.Explore
            LessonDifficulty.ADVANCED -> WordBridgeIcons.Star
        }

    val defaultBorder = WordBridgeColors.BorderSubtle
    val activeBorder = WordBridgeColors.PrimaryPurple

    val borderColor by animateColorAsState(
        targetValue = if (isHovered && !categoryInfo.isLocked) activeBorder else defaultBorder,
        label = "category_border",
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .clickable(enabled = !categoryInfo.isLocked) { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackground,
            ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = categoryInfo.title,
                tint = WordBridgeColors.PrimaryPurple,
                modifier = Modifier.size(32.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = categoryInfo.title,
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )

                    if (categoryInfo.isLocked) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WordBridgeColors.BorderSubtle,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = WordBridgeIcons.Lock,
                                    contentDescription = "Locked",
                                    tint = WordBridgeColors.TextMuted,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "Locked",
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    color = WordBridgeColors.TextMuted,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = categoryInfo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = WordBridgeColors.BorderSubtle,
                    ) {
                        Text(
                            text = "${categoryInfo.totalLessons} Lessons",
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            color = WordBridgeColors.PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    if (!categoryInfo.isLocked) {
                        Icon(
                            imageVector = WordBridgeIcons.ChevronRight,
                            contentDescription = null,
                            tint = if (isHovered) WordBridgeColors.PrimaryPurple else WordBridgeColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
