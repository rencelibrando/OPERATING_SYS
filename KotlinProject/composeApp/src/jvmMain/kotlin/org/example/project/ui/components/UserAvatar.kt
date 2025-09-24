package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.sp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun UserAvatar(
    initials: String,
    profileImageUrl: String? = null,
    size: Dp = 40.dp,
    backgroundColor: Color = WordBridgeColors.PrimaryPurple,
    textColor: Color = WordBridgeColors.BackgroundWhite,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        contentAlignment = Alignment.Center
    ) {
        if (!profileImageUrl.isNullOrEmpty()) {
            val painter = asyncPainterResource(data = profileImageUrl)
            KamelImage(
                resource = painter,
                contentDescription = "Profile Picture",
                modifier = Modifier.fillMaxSize(),
                onLoading = {
                    // Show initials while loading
                    Text(
                        text = initials.take(2).uppercase(),
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                onFailure = {
                    // Show initials on failure
                    Text(
                        text = initials.take(2).uppercase(),
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            )
        } else {
            // Show initials
            Text(
                text = initials.take(2).uppercase(),
                color = textColor,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
