package org.example.project.ui.components

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
import org.example.project.domain.model.VocabularyFeature
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun VocabularyEmptyState(
    features: List<VocabularyFeature>,
    onAddFirstWordClick: () -> Unit,
    onExploreLessonsClick: () -> Unit,
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
            imageVector = WordBridgeIcons.Vocabulary,
            contentDescription = null,
            tint = WordBridgeColors.Primary,
            modifier = Modifier.size(44.dp),
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Your Vocabulary Library Awaits",
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Build your personalized vocabulary bank. Save words from lessons, practice with smart spaced repetition, and track your retention over time.",
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onAddFirstWordClick,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = WordBridgeColors.Primary,
                    contentColor = WordBridgeColors.OnPrimary,
                ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Add Your First Word",
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
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
                onClick = onExploreLessonsClick,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = WordBridgeColors.Primary,
                    ),
            ) {
                Text(
                    text = "explore lessons to discover new vocabulary",
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
                VocabularyFeatureCard(feature = feature)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
