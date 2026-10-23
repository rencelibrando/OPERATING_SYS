package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.ChatFeature
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun AIChatEmptyState(
    features: List<ChatFeature>,
    onStartFirstConversationClick: () -> Unit,
    onExploreChatBotsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = WordBridgeIcons.Sparkle,
                contentDescription = null,
                tint = WordBridgeColors.Primary,
                modifier = Modifier.size(44.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Meet Your AI Language Tutors",
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Start conversations with AI tutors tailored to your learning goals. Practice real-world scenarios, get instant feedback, and build confidence through interactive dialogue.",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onStartFirstConversationClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.Primary,
                        contentColor = WordBridgeColors.OnPrimary,
                    ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WordBridgeIcons.AIChat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Start Your First Conversation",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
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
                    onClick = onExploreChatBotsClick,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = WordBridgeColors.Primary,
                        ),
                ) {
                    Text(
                        text = "explore our AI tutors",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }
            }
        }

        if (features.isNotEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                features.chunked(3).forEach { rowFeatures ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rowFeatures.forEach { feature ->
                            FeatureCard(
                                feature = feature,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowFeatures.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    feature: ChatFeature,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val defaultBorder = WordBridgeColors.BorderSubtle
    val activeBorder = WordBridgeColors.Primary

    val borderColor by animateColorAsState(
        targetValue = if (isHovered) activeBorder else defaultBorder,
        label = "feature_card_border",
    )

    Card(
        modifier =
            modifier
                .hoverable(interactionSource)
                .clickable { },
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackground,
            ),
        border = BorderStroke(1.dp, borderColor),
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
