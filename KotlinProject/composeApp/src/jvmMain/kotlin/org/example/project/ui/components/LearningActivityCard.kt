package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LearningActivity
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun LearningActivityCard(
    activity: LearningActivity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val defaultBorder = WordBridgeColors.BorderSubtle
    val activeBorder = WordBridgeColors.PrimaryPurple

    val borderColor by animateColorAsState(
        targetValue = if (isHovered) activeBorder else defaultBorder,
        label = "learning_card_border",
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .clickable { onClick() },
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackground,
            ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = WordBridgeIcons.getByName(activity.icon),
                contentDescription = activity.title,
                tint = WordBridgeColors.PrimaryPurple,
                modifier = Modifier.size(24.dp),
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

            Icon(
                imageVector = WordBridgeIcons.ChevronRight,
                contentDescription = null,
                tint = WordBridgeColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
