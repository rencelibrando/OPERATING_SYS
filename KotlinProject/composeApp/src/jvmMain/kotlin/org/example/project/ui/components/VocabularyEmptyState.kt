package org.example.project.ui.components

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
import org.example.project.domain.model.VocabularyFeature
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun VocabularyEmptyState(
    features: List<VocabularyFeature>,
    onAddFirstWordClick: () -> Unit,
    onExploreLessonsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Book icon
        Text(
            text = "📖",
            style = MaterialTheme.typography.displayMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = "Your Vocabulary Library Awaits",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Description
        Text(
            text = "Start building your personal vocabulary collection! Add words you encounter during lessons, reading, or conversations. Our AI-powered system will help you master each word with personalized practice and smart review schedules.",
            style = MaterialTheme.typography.bodyLarge,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Add First Word Button
        Button(
            onClick = onAddFirstWordClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = WordBridgeColors.PrimaryPurple
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "+ Add Your First Word",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Explore lessons link
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "or ",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary
            )
            
            TextButton(
                onClick = onExploreLessonsClick
            ) {
                Text(
                    text = "explore lessons to discover new vocabulary",
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
                VocabularyFeatureCard(
                    feature = feature
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
