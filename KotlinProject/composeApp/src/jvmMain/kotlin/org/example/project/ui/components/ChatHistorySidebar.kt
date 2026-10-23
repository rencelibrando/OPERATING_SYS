package org.example.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.ChatSession
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons
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
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(WordBridgeColors.BackgroundLight)
                .padding(16.dp),
    ) {
        // History Label
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = WordBridgeIcons.AIChat,
                contentDescription = null,
                tint = WordBridgeColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )

            Text(
                text = "History",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // New Chat Button
        Button(
            onClick = onNewChatClick,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = WordBridgeColors.Primary,
                    contentColor = WordBridgeColors.PrimaryForeground,
                ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+",
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Text(
                    text = "New Chat",
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat Sessions List
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chatSessions) { session ->
                ChatSessionItem(
                    session = session,
                    isSelected = session.id == currentSessionId,
                    onClick = { onSessionClick(session.id) },
                    onDelete = { onDeleteSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun ChatSessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val formattedDate =
        remember(session.startTime) {
            dateFormat.format(Date(session.startTime))
        }

    val tags =
        remember(session.topic, session.difficulty) {
            listOf(session.topic, session.difficulty)
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        WordBridgeColors.SurfaceVariant
                    } else {
                        WordBridgeColors.Surface
                    },
            ),
        border =
            if (isSelected) {
                BorderStroke(1.dp, WordBridgeColors.BorderLight)
            } else {
                BorderStroke(1.dp, Color.Transparent)
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onClick() },
            ) {
                Text(
                    text = session.title,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    color = WordBridgeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${session.messageCount} msgs",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary,
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary,
                    )

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary,
                    )
                }

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = WordBridgeColors.SurfaceVariant,
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WordBridgeColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = WordBridgeIcons.Delete,
                    contentDescription = "Delete chat",
                    tint = WordBridgeColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
