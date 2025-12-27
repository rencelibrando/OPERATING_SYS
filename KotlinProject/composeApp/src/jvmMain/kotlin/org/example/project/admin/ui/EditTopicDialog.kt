package org.example.project.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LessonTopic

@Composable
fun EditTopicDialog(
    topic: LessonTopic?,
    onDismiss: () -> Unit,
    onSave: (LessonTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (topic == null) return

    var title by remember { mutableStateOf(TextFieldValue(topic.title)) }
    var description by remember { mutableStateOf(TextFieldValue(topic.description)) }
    var lessonNumber by remember { mutableStateOf(topic.lessonNumber?.toString() ?: "") }
    var durationMinutes by remember { mutableStateOf(topic.durationMinutes?.toString() ?: "") }
    var isLocked by remember { mutableStateOf(topic.isLocked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1B2E),
        title = {
            Text(
                "Edit Topic",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF3A3147),
                            focusedContainerColor = Color(0xFF2D2A3E),
                            unfocusedContainerColor = Color(0xFF2D2A3E),
                            cursorColor = Color(0xFF8B5CF6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF8B5CF6),
                            unfocusedLabelColor = Color(0xFFB4B4C4),
                        ),
                    shape = MaterialTheme.shapes.medium,
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF3A3147),
                            focusedContainerColor = Color(0xFF2D2A3E),
                            unfocusedContainerColor = Color(0xFF2D2A3E),
                            cursorColor = Color(0xFF8B5CF6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF8B5CF6),
                            unfocusedLabelColor = Color(0xFFB4B4C4),
                        ),
                    shape = MaterialTheme.shapes.medium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = lessonNumber,
                        onValueChange = { lessonNumber = it },
                        label = { Text("Lesson #") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF8B5CF6),
                                unfocusedBorderColor = Color(0xFF3A3147),
                                focusedContainerColor = Color(0xFF2D2A3E),
                                unfocusedContainerColor = Color(0xFF2D2A3E),
                                cursorColor = Color(0xFF8B5CF6),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF8B5CF6),
                                unfocusedLabelColor = Color(0xFFB4B4C4),
                            ),
                        shape = MaterialTheme.shapes.medium,
                    )

                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it },
                        label = { Text("Duration (min)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF8B5CF6),
                                unfocusedBorderColor = Color(0xFF3A3147),
                                focusedContainerColor = Color(0xFF2D2A3E),
                                unfocusedContainerColor = Color(0xFF2D2A3E),
                                cursorColor = Color(0xFF8B5CF6),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF8B5CF6),
                                unfocusedLabelColor = Color(0xFFB4B4C4),
                            ),
                        shape = MaterialTheme.shapes.medium,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isLocked,
                        onCheckedChange = { isLocked = it },
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = Color(0xFF8B5CF6),
                                uncheckedColor = Color(0xFF6B6B7B),
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Locked", color = Color.White)
                }

                Text(
                    text = "ID: ${topic.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B6B7B),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedTopic =
                        LessonTopic(
                            id = topic.id,
                            title = title.text,
                            description = description.text,
                            lessonNumber = lessonNumber.toIntOrNull(),
                            isCompleted = topic.isCompleted,
                            isLocked = isLocked,
                            durationMinutes = durationMinutes.toIntOrNull(),
                            language = topic.language,
                        )
                    onSave(updatedTopic)
                },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                    ),
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFB4B4C4))
            }
        },
    )
}
