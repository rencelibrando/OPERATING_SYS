package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.delete_icon
import org.example.project.domain.model.ChatSession
import org.example.project.ui.theme.WordBridgeColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatHistorySidebar(
    chatSessions: List<ChatSession>,
    currentSessionId: String?,
    onSessionClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteSession: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    
    // Animated width
    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 280.dp else 60.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Delete confirmation dialog
    showDeleteDialog?.let { sessionId ->
        DeleteConfirmationDialog(
            onConfirm = {
                onDeleteSession(sessionId)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    Card(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isExpanded) 16.dp else 8.dp),
        ) {
            // Toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isExpanded) Arrangement.End else Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isExpanded = !isExpanded }
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isExpanded) "◀" else "▶",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "●",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = WordBridgeColors.TextPrimary,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // New Chat Button
                    Button(
                        onClick = onNewChatClick,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C3AED),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = "New Chat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Divider
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.LightGray.copy(alpha = 0.3f),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sessions List
                    if (chatSessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "○",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Color(0xFFD1D5DB),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No history yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Start chatting!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(chatSessions) { session ->
                                ChatSessionItem(
                                    session = session,
                                    isSelected = session.id == currentSessionId,
                                    onClick = { onSessionClick(session.id) },
                                    onDelete = { showDeleteDialog = session.id },
                                )
                            }
                        }
                    }
                }
            }
            
            // Collapsed state - just show dots
            if (!isExpanded && chatSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(chatSessions.take(10)) { session ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { onSessionClick(session.id) }
                                .background(
                                    if (session.id == currentSessionId) 
                                        Color(0xFF7C3AED).copy(alpha = 0.2f)
                                    else 
                                        Color(0xFFF3F4F6)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "●",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (session.id == currentSessionId)
                                    Color(0xFF7C3AED)
                                else
                                    Color(0xFF9CA3AF),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
) {
    val primaryColor = Color(0xFF7C3AED)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> primaryColor.copy(alpha = 0.15f)
            isHovered -> Color(0xFFF3F4F6)
            else -> Color.White
        },
        animationSpec = tween(200)
    )

    val elevation by animateDpAsState(
        targetValue = if (isHovered) 4.dp else 0.dp,
        animationSpec = tween(200)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, primaryColor)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            // Title with delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (isSelected) primaryColor else WordBridgeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                
                // Delete button (shows on hover)
                AnimatedVisibility(
                    visible = isHovered || isSelected,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(onClick = onDelete)
                            .background(
                                color = Color(0xFFEF4444).copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.delete_icon),
                            contentDescription = "Delete chat",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFEF4444),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${session.messageCount} msgs",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF),
                )
                
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF),
                )
                
                Text(
                    text = formatTimestamp(session.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF),
                )
            }

            // Topic/Difficulty badges (compact)
            if (session.topic.isNotEmpty() || session.difficulty.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (session.topic.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = primaryColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = session.topic,
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (session.difficulty.isNotEmpty()) {
                        val difficultyColor = getDifficultyColor(session.difficulty)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = difficultyColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = session.difficulty.take(3), // Show only first 3 chars
                                style = MaterialTheme.typography.labelSmall,
                                color = difficultyColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Chat?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Text(
                text = "This will permanently delete this chat session and all its messages. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444),
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF6B7280))
            }
        },
        shape = RoundedCornerShape(16.dp),
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

private fun getDifficultyColor(difficulty: String): Color {
    return when (difficulty.lowercase()) {
        "beginner" -> Color(0xFF4CAF50)
        "intermediate" -> Color(0xFFFF9800)
        "advanced" -> Color(0xFFF44336)
        else -> Color.Gray
    }
}

