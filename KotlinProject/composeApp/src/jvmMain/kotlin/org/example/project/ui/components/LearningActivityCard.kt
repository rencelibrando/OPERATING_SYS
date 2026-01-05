package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LearningActivity
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun LearningActivityCard(
    activity: LearningActivity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick() },
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                hoveredElevation = 6.dp,
                pressedElevation = 1.dp,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlaceholder(
                icon = activity.icon,
                modifier = Modifier.size(48.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = activity.title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary,
                )
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = WordBridgeColors.TextMuted,
            )
        }
    }
}

@Composable
private fun IconPlaceholder(
    icon: String,
    modifier: Modifier = Modifier,
) {
    val (emoji, backgroundColor) =
        when (icon) {
            "book" -> "📚" to WordBridgeColors.AccentBlue
            "vocabulary" -> "📝" to WordBridgeColors.AccentGreen
            "microphone" -> "🎤" to WordBridgeColors.AccentOrange
            "chat" -> "💬" to WordBridgeColors.PrimaryPurple
            else -> "📱" to WordBridgeColors.TextMuted
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}
