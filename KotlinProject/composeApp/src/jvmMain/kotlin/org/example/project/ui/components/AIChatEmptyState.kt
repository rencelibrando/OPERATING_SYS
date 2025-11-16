package org.example.project.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.ChatFeature
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun AIChatEmptyState(
    features: List<ChatFeature>,
    onStartFirstConversationClick: () -> Unit,
    onExploreChatBotsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Sparkle Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFF3E5F5), // purple-100
                                Color(0xFFE3F2FD)  // blue-100
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFA855F7), // purple-500
                                    Color(0xFF3B82F6)  // blue-500
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✨",
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Meet Your AI Language Tutors",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Start conversations with AI tutors who understand your learning needs. Practice real-world scenarios, get instant feedback, and improve your English through natural dialogue.",
                style = MaterialTheme.typography.bodyLarge,
                color = WordBridgeColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 600.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Start Conversation Button
            val buttonInteraction = remember { MutableInteractionSource() }
            val buttonHovered by buttonInteraction.collectIsHoveredAsState()
            val buttonScale by animateFloatAsState(
                targetValue = if (buttonHovered) 1.05f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )

            Button(
                onClick = onStartFirstConversationClick,
                modifier = Modifier
                    .scale(buttonScale)
                    .hoverable(buttonInteraction),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 12.dp,
                    hoveredElevation = 16.dp
                )
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFA855F7), // purple-500
                                    Color(0xFF3B82F6)  // blue-500
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💬",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Start Your First Conversation",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "or",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WordBridgeColors.TextSecondary,
                )
                
                Text(
                    text = "explore our AI tutors",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF9333EA), // purple-600
                    modifier = Modifier.clickable { onExploreChatBotsClick() }
                )
            }
        }

        // Features Grid
        if (features.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                features.chunked(3).forEach { rowFeatures ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        rowFeatures.forEach { feature ->
                            FeatureCard(
                                feature = feature,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining space if last row has fewer items
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
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val (bgColor, gradientStart, gradientEnd) = when (feature.icon) {
        "💬" -> Triple(Color(0xFFEFF6FF), Color(0xFF60A5FA), Color(0xFF06B6D4)) // blue-50, blue-400, cyan-500
        "⚡" -> Triple(Color(0xFFFFF7ED), Color(0xFFFB923C), Color(0xFFEF4444)) // orange-50, orange-400, red-500
        "🎯" -> Triple(Color(0xFFFCE7F3), Color(0xFFF472B6), Color(0xFFF43F5E)) // pink-50, pink-400, rose-500
        "🌙" -> Triple(Color(0xFFEEF2FF), Color(0xFF818CF8), Color(0xFFA855F7)) // indigo-50, indigo-400, purple-500
        "👥" -> Triple(Color(0xFFF0FDFA), Color(0xFF2DD4BF), Color(0xFF10B981)) // teal-50, teal-400, emerald-500
        "💡" -> Triple(Color(0xFFFFFBEB), Color(0xFFFBBF24), Color(0xFFF59E0B)) // amber-50, amber-400, amber-500
        else -> Triple(Color(0xFFF5F5F5), Color(0xFF9CA3AF), Color(0xFF6B7280))
    }

    Card(
        modifier = modifier
            .scale(scale)
            .hoverable(interactionSource)
            .clickable { },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = bgColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            hoveredElevation = 12.dp
        ),
        border = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(gradientStart, gradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feature.icon,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}
