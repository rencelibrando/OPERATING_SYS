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
import org.example.project.domain.model.SpeakingExercise
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun SpeakingExerciseCard(
    exercise: SpeakingExercise,
    onStartClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBackgroundColor = when (exercise.type.displayName) {
        "Pronunciation" -> Color(0xFFFEF2F2) // Light red
        "Conversation" -> Color(0xFFECFDF5) // Light green
        "Accent Training" -> Color(0xFFFEF3E2) // Light orange
        "Storytelling" -> Color(0xFFEBF8FF) // Light blue
        "Reading Aloud" -> Color(0xFFF3E8FF) // Light purple
        else -> WordBridgeColors.BackgroundLight
    }
    
    val iconBackgroundColor = when (exercise.type.displayName) {
        "Pronunciation" -> Color(0xFFEF4444) // Red
        "Conversation" -> Color(0xFF10B981) // Green
        "Accent Training" -> Color(0xFFF59E0B) // Orange
        "Storytelling" -> Color(0xFF3B82F6) // Blue
        "Reading Aloud" -> Color(0xFF8B5CF6) // Purple
        else -> WordBridgeColors.PrimaryPurple
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {  },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = exercise.icon,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = exercise.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = exercise.type.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondary
                        )
                        
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondary
                        )
                        
                        Text(
                            text = exercise.difficulty,
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondary
                        )
                        
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondary
                        )
                        
                        Text(
                            text = "${exercise.duration} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = exercise.description,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (exercise.completionRate > 0 || exercise.lastAttempt != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (exercise.completionRate > 0) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${exercise.completionRate}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = WordBridgeColors.TextPrimary
                            )
                            
                            Text(
                                text = "Completion",
                                style = MaterialTheme.typography.bodySmall,
                                color = WordBridgeColors.TextSecondary
                            )
                        }
                    }
                    
                    if (exercise.lastAttempt != null) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = WordBridgeColors.TextPrimary
                            )
                            
                            Text(
                                text = "Last Practice",
                                style = MaterialTheme.typography.bodySmall,
                                color = WordBridgeColors.TextSecondary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Button(
                onClick = { onStartClick(exercise.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = iconBackgroundColor
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (exercise.completionRate > 0) "Continue Practice" else "Start Exercise",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
            }
        }
    }
}
