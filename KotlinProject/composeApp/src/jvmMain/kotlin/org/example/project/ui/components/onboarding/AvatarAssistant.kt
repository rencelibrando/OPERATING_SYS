package org.example.project.ui.components.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun AvatarAssistant(
    modifier: Modifier = Modifier,
    avatarSize: Dp = 72.dp,
    showIndicator: Boolean = true,
    toneLabel: String = "Ceddie",
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(WordBridgeColors.PrimaryPurple.copy(alpha = 0.2f))
                    .padding(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(WordBridgeColors.PrimaryPurple)
                        .fillMaxWidth(),
            ) {
                Text(
                    text = "C",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (showIndicator) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4ADE80)),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = toneLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = WordBridgeColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your AI language tutor",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = WordBridgeColors.PrimaryPurple,
    backgroundColor: Color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f),
) {
    Card(
        modifier =
            modifier
                .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Dot(dotColor)
            Dot(dotColor.copy(alpha = 0.6f))
            Dot(dotColor.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
fun QuickChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isSelected) WordBridgeColors.PrimaryPurple else WordBridgeColors.BackgroundWhite
    val content = if (isSelected) Color.White else WordBridgeColors.TextSecondary

    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = content,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        )
    }
}
