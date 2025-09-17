package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.Lesson
import org.example.project.ui.theme.WordBridgeColors

/**
 * Lesson card component displaying lesson information and progress
 * 
 * @param lesson The lesson data
 * @param onContinueClick Callback when continue button is clicked
 * @param onStartClick Callback when start button is clicked
 * @param modifier Optional modifier for styling
 */
@Composable
fun LessonCard(
    lesson: Lesson,
    onContinueClick: (String) -> Unit,
    onStartClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBackgroundColor = when (lesson.category.displayName) {
        "Grammar" -> Color(0xFFFEF2F2) // Light red
        "Vocabulary" -> Color(0xFFECFDF5) // Light green
        "Conversation" -> Color(0xFFFEF3E2) // Light orange
        "Pronunciation" -> Color(0xFFEBF8FF) // Light blue
        else -> WordBridgeColors.BackgroundLight
    }
    
    val iconBackgroundColor = when (lesson.category.displayName) {
        "Grammar" -> Color(0xFFEF4444) // Red
        "Vocabulary" -> Color(0xFF10B981) // Green
        "Conversation" -> Color(0xFFF59E0B) // Orange
        "Pronunciation" -> Color(0xFF3B82F6) // Blue
        else -> WordBridgeColors.PrimaryPurple
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { /* Handle card click if needed */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            hoveredElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lesson.icon,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                    
                    Text(
                        text = "Learn essential ${lesson.category.displayName.lowercase()} rules with AI-powered explanations and interactive exercises.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress and stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Lessons progress
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${lesson.completedCount}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = WordBridgeColors.TextPrimary
                        )
                        
                        Text(
                            text = " Lessons",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextSecondary
                        )
                    }
                    
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary
                    )
                }
                
                // Progress percentage
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${lesson.progressPercentage}%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = WordBridgeColors.TextPrimary
                        )
                        
                        Text(
                            text = " Progress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextSecondary
                        )
                    }
                    
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action button
            if (lesson.progressPercentage > 0) {
                Button(
                    onClick = { onContinueClick(lesson.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iconBackgroundColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Continue Learning",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )
                }
            } else {
                Button(
                    onClick = { onStartClick(lesson.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iconBackgroundColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Start Lesson",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}
