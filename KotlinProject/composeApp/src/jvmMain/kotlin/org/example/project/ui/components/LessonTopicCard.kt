package org.example.project.ui.components

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LessonTopic
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun LessonTopicCard(
    topic: LessonTopic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    
    val gradientStart = Color(0xFF34D399) 
    val gradientEnd = Color(0xFF14B8A6)   
    val hoverBorderColor = Color(0xFF6EE7B7) 
    val hoverTitleColor = Color(0xFF059669) 
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 12.dp else 4.dp,
        animationSpec = tween(300),
        label = "elevation"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isHovered && !topic.isLocked) hoverBorderColor else Color.Transparent,
        animationSpec = tween(300),
        label = "border"
    )
    
    val titleColor by animateColorAsState(
        targetValue = if (isHovered && !topic.isLocked) hoverTitleColor else WordBridgeColors.TextPrimary,
        animationSpec = tween(300),
        label = "titleColor"
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .clickable(enabled = !topic.isLocked) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = elevation,
            ),
        border = BorderStroke(2.dp, borderColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            if (topic.lessonNumber != null) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(gradientStart, gradientEnd)
                                )
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${topic.lessonNumber}",
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = Color.White,
                    )
                }
            } else {
                
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(gradientStart, gradientEnd)
                                )
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "📚",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = topic.title,
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = titleColor,
                        modifier = Modifier.weight(1f),
                    )
                    
                    
                    if (topic.durationMinutes != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF1F5F9),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🕐",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "${topic.durationMinutes}m",
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    color = Color(0xFF64748B),
                                )
                            }
                        }
                    }
                }

                if (topic.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = topic.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WordBridgeColors.TextSecondary,
                    )
                }
            }
            
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    topic.isCompleted -> {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = WordBridgeColors.AccentGreen,
                        )
                    }
                    topic.isLocked -> {
                        Text(
                            text = "🔒",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> {
                        
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isHovered) gradientStart else Color(0xFFCBD5E1),
                        )
                    }
                }
            }
        }
    }
}
