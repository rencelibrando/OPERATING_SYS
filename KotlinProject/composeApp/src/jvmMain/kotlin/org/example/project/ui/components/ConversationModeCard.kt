package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import org.example.project.models.PracticeLanguage
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
    isRecording: Boolean,
    isConversationActive: Boolean,
    onStartConversation: () -> Unit,
    onStopConversation: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
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
                            animationDelay = index * 50
                        )
                    }

                    // Agent typing indicator
                    if (isAgentSpeaking) {
                        item {
                            TypingIndicator()
                        }
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
            
            // Audio is now continuous - show listening indicator when active
            if (isConversationActive) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎤",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Listening continuously - just speak naturally",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF10B981)
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
    animationDelay: Int = 0
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
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(10.dp),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * Typing indicator for when agent is speaking with fade-in animation.
 */
@Composable
private fun TypingIndicator() {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(200)) + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF3A3147).copy(alpha = 0.6f),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) { index ->
                        TypingDot(delay = index * 150)
                    }
                }
            }
        }
    }
}

/**
 * Animated typing dot with smooth transitions.
 */
@Composable
private fun TypingDot(delay: Int = 0) {
    var visible by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition()
    
    // Smooth scale animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delay),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        while (true) {
            visible = true
            kotlinx.coroutines.delay(600)
            visible = false
            kotlinx.coroutines.delay(400)
        }
    }

    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(
                color = if (visible) Color(0xFFB4B4C4) else Color(0xFF6B7280),
                shape = RoundedCornerShape(4.dp)
            )
    )
}
