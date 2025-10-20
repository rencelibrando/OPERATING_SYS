package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.example.project.presentation.viewmodel.PracticeLanguage
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun LanguageSelectionDialog(
    wordToLearn: String,
    onLanguageSelected: (PracticeLanguage) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .width(600.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = WordBridgeColors.BackgroundWhite
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                // Header
                Text(
                    text = "🌍 Choose Your Practice Language",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Practice pronouncing \"$wordToLearn\" in:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WordBridgeColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Language Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // First row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LanguageCard(
                            language = PracticeLanguage.FRENCH,
                            onClick = { onLanguageSelected(PracticeLanguage.FRENCH) },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageCard(
                            language = PracticeLanguage.GERMAN,
                            onClick = { onLanguageSelected(PracticeLanguage.GERMAN) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Second row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LanguageCard(
                            language = PracticeLanguage.HANGEUL,
                            onClick = { onLanguageSelected(PracticeLanguage.HANGEUL) },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageCard(
                            language = PracticeLanguage.MANDARIN,
                            onClick = { onLanguageSelected(PracticeLanguage.MANDARIN) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Third row (centered)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        LanguageCard(
                            language = PracticeLanguage.SPANISH,
                            onClick = { onLanguageSelected(PracticeLanguage.SPANISH) },
                            modifier = Modifier.width(270.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Individual language selection card
 */
@Composable
private fun LanguageCard(
    language: PracticeLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundLight
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            hoveredElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Flag emoji
            Text(
                text = language.flag,
                style = MaterialTheme.typography.displayMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Language name
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = language.description.substringAfter("Practice ").substringBefore(" pronunciation"),
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}