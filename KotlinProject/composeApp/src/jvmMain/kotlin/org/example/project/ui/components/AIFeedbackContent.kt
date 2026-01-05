package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.domain.model.ConversationFeedback
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun AIFeedbackContent(
    feedback: ConversationFeedback,
    isCompactScreen: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OverallScoreCard(
            score = feedback.overallScore,
            label = feedback.getScoreLabel(),
            isCompactScreen = isCompactScreen
        )
        
        ScoreBreakdownSection(
            grammarScore = feedback.grammarScore,
            pronunciationScore = feedback.pronunciationScore,
            vocabularyScore = feedback.vocabularyScore,
            fluencyScore = feedback.fluencyScore,
            isCompactScreen = isCompactScreen
        )
        
        DetailedAnalysisSection(
            analysis = feedback.detailedAnalysis
        )
        
        StrengthsSection(
            strengths = feedback.strengths
        )
        
        ImprovementAreasSection(
            areas = feedback.areasForImprovement
        )
        
        SpecificExamplesSection(
            examples = feedback.specificExamples
        )
        
        SuggestionsSection(
            suggestions = feedback.suggestions
        )
    }
}

@Composable
private fun OverallScoreCard(
    score: Int,
    label: String,
    isCompactScreen: Boolean
) {
    val scoreColor = when {
        score >= 80 -> Color(0xFF10B981)
        score >= 60 -> Color(0xFFF59E0B)
        score >= 40 -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = scoreColor.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompactScreen) 16.dp else 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Overall Performance",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimaryDark
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = scoreColor
                )
            }
            
            Surface(
                shape = CircleShape,
                color = scoreColor,
                modifier = Modifier.size(if (isCompactScreen) 60.dp else 72.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isCompactScreen) 24.sp else 28.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreBreakdownSection(
    grammarScore: Int,
    pronunciationScore: Int,
    vocabularyScore: Int,
    fluencyScore: Int,
    isCompactScreen: Boolean
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Score Breakdown",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimaryDark
            )
            
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScoreBar(
                    label = "Grammar",
                    score = grammarScore,
                    icon = "📝"
                )
                ScoreBar(
                    label = "Pronunciation",
                    score = pronunciationScore,
                    icon = "🗣️"
                )
                ScoreBar(
                    label = "Vocabulary",
                    score = vocabularyScore,
                    icon = "📚"
                )
                ScoreBar(
                    label = "Fluency",
                    score = fluencyScore,
                    icon = "💬"
                )
            }
        }
    }
}

@Composable
private fun ScoreBar(
    label: String,
    score: Int,
    icon: String
) {
    val scoreColor = when {
        score >= 80 -> Color(0xFF10B981)
        score >= 60 -> Color(0xFFF59E0B)
        score >= 40 -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = WordBridgeColors.TextPrimaryDark
                )
            }
            
            Text(
                text = "$score/100",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = scoreColor
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    Color.Gray.copy(alpha = 0.2f),
                    RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score / 100f)
                    .fillMaxHeight()
                    .background(
                        scoreColor,
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
private fun DetailedAnalysisSection(
    analysis: String
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Detailed Analysis",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimaryDark
            )
            
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp
                )
            ) {
                Text(
                    text = analysis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextPrimaryDark,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StrengthsSection(
    strengths: List<String>
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Key Strengths",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimaryDark
                )
            }
            
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                strengths.forEach { strength ->
                    FeedbackItem(
                        text = strength,
                        icon = "✅",
                        backgroundColor = Color(0xFF10B981).copy(alpha = 0.1f),
                        textColor = WordBridgeColors.TextPrimaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun ImprovementAreasSection(
    areas: List<String>
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Areas for Improvement",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimaryDark
                )
            }
            
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                areas.forEach { area ->
                    FeedbackItem(
                        text = area,
                        icon = "💡",
                        backgroundColor = Color(0xFFF59E0B).copy(alpha = 0.1f),
                        textColor = WordBridgeColors.TextPrimaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecificExamplesSection(
    examples: List<org.example.project.domain.model.FeedbackExample>
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Specific Examples",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimaryDark
                )
            }
            
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                examples.forEach { example ->
                    ExampleCard(example = example)
                }
            }
        }
    }
}

@Composable
private fun ExampleCard(
    example: org.example.project.domain.model.FeedbackExample
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "You said:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextSecondaryDark
                )
                Text(
                    text = "\"${example.userUtterance}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextPrimaryDark,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (example.issue.isNotBlank()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Issue:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFFF59E0B)
                    )
                    Text(
                        text = example.issue,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextPrimaryDark
                    )
                }
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Better:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF10B981)
                )
                Text(
                    text = "\"${example.correction}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Why:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextSecondaryDark
                )
                Text(
                    text = example.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextPrimaryDark,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SuggestionsSection(
    suggestions: List<String>
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💪",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Actionable Suggestions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimaryDark
                )
            }
            
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    FeedbackItem(
                        text = suggestion,
                        icon = "🚀",
                        backgroundColor = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f),
                        textColor = WordBridgeColors.TextPrimaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackItem(
    text: String,
    icon: String,
    backgroundColor: Color,
    textColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
