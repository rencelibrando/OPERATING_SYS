package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.project.ui.theme.WordBridgeColors

/**
 * A circular avatar component that displays user initials
 * 
 * @param initials The user's initials to display
 * @param size The size of the avatar
 * @param backgroundColor The background color of the avatar
 * @param textColor The color of the initials text
 * @param modifier Optional modifier for styling
 */
@Composable
fun UserAvatar(
    initials: String,
    size: Dp = 40.dp,
    backgroundColor: Color = WordBridgeColors.PrimaryPurple,
    textColor: Color = WordBridgeColors.BackgroundWhite,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = textColor,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
