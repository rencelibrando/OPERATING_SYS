package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.VocabularyFeature
import org.example.project.ui.theme.WordBridgeColors


@Composable
fun VocabularyFeatureCard(
    feature: VocabularyFeature,
    modifier: Modifier = Modifier
) {
    val featureColor = try {
        Color(feature.color.removePrefix("#").toLong(16) or 0xFF000000)
    } catch (e: Exception) {
        WordBridgeColors.PrimaryPurple
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(featureColor.copy(alpha = 0.1f)),
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
