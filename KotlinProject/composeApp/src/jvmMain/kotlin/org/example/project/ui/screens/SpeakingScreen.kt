package org.example.project.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.models.PracticeLanguage
import org.example.project.models.PracticeFeedback
import org.example.project.domain.model.VocabularyWord
import org.example.project.presentation.viewmodel.SpeakingViewModel
import org.example.project.ui.components.LanguageSelectionDialog
import org.example.project.ui.components.SpeakingEmptyState
import org.example.project.ui.components.UserAvatar
import org.example.project.ui.components.VoiceTutorSelectionFlow
import org.example.project.ui.components.ConversationModeCard
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
    modifier: Modifier = Modifier,
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
    val showVoiceTutorSelection by viewModel.showVoiceTutorSelection
    val voiceTutorLanguage by viewModel.voiceTutorLanguage
    val voiceTutorLevel by viewModel.voiceTutorLevel
    val voiceTutorScenario by viewModel.voiceTutorScenario
    val currentPrompt by viewModel.currentPrompt

    // Show language selection dialog
    if (showLanguageDialog && currentWord != null) {
        LanguageSelectionDialog(
            wordToLearn = currentWord!!.word,
            onLanguageSelected = viewModel::onLanguageSelected,
            onDismiss = viewModel::hideLanguageDialog,
        )
    }

    // Show Voice Tutor selection flow
    if (showVoiceTutorSelection) {
        VoiceTutorSelectionFlow(
            onStartPractice = viewModel::startVoiceTutorPractice,
            onBack = viewModel::hideVoiceTutorSelection,
            modifier = Modifier.fillMaxSize()
        )
        return // Don't show other content when selection is active
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(WordBridgeColors.BackgroundLight)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Header - shows either back button OR user avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Show back button if onBackClick is provided (from VocabularyScreen)
            if (onBackClick != null) {
                TextButton(onClick = onBackClick) {
                    Text(
                        text = "← Back to Vocabulary",
                        color = WordBridgeColors.TextPrimary,
                    )
                }
            } else {
                // Otherwise show title (from sidebar navigation)
                Text(
                    text = "Speaking Practice",
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )
            }

            // Always show user avatar on the right
            UserAvatar(
                initials = authenticatedUser?.initials ?: "U",
                profileImageUrl = authenticatedUser?.profileImageUrl,
                size = 48.dp,
                onClick = onUserAvatarClick,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main content - show Voice Tutor practice, word practice, or empty state
        if (voiceTutorLanguage != null && voiceTutorLevel != null && voiceTutorScenario != null && currentPrompt != null) {
            // Voice Tutor Practice Mode with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = viewModel::exitVoiceTutorPractice,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = WordBridgeColors.TextPrimary
                    )
                ) {
                    Text("← Back to Selection")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode toggle
            val isConversationMode = viewModel.isConversationMode.value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WordBridgeColors.BackgroundWhite,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exitConversationMode() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isConversationMode) WordBridgeColors.PrimaryPurple else Color.Transparent,
                                contentColor = if (!isConversationMode) Color.White else WordBridgeColors.TextPrimary
                            ),
                            elevation = if (!isConversationMode) ButtonDefaults.buttonElevation(2.dp) else ButtonDefaults.buttonElevation(0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Practice Mode")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.enterConversationMode() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConversationMode) WordBridgeColors.PrimaryPurple else Color.Transparent,
                                contentColor = if (isConversationMode) Color.White else WordBridgeColors.TextPrimary
                            ),
                            elevation = if (isConversationMode) ButtonDefaults.buttonElevation(2.dp) else ButtonDefaults.buttonElevation(0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Conversation Mode")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show appropriate UI based on mode
            if (isConversationMode) {
                // Conversation Mode UI
                val conversationTurns = viewModel.conversationTurns.value
                val isAgentSpeaking = viewModel.isAgentSpeaking.value
                val isConversationRecording = viewModel.isConversationRecording.value
                val isConversationActive = viewModel.isConversationActive.value
                
                ConversationModeCard(
                    language = voiceTutorLanguage!!,
                    level = voiceTutorLevel!!,
                    scenario = voiceTutorScenario!!,
                    conversationTurns = conversationTurns,
                    isAgentSpeaking = isAgentSpeaking,
                    isRecording = isConversationRecording,
                    isConversationActive = isConversationActive,
                    onStartConversation = viewModel::startConversationMode,
                    onStopConversation = viewModel::stopConversationMode,
                    onStartRecording = viewModel::startConversationRecording,
                    onStopRecording = viewModel::stopConversationRecording
                )
            } else {
                // Practice Mode UI
                VoiceTutorPracticeCard(
                    language = voiceTutorLanguage!!,
                    level = voiceTutorLevel!!,
                    scenario = voiceTutorScenario!!,
                    prompt = currentPrompt!!
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Recording Controls
                RecordingControlsCard(
                    isRecording = isRecording,
                    hasRecording = hasRecording,
                    isPlayingRecording = isPlayingRecording,
                    recordingDuration = recordingDuration,
                    onToggleRecording = viewModel::toggleRecording,
                    onPlayRecording = viewModel::playRecording,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Analyzing indicator
                if (isAnalyzing) {
                    AnalyzingIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Feedback Card
                if (feedback != null && !isAnalyzing) {
                    FeedbackCard(
                        feedback = feedback!!,
                        onTryAgain = viewModel::tryAgain,
                        onComplete = viewModel::completePractice,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

        } else if (currentWord != null && selectedLanguage != null) {
            // Word Information Card
            WordInfoCard(
                word = currentWord!!.word,
                definition = currentWord!!.definition,
                example = currentWord!!.examples.firstOrNull() ?: "No example available",
                language = selectedLanguage!!,
                pronunciation = currentWord!!.pronunciation,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Recording Controls
            RecordingControlsCard(
                isRecording = isRecording,
                hasRecording = hasRecording,
                isPlayingRecording = isPlayingRecording,
                recordingDuration = recordingDuration,
                onToggleRecording = viewModel::toggleRecording,
                onPlayRecording = viewModel::playRecording,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Analyzing indicator
            if (isAnalyzing) {
                AnalyzingIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Feedback Card
            if (feedback != null && !isAnalyzing) {
                FeedbackCard(
                    feedback = feedback!!,
                    onTryAgain = viewModel::tryAgain,
                    onComplete = {
                        viewModel.completePractice()
                        onBackClick?.invoke() // Go back to vocabulary if available
                    },
                )
            }
        } else {
            // Empty state when accessed from sidebar (no word selected)
            SpeakingEmptyState(
                features = viewModel.speakingFeatures.value,
                onStartFirstPracticeClick = viewModel::onStartFirstPracticeClicked,
                onExploreExercisesClick = viewModel::onExploreExercisesClicked,
                onStartConversationClick = viewModel::onStartConversationClicked,
            )
        }
    }
}

/**
 * Card showing Voice Tutor practice information
 */
@Composable
private fun VoiceTutorPracticeCard(
    language: PracticeLanguage,
    level: String,
    scenario: String,
    prompt: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.BackgroundWhite,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            // Header with language and level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Language indicator
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = language.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = WordBridgeColors.PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Level badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (level) {
                        "beginner" -> WordBridgeColors.AccentGreen.copy(alpha = 0.1f)
                        "intermediate" -> WordBridgeColors.AccentOrange.copy(alpha = 0.1f)
                        "advanced" -> WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f)
                        else -> WordBridgeColors.TextMuted.copy(alpha = 0.1f)
                    }
                ) {
                    Text(
                        text = level.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = when (level) {
                            "beginner" -> WordBridgeColors.AccentGreen
                            "intermediate" -> WordBridgeColors.AccentOrange
                            "advanced" -> WordBridgeColors.PrimaryPurple
                            else -> WordBridgeColors.TextMuted
                        },
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scenario tag
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WordBridgeColors.AccentBlue.copy(alpha = 0.1f)
            ) {
                Text(
                    text = scenario.replaceFirstChar { it.uppercase() }.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    color = WordBridgeColors.AccentBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = WordBridgeColors.ProgressBackground)

            Spacer(modifier = Modifier.height(16.dp))

            // Practice prompt section
            Text(
                text = "Practice Prompt",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Prominent prompt display
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WordBridgeColors.BackgroundLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "\"$prompt\"",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 28.sp
                    ),
                    color = WordBridgeColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instructions
            Text(
                text = "Speak this phrase clearly in ${language.displayName}. The AI will analyze your pronunciation, fluency, and accuracy.",
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
                lineHeight = 18.sp
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.BackgroundWhite,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            // Language badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f)
            ) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = WordBridgeColors.PrimaryPurple,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Word
            Text(
                text = word,
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            if (pronunciation.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pronunciation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = WordBridgeColors.ProgressBackground)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Definition",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = definition,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Example",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WordBridgeColors.BackgroundLight,
            ) {
                Text(
                    text = "\"$example\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextPrimary,
                    modifier = Modifier.padding(12.dp),
                    lineHeight = 20.sp
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isRecording) WordBridgeColors.AccentRed.copy(alpha = 0.05f) else WordBridgeColors.BackgroundWhite,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Recording",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text =
                    if (isRecording) {
                        "Recording... Speak clearly!"
                    } else if (hasRecording) {
                        "Recording saved. Play it back or record again."
                    } else {
                        "Press the microphone button to start recording"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Microphone Button
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing background ring for recording state
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(WordBridgeColors.AccentRed.copy(alpha = 0.15f))
                    )
                }

                // Main button
                IconButton(
                    onClick = onToggleRecording,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) WordBridgeColors.AccentRed else WordBridgeColors.PrimaryPurple,
                        ),
                ) {
                    // Microphone icon using Canvas
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(28.dp)
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val iconColor = Color.White

                        if (isRecording) {
                            // Stop icon (square)
                            drawRoundRect(
                                color = iconColor,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    canvasWidth * 0.25f,
                                    canvasHeight * 0.25f
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    canvasWidth * 0.5f,
                                    canvasHeight * 0.5f
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                            )
                        } else {
                            // Microphone icon
                            // Mic body (rounded rectangle)
                            drawRoundRect(
                                color = iconColor,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    canvasWidth * 0.35f,
                                    canvasHeight * 0.2f
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    canvasWidth * 0.3f,
                                    canvasHeight * 0.35f
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                    canvasWidth * 0.15f,
                                    canvasWidth * 0.15f
                                )
                            )

                            // Mic stand (vertical line)
                            drawLine(
                                color = iconColor,
                                start = androidx.compose.ui.geometry.Offset(
                                    canvasWidth * 0.5f,
                                    canvasHeight * 0.55f
                                ),
                                end = androidx.compose.ui.geometry.Offset(
                                    canvasWidth * 0.5f,
                                    canvasHeight * 0.75f
                                ),
                                strokeWidth = 3f
                            )

                            // Mic base (horizontal line)
                            drawLine(
                                color = iconColor,
                                start = androidx.compose.ui.geometry.Offset(
                                    canvasWidth * 0.35f,
                                    canvasHeight * 0.75f
                                ),
                                end = androidx.compose.ui.geometry.Offset(
                                    canvasWidth * 0.65f,
                                    canvasHeight * 0.75f
                                ),
                                strokeWidth = 3f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recording duration
            AnimatedVisibility(visible = isRecording || hasRecording) {
                Text(
                    text = "${String.format("%.1f", recordingDuration)}s",
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    color = if (isRecording) WordBridgeColors.AccentRed else WordBridgeColors.TextPrimary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Playback Button
            AnimatedVisibility(visible = hasRecording && !isRecording) {
                Button(
                    onClick = onPlayRecording,
                    enabled = !isPlayingRecording,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.AccentGreen,
                        ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = if (isPlayingRecording) "Playing..." else "Play Recording",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
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
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.AccentOrange.copy(alpha = 0.08f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = WordBridgeColors.AccentOrange,
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Analyzing Your Pronunciation",
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )

                Text(
                    text = "AI is processing your recording",
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondary,
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
    feedback: PracticeFeedback,
    onTryAgain: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scoreColor =
        when {
            feedback.overallScore >= 85 -> WordBridgeColors.AccentGreen
            feedback.overallScore >= 70 -> WordBridgeColors.AccentOrange
            else -> WordBridgeColors.AccentRed
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.BackgroundWhite,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Text(
                text = "Your Performance",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Overall Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(scoreColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${feedback.overallScore}",
                        style =
                            MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = scoreColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ScoreItem(
                    label = "Pronunciation",
                    score = feedback.pronunciationScore,
                    color = WordBridgeColors.AccentBlue,
                )

                ScoreItem(
                    label = "Clarity",
                    score = feedback.clarityScore,
                    color = WordBridgeColors.AccentGreen,
                )

                ScoreItem(
                    label = "Fluency",
                    score = feedback.fluencyScore,
                    color = WordBridgeColors.AccentOrange,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = WordBridgeColors.ProgressBackground)

            Spacer(modifier = Modifier.height(16.dp))

            // Feedback Messages
            Text(
                text = "Feedback",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            feedback.messages.forEach { message ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Suggestions
            Text(
                text = "Tips for Improvement",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            feedback.suggestions.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WordBridgeColors.BackgroundLight,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextPrimary,
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onTryAgain,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = WordBridgeColors.PrimaryPurple
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, WordBridgeColors.PrimaryPurple)
                ) {
                    Text(
                        text = "Try Again",
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }

                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple,
                        ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "Complete",
                        modifier = Modifier.padding(vertical = 2.dp),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$score%",
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = color,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = WordBridgeColors.TextSecondary,
        )
    }
}
