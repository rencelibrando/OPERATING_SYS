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
import org.example.project.domain.model.SpeakingFeature
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun SpeakingEmptyState(
    features: List<SpeakingFeature>,
    onStartFirstPracticeClick: () -> Unit,
    onExploreExercisesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "🎤",
            style = MaterialTheme.typography.displayMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Perfect Your Speaking Skills",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Start your journey to confident English speaking! Practice pronunciation, conversation skills, and fluency with AI-powered feedback. Record yourself, get instant analysis, and track your improvement over time.",
            style = MaterialTheme.typography.bodyLarge,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onStartFirstPracticeClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = WordBridgeColors.PrimaryPurple
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "🎙️ Start Your First Practice",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "or ",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary
            )
            
            TextButton(
                onClick = onExploreExercisesClick
            ) {
                Text(
                    text = "explore speaking exercises",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(400.dp) // Fixed height to prevent scroll conflicts
        ) {
            items(features) { feature ->
                SpeakingFeatureCard(
                    feature = feature
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}


@Composable
private fun SpeakingFeatureCard(
    feature: SpeakingFeature,
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
            
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}
