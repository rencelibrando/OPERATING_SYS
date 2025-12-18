package org.example.project.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LessonTopic

@Composable
fun AdminTopicsList(
    topics: List<LessonTopic>,
    isLoading: Boolean,
    selectedTopics: Set<String>,
    onTopicSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onEdit: (LessonTopic) -> Unit,
    onDelete: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onManageLessons: (LessonTopic) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isLoading && topics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF8B5CF6)
            )
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF1E1B2E),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Table header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF252132)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Checkbox(
                            checked = selectedTopics.isNotEmpty() && selectedTopics.size == topics.size,
                            onCheckedChange = { 
                                if (selectedTopics.isEmpty()) onSelectAll() else onClearSelection()
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF8B5CF6),
                                uncheckedColor = Color(0xFF6B6B7B)
                            )
                        )
                        
                        Text(
                            text = "TITLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB4B4C4),
                            modifier = Modifier.weight(0.35f)
                        )
                        Text(
                            text = "DESCRIPTION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB4B4C4),
                            modifier = Modifier.weight(0.35f)
                        )
                        Text(
                            text = "LESSON #",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB4B4C4),
                            modifier = Modifier.weight(0.15f)
                        )
                        Text(
                            text = "DURATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB4B4C4),
                            modifier = Modifier.weight(0.15f)
                        )
                    }
                    
                    Box(modifier = Modifier.width(160.dp)) {
                        Text(
                            text = "ACTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB4B4C4)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF3A3147), thickness = 1.dp)

            // Table rows
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(topics) { topic ->
                    AdminTopicRow(
                        topic = topic,
                        isSelected = selectedTopics.contains(topic.id),
                        onSelect = { onTopicSelect(topic.id) },
                        onEdit = { onEdit(topic) },
                        onDelete = { onDelete(topic.id) },
                        onDuplicate = { onDuplicate(topic.id) },
                        onManageLessons = { onManageLessons(topic) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminTopicRow(
    topic: LessonTopic,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onManageLessons: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
            color = if (isSelected) {
                Color(0xFF8B5CF6).copy(alpha = 0.1f)
            } else {
                Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF8B5CF6),
                            uncheckedColor = Color(0xFF6B6B7B)
                        )
                    )
                    
                    // Title column
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.35f)
                    )
                    
                    // Description column
                    Text(
                        text = if (topic.description.isNotEmpty()) {
                            topic.description.take(60) + if (topic.description.length > 60) "..." else ""
                        } else {
                            "—"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB4B4C4),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.35f)
                    )
                    
                    // Lesson number column
                    Box(modifier = Modifier.weight(0.15f)) {
                        if (topic.lessonNumber != null) {
                            Surface(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "#${topic.lessonNumber}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF8B5CF6)
                                )
                            }
                        } else {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF6B6B7B)
                            )
                        }
                    }
                    
                    // Duration column
                    Text(
                        text = if (topic.durationMinutes != null) "${topic.durationMinutes} min" else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB4B4C4),
                        modifier = Modifier.weight(0.15f)
                    )
                }
                
                // Actions column
                Row(
                    modifier = Modifier.width(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onManageLessons,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = "Manage Lessons",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF10B981)
                        )
                    }
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Duplicate",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFB4B4C4)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF8B5CF6)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
        
        HorizontalDivider(color = Color(0xFF3A3147), thickness = 1.dp)
    }
}
