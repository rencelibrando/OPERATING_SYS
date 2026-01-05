package org.example.project.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LanguageProgress
import org.example.project.domain.model.VoiceAnalysisScores
import org.example.project.ui.theme.WordBridgeColors

/**
 * Comprehensive progress card for a single language.
 * Shows lessons, conversations, vocabulary, voice analysis, and time metrics.
 */
@Composable
fun LanguageProgressCard(
    progress: LanguageProgress,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.CardBackgroundDark
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header with language and overall score
            ProgressHeader(
                language = progress.language.displayName,
                languageEmoji = getLanguageEmoji(progress.language.code),
                overallScore = progress.voiceAnalysis.averageScore
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics Grid (2x2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricCard(
                        icon = "📚",
                        label = "Lessons",
                        value = "${progress.lessonsCompleted}/${progress.totalLessons}",
                        percentage = progress.lessonsProgressPercentage
                    )
                    
                    MetricCard(
                        icon = "💬",
                        label = "Sessions",
                        value = progress.conversationSessions.toString()
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricCard(
                        icon = "📖",
                        label = "Vocabulary",
                        value = "${progress.vocabularyWords} words"
                    )
                    
                    MetricCard(
                        icon = "⏱️",
                        label = "Practice Time",
                        value = progress.formattedTime
                    )
                }
            }

            // Voice Analysis Scores (expandable)
            if (progress.voiceAnalysis.hasScores) {
                Spacer(modifier = Modifier.height(20.dp))
                VoiceAnalysisSection(scores = progress.voiceAnalysis)
            }
        }
    }
}

@Composable
private fun ProgressHeader(
    language: String,
    languageEmoji: String,
    overallScore: Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = languageEmoji,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = language,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimaryDark
            )
        }

        if (overallScore > 0) {
            ScoreBadge(score = overallScore)
        }
    }
}

@Composable
private fun ScoreBadge(
    score: Double,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
        score >= 80 -> Color(0xFF10B981)
        score >= 60 -> Color(0xFFF59E0B)
        score >= 40 -> Color(0xFFEF4444)
        else -> Color(0xFF6B7280)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "⭐",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${score.toInt()}/100",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = backgroundColor
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: String,
    label: String,
    value: String,
    percentage: Int? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleLarge
                )
                
                if (percentage != null && percentage > 0) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = WordBridgeColors.TextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimaryDark
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondaryDark
            )
        }
    }
}

@Composable
private fun VoiceAnalysisSection(
    scores: VoiceAnalysisScores,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Divider(color = Color(0xFFE5E7EB))
        
        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { isExpanded = !isExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎤 Voice Analysis Scores",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimaryDark
                )
                
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondaryDark
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (scores.pronunciation > 0) {
                    ScoreRow("Pronunciation", scores.pronunciation)
                }
                if (scores.fluency > 0) {
                    ScoreRow("Fluency", scores.fluency)
                }
                if (scores.grammar > 0) {
                    ScoreRow("Grammar", scores.grammar)
                }
                if (scores.vocabulary > 0) {
                    ScoreRow("Vocabulary", scores.vocabulary)
                }
                if (scores.accuracy > 0) {
                    ScoreRow("Accuracy", scores.accuracy)
                }
                if (scores.overall > 0) {
                    ScoreRow("Overall", scores.overall, isOverall = true)
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(
    label: String,
    score: Double,
    isOverall: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scoreColor = when {
        score >= 80 -> Color(0xFF10B981)
        score >= 60 -> Color(0xFFF59E0B)
        score >= 40 -> Color(0xFFFF8C42)
        else -> Color(0xFFEF4444)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isOverall) {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = WordBridgeColors.TextPrimaryDark
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Progress bar
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE5E7EB))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (score / 100).toFloat())
                        .fillMaxHeight()
                        .background(scoreColor)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "${score.toInt()}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isOverall) FontWeight.Bold else FontWeight.Normal
                ),
                color = scoreColor,
                modifier = Modifier.width(30.dp)
            )
        }
    }
}

@Composable
private fun getLanguageEmoji(code: String): String {
    return when (code) {
        "ko" -> "🇰🇷"
        "zh" -> "🇨🇳"
        "fr" -> "🇫🇷"
        "de" -> "🇩🇪"
        "es" -> "🇪🇸"
        else -> "🌍"
    }
}
