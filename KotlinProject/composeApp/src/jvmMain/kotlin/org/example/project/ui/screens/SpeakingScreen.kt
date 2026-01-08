package org.example.project.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import org.example.project.core.audio.AudioPlayer
import org.example.project.core.tts.EdgeTTSService
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
                .background(
                    Color(0xFF0F0F23).copy(alpha = 0.95f) // Softer, warmer background
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Soft Header with connection feel
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = WordBridgeColors.CardBackgroundDark.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Show back button if onBackClick is provided (from VocabularyScreen)
                if (onBackClick != null) {
                    TextButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "←",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = WordBridgeColors.PrimaryPurple,
                            )
                            Text(
                                text = "Back to Progress",
                                color = WordBridgeColors.PrimaryPurple,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    // Otherwise show title (from sidebar navigation)
                    Column {
                        Text(
                            text = "Speaking Practice",
                            style =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                            color = WordBridgeColors.TextPrimaryDark,
                        )
                        Text(
                            text = "Let's practice together!",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp
                            ),
                            color = WordBridgeColors.TextSecondaryDark.copy(alpha = 0.8f),
                        )
                    }
                }

                // Always show user avatar on the right with soft feel
                UserAvatar(
                    initials = authenticatedUser?.initials ?: "U",
                    profileImageUrl = authenticatedUser?.profileImageUrl,
                    size = 48.dp,
                    onClick = onUserAvatarClick,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    colors =
                        ButtonDefaults.textButtonColors(
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
                audioUrl = currentWord!!.audioUrl,
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
            val vocabularyWords by viewModel.vocabularyWordsForPractice
            
            SpeakingEmptyState(
                features = viewModel.speakingFeatures.value,
                onStartFirstPracticeClick = viewModel::onStartFirstPracticeClicked,
                onExploreExercisesClick = viewModel::onExploreExercisesClicked,
                onStartConversationClick = viewModel::onStartConversationClicked,
                vocabularyWords = vocabularyWords,
                onWordPracticeClick = { word ->
                    viewModel.startWordPractice(word, word.language)
                }
            )
        }
    }
}

/**
 * Card showing Voice Tutor practice information - Soft and User-Centric Design
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
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark.copy(alpha = 0.98f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
        ) {
            // Soft header with language, level, scenario in one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Language with soft gradient feel
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                    modifier = Modifier.shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(10.dp),
                        spotColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = language.displayName,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                // Level with soft feel
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color =
                        when (level) {
                            "beginner" -> Color(0xFF34D399).copy(alpha = 0.15f)
                            "intermediate" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                            "advanced" -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                            else -> Color(0xFF6B7280).copy(alpha = 0.15f)
                        },
                    modifier = Modifier.shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(10.dp),
                        spotColor = when (level) {
                            "beginner" -> Color(0xFF34D399).copy(alpha = 0.3f)
                            "intermediate" -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                            "advanced" -> Color(0xFF8B5CF6).copy(alpha = 0.3f)
                            else -> Color(0xFF6B7280).copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Text(
                        text = level.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color =
                            when (level) {
                                "beginner" -> Color(0xFF34D399)
                                "intermediate" -> Color(0xFFF59E0B)
                                "advanced" -> Color(0xFF8B5CF6)
                                else -> Color(0xFF6B7280)
                            },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                // Scenario with soft feel
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                    modifier = Modifier.shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(10.dp),
                        spotColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = scenario.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Soft: Enhanced prompt display with connection feel
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF374151).copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Say this:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "\"$prompt\"",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 26.sp,
                                fontSize = 17.sp
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
 * Card showing word information with TTS pronunciation playback
 */
