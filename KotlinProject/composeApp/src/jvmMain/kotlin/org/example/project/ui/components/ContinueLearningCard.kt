package org.example.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun ContinueLearningCard(
    title: String = "Continue Your Journey",
    subtitle: String = "You're making great progress! Ready for today's lesson?",
    buttonText: String = "Continue Learning",
    onButtonClick: () -> Unit,
    isLoading: Boolean = false,
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
                    .fillMaxWidth()
                    .padding(24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = WordBridgeIcons.Sparkle,
                    contentDescription = null,
                    tint = WordBridgeColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Daily Focus",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = WordBridgeColors.Primary,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onButtonClick,
                enabled = !isLoading,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.Primary,
                        contentColor = WordBridgeColors.OnPrimary,
                    ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.height(44.dp),
            ) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = "Loading...",
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = WordBridgeIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = buttonText,
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                        )
                    }
                }
            }
        }
    }
}
