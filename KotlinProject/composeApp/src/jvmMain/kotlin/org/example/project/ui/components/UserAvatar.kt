package org.example.project.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun UserAvatar(
    initials: String,
    profileImageUrl: String? = null,
    size: Dp = 40.dp,
    backgroundColor: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val actualBg = if (backgroundColor != Color.Unspecified) backgroundColor else WordBridgeColors.Primary
    val actualText = if (textColor != Color.Unspecified) textColor else WordBridgeColors.OnPrimary
    val borderColor = WordBridgeColors.BorderSubtle

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, borderColor, CircleShape)
                .background(actualBg)
                .let { if (onClick != null) it.clickable { onClick() } else it },
        contentAlignment = Alignment.Center,
    ) {
        if (!profileImageUrl.isNullOrEmpty()) {
            val painter = asyncPainterResource(data = profileImageUrl)
            KamelImage(
                resource = painter,
                contentDescription = "Profile Picture",
                modifier = Modifier.fillMaxSize(),
                onLoading = {
                    Text(
                        text = initials.take(2).uppercase(),
                        color = actualText,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                },
                onFailure = {
                    Text(
                        text = initials.take(2).uppercase(),
                        color = actualText,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                },
            )
        } else {
            Text(
                text = initials.take(2).uppercase(),
                color = actualText,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
    }
}
