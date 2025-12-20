package org.example.project.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.PracticeLanguage
import org.example.project.presentation.viewmodel.SpeakingViewModel
import org.example.project.ui.components.LanguageSelectionDialog
import org.example.project.ui.components.SpeakingEmptyState
import org.example.project.ui.components.UserAvatar
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.core.auth.User as AuthUser

/**
 * Main Speaking Practice Screen
 * Shows word details, recording controls, and AI feedback
 */
@Composable
fun SpeakingScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null, // Optional back navigation
    viewModel: SpeakingViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val currentWord by viewModel.currentWord
    val selectedLanguage by viewModel.selectedLanguage
    val isRecording by viewModel.isRecording
    val hasRecording by viewModel.hasRecording
    val isPlayingRecording by viewModel.isPlayingRecording
    val feedback by viewModel.feedback
    val isAnalyzing by viewModel.isAnalyzing
    val showLanguageDialog by viewModel.showLanguageDialog
    val recordingDuration by viewModel.recordingDuration
    val isGeneratingAudio by viewModel.isGeneratingAudio

    // Show language selection dialog
    if (showLanguageDialog && currentWord != null) {
        LanguageSelectionDialog(
            wordToLearn = currentWord!!.word,
            onLanguageSelected = viewModel::onLanguageSelected,
            onDismiss = viewModel::hideLanguageDialog
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WordBridgeColors.BackgroundLight)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header - shows either back button OR user avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show back button if onBackClick is provided (from VocabularyScreen)
            if (onBackClick != null) {
                TextButton(onClick = onBackClick) {
                    Text(
                        text = "← Back to Vocabulary",
                        color = WordBridgeColors.TextSecondary
                    )
                }
            } else {
                // Otherwise show title (from sidebar navigation)
                Text(
                    text = "Speaking Practice",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimary
                )
            }

            // Always show user avatar on the right
            UserAvatar(
                initials = authenticatedUser?.initials ?: "U",
                profileImageUrl = authenticatedUser?.profileImageUrl,
                size = 48.dp,
                onClick = onUserAvatarClick
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main content - show word practice if we have a word, otherwise show empty state
        if (currentWord != null && selectedLanguage != null) {
            // Word Information Card
            WordInfoCard(
                word = currentWord!!.word,
                definition = currentWord!!.definition,
                example = currentWord!!.examples.firstOrNull() ?: "No example available",
                language = selectedLanguage!!,
                pronunciation = currentWord!!.pronunciation,
                audioUrl = currentWord!!.audioUrl,
                isGeneratingAudio = isGeneratingAudio,
                onPlayAudio = { audioUrl ->
                    viewModel.playReferenceAudio(audioUrl)
                },
                onGenerateAudio = {
                    viewModel.generateReferenceAudioManually()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Recording Controls
            RecordingControlsCard(
                isRecording = isRecording,
                hasRecording = hasRecording,
                isPlayingRecording = isPlayingRecording,
                recordingDuration = recordingDuration,
                onToggleRecording = viewModel::toggleRecording,
                onPlayRecording = viewModel::playRecording
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Analyzing indicator
            if (isAnalyzing) {
                AnalyzingIndicator()
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Feedback Card
            if (feedback != null && !isAnalyzing) {
                FeedbackCard(
                    feedback = feedback!!,
                    onTryAgain = viewModel::tryAgain,
                    onComplete = {
                        viewModel.completePractice()
                        onBackClick?.invoke() // Go back to vocabulary if available
                    }
                )
            }
        } else {
            // Empty state when accessed from sidebar (no word selected)
            SpeakingEmptyState(
                features = viewModel.speakingFeatures.value,
                onStartFirstPracticeClick = viewModel::onStartFirstPracticeClicked,
                onExploreExercisesClick = viewModel::onExploreExercisesClicked
            )
        }
    }
}

/**
 * Card showing word information
 */
@Composable
private fun WordInfoCard(
    word: String,
    definition: String,
    example: String,
    language: PracticeLanguage,
    pronunciation: String,
    audioUrl: String? = null,
    isGeneratingAudio: Boolean = false,
    onPlayAudio: (String) -> Unit = {},
    onGenerateAudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.flag,
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Practicing in ${language.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary
                    )

                    Text(
                        text = word,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = WordBridgeColors.TextPrimary
                    )

                    if (pronunciation.isNotBlank()) {
                        Text(
                            text = pronunciation,
                            style = MaterialTheme.typography.bodyLarge,
                            color = WordBridgeColors.TextSecondary
                        )
                    }
                }
            }
            
            // Play audio button or generate audio button
            Spacer(modifier = Modifier.height(12.dp))
            if (!audioUrl.isNullOrBlank()) {
                Button(
                    onClick = { onPlayAudio(audioUrl!!) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.PrimaryPurple
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isGeneratingAudio
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Play pronunciation",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔊 Listen to Pronunciation",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onGenerateAudio,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = WordBridgeColors.PrimaryPurple
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isGeneratingAudio
                ) {
                    if (isGeneratingAudio) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = WordBridgeColors.PrimaryPurple
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generating Audio...",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Generate pronunciation audio",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate Pronunciation Audio",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = WordBridgeColors.TextMuted.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Definition",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = definition,
                style = MaterialTheme.typography.bodyLarge,
                color = WordBridgeColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Example",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF3F4F6)
            ) {
                Text(
                    text = "\"$example\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/**
 * Card with recording controls
 */
@Composable
private fun RecordingControlsCard(
    isRecording: Boolean,
    hasRecording: Boolean,
    isPlayingRecording: Boolean,
    recordingDuration: Float,
    onToggleRecording: () -> Unit,
    onPlayRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) Color(0xFFFFEBEE) else WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎤 Record Your Pronunciation",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isRecording) {
                    "Recording... Speak clearly!"
                } else if (hasRecording) {
                    "Recording saved! Play it back or record again."
                } else {
                    "Press the microphone button to start recording"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Microphone Button
            IconButton(
                onClick = onToggleRecording,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) Color(0xFFEF4444) else WordBridgeColors.PrimaryPurple
                    )
            ) {
                Text(
                    text = if (isRecording) "⏹️" else "🎤",
                    style = MaterialTheme.typography.displayMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recording duration
            AnimatedVisibility(visible = isRecording || hasRecording) {
                Text(
                    text = "Duration: ${String.format("%.1f", recordingDuration)}s",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isRecording) Color(0xFFEF4444) else WordBridgeColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Button
            AnimatedVisibility(visible = hasRecording && !isRecording) {
                Button(
                    onClick = onPlayRecording,
                    enabled = !isPlayingRecording,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isPlayingRecording) "▶️ Playing..." else "▶️ Play Recording",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Analyzing indicator
 */
@Composable
private fun AnalyzingIndicator(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFEF3E2)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFFF59E0B)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "🧠 Analyzing Your Pronunciation...",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )

                Text(
                    text = "AI is processing your recording",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Feedback card with scores and suggestions
 */
@Composable
private fun FeedbackCard(
    feedback: org.example.project.presentation.viewmodel.PracticeFeedback,
    onTryAgain: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scoreColor = when {
        feedback.overallScore >= 85 -> Color(0xFF10B981)
        feedback.overallScore >= 70 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "📊 Your Performance",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Overall Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(scoreColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${feedback.overallScore}",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = scoreColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Score Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreItem(
                    label = "Pronunciation",
                    score = feedback.pronunciationScore,
                    color = Color(0xFF3B82F6)
                )

                ScoreItem(
                    label = "Clarity",
                    score = feedback.clarityScore,
                    color = Color(0xFF10B981)
                )

                ScoreItem(
                    label = "Fluency",
                    score = feedback.fluencyScore,
                    color = Color(0xFFF59E0B)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(color = WordBridgeColors.TextMuted.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(24.dp))

            // Feedback Messages
            Text(
                text = "Feedback",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            feedback.messages.forEach { message ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextPrimary
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Suggestions
            Text(
                text = "💡 Tips for Improvement",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            feedback.suggestions.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onTryAgain,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "🔄 Try Again",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.PrimaryPurple
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "✓ Complete",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual score item
 */
@Composable
private fun ScoreItem(
    label: String,
    score: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$score%",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = color
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = WordBridgeColors.TextSecondary
        )
    }
}