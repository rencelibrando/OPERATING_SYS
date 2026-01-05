package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    // Enhanced animation states with reduced scale to prevent overlap
    val scale by animateFloatAsState(
        targetValue = if (isHovered && !categoryInfo.isLocked) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "scale"
    )
    
    val iconScale by animateFloatAsState(
        targetValue = if (isHovered && !categoryInfo.isLocked) 1.05f else 1f,
        animationSpec = tween(200),
        label = "iconScale"
    )

    // Enhanced color schemes with gradients matching sidebar
    val (primaryColor, secondaryColor, backgroundColor, iconColor) =
        when (categoryInfo.difficulty) {
            LessonDifficulty.BEGINNER ->
                Quadruple(
                    Color(0xFF10B981), // emerald-500
                    Color(0xFF34D399), // emerald-400
                    Color(0xFF1E293B), // slate-800 (matching sidebar)
                    Color(0xFF059669)  // emerald-600
                )
            LessonDifficulty.INTERMEDIATE ->
                Quadruple(
                    Color(0xFF8B5CF6), // violet-500
                    Color(0xFFA78BFA), // violet-400
                    Color(0xFF1E293B), // slate-800 (matching sidebar)
                    Color(0xFF7C3AED)  // violet-600
                )
            LessonDifficulty.ADVANCED ->
                Quadruple(
                    Color(0xFFF59E0B), // amber-500
                    Color(0xFFFBBF24), // amber-400
                    Color(0xFF1E293B), // slate-800 (matching sidebar)
                    Color(0xFFD97706)  // amber-600
                )
        }

    // Enhanced icons with better visual appeal
    val (icon, iconBgColor) =
        when (categoryInfo.difficulty) {
            LessonDifficulty.BEGINNER -> "🌱" to Color(0xFF34D399)
            LessonDifficulty.INTERMEDIATE -> "🚀" to Color(0xFF8B5CF6)
            LessonDifficulty.ADVANCED -> "⭐" to Color(0xFFF59E0B)
        }

    // Compact modern card design with enhanced visual hierarchy
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .hoverable(interactionSource)
            .clickable(
                enabled = !categoryInfo.isLocked,
                onClick = onClick
            )
            .shadow(
                elevation = if (isHovered && !categoryInfo.isLocked) 12.dp else 6.dp,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with icon and locked status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Enhanced icon with gradient background
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(iconScale)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    primaryColor,
                                    secondaryColor
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 28.sp
                    )
                }

                // Locked badge or progress indicator
                if (categoryInfo.isLocked) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Locked",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFF6B7280)
                        )
                    }
                } else if (categoryInfo.progressPercentage > 0) {
                    // Clean percentage display without border
                    Text(
                        text = "${categoryInfo.progressPercentage}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = primaryColor
                    )
                }
            }

            // Content section
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title with better typography for dark background
                Text(
                    text = categoryInfo.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Description with better readability for dark background
                Text(
                    text = categoryInfo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1), // slate-300 for better contrast
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // Bottom section with topics and progress
            if (categoryInfo.totalLessons > 0) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Topics count with clean styling
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${categoryInfo.totalLessons} ${if (categoryInfo.totalLessons == 1) "Topic" else "Topics"}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = primaryColor
                        )

                        // Play arrow indicator for unlocked cards
                        if (!categoryInfo.isLocked) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start",
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Enhanced progress bar
                    if (categoryInfo.progressPercentage > 0) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { categoryInfo.progressPercentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = primaryColor,
                                trackColor = primaryColor.copy(alpha = 0.2f),
                            )
                            
                            // Progress text with better contrast for dark background
                            Text(
                                text = when {
                                    categoryInfo.progressPercentage == 100 -> "Completed! 🎉"
                                    categoryInfo.progressPercentage >= 75 -> "Almost there!"
                                    categoryInfo.progressPercentage >= 50 -> "Good progress!"
                                    categoryInfo.progressPercentage >= 25 -> "Getting started"
                                    else -> "Just begun"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF94A3B8) // slate-400 for better contrast
                            )
                        }
                    }
                }
            } else {
                // Clean "Coming Soon" without gray border
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Coming Soon",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF94A3B8) // slate-400 for better contrast
                    )
                    
                    if (!categoryInfo.isLocked) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            tint = Color(0xFF64748B), // slate-500 for muted look
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// Helper data class for quadruple values
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
