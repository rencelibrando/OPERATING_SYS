package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.AIChatViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.domain.model.MessageSender

/**
 * AI Chat screen of the WordBridge application
 * 
 * Displays chat interface with AI tutors and empty state
 */
@Composable
fun AIChatScreen(
    viewModel: AIChatViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val chatMessages by viewModel.chatMessages
    val chatFeatures by viewModel.chatFeatures
    val selectedBot by viewModel.selectedBot
    val currentMessage by viewModel.currentMessage
    val isTyping by viewModel.isTyping
    val isLoading by viewModel.isLoading
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header with title and user info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Chat Tutor",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            // User info placeholder - can be expanded later
            UserAvatar(
                initials = "SC", // This should come from user data
                size = 40.dp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Content based on whether user has started chatting
        if (chatMessages.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                AIChatEmptyState(
                    features = chatFeatures,
                    onStartFirstConversationClick = viewModel::onStartFirstConversationClicked,
                    onExploreChatBotsClick = viewModel::onExploreChatBotsClicked
                )
            }
        } else {
            // Chat interface
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Current bot info (if selected)
                selectedBot?.let { bot ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF3E8FF) // Light purple
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bot.avatar,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    text = "Chatting with ${bot.name}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = WordBridgeColors.TextPrimary
                                )
                                
                                Text(
                                    text = bot.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WordBridgeColors.TextSecondary
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Messages list
                val listState = rememberLazyListState()
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages) { message ->
                        MessageBubble(
                            message = message.content,
                            isFromUser = message.sender == MessageSender.USER,
                            timestamp = message.timestamp
                        )
                    }
                    
                    // Typing indicator
                    if (isTyping) {
                        item {
                            MessageBubble(
                                message = "Typing...",
                                isFromUser = false,
                                timestamp = System.currentTimeMillis(),
                                isTyping = true
                            )
                        }
                    }
                }
                
                // Scroll to bottom when new message arrives
                LaunchedEffect(chatMessages.size, isTyping) {
                    if (chatMessages.isNotEmpty() || isTyping) {
                        listState.animateScrollToItem(
                            if (isTyping) chatMessages.size else chatMessages.size - 1
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Message input
                ChatInput(
                    message = currentMessage,
                    onMessageChange = viewModel::onMessageChanged,
                    onSendMessage = viewModel::onSendMessage,
                    enabled = !isLoading
                )
            }
        }
    }
}

/**
 * Message bubble component for chat messages
 */
@Composable
private fun MessageBubble(
    message: String,
    isFromUser: Boolean,
    timestamp: Long,
    isTyping: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isFromUser) {
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromUser) 16.dp else 4.dp,
                bottomEnd = if (isFromUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromUser) WordBridgeColors.PrimaryPurple 
                                else WordBridgeColors.BackgroundWhite
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFromUser) Color.White else WordBridgeColors.TextPrimary
            )
        }
        
        if (isFromUser) {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

/**
 * Chat input component for typing messages
 */
@Composable
private fun ChatInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            placeholder = {
                Text(
                    text = "Type your message...",
                    color = WordBridgeColors.TextMuted
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WordBridgeColors.PrimaryPurple,
                unfocusedBorderColor = WordBridgeColors.TextMuted.copy(alpha = 0.3f)
            ),
            maxLines = 3
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Button(
            onClick = onSendMessage,
            enabled = enabled && message.trim().isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = WordBridgeColors.PrimaryPurple
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(48.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "➤",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
