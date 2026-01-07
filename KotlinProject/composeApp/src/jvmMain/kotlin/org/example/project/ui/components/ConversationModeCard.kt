package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.domain.model.PracticeLanguage
import org.example.project.models.ConversationTurnUI
import org.example.project.ui.theme.WordBridgeColors

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
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B2E).copy(alpha = 0.95f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
        ) {
            // Soft Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "💬",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${language.displayName} Conversation",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                    )
                }

                // Soft status indicator
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isConversationActive) Color(0xFF34D399).copy(alpha = 0.2f) else Color(0xFF6B7280).copy(alpha = 0.2f),
                ) {
                    Text(
                        text = if (isConversationActive) "Live" else "Off",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isConversationActive) Color(0xFF34D399) else Color(0xFF9CA3AF),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Soft error display
            conversationError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$error",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onRetryConnection ?: onStartConversation,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFFDC2626),
                            ),
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Conversation turns - user-centric focus
            if (conversationTurns.isEmpty()) {
                // Compact empty state
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isConversationActive && isAgentThinking) {
                        AgentThinkingIndicator(isThinking = true)
                    } else {
                        Text(
                            text = "🎙️",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ready to chat",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            } else {
                // Soft chat messages - more visible area
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = conversationTurns.size,
                        key = { index -> conversationTurns[index].id },
                    ) { index ->
                        val turn = conversationTurns[index]
                        val isLastAssistantTurn = turn.role == "assistant" && index == conversationTurns.lastIndex
                        ConversationBubble(
                            turn = turn,
                            animationDelay = 0, // No delay - immediate display for responsiveness
                            isAgentThinking = isAgentThinking && isLastAssistantTurn,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Soft user-centric controls - mic is the focus
            if (isConversationActive) {
                // Microphone as primary control
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWaveformMicrophone(
                        isRecording = isRecording,
                        isAgentSpeaking = isAgentSpeaking,
                        isAgentThinking = isAgentThinking,
                        isAgentReady = true,
                        audioLevel = audioLevel,
                        onPress = onStartRecording,
                        onRelease = onStopRecording,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // End button as secondary - with red hover color
                val endButtonInteractionSource = remember { MutableInteractionSource() }
                val isEndButtonHovered by endButtonInteractionSource.collectIsHoveredAsState()
                
                val endButtonColor by animateColorAsState(
                    targetValue = if (isEndButtonHovered) Color(0xFFDC2626) else Color(0xFF9CA3AF),
                    animationSpec = tween(150),
                    label = "endButtonColor"
                )
                
                TextButton(
                    onClick = onStopConversation,
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = endButtonInteractionSource,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = endButtonColor,
                    ),
                ) {
                    Text("End Conversation", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                // Soft start button
                Button(
                    onClick = onStartConversation,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6).copy(alpha = 0.9f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                ) {
                    Text("Start Conversation")
                }
            }
        }
    }
}

/**
 * Individual conversation bubble with smooth animations.
 * Supports real-time streaming with visual indicator.
 * Optimized: No LaunchedEffect delay - bubbles appear immediately for real-time responsiveness.
 */
@Composable
private fun ConversationBubble(
    turn: ConversationTurnUI,
    animationDelay: Int = 0,
    isAgentThinking: Boolean = false,
) {
    val isUser = turn.role == "user"
    
    // Start visible immediately - no delay for real-time chat responsiveness
    // Use remember with turn.id as key to track visibility per bubble
    var visible by remember(turn.id) { mutableStateOf(true) }

    // Cursor blink animation for streaming text
    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "cursorBlink",
    )

    // Slide and fade animation
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(animationSpec = tween(200)) +
                slideInHorizontally(
                    initialOffsetX = { if (isUser) 30 else -30 },
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                ),
        exit = fadeOut() + slideOutHorizontally(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape =
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 3.dp,
                        bottomEnd = if (isUser) 3.dp else 12.dp,
                    ),
                color =
                    if (isUser) {
                        if (turn.isStreaming) Color(0xFF8B5CF6).copy(alpha = 0.9f) else Color(0xFF8B5CF6)
                    } else {
                        Color(0xFF374151).copy(alpha = 0.95f)
                    },
                modifier = Modifier.widthIn(max = 260.dp),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                ) {
                    // Show thinking indicator for agent messages that are currently being generated
                    if (!isUser && isAgentThinking && turn.text.isBlank()) {
                        CompactThinkingIndicator(isThinking = true)
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = turn.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.95f),
                                lineHeight = 18.sp,
                            )
                            // Show blinking cursor for streaming text
                            if (turn.isStreaming && turn.text.isNotEmpty()) {
                                Text(
                                    text = "▌",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f * cursorAlpha),
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
