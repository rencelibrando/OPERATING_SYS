package org.example.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun TodaysProgressCard(
    streak: Int,
    xpPoints: Int,
    wordsLearned: Int,
    accuracy: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackground,
            ),
        border = BorderStroke(1.dp, WordBridgeColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
        ) {
            Text(
                text = "Today's Progress",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f),
                ) {
                    ProgressMetric(
                        label = "Streak",
                        value = "$streak days",
                        icon = WordBridgeIcons.Streak,
                        color = WordBridgeColors.StreakFire,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ProgressMetric(
                        label = "Words Learned",
                        value = wordsLearned.toString(),
                        icon = WordBridgeIcons.Book,
                        color = WordBridgeColors.AccentBlue,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f),
                ) {
                    ProgressMetric(
                        label = "XP Points",
                        value = xpPoints.toString(),
                        icon = WordBridgeIcons.Trophy,
                        color = WordBridgeColors.AccentOrange,
                        alignEnd = true,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ProgressMetric(
                        label = "Accuracy",
                        value = "$accuracy%",
                        icon = WordBridgeIcons.Check,
                        color = WordBridgeColors.AccentGreen,
                        alignEnd = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressMetric(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = WordBridgeColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!alignEnd) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = value,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            if (alignEnd) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
