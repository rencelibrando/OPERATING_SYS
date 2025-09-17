package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.ChatFeature
import org.example.project.ui.theme.WordBridgeColors

/**
 * Empty state component for AI Chat screen
 * 
 * @param features List of chat features to display
 * @param onStartFirstConversationClick Callback when "Start First Conversation" button is clicked
 * @param onExploreChatBotsClick Callback when "explore chat bots" link is clicked
 * @param modifier Optional modifier for styling
 */
@Composable
fun AIChatEmptyState(
    features: List<ChatFeature>,
    onStartFirstConversationClick: () -> Unit,
    onExploreChatBotsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Robot/AI icon
        Text(
            text = "🤖",
            style = MaterialTheme.typography.displayMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = "Meet Your AI Language Tutors",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Description
        Text(
            text = "Start conversations with AI tutors who understand your learning needs. Practice real-world scenarios, get instant feedback, and improve your English through natural dialogue.",
            style = MaterialTheme.typography.bodyLarge,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Start First Conversation Button
        Button(
            onClick = onStartFirstConversationClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = WordBridgeColors.PrimaryPurple
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "💬 Start Your First Conversation",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Explore chat bots link
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "or ",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary
            )
            
            TextButton(
                onClick = onExploreChatBotsClick
            ) {
                Text(
                    text = "explore our AI tutors",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Features Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(400.dp) // Fixed height to prevent scroll conflicts
        ) {
            items(features) { feature ->
                ChatFeatureCard(
                    feature = feature
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Feature card component for chat features
 */
@Composable
private fun ChatFeatureCard(
    feature: ChatFeature,
    modifier: Modifier = Modifier
) {
    val featureColor = try {
        androidx.compose.ui.graphics.Color(feature.color.removePrefix("#").toLong(16) or 0xFF000000)
    } catch (e: Exception) {
        WordBridgeColors.PrimaryPurple
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            hoveredElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        featureColor.copy(alpha = 0.1f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feature.icon,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}
