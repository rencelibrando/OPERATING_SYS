package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.project.core.audio.AudioPlayer
import org.example.project.core.tts.EdgeTTSService
import org.example.project.domain.model.SpeakingFeature
import org.example.project.domain.model.VocabularyWord
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun SpeakingEmptyState(
    features: List<SpeakingFeature>,
    onStartFirstPracticeClick: () -> Unit,
    onExploreExercisesClick: () -> Unit,
    onStartConversationClick: () -> Unit = {},
    vocabularyWords: List<VocabularyWord> = emptyList(),
    onWordPracticeClick: (VocabularyWord) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Perfect Your Speaking Skills",
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = WordBridgeColors.TextPrimaryDark,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Start your journey to confident speaking! Practice pronunciation with AI feedback or have natural conversations with our voice agent. Choose the mode that fits your learning goals.",
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondaryDark,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Mode selection buttons
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onStartFirstPracticeClick,
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.PrimaryPurple,
                    ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Practice Mode",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        color = WordBridgeColors.TextPrimaryDark,
                    )
                }
            }

            Button(
                onClick = onStartConversationClick,
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                    ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Conversation",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        color = WordBridgeColors.TextPrimaryDark,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "or ",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondaryDark,
            )

            TextButton(
                onClick = onExploreExercisesClick,
            ) {
                Text(
                    text = "explore speaking exercises",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.PrimaryPurple,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Word Practice Section - Show vocabulary words for practice
        if (vocabularyWords.isNotEmpty()) {
            WordPracticeSection(
                words = vocabularyWords,
                onWordClick = onWordPracticeClick
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(400.dp), // Fixed height to prevent scroll conflicts
        ) {
            items(features) { feature ->
                SpeakingFeatureCard(
                    feature = feature,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Word Practice Section - Shows vocabulary words from the bank for pronunciation practice
 */
@Composable
private fun WordPracticeSection(
    words: List<VocabularyWord>,
    onWordClick: (VocabularyWord) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1625).copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(10.dp),
                                spotColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF3B82F6),
                                        Color(0xFF2563EB)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📚",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Column {
                        Text(
                            text = "Word Practice",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White.copy(alpha = 0.95f)
                        )
                        Text(
                            text = "${words.size} words from your vocabulary",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Practice pronunciation",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Word cards in horizontal scroll
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(words.take(10)) { word -> // Limit to 10 words for performance
                    WordPracticeCard(
                        word = word,
                        onClick = { onWordClick(word) }
                    )
                }
            }
        }
    }
}

/**
 * Individual word card for practice with TTS voice narration
 */
@Composable
private fun WordPracticeCard(
    word: VocabularyWord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            if (!word.audioUrl.isNullOrBlank()) {
                isPlayingAudio = true
                try {
                    audioPlayer.setPlaybackFinishedCallback {
                        isPlayingAudio = false
                    }
                    audioPlayer.playAudioFromUrl(word.audioUrl)
                } catch (e: Exception) {
                    println("[WordPractice] Error playing existing audio: ${e.message}")
                    isPlayingAudio = false
                }
            } else {
                // Generate audio using Edge TTS
                isGeneratingAudio = true
                try {
                    val languageCode = word.language.takeIf { it.isNotBlank() } ?: "en"
                    val result = ttsService.generateAudio(word.word, languageCode)
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
                        println("[WordPractice] TTS generation failed: ${it.message}")
                        isGeneratingAudio = false
                    }
                } catch (e: Exception) {
                    println("[WordPractice] Error generating audio: ${e.message}")
                    isGeneratingAudio = false
                }
            }
        }
    }
    
    Card(
        modifier = modifier
            .width(180.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Word with audio button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = Color.White.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // Audio play button
                IconButton(
                    onClick = { playWordAudio() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isPlayingAudio) Color(0xFF10B981)
                            else if (isGeneratingAudio) Color(0xFFF59E0B)
                            else Color(0xFF3B82F6).copy(alpha = 0.3f)
                        )
                ) {
                    if (isGeneratingAudio) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Play pronunciation",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
            
            // Pronunciation if available
            if (word.pronunciation.isNotBlank()) {
                Text(
                    text = word.pronunciation,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Definition preview
            Text(
                text = word.definition,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Practice button
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Practice",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SpeakingFeatureCard(
    feature: SpeakingFeature,
    modifier: Modifier = Modifier,
) {
    val featureColor =
        try {
            androidx.compose.ui.graphics.Color(feature.color.removePrefix("#").toLong(16) or 0xFF000000)
        } catch (e: Exception) {
            WordBridgeColors.PrimaryPurple
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.BackgroundMain,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                hoveredElevation = 4.dp,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(
                            featureColor.copy(alpha = 0.1f),
                            androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = feature.icon,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = feature.title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimaryDark,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondaryDark,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )
        }
    }
}
