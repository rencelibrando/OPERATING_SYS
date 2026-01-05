package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.presentation.viewmodel.SpeakingViewModel
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.domain.model.PracticeLanguage
import org.example.project.models.ConversationTurnUI

/**
 * Conversation mode card showing real-time dialogue with AI tutor.
 */
@Composable
fun ConversationModeCard(
    language: PracticeLanguage,
    level: String,
    scenario: String,
    conversationTurns: List<ConversationTurnUI>,
    isAgentSpeaking: Boolean,
    isAgentThinking: Boolean = false,
    isRecording: Boolean,
    isConversationActive: Boolean,
    audioLevel: Float = 0f,
    onStartConversation: () -> Unit,
    onStopConversation: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRetryConnection: (() -> Unit)? = null,
    conversationError: String? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(conversationTurns.size) {
        if (conversationTurns.isNotEmpty()) {
            listState.animateScrollToItem(conversationTurns.size - 1)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D2A3E).copy(alpha = 0.8f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "💬",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Conversation Mode",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Chat with AI tutor in ${language.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB4B4C4)
                        )
                    }
                }

                // Status indicator
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isConversationActive) Color(0xFF10B981) else Color(0xFF6B7280)
                ) {
                    Text(
                        text = if (isConversationActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error display
            conversationError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connection Error",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFEF4444)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFEF4444).copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRetryConnection ?: onStartConversation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retry Connection", color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider(color = Color(0xFF3A3147).copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(20.dp))

            // Conversation turns
            if (conversationTurns.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isConversationActive && isAgentThinking) {
                        // Show thinking indicator when agent is processing first response
                        AgentThinkingIndicator(isThinking = true)
                    } else {
                        // Show normal empty state
                        Text(
                            text = "🎙️",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Start Conversation",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Click below to start a real-time conversation with your AI tutor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            } else {
                // Chat messages with animations
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = conversationTurns,
                        key = { index, _ -> index }
                    ) { index, turn ->
                        ConversationBubble(
                            turn = turn,
                            animationDelay = index * 50,
                            isAgentThinking = isAgentThinking && turn.role == "assistant" && index == conversationTurns.size - 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = Color(0xFF3A3147).copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(20.dp))

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isConversationActive) {
                    Button(
                        onClick = onStartConversation,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Conversation")
                    }
                } else {
                    Button(
                        onClick = onStopConversation,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("End Conversation")
                    }
                }
            }
            
            // Push-to-talk button when conversation is active
            if (isConversationActive) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWaveformMicrophone(
                        isRecording = isRecording,
                        isAgentSpeaking = isAgentSpeaking,
                        isAgentThinking = isAgentThinking,
                        isAgentReady = true,
                        audioLevel = audioLevel,
                        onPress = onStartRecording,
                        onRelease = onStopRecording
                    )
                }
            }

        }
    }
}

/**
 * Individual conversation bubble with smooth animations.
 */
@Composable
private fun ConversationBubble(
    turn: ConversationTurnUI,
    animationDelay: Int = 0,
    isAgentThinking: Boolean = false
) {
    val isUser = turn.role == "user"
    var visible by remember { mutableStateOf(false) }
    
    // Trigger animation on first composition
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }
    
    // Slide and fade animation
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInHorizontally(
                    initialOffsetX = { if (isUser) 50 else -50 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ),
        exit = fadeOut() + slideOutHorizontally()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 12.dp
                ),
                color = if (isUser) Color(0xFF8B5CF6) else Color(0xFF3A3147).copy(alpha = 0.8f),
                modifier = Modifier.widthIn(max = 280.dp),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(10.dp)
                ) {
                    // Show thinking indicator for agent messages that are currently being generated
                    if (!isUser && isAgentThinking && turn.text.isBlank()) {
                        CompactThinkingIndicator(isThinking = true)
                    } else {
                        Text(
                            text = turn.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}
