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
import org.example.project.domain.model.SpeakingSession
import org.example.project.ui.theme.WordBridgeColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * Speaking session card component for displaying completed sessions
 * 
 * @param session The speaking session data
 * @param exerciseTitle The title of the associated exercise
 * @param onReviewClick Callback when the review button is clicked
 * @param modifier Optional modifier for styling
 */
@Composable
fun SpeakingSessionCard(
    session: SpeakingSession,
    exerciseTitle: String,
    onReviewClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val overallScore = session.overallScore ?: 0
    val scoreColor = when {
        overallScore >= 80 -> Color(0xFF10B981) // Green for good scores
        overallScore >= 60 -> Color(0xFFF59E0B) // Orange for average scores
        else -> Color(0xFFEF4444) // Red for low scores
    }
    
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val sessionDate = dateFormatter.format(Date(session.startTime))
    
    val duration = if (session.endTime != null) {
        val durationMs = session.endTime - session.startTime
        val minutes = (durationMs / 60000).toInt()
        "${minutes}m"
    } else {
        "Ongoing"
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onReviewClick(session.id) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            hoveredElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scoreColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (overallScore > 0) "${overallScore}%" else "—",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = scoreColor
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Session details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = exerciseTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sessionDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary
                    )
                    
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary
                    )
                    
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary
                    )
                }
                
                // Show individual scores if available
                if (session.accuracyScore != null || session.fluencyScore != null || session.pronunciationScore != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        session.accuracyScore?.let { score ->
                            ScoreBadge(
                                label = "Accuracy",
                                score = score,
                                color = Color(0xFF3B82F6) // Blue
                            )
                        }
                        
                        session.fluencyScore?.let { score ->
                            ScoreBadge(
                                label = "Fluency",
                                score = score,
                                color = Color(0xFF10B981) // Green
                            )
                        }
                        
                        session.pronunciationScore?.let { score ->
                            ScoreBadge(
                                label = "Pronunciation",
                                score = score,
                                color = Color(0xFFF59E0B) // Orange
                            )
                        }
                    }
                }
            }
            
            // Review button
            TextButton(
                onClick = { onReviewClick(session.id) }
            ) {
                Text(
                    text = "Review",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = WordBridgeColors.PrimaryPurple
                )
            }
        }
    }
}

/**
 * Small score badge component
 */
@Composable
private fun ScoreBadge(
    label: String,
    score: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$score%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = color
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}
