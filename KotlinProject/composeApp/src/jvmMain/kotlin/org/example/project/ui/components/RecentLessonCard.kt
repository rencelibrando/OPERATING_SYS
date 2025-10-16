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
import org.example.project.domain.model.RecentLesson
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun RecentLessonCard(
    recentLesson: RecentLesson,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressColor =
        when {
            recentLesson.progressPercentage == 100 -> Color(0xFF10B981) // Green for completed
            recentLesson.progressPercentage > 0 -> Color(0xFFF59E0B) // Orange for in progress
            else -> Color(0xFF6B7280) // Gray for not started
        }

    val iconBackgroundColor =
        when (recentLesson.category) {
            "Grammar" -> Color(0xFFEF4444) // Red
            "Vocabulary" -> Color(0xFF10B981) // Green
            "Conversation" -> Color(0xFFF59E0B) // Orange
            "Pronunciation" -> Color(0xFF3B82F6) // Blue
            else -> WordBridgeColors.PrimaryPurple
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick(recentLesson.id) }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = recentLesson.icon,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = recentLesson.title,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Text(
                text = "${recentLesson.category} • ${recentLesson.difficulty} • ${recentLesson.duration} minutes",
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
            )
        }

        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(progressColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    when {
                        recentLesson.progressPercentage == 100 -> "✓"
                        recentLesson.progressPercentage > 0 -> "${recentLesson.progressPercentage}%"
                        else -> "0%"
                    },
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = progressColor,
            )
        }
    }
}
