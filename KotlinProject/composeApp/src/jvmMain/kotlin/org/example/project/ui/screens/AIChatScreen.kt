package org.example.project.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.domain.model.MessageSender
import org.example.project.presentation.viewmodel.AIChatViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import java.text.SimpleDateFormat
import java.util.*
import org.example.project.core.auth.User as AuthUser

@Composable
fun AIChatScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    viewModel: AIChatViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val chatMessages by viewModel.chatMessages
    val chatSessions by viewModel.chatSessions
    val chatFeatures by viewModel.chatFeatures
    val selectedBot by viewModel.selectedBot
    val currentMessage by viewModel.currentMessage
    val isTyping by viewModel.isTyping
    val isLoading by viewModel.isLoading
    val currentSession by viewModel.currentSession

    // Initialize chat and load data when screen opens
    LaunchedEffect(Unit) {
        viewModel.initializeChat()
    }

    Row(
        modifier = modifier.fillMaxSize(),
    ) {
        // Chat History Sidebar
        ChatHistorySidebar(
            chatSessions = chatSessions,
            currentSessionId = currentSession?.id,
            onSessionClick = viewModel::onSessionSelected,
            onNewChatClick = viewModel::onNewSessionClicked,
            onDeleteSession = viewModel::onDeleteSession,
        )

        // Main Chat Area
        // Show empty state ONLY if:
        // 1. No messages displayed
        // 2. No existing chat sessions in history
        // 3. No current session active
        val hasNoChatHistory = chatSessions.isEmpty()
        val hasNoActiveChat = chatMessages.isEmpty() && currentSession == null
        val shouldShowEmptyState = hasNoChatHistory && hasNoActiveChat

        if (shouldShowEmptyState) {
            // Empty State - user has never started a conversation
            // Don't create any session - just show the empty state
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AI Chat Tutor",
                        style =
                            MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )

                    UserAvatar(
                        initials = authenticatedUser?.initials ?: "U",
                        profileImageUrl = authenticatedUser?.profileImageUrl,
                        size = 48.dp,
                        onClick = onUserAvatarClick,
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Empty State Content - only way to start is clicking "Start Your First Conversation"
                AIChatEmptyState(
                    features = chatFeatures,
                    onStartFirstConversationClick = viewModel::onStartFirstConversationClicked,
                    onExploreChatBotsClick = viewModel::onExploreChatBotsClicked,
                )
            }
        } else if (chatMessages.isEmpty() && chatSessions.isNotEmpty() && currentSession == null) {
            // Loading existing chat - show loading indicator
            // This happens when user has chat history but we're still loading the session
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = WordBridgeColors.PrimaryPurple)
            }
        } else {
            // Active Chat
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) {
                // Chat Header
                ChatHeader(
                    bot = selectedBot,
                    authenticatedUser = authenticatedUser,
                    onBackClick = { /* TODO: implement back navigation */ },
                    onUserAvatarClick = onUserAvatarClick,
                )

                // Messages Area
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.background,
                            ),
                ) {
                    val listState = rememberLazyListState()

                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items(chatMessages) { message ->
                            ChatMessageBubble(
                                message = message.content,
                                isFromUser = message.sender == MessageSender.USER,
                                timestamp = message.timestamp,
                                botAvatar = selectedBot?.avatar ?: "🤖",
                                botName = selectedBot?.name ?: "AI",
                            )
                        }

                        if (isTyping) {
                            item {
                                TypingIndicator(
                                    botAvatar = selectedBot?.avatar ?: "🤖",
                                    botName = selectedBot?.name ?: "AI",
                                )
                            }
                        }

                        // Quick Replies (show after first message)
                        if (chatMessages.size == 1 && !isTyping) {
                            item {
                                QuickReplies(
                                    onReplyClick = { reply ->
                                        viewModel.onMessageChanged(reply)
                                        viewModel.onSendMessage()
                                    },
                                )
                            }
                        }
                    }

                    LaunchedEffect(chatMessages.size, isTyping) {
                        if (chatMessages.isNotEmpty() || isTyping) {
                            listState.animateScrollToItem(
                                if (isTyping) chatMessages.size else chatMessages.size - 1,
                            )
                        }
                    }
                }

                // Message Input
                ModernChatInput(
                    message = currentMessage,
                    onMessageChange = viewModel::onMessageChanged,
                    onSendMessage = viewModel::onSendMessage,
                    enabled = !isLoading,
                    botOnline = true,
                    botName = selectedBot?.name ?: "AI",
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(
    bot: org.example.project.domain.model.ChatBot?,
    authenticatedUser: AuthUser?,
    onBackClick: () -> Unit,
    onUserAvatarClick: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back Button
                IconButton(
                    onClick = onBackClick,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                ) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (bot != null) {
                    // Bot Avatar
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    brush =
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFFF472B6), // pink-400
                                                    Color(0xFFFB923C), // orange-400
                                                ),
                                        ),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = bot.avatar,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }

                    Column {
                        Text(
                            text = "Chatting with ${bot.name}",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = bot.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // More Options
                IconButton(
                    onClick = { },
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                ) {
                    Text(
                        text = "⋮",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                UserAvatar(
                    initials = authenticatedUser?.initials ?: "U",
                    profileImageUrl = authenticatedUser?.profileImageUrl,
                    size = 48.dp,
                    onClick = onUserAvatarClick,
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: String,
    isFromUser: Boolean,
    timestamp: Long,
    botAvatar: String,
    botName: String,
) {
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime =
        remember(timestamp) {
            dateFormat.format(Date(timestamp))
        }

    if (isFromUser) {
        // User message (right-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color.Transparent,
                    ),
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 0.dp,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                brush =
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                Color(0xFFA855F7), // purple-500
                                                Color(0xFF3B82F6), // blue-500
                                            ),
                                    ),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                }
            }
        }
    } else {
        // Bot message (left-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            // Bot Avatar
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFF472B6), // pink-400
                                            Color(0xFFFB923C), // orange-400
                                        ),
                                ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = botAvatar,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = botName,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape =
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 16.dp,
                        ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 0.dp,
                        ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape =
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 16.dp,
                                        ),
                                )
                                .padding(16.dp),
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(
    botAvatar: String,
    botName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0xFFF472B6),
                                        Color(0xFFFB923C),
                                    ),
                            ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = botAvatar,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "$botName is typing...",
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(3) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickReplies(onReplyClick: (String) -> Unit) {
    val replies =
        listOf(
            "Tell me about yourself",
            "Let's practice greetings",
            "Help me with pronunciation",
            "Discuss daily routines",
        )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 56.dp),
        // Align with message bubble
    ) {
        Text(
            text = "Quick replies:",
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            replies.take(2).forEach { reply ->
                QuickReplyButton(
                    text = reply,
                    onClick = { onReplyClick(reply) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            replies.drop(2).forEach { reply ->
                QuickReplyButton(
                    text = reply,
                    onClick = { onReplyClick(reply) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickReplyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        modifier =
            modifier
                .hoverable(interactionSource)
                .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isHovered) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        border =
            androidx.compose.foundation.BorderStroke(
                2.dp,
                if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (isHovered) 4.dp else 2.dp,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ModernChatInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    enabled: Boolean,
    botOnline: Boolean,
    botName: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Message Input
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    placeholder = {
                        Text(
                            text = "Type your message...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = enabled,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                    maxLines = 3,
                )

                // Send Button
                val buttonInteraction = remember { MutableInteractionSource() }
                val buttonHovered by buttonInteraction.collectIsHoveredAsState()
                val buttonScale by animateFloatAsState(
                    targetValue = if (buttonHovered) 1.05f else 1f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                )

                IconButton(
                    onClick = onSendMessage,
                    enabled = enabled && message.trim().isNotEmpty(),
                    modifier =
                        Modifier
                            .size(48.dp)
                            .scale(buttonScale)
                            .hoverable(buttonInteraction)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush =
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                Color(0xFFA855F7),
                                                Color(0xFF3B82F6),
                                            ),
                                    ),
                            ),
                ) {
                    Text(
                        text = "➤",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Row
            if (botOnline) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = "$botName is online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
