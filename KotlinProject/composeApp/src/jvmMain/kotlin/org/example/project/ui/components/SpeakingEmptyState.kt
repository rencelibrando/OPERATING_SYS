package org.example.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.models.SpeakingFeature
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun SpeakingEmptyState(
    features: List<SpeakingFeature>,
    onStartFirstPracticeClick: () -> Unit,
    onExploreExercisesClick: () -> Unit,
    onStartConversationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        Icon(
            imageVector = WordBridgeIcons.Speaking,
            contentDescription = null,
            tint = WordBridgeColors.Primary,
            modifier = Modifier.size(44.dp),
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Perfect Your Speaking Skills",
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Practice pronunciation with real-time feedback or engage in natural conversations with an AI tutor. Select the learning mode that matches your daily goals.",
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.6f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onStartFirstPracticeClick,
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.Primary,
                        contentColor = WordBridgeColors.OnPrimary,
                    ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WordBridgeIcons.Speaking,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Practice Mode",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }
            }

            OutlinedButton(
                onClick = onStartConversationClick,
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = WordBridgeColors.TextPrimary,
                    ),
                border = BorderStroke(1.dp, WordBridgeColors.BorderSubtle),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WordBridgeIcons.AIChat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Conversation",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "or",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )

            TextButton(
                onClick = onExploreExercisesClick,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = WordBridgeColors.Primary,
                    ),
            ) {
                Text(
                    text = "explore speaking exercises",
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(400.dp),
        ) {
            items(features) { feature ->
                SpeakingFeatureCard(feature = feature)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SpeakingFeatureCard(
    feature: SpeakingFeature,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackground,
            ),
        border = BorderStroke(1.dp, WordBridgeColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Icon(
                imageVector = WordBridgeIcons.getByName(feature.title),
                contentDescription = feature.title,
                tint = WordBridgeColors.Primary,
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = feature.title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}
