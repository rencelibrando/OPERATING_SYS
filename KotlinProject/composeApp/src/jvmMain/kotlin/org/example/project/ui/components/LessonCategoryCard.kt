package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LessonCategoryInfo
import org.example.project.domain.model.LessonDifficulty
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun LessonCategoryCard(
    categoryInfo: LessonCategoryInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val (backgroundColor, gradientStart, gradientEnd) =
        when (categoryInfo.difficulty) {
            LessonDifficulty.BEGINNER -> Triple(
                Color(0xFFECFDF5), // emerald-50
                Color(0xFF34D399), // emerald-400
                Color(0xFF14B8A6)  // teal-500
            )
            LessonDifficulty.INTERMEDIATE -> Triple(
                Color(0xFFFAF5FF), // purple-50
                Color(0xFFC084FC), // purple-400
                Color(0xFFEC4899)  // pink-500
            )
            LessonDifficulty.ADVANCED -> Triple(
                Color(0xFFFFFBEB), // amber-50
                Color(0xFFFBBF24), // amber-400
                Color(0xFFF97316)  // orange-500
            )
        }

    val iconText =
        when (categoryInfo.difficulty) {
            LessonDifficulty.BEGINNER -> "🌱"
            LessonDifficulty.INTERMEDIATE -> "🚀"
            LessonDifficulty.ADVANCED -> "⭐"
        }
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered && !categoryInfo.isLocked) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isHovered && !categoryInfo.isLocked) 
            Color(0xFFE2E8F0) else Color.Transparent,
        animationSpec = tween(300)
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .scale(scale)
                .hoverable(interactionSource)
                .clickable(enabled = !categoryInfo.isLocked) { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 4.dp,
                hoveredElevation = 12.dp,
            ),
        border = BorderStroke(2.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Decorative blur circle background
            Box(
                modifier = Modifier
                    .size(256.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 64.dp, y = (-64).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                gradientEnd.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Large icon with gradient
                Box(
                    modifier =
                        Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(gradientStart, gradientEnd)
                                )
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = iconText,
                        style = MaterialTheme.typography.displaySmall,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = categoryInfo.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = WordBridgeColors.TextPrimary,
                        )
                        
                        if (categoryInfo.isLocked) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFCBD5E1),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔒",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = "Locked",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = Color(0xFF475569),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = categoryInfo.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WordBridgeColors.TextSecondary,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Large gradient button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(gradientStart, gradientEnd)
                                    )
                                )
                        ) {
                            Text(
                                text = "${categoryInfo.totalLessons} Total Lessons",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                        
                        // ChevronRight icon
                        if (!categoryInfo.isLocked) {
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isHovered) WordBridgeColors.TextSecondary else Color(0xFFCBD5E1),
                            )
                        }
                    }
                }
            }
        }
    }
}