@Composable
private fun WordInfoCard(
    word: String,
    definition: String,
    example: String,
    language: PracticeLanguage,
    pronunciation: String,
    audioUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isPlayingAudio by remember { mutableStateOf(false) }
    var isGeneratingAudio by remember { mutableStateOf(false) }
    val audioPlayer = remember { AudioPlayer() }
    val ttsService = remember { EdgeTTSService() }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.dispose()
            ttsService.close()
        }
    }
    
    fun playWordAudio() {
        if (isPlayingAudio || isGeneratingAudio) return
        
        scope.launch {
            // First check if word has an existing audio URL
            if (!audioUrl.isNullOrBlank()) {
                isPlayingAudio = true
                try {
                    audioPlayer.setPlaybackFinishedCallback {
                        isPlayingAudio = false
                    }
                    audioPlayer.playAudioFromUrl(audioUrl)
                } catch (e: Exception) {
                    println("[WordInfo] Error playing existing audio: ${e.message}")
                    isPlayingAudio = false
                }
            } else {
                // Generate audio using Edge TTS
                isGeneratingAudio = true
                try {
                    val languageCode = when (language) {
                        PracticeLanguage.SPANISH -> "es"
                        PracticeLanguage.FRENCH -> "fr"
                        PracticeLanguage.GERMAN -> "de"
                        PracticeLanguage.HANGEUL -> "ko"
                        PracticeLanguage.MANDARIN -> "zh"
                        else -> "en"
                    }
                    val result = ttsService.generateAudio(word, languageCode)
                    result.onSuccess { response ->
                        if (!response.audioUrl.isNullOrBlank()) {
                            isPlayingAudio = true
                            isGeneratingAudio = false
                            audioPlayer.setPlaybackFinishedCallback {
                                isPlayingAudio = false
                            }
                            audioPlayer.playAudioFromUrl(response.audioUrl)
                        } else {
                            isGeneratingAudio = false
                        }
                    }.onFailure {
                        println("[WordInfo] TTS generation failed: ${it.message}")
                        isGeneratingAudio = false
                    }
                } catch (e: Exception) {
                    println("[WordInfo] Error generating audio: ${e.message}")
                    isGeneratingAudio = false
                }
            }
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
            ) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = WordBridgeColors.PrimaryPurple,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Word with speaker button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = word,
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                        color = WordBridgeColors.TextPrimaryDark,
                    )

                    if (pronunciation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pronunciation,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp
                            ),
                            color = WordBridgeColors.TextSecondaryDark.copy(alpha = 0.8f),
                        )
                    }
                }
                
                // Speaker button for TTS playback
                IconButton(
                    onClick = { playWordAudio() },
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            spotColor = if (isPlayingAudio) Color(0xFF10B981).copy(alpha = 0.4f)
                                       else Color(0xFF3B82F6).copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .background(
                            if (isPlayingAudio) Color(0xFF10B981)
                            else if (isGeneratingAudio) Color(0xFFF59E0B)
                            else Color(0xFF3B82F6)
                        )
                ) {
                    if (isGeneratingAudio) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Play pronunciation",
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                }
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
 * Card with recording controls - Soft and User-Centric Design
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
    var isPressed by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isRecording) 8.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isRecording) Color(0xFFEF4444).copy(alpha = 0.3f) else Color(0xFF8B5CF6).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isRecording) 
                    Color(0xFFFEF2F2).copy(alpha = 0.95f) 
                else 
                    Color(0xFF1E1B2E).copy(alpha = 0.98f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                awaitRelease()
                                isPressed = false
                            },
                        )
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Soft, encouraging status text with connection feel
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = when {
                        isRecording -> "Recording... I'm listening to you!"
                        hasRecording -> "Great job! Ready to hear your practice"
                        else -> "Ready when you are - tap to begin"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = if (isRecording) 
                        Color(0xFFDC2626).copy(alpha = 0.8f) 
                    else 
                        Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // User-centric: Enhanced Microphone Button with connection feel
            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Soft pulsing background ring for recording state
                if (isRecording) {
                    Box(
                        modifier =
                            Modifier
                                .size(90.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Color(0xFFEF4444).copy(alpha = 0.1f),
                                )
                    )
                }

                // Soft main button with enhanced interaction
                IconButton(
                    onClick = onToggleRecording,
                    modifier =
                        Modifier
                            .size(70.dp)
                            .shadow(
                                elevation = if (isPressed || isRecording) 12.dp else 6.dp,
                                shape = CircleShape,
                                spotColor = if (isRecording) 
                                    Color(0xFFEF4444).copy(alpha = 0.4f) 
                                else 
                                    Color(0xFF8B5CF6).copy(alpha = 0.3f)
                            )
                            .clip(CircleShape)
                            .background(
                                if (isRecording) 
                                    Color(0xFFEF4444) 
                                else 
                                    Color(0xFF8B5CF6),
                            ),
                ) {
                    // Enhanced icons with better visual feedback
                    if (isRecording) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Recording",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Start Recording",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Soft duration + playback with enhanced connection feel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedVisibility(
                    visible = isRecording || hasRecording,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRecording) 
                            Color(0xFFEF4444).copy(alpha = 0.1f) 
                        else 
                            Color(0xFF8B5CF6).copy(alpha = 0.1f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "${String.format("%.1f", recordingDuration)}s",
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                            color = if (isRecording) 
                                Color(0xFFDC2626) 
                            else 
                                Color(0xFF8B5CF6),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Enhanced Playback Button with connection feel
                AnimatedVisibility(
                    visible = hasRecording && !isRecording,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    TextButton(
                        onClick = onPlayRecording,
                        enabled = !isPlayingRecording,
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF34D399),
                            ),
                        modifier = Modifier
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = Color(0xFF34D399).copy(alpha = 0.3f)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isPlayingRecording) "Playing..." else "Play Back",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                            )
                        }
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
 * Enhanced Feedback card with comprehensive scores and AI analysis
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
            feedback.overallScore >= 85 -> Color(0xFF10B981) // Green
            feedback.overallScore >= 70 -> Color(0xFFF59E0B) // Orange
            feedback.overallScore >= 50 -> Color(0xFFF97316) // Darker Orange
            else -> Color(0xFFEF4444) // Red
        }
    
    val scoreEmoji = when {
        feedback.overallScore >= 90 -> "🌟"
        feedback.overallScore >= 80 -> "✨"
        feedback.overallScore >= 70 -> "👍"
        feedback.overallScore >= 50 -> "💪"
        else -> "🎯"
    }
    
    val scoreMessage = when {
        feedback.overallScore >= 90 -> "Excellent pronunciation!"
        feedback.overallScore >= 80 -> "Great job!"
        feedback.overallScore >= 70 -> "Good progress!"
        feedback.overallScore >= 50 -> "Keep practicing!"
        else -> "Let's try again"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = scoreColor.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            // Enhanced header with score circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "$scoreEmoji $scoreMessage",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                        color = Color.White,
                    )
                    Text(
                        text = "AI Pronunciation Analysis",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                // Circular score badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(scoreColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${feedback.overallScore}",
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = scoreColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score Breakdown with progress bars
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScoreProgressItem(
                    label = "Pronunciation",
                    score = feedback.pronunciationScore,
                    color = Color(0xFF3B82F6),
                    icon = "🗣️"
                )
                ScoreProgressItem(
                    label = "Clarity",
                    score = feedback.clarityScore,
                    color = Color(0xFF10B981),
                    icon = "🎯"
                )
                ScoreProgressItem(
                    label = "Fluency",
                    score = feedback.fluencyScore,
                    color = Color(0xFFF59E0B),
                    icon = "🌊"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Feedback messages
            if (feedback.messages.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F2937).copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "📝", fontSize = 16.sp)
                            Text(
                                text = "Feedback",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        feedback.messages.forEach { message ->
                            Text(
                                text = "• $message",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 18.sp
                                ),
                                color = Color.White.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Suggestions with improved styling
            if (feedback.suggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "💡", fontSize = 16.sp)
                            Text(
                                text = "Tips to Improve",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF60A5FA),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        feedback.suggestions.take(3).forEach { suggestion ->
                            Text(
                                text = "→ $suggestion",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 18.sp
                                ),
                                color = Color.White.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action Buttons with enhanced styling
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onTryAgain,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
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
                    colors =
                        ButtonDefaults.buttonColors(
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
 * Score item with progress bar for enhanced feedback display
 */
@Composable
fun ScoreProgressItem(
    label: String,
    score: Int,
    color: Color,
    icon: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = icon, fontSize = 16.sp)
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    text = "$score%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = color,
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(score / 100f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}

/**
 * Individual score item (compact version)
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
            style =
                MaterialTheme.typography.titleSmall.copy(
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
