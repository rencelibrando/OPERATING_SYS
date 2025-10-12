package org.example.project.ui.components.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.core.onboarding.OnboardingMessage
import org.example.project.core.onboarding.OnboardingMessageSender
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun ChatMessageBubble(
    message: OnboardingMessage,
    modifier: Modifier = Modifier
) {
    val bubbleShape = RoundedCornerShape(20.dp)
    val isAssistant = message.sender == OnboardingMessageSender.ASSISTANT

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isAssistant) {
            androidx.compose.foundation.layout.Arrangement.Start
        } else {
            androidx.compose.foundation.layout.Arrangement.End
        }
    ) {
        Card(
            modifier = Modifier
                .clip(bubbleShape)
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAssistant) WordBridgeColors.BackgroundWhite else WordBridgeColors.PrimaryPurple,
                contentColor = if (isAssistant) WordBridgeColors.TextPrimary else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            if (message.isTyping) {
                TypingIndicator(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }
    }
}

