package org.example.project.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.domain.model.PracticeFeedback
import org.example.project.domain.model.PracticeLanguage
import org.example.project.presentation.viewmodel.SpeakingViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import kotlin.reflect.KClass
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
    modifier: Modifier = Modifier,
) {
    // Create ViewModel with user ID
    val viewModel: SpeakingViewModel =
        viewModel(
            factory =
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(
                        modelClass: KClass<T>,
                        extras: CreationExtras,
                    ): T {
                        @Suppress("UNCHECKED_CAST")
                        return SpeakingViewModel(
                            userId = authenticatedUser?.id ?: "current_user",
                        ) as T
                    }
                },
        )
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
    val conversationError by viewModel.conversationError

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
            modifier = Modifier.fillMaxSize(),
        )
        return // Don't show other content when selection is active
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(WordBridgeColors.BackgroundMain)
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        color = WordBridgeColors.TextPrimaryDark,
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
                    color = WordBridgeColors.TextPrimaryDark,
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

        Spacer(modifier = Modifier.height(10.dp))

        // Main content - show Voice Tutor practice, word practice, or empty state
        if (voiceTutorLanguage != null && voiceTutorLevel != null && voiceTutorScenario != null && currentPrompt != null) {
            // Compact back button + mode toggle in one row
            val isConversationMode = viewModel.isConversationMode.value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button
                TextButton(
                    onClick = viewModel::exitVoiceTutorPractice,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = WordBridgeColors.TextSecondaryDark,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("← Back", style = MaterialTheme.typography.labelMedium)
                }

                // Compact mode toggle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WordBridgeColors.CardBackgroundDark,
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                    ) {
                        Surface(
                            onClick = { viewModel.exitConversationMode() },
                            shape = RoundedCornerShape(6.dp),
                            color = if (!isConversationMode) WordBridgeColors.PrimaryPurple else Color.Transparent,
                        ) {
                            Text(
                                text = "Practice",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!isConversationMode) Color.White else WordBridgeColors.TextSecondaryDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Surface(
                            onClick = { viewModel.startConversationMode(autoStartAgent = false) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isConversationMode) WordBridgeColors.PrimaryPurple else Color.Transparent,
                        ) {
                            Text(
                                text = "Conversation",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConversationMode) Color.White else WordBridgeColors.TextSecondaryDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Show appropriate UI based on mode
            if (isConversationMode) {
                // Conversation Mode UI
                val conversationTurns = viewModel.conversationTurns
                val isAgentSpeaking = viewModel.isAgentSpeaking.value
                val isAgentThinking = viewModel.isAgentThinking.value
                val isConversationRecording = viewModel.isConversationRecording.value
                val isConversationActive = viewModel.isConversationActive.value

                ConversationModeCard(
                    language = voiceTutorLanguage!!,
                    level = voiceTutorLevel!!,
                    scenario = voiceTutorScenario!!,
                    conversationTurns = conversationTurns,
                    isAgentSpeaking = isAgentSpeaking,
                    isAgentThinking = isAgentThinking,
                    isRecording = isConversationRecording,
                    isConversationActive = isConversationActive,
                    audioLevel = viewModel.audioLevel.value,
                    onStartConversation = viewModel::startAgentConnection,
                    onStopConversation = viewModel::stopConversationMode,
                    onStartRecording = viewModel::startConversationRecording,
                    onStopRecording = viewModel::stopConversationRecording,
                    onRetryConnection = viewModel::retryConversationConnection,
                    conversationError = conversationError,
                )
            } else {
                // Practice Mode UI
                VoiceTutorPracticeCard(
                    language = voiceTutorLanguage!!,
                    level = voiceTutorLevel!!,
                    scenario = voiceTutorScenario!!,
                    prompt = currentPrompt!!,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Recording Controls
                RecordingControlsCard(
                    isRecording = isRecording,
                    hasRecording = hasRecording,
                    isPlayingRecording = isPlayingRecording,
                    recordingDuration = recordingDuration,
                    onToggleRecording = viewModel::toggleRecording,
                    onPlayRecording = viewModel::playRecording,
                )

                // Analyzing indicator
                if (isAnalyzing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AnalyzingIndicator()
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

            Spacer(modifier = Modifier.height(12.dp))
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
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
        ) {
            // Compact header with language, level, scenario in one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Language
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                ) {
                    Text(
                        text = language.displayName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                // Level
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color =
                        when (level) {
                            "beginner" -> Color(0xFF34D399).copy(alpha = 0.15f)
                            "intermediate" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                            "advanced" -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                            else -> Color(0xFF6B7280).copy(alpha = 0.15f)
                        },
                ) {
                    Text(
                        text = level.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            when (level) {
                                "beginner" -> Color(0xFF34D399)
                                "intermediate" -> Color(0xFFF59E0B)
                                "advanced" -> Color(0xFF8B5CF6)
                                else -> Color(0xFF6B7280)
                            },
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Scenario
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                ) {
                    Text(
                        text = scenario.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Soft: Prominent prompt display as main focus
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF374151).copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Say this:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$prompt\"",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp,
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
                containerColor = WordBridgeColors.CardBackgroundDark,
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
                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f),
            ) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = WordBridgeColors.PrimaryPurple,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                color = WordBridgeColors.TextPrimaryDark,
            )

            if (pronunciation.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pronunciation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondaryDark,
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
                color = WordBridgeColors.TextPrimaryDark,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = definition,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondaryDark,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Example",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimaryDark,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WordBridgeColors.BackgroundDark,
            ) {
                Text(
                    text = "\"$example\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextPrimaryDark,
                    modifier = Modifier.padding(12.dp),
                    lineHeight = 20.sp,
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
                containerColor = if (isRecording) Color(0xFFFEF2F2) else Color(0xFF1E1B2E).copy(alpha = 0.95f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Soft status text
            Text(
                text =
                    if (isRecording) {
                        "🔴 Recording... Speak clearly!"
                    } else if (hasRecording) {
                        "✅ Recording saved"
                    } else {
                        "Tap to record"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = if (isRecording) Color(0xFFDC2626) else Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // User-centric: Microphone Button as primary focus
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Soft pulsing background ring for recording state
                if (isRecording) {
                    Box(
                        modifier =
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF2F2)),
                    )
                }

                // Soft main button
                IconButton(
                    onClick = onToggleRecording,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) Color(0xFFDC2626) else Color(0xFF8B5CF6),
                            ),
                ) {
                    // Microphone icon using Canvas
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(28.dp),
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val iconColor = Color.White

                        if (isRecording) {
                            // Stop icon (square)
                            drawRoundRect(
                                color = iconColor,
                                topLeft =
                                    androidx.compose.ui.geometry.Offset(
                                        canvasWidth * 0.25f,
                                        canvasHeight * 0.25f,
                                    ),
                                size =
                                    androidx.compose.ui.geometry.Size(
                                        canvasWidth * 0.5f,
                                        canvasHeight * 0.5f,
                                    ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                            )
                        } else {
                            // Microphone icon
                            // Mic body (rounded rectangle)
                            drawRoundRect(
                                color = iconColor,
                                topLeft =
                                    androidx.compose.ui.geometry.Offset(
                                        canvasWidth * 0.35f,
                                        canvasHeight * 0.2f,
                                    ),
                                size =
                                    androidx.compose.ui.geometry.Size(
                                        canvasWidth * 0.3f,
                                        canvasHeight * 0.35f,
                                    ),
                                cornerRadius =
                                    androidx.compose.ui.geometry.CornerRadius(
                                        canvasWidth * 0.15f,
                                        canvasWidth * 0.15f,
                                    ),
                            )

                            // Mic stand (vertical line)
                            drawLine(
                                color = iconColor,
                                start =
                                    androidx.compose.ui.geometry.Offset(
                                        canvasWidth * 0.5f,
                                        canvasHeight * 0.55f,
                                    ),
                                end =
                                    androidx.compose.ui.geometry.Offset(
                                        canvasWidth * 0.5f,
                                        canvasHeight * 0.75f,
                                    ),
                                strokeWidth = 3f,
                            )

                            // Mic base (horizontal line)
                            drawLine(
                                color = iconColor,
                                start =
                                    androidx.compose.ui.geometry.Offset(
                                        canvasWidth * 0.35f,
                                        canvasHeight * 0.75f,
                                    ),
                                end =
                                    androidx.compose.ui.geometry.Offset(
                                        canvasWidth * 0.65f,
                                        canvasHeight * 0.75f,
                                    ),
                                strokeWidth = 3f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact duration + playback in one row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AnimatedVisibility(visible = isRecording || hasRecording) {
                    Text(
                        text = "${String.format("%.1f", recordingDuration)}s",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (isRecording) Color(0xFFDC2626) else Color.White.copy(alpha = 0.8f),
                    )
                }

                // Soft Playback Button
                AnimatedVisibility(visible = hasRecording && !isRecording) {
                    TextButton(
                        onClick = onPlayRecording,
                        enabled = !isPlayingRecording,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF34D399),
                        ),
                    ) {
                        Text(
                            text = if (isPlayingRecording) "▶ Playing..." else "▶ Play",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEF3C7),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFFF59E0B),
                strokeWidth = 2.dp,
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Analyzing pronunciation...",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF92400E),
            )
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
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
        ) {
            // Compact header with score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your Score",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                )

                // Soft overall score badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scoreColor.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "${feedback.overallScore}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact Score Breakdown - horizontal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CompactScoreItem(
                    label = "Pronunciation",
                    score = feedback.pronunciationScore,
                    color = WordBridgeColors.AccentBlue,
                )
                CompactScoreItem(
                    label = "Clarity",
                    score = feedback.clarityScore,
                    color = WordBridgeColors.AccentGreen,
                )
                CompactScoreItem(
                    label = "Fluency",
                    score = feedback.fluencyScore,
                    color = WordBridgeColors.AccentOrange,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = WordBridgeColors.ProgressBackground)
            Spacer(modifier = Modifier.height(10.dp))

            // Soft Feedback
            if (feedback.messages.isNotEmpty()) {
                Text(
                    text = "💡 ${feedback.messages.first()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 16.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Soft suggestion
            if (feedback.suggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF374151).copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Tip: ${feedback.suggestions.first()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 16.sp,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Compact Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onTryAgain,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF8B5CF6),
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Text("Try Again", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6).copy(alpha = 0.9f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Text("Complete", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Individual score item
 */
@Composable
private fun CompactScoreItem(
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
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WordBridgeColors.TextSecondaryDark,
        )
    }
}
