package org.example.project.admin.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.example.project.admin.presentation.AdminLessonContentViewModel
import org.example.project.admin.presentation.ChoiceBuilder
import org.example.project.admin.presentation.NarrationStatus
import org.example.project.admin.presentation.QuestionBuilder
import org.example.project.core.audio.AudioPlayer
import org.example.project.core.image.DesktopFilePicker
import org.example.project.domain.model.QuestionType
import java.io.File

/**
 * Admin screen for creating and editing lesson content.
 * Supports dynamic fields based on question types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLessonContentScreen(
    topicId: String,
    topicTitle: String,
    onBack: () -> Unit,
    viewModel: AdminLessonContentViewModel = viewModel(),
) {
    val lessons by viewModel.lessons
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val successMessage by viewModel.successMessage
    val currentLesson by viewModel.currentLesson

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingLessonId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(topicId) {
        viewModel.loadLessonsForTopic(topicId)
    }

    // Open a dialog when a lesson is loaded for editing
    LaunchedEffect(currentLesson) {
        if (currentLesson != null && !showCreateDialog) {
            editingLessonId = currentLesson!!.id
            showCreateDialog = true
        }
    }

    // Clear messages after 3 seconds
    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null || successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF15121F),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }

                    Column {
                        Text(
                            text = "Lessons: $topicTitle",
                            style =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = Color.White,
                        )
                        Text(
                            text = "${lessons.size} lessons",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB4B4C4),
                        )
                    }
                }

                Button(
                    onClick = { showCreateDialog = true },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                        ),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Lesson")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Messages
            if (errorMessage != null) {
                MessageCard(
                    message = errorMessage!!,
                    isError = true,
                    onDismiss = { viewModel.clearMessages() },
                )
            }

            if (successMessage != null) {
                MessageCard(
                    message = successMessage!!,
                    isError = false,
                    onDismiss = { viewModel.clearMessages() },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lessons list
            if (lessons.isEmpty() && !isLoading) {
                EmptyState(
                    icon = "📝",
                    title = "No lessons yet",
                    description = "Create your first lesson to get started",
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lessons.size) { index ->
                        val lesson = lessons[index]
                        LessonCard(
                            lesson = lesson,
                            onEdit = { viewModel.loadLesson(lesson.id) },
                            onDelete = { viewModel.deleteLesson(lesson.id) },
                        )
                    }
                }
            }
        }

        // Create/Edit Dialog
        if (showCreateDialog) {
            // Close dialog on successful save
            LaunchedEffect(successMessage) {
                if (successMessage != null) {
                    showCreateDialog = false
                    editingLessonId = null
                }
            }

            LessonCreationDialog(
                isEditing = editingLessonId != null,
                viewModel = viewModel,
                onDismiss = {
                    showCreateDialog = false
                    editingLessonId = null
                    viewModel.clearForm()
                },
                onSave = {
                    if (editingLessonId != null) {
                        viewModel.updateLesson(editingLessonId!!)
                    } else {
                        viewModel.createLesson(topicId)
                    }
                    // Don't close the dialog here - let LaunchedEffect handle it on success
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonCreationDialog(
    isEditing: Boolean,
    viewModel: AdminLessonContentViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val lessonTitle by viewModel.lessonTitle
    val lessonDescription by viewModel.lessonDescription
    val questions by viewModel.questions
    val isPublished by viewModel.isPublished

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Check if there are unsaved changes
    val hasChanges =
        lessonTitle.isNotBlank() ||
            lessonDescription.isNotBlank() ||
            questions.isNotEmpty()

    // Discard confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color(0xFF1E1B2E),
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEAB308),
                    )
                    Text(
                        "Discard Changes?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            },
            text = {
                Text(
                    "You have unsaved changes. Are you sure you want to close without saving?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB4B4C4),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearForm()
                        onDismiss()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                        ),
                ) {
                    Text("Discard", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { },
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                        ),
                ) {
                    Text("Keep Editing")
                }
            },
        )
    }

    Dialog(
        onDismissRequest = {
            if (hasChanges) {
            } else {
                onDismiss()
            }
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .width(1000.dp)
                    .heightIn(max = 900.dp)
                    .padding(vertical = 28.dp),
            color = Color(0xFF1E1B2E),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
            ) {
                Text(
                    if (isEditing) "Edit Lesson" else "Create New Lesson",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val errorMessage by viewModel.errorMessage
                    // Show validation error at the top
                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFEF4444).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                )
                                Text(
                                    errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFEF4444),
                                )
                            }
                        }
                    }

                    // Lesson Info
                    OutlinedTextField(
                        value = lessonTitle,
                        onValueChange = { viewModel.setLessonTitle(it) },
                        label = { Text("Lesson Title*") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors(),
                    )

                    OutlinedTextField(
                        value = lessonDescription,
                        onValueChange = { viewModel.setLessonDescription(it) },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = textFieldColors(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Published",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                        Switch(
                            checked = isPublished,
                            onCheckedChange = { viewModel.setIsPublished(it) },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF8B5CF6),
                                ),
                        )
                    }

                    HorizontalDivider(color = Color(0xFF3A3147))

                    // Narration Settings Section
                    val enableNarration by viewModel.enableLessonNarration
                    Text(
                        "🎙️ Audio Narration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable Narration (Optional)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                            )
                            Text(
                                "Auto-generates audio for questions using Edge TTS • You can save without audio",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B6B7B),
                            )
                        }
                        Switch(
                            checked = enableNarration,
                            onCheckedChange = { viewModel.setEnableLessonNarration(it) },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF8B5CF6),
                                ),
                        )
                    }

                    if (enableNarration) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF2D2A3E).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Advanced Settings (Optional)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF8B5CF6),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Leave blank for automatic language detection via FastText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B6B7B),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "Supports: Korean, German, Chinese, Spanish, French, English",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB4B4C4),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = Color(0xFF3A3147))

                    // Questions Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Questions (${questions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Multiple Choice Button
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text("Add Multiple Choice Question")
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(
                                    onClick = { viewModel.addQuestion(QuestionType.MULTIPLE_CHOICE) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        "Multiple Choice",
                                        tint = Color(0xFF8B5CF6),
                                    )
                                }
                            }

                            // Text Entry Button
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text("Add Text Entry Question")
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(
                                    onClick = { viewModel.addQuestion(QuestionType.TEXT_ENTRY) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        "Text Entry",
                                        tint = Color(0xFF10B981),
                                    )
                                }
                            }

                            // Matching Button
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text("Add Matching Question")
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(
                                    onClick = { viewModel.addQuestion(QuestionType.MATCHING) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        "Matching",
                                        tint = Color(0xFFF59E0B),
                                    )
                                }
                            }

                            // Paraphrasing Button
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text("Add Paraphrasing Question")
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(
                                    onClick = { viewModel.addQuestion(QuestionType.PARAPHRASING) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        "Paraphrasing",
                                        tint = Color(0xFF3B82F6),
                                    )
                                }
                            }

                            // Error Correction Button
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text("Add Error Correction Question")
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(
                                    onClick = { viewModel.addQuestion(QuestionType.ERROR_CORRECTION) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.BugReport,
                                        "Error Correction",
                                        tint = Color(0xFFEF4444),
                                    )
                                }
                            }
                        }
                    }

                    // Question Cards
                    questions.forEachIndexed { index, question ->
                        QuestionBuilderCard(
                            question = question,
                            index = index,
                            viewModel = viewModel,
                            onRemove = { viewModel.removeQuestion(index) },
                        )
                    }

                    if (questions.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF2D2A3E),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "No questions yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFB4B4C4),
                                )
                                Text(
                                    "Click the icons above to add questions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B6B7B),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (hasChanges) {
                            } else {
                                onDismiss()
                            }
                        },
                    ) {
                        Text("Cancel", color = Color(0xFFB4B4C4))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val canSave = lessonTitle.isNotBlank() && questions.isNotEmpty()

                    Button(
                        onClick = onSave,
                        enabled = canSave,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6),
                            ),
                    ) {
                        Text(if (isEditing) "Save Changes" else "Create Lesson")
                    }

                    if (!canSave) {
                        val reason =
                            when {
                                lessonTitle.isBlank() -> "Lesson title is required"
                                questions.isEmpty() -> "At least one question is required"
                                else -> ""
                            }
                        if (reason.isNotEmpty()) {
                            Text(
                                reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionBuilderCard(
    question: QuestionBuilder,
    index: Int,
    viewModel: AdminLessonContentViewModel,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF2D2A3E),
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (question.type) {
                            QuestionType.MULTIPLE_CHOICE -> Icons.Default.CheckCircle
                            QuestionType.TEXT_ENTRY -> Icons.Default.Edit
                            QuestionType.MATCHING -> Icons.Default.SwapHoriz
                            QuestionType.PARAPHRASING -> Icons.Default.AutoAwesome
                            QuestionType.ERROR_CORRECTION -> Icons.Default.BugReport
                        },
                        contentDescription = null,
                        tint =
                            when (question.type) {
                                QuestionType.MULTIPLE_CHOICE -> Color(0xFF8B5CF6)
                                QuestionType.TEXT_ENTRY -> Color(0xFF10B981)
                                QuestionType.MATCHING -> Color(0xFFF59E0B)
                                QuestionType.PARAPHRASING -> Color(0xFF3B82F6)
                                QuestionType.ERROR_CORRECTION -> Color(0xFFEF4444)
                            },
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Question ${index + 1} - ${question.type.displayName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "Remove",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Question Text
            OutlinedTextField(
                value = question.text,
                onValueChange = { viewModel.updateQuestionText(index, it) },
                label = { Text("Question Text*") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = textFieldColors(),
            )

            // Audio for Question with Narration Controls
            NarrationControlField(
                label = "Question Audio",
                text = question.text,
                currentUrl = question.questionAudioUrl,
                statusKey = "question_$index",
                viewModel = viewModel,
                onGenerate = { viewModel.generateQuestionNarration(index) },
                onUrlChanged = { viewModel.updateQuestionAudioUrl(index, it) },
            )

            // Type-specific fields
            when (question.type) {
                QuestionType.MULTIPLE_CHOICE -> {
                    MultipleChoiceEditor(
                        question = question,
                        questionIndex = index,
                        viewModel = viewModel,
                    )
                }
                QuestionType.TEXT_ENTRY -> {
                    TextEntryEditor(
                        question = question,
                        questionIndex = index,
                        viewModel = viewModel,
                    )
                }
                QuestionType.MATCHING -> {
                    MatchingEditor(
                        question = question,
                        questionIndex = index,
                        viewModel = viewModel,
                    )
                }
                QuestionType.PARAPHRASING -> {
                    ParaphrasingEditor(
                        question = question,
                        questionIndex = index,
                        viewModel = viewModel,
                    )
                }
                QuestionType.ERROR_CORRECTION -> {
                    ErrorCorrectionEditor(
                        question = question,
                        questionIndex = index,
                        viewModel = viewModel,
                    )
                }
            }

            // Explanation field (optional for all types)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF8B5CF6).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "📚 EXPLANATION FIELD",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "This text will be shown as the 'Explanation' to students after they answer",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB4B4C4),
                    )

                    OutlinedTextField(
                        value = question.explanation,
                        onValueChange = { viewModel.updateQuestionExplanation(index, it) },
                        label = { Text("Explanation (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        placeholder = { Text("Explain the answer to help students learn") },
                        colors = textFieldColors(),
                    )
                }
            }

            // Explanation audio narration
            if (question.explanation.isNotBlank()) {
                NarrationControlField(
                    label = "Explanation Audio",
                    text = question.explanation,
                    currentUrl = question.explanationAudioUrl,
                    statusKey = "explanation_$index",
                    viewModel = viewModel,
                    onGenerate = { viewModel.generateExplanationNarration(index) },
                    onUrlChanged = { viewModel.updateExplanationAudioUrl(index, it) },
                )
            }

            // Student view preview
            StudentViewPreview(question = question, questionIndex = index)
        }
    }
}

@Composable
private fun MultipleChoiceEditor(
    question: QuestionBuilder,
    questionIndex: Int,
    viewModel: AdminLessonContentViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Choices",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFB4B4C4),
            )
            TextButton(
                onClick = { viewModel.addChoice(questionIndex) },
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Choice")
            }
        }

        question.choices.forEachIndexed { choiceIndex, choice ->
            ChoiceEditor(
                choice = choice,
                choiceIndex = choiceIndex,
                questionIndex = questionIndex,
                viewModel = viewModel,
                onRemove = { viewModel.removeChoice(questionIndex, choiceIndex) },
            )
        }
    }
}

@Composable
private fun ChoiceEditor(
    choice: ChoiceBuilder,
    choiceIndex: Int,
    questionIndex: Int,
    viewModel: AdminLessonContentViewModel,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1E1B2E),
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = choice.isCorrect,
                        onCheckedChange = {
                            viewModel.updateChoiceCorrect(questionIndex, choiceIndex, it)
                        },
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = Color(0xFF10B981),
                            ),
                    )

                    OutlinedTextField(
                        value = choice.text,
                        onValueChange = {
                            viewModel.updateChoiceText(questionIndex, choiceIndex, it)
                        },
                        label = { Text("Choice ${choiceIndex + 1}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = textFieldColors(),
                    )
                }

                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        "Remove",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaUploadField(
                    label = "Image",
                    currentUrl = choice.imageUrl,
                    mediaType = "image",
                    viewModel = viewModel,
                    onUrlChanged = {
                        viewModel.updateChoiceImageUrl(questionIndex, choiceIndex, it)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // Audio narration for choice
            NarrationControlField(
                label = "Choice Audio",
                text = choice.text,
                currentUrl = choice.audioUrl,
                statusKey = "choice_${questionIndex}_$choiceIndex",
                viewModel = viewModel,
                onGenerate = { viewModel.generateChoiceNarration(questionIndex, choiceIndex) },
                onUrlChanged = { viewModel.updateChoiceAudioUrl(questionIndex, choiceIndex, it) },
            )
        }
    }
}

@Composable
private fun TextEntryEditor(
    question: QuestionBuilder,
    questionIndex: Int,
    viewModel: AdminLessonContentViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Students will type their answer",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB4B4C4),
        )

        // Answer field for text entry questions
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF2D2A3E).copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "✅ CORRECT ANSWER FIELD",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "This text will be shown as the 'Sample Answer' to students",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB4B4C4),
                )

                OutlinedTextField(
                    value = question.answerText,
                    onValueChange = { viewModel.updateQuestionAnswerText(questionIndex, it) },
                    label = { Text("Expected Answer/Keywords*") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("Enter the correct answer or keywords to check for") },
                    colors = textFieldColors(),
                )

                // Answer audio narration control
                if (question.answerText.isNotBlank()) {
                    NarrationControlField(
                        label = "Answer Audio",
                        text = question.answerText,
                        currentUrl = question.answerAudioUrl,
                        statusKey = "answer_$questionIndex",
                        viewModel = viewModel,
                        onGenerate = { viewModel.generateAnswerNarration(questionIndex) },
                        onUrlChanged = { viewModel.updateAnswerAudioUrl(questionIndex, it) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchingEditor(
    question: QuestionBuilder,
    questionIndex: Int,
    viewModel: AdminLessonContentViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Matching Pairs (students match left to right)",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFB4B4C4),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Shuffle button
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip {
                            Text("Shuffle right-side answers to randomize order")
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.shuffleMatchingRightSide(questionIndex)
                        },
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF59E0B),
                            ),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle Answers")
                    }
                }

                TextButton(
                    onClick = {
                        // Add a new matching pair
                        val pairId = "pair_${System.currentTimeMillis()}"
                        viewModel.addChoice(questionIndex, pairId)
                        viewModel.addChoice(questionIndex, pairId)
                    },
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Pair")
                }
            }
        }

        // Group choices by pair ID
        val pairs = question.choices.groupBy { it.matchPairId ?: "unpaired" }
        val validPairs =
            pairs.filter { (pairId, choices) ->
                pairId != "unpaired" && choices.size >= 2
            }

        if (validPairs.isEmpty()) {
            Text(
                "Click 'Add Pair' to create matching pairs",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB4B4C4),
                modifier = Modifier.padding(8.dp),
            )
        } else {
            // Info card about shuffling
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Tip: Click 'Shuffle Answers' to randomize the right column, so answers won't align with questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB4B4C4),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Student view preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF10B981).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Student View Preview:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Left column (questions)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Questions",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8B5CF6),
                                fontWeight = FontWeight.Bold,
                            )
                            validPairs.values.forEachIndexed { idx, choices ->
                                if (choices[0].text.isNotEmpty()) {
                                    Text(
                                        "${idx + 1}. ${choices[0].text}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB4B4C4),
                                    )
                                }
                            }
                        }

                        // Right column (answers) - Show in the same order as questions (they'll be shuffled for students)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Answers",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                            )
                            validPairs.values.forEach { choices ->
                                if (choices.getOrNull(1)?.text?.isNotEmpty() == true) {
                                    Text(
                                        "• ${choices[1].text}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB4B4C4),
                                    )
                                }
                            }
                            if (validPairs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "* Answers will be shuffled for students",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B6B7B),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        validPairs.values.forEachIndexed { pairIndex, choices ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1B2E),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF3A3147).copy(alpha = 0.5f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Pair number badge
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = Color(0xFF8B5CF6),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    "${pairIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OutlinedTextField(
                                value = choices[0].text,
                                onValueChange = {
                                    val index = question.choices.indexOf(choices[0])
                                    viewModel.updateChoiceText(questionIndex, index, it)
                                },
                                label = { Text("Question") },
                                placeholder = { Text("Enter question/term") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = textFieldColors(),
                            )

                            // Narration control for question/left side
                            val leftIndex = question.choices.indexOf(choices[0])
                            if (leftIndex >= 0) {
                                NarrationControlField(
                                    label = "Question Audio",
                                    text = choices[0].text,
                                    currentUrl = choices[0].audioUrl,
                                    statusKey = "choice_${questionIndex}_$leftIndex",
                                    viewModel = viewModel,
                                    onGenerate = { viewModel.generateChoiceNarration(questionIndex, leftIndex) },
                                    onUrlChanged = { viewModel.updateChoiceAudioUrl(questionIndex, leftIndex, it) },
                                )
                            }
                        }

                        Icon(
                            Icons.Default.SwapHoriz,
                            null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(28.dp),
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OutlinedTextField(
                                value = choices.getOrNull(1)?.text ?: "",
                                onValueChange = {
                                    val index = question.choices.indexOf(choices.getOrNull(1))
                                    if (index >= 0) viewModel.updateChoiceText(questionIndex, index, it)
                                },
                                label = { Text("Answer") },
                                placeholder = { Text("Enter matching answer") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = textFieldColors(),
                            )

                            // Narration control for answer/right side
                            val rightIndex = question.choices.indexOf(choices.getOrNull(1))
                            if (rightIndex >= 0) {
                                NarrationControlField(
                                    label = "Answer Audio",
                                    text = choices.getOrNull(1)?.text ?: "",
                                    currentUrl = choices.getOrNull(1)?.audioUrl,
                                    statusKey = "choice_${questionIndex}_$rightIndex",
                                    viewModel = viewModel,
                                    onGenerate = { viewModel.generateChoiceNarration(questionIndex, rightIndex) },
                                    onUrlChanged = { viewModel.updateChoiceAudioUrl(questionIndex, rightIndex, it) },
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                // Remove both items in the pair
                                choices.forEach { choice ->
                                    val index = question.choices.indexOf(choice)
                                    if (index >= 0) {
                                        viewModel.removeChoice(questionIndex, index)
                                    }
                                }
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "Remove pair",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParaphrasingEditor(
    question: QuestionBuilder,
    questionIndex: Int,
    viewModel: AdminLessonContentViewModel,
) {
    BoxWithConstraints {
        val isWide = maxWidth > 720.dp

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Students will rewrite the sentence in their own words",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB4B4C4),
            )

            // Question text field with visual clarification
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF3A3147).copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "❓ QUESTION/PROMPT FIELD",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "This is the text students will paraphrase (rewrite in their own words)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB4B4C4),
                    )

                    OutlinedTextField(
                        value = question.text,
                        onValueChange = { viewModel.updateQuestionText(questionIndex, it) },
                        label = { Text("Question Text*") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = textFieldColors(),
                    )
                }
            }

            // Place audio + sample/wrong message side by side when wide
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaUploadField(
                        label = "Question Audio (Optional)",
                        currentUrl = question.questionAudioUrl,
                        mediaType = "audio",
                        viewModel = viewModel,
                        onUrlChanged = { viewModel.updateQuestionAudioUrl(questionIndex, it) },
                        modifier = Modifier.weight(0.6f),
                    )

                    Column(
                        modifier = Modifier.weight(0.4f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF10B981).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "✅ SAMPLE ANSWER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Shown as hint to students",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB4B4C4),
                                )

                                OutlinedTextField(
                                    value = question.answerText,
                                    onValueChange = { viewModel.updateQuestionAnswerText(questionIndex, it) },
                                    label = { Text("Sample Answer (Optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    placeholder = { Text("Provide a sample paraphrased answer") },
                                    colors = textFieldColors(),
                                )
                            }
                        }
                    }
                }
            } else {
                MediaUploadField(
                    label = "Question Audio (Optional)",
                    currentUrl = question.questionAudioUrl,
                    mediaType = "audio",
                    viewModel = viewModel,
                    onUrlChanged = { viewModel.updateQuestionAudioUrl(questionIndex, it) },
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "✅ SAMPLE ANSWER",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Shown as hint to students",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB4B4C4),
                        )

                        OutlinedTextField(
                            value = question.answerText,
                            onValueChange = { viewModel.updateQuestionAnswerText(questionIndex, it) },
                            label = { Text("Sample Answer (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            placeholder = { Text("Provide a sample paraphrased answer") },
                            colors = textFieldColors(),
                        )
                    }
                }
            }

            Text(
                "Note: Paraphrasing is usually manually/AI reviewed; sample answer helps show intent but is not required.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B6B7B),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF8B5CF6).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "📚 EXPLANATION FIELD",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "This explains the paraphrasing concept or provides learning context",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB4B4C4),
                    )

                    OutlinedTextField(
                        value = question.explanation,
                        onValueChange = { viewModel.updateQuestionExplanation(questionIndex, it) },
                        label = { Text("Explanation (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        placeholder = { Text("Explain the answer to help students learn") },
                        colors = textFieldColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCorrectionEditor(
    question: QuestionBuilder,
    questionIndex: Int,
    viewModel: AdminLessonContentViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Students will find and correct errors in the text",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB4B4C4),
        )

        OutlinedTextField(
            value = question.errorText,
            onValueChange = { viewModel.updateQuestionErrorText(questionIndex, it) },
            label = { Text("Text with Errors*") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("Enter text containing intentional errors") },
            colors = textFieldColors(),
        )

        OutlinedTextField(
            value = question.answerText,
            onValueChange = { viewModel.updateQuestionAnswerText(questionIndex, it) },
            label = { Text("Corrected Text*") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("Enter the corrected version") },
            colors = textFieldColors(),
        )
    }
}

@Composable
private fun MediaUploadField(
    label: String,
    currentUrl: String?,
    mediaType: String,
    viewModel: AdminLessonContentViewModel,
    onUrlChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color(0xFF1E1B2E),
            title = {
                Text(
                    "Remove ${if (mediaType == "image") "Image" else "Audio"}?",
                    color = Color.White,
                )
            },
            text = {
                Text(
                    "Are you sure you want to remove this $mediaType?",
                    color = Color(0xFFB4B4C4),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUrlChanged(null)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                        ),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { },
                ) {
                    Text("Cancel", color = Color.White)
                }
            },
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB4B4C4),
        )

        // Image preview
        if (mediaType == "image" && currentUrl != null) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2D2A3E),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Show image preview
                    NetworkImage(
                        url = currentUrl,
                        contentDescription = "Preview",
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )

                    // Delete button overlay
                    IconButton(
                        onClick = { },
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(32.dp)
                                .background(
                                    color = Color(0xFFEF4444).copy(alpha = 0.9f),
                                    shape = CircleShape,
                                ),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    val file =
                        DesktopFilePicker.pickFile(
                            title = "Select $mediaType file",
                            allowedExtensions =
                                when (mediaType) {
                                    "image" -> listOf("jpg", "jpeg", "png", "gif", "webp")
                                    "audio" -> listOf("mp3", "wav", "m4a", "mov")
                                    else -> listOf("*")
                                },
                        )
                    file?.let {
                        viewModel.uploadMedia(File(it), mediaType) { url ->
                            onUrlChanged(url)
                        }
                    }
                },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3A3147),
                    ),
                modifier = Modifier.height(36.dp),
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (currentUrl != null) "Change" else "Upload",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (currentUrl != null && mediaType != "image") {
                Text(
                    "✓ Uploaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF10B981),
                )
                IconButton(
                    onClick = { },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Remove",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// NetworkImage component for loading images from URLs
@Composable
private fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
) {
    var bitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        isLoading = true
        error = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = java.net.URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                val inputStream = connection.getInputStream()
                val loadedBitmap =
                    inputStream.use {
                        org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
                    }
                bitmap = loadedBitmap
                isLoading = false
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF8B5CF6),
                )
            }
            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = "Error",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        "Failed to load",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444),
                    )
                }
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = contentScale,
                )
            }
        }
    }
}

@Composable
private fun textFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFF8B5CF6),
        unfocusedBorderColor = Color(0xFF3A3147),
        focusedContainerColor = Color(0xFF1E1B2E),
        unfocusedContainerColor = Color(0xFF1E1B2E),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color(0xFF8B5CF6),
        focusedLabelColor = Color(0xFF8B5CF6),
        unfocusedLabelColor = Color(0xFFB4B4C4),
    )

@Composable
private fun MessageCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                    null,
                    tint = if (isError) Color(0xFFEF4444) else Color(0xFF10B981),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) Color(0xFFEF4444) else Color(0xFF10B981),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    null,
                    tint = if (isError) Color(0xFFEF4444) else Color(0xFF10B981),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: String,
    title: String,
    description: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                icon,
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB4B4C4),
            )
        }
    }
}

@Composable
private fun StudentViewPreview(
    question: QuestionBuilder,
    questionIndex: Int,
) {
    var showPreview by remember { mutableStateOf(false) }
    var selectedChoice by remember { mutableStateOf<String?>(null) }
    var textAnswer by remember { mutableStateOf("") }
    var matchingAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showFeedback by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Toggle button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Student View Preview",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF8B5CF6),
                fontWeight = FontWeight.Bold,
            )
            TextButton(
                onClick = {
                    showPreview = !showPreview
                    if (!showPreview) {
                        // Reset preview state
                        selectedChoice = null
                        textAnswer = ""
                        matchingAnswers = emptyMap()
                        showFeedback = false
                    }
                },
            ) {
                Icon(
                    if (showPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showPreview) "Hide Preview" else "Show Preview")
            }
        }

        // Preview content
        if (showPreview) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF15121F),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Preview header
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "How students will see this question:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8B5CF6),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    HorizontalDivider(color = Color(0xFF3A3147))

                    // Question type badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color =
                            when (question.type) {
                                QuestionType.MULTIPLE_CHOICE -> Color(0xFF8B5CF6).copy(alpha = 0.1f)
                                QuestionType.TEXT_ENTRY -> Color(0xFF10B981).copy(alpha = 0.1f)
                                QuestionType.MATCHING -> Color(0xFFF59E0B).copy(alpha = 0.1f)
                                QuestionType.PARAPHRASING -> Color(0xFF3B82F6).copy(alpha = 0.1f)
                                QuestionType.ERROR_CORRECTION -> Color(0xFFEF4444).copy(alpha = 0.1f)
                            },
                        border =
                            BorderStroke(
                                1.dp,
                                when (question.type) {
                                    QuestionType.MULTIPLE_CHOICE -> Color(0xFF8B5CF6).copy(alpha = 0.3f)
                                    QuestionType.TEXT_ENTRY -> Color(0xFF10B981).copy(alpha = 0.3f)
                                    QuestionType.MATCHING -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                                    QuestionType.PARAPHRASING -> Color(0xFF3B82F6).copy(alpha = 0.3f)
                                    QuestionType.ERROR_CORRECTION -> Color(0xFFEF4444).copy(alpha = 0.3f)
                                },
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                question.type.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color =
                                    when (question.type) {
                                        QuestionType.MULTIPLE_CHOICE -> Color(0xFF8B5CF6)
                                        QuestionType.TEXT_ENTRY -> Color(0xFF10B981)
                                        QuestionType.MATCHING -> Color(0xFFF59E0B)
                                        QuestionType.PARAPHRASING -> Color(0xFF3B82F6)
                                        QuestionType.ERROR_CORRECTION -> Color(0xFFEF4444)
                                    },
                            )
                        }
                    }

                    // Question text
                    if (question.text.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = Color(0xFF2D2A3E).copy(alpha = 0.5f),
                                ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                question.text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    // Question-specific preview
                    when (question.type) {
                        QuestionType.MULTIPLE_CHOICE -> {
                            if (question.choices.isNotEmpty()) {
                                Text(
                                    "Choices:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFB4B4C4),
                                )
                                question.choices.forEachIndexed { index, choice ->
                                    if (choice.text.isNotBlank()) {
                                        val isSelected = selectedChoice == choice.id
                                        val isCorrect = choice.isCorrect
                                        Card(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedChoice = choice.id
                                                        showFeedback = false
                                                    },
                                            colors =
                                                CardDefaults.cardColors(
                                                    containerColor =
                                                        when {
                                                            showFeedback && isSelected && isCorrect -> Color(0xFF10B981).copy(alpha = 0.2f)
                                                            showFeedback && isSelected && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                                            isSelected -> Color(0xFF2D2A3E).copy(alpha = 0.7f)
                                                            else -> Color(0xFF2D2A3E).copy(alpha = 0.3f)
                                                        },
                                                ),
                                            border =
                                                BorderStroke(
                                                    2.dp,
                                                    when {
                                                        showFeedback && isSelected && isCorrect -> Color(0xFF10B981)
                                                        showFeedback && isSelected && !isCorrect -> Color(0xFFEF4444)
                                                        isSelected -> Color(0xFF8B5CF6)
                                                        else -> Color(0xFF3A3147).copy(alpha = 0.5f)
                                                    },
                                                ),
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(20.dp),
                                                    shape = CircleShape,
                                                    color =
                                                        when {
                                                            showFeedback && isSelected && isCorrect -> Color(0xFF10B981)
                                                            showFeedback && isSelected && !isCorrect -> Color(0xFFEF4444)
                                                            isSelected -> Color(0xFF8B5CF6)
                                                            else -> Color.Transparent
                                                        },
                                                    border = if (!isSelected) BorderStroke(2.dp, Color(0xFF4A4658)) else null,
                                                ) {
                                                    if (isSelected) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                if (showFeedback && !isCorrect) Icons.Default.Close else Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    choice.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isSelected) Color.White else Color(0xFFB4B4C4),
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "Add choices to see preview",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B6B7B),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                )
                            }
                        }
                        QuestionType.TEXT_ENTRY, QuestionType.PARAPHRASING -> {
                            OutlinedTextField(
                                value = textAnswer,
                                onValueChange = {
                                    textAnswer = it
                                    showFeedback = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Students type their answer here...") },
                                minLines = if (question.type == QuestionType.PARAPHRASING) 3 else 1,
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF8B5CF6),
                                        unfocusedBorderColor = Color(0xFF3A3147),
                                        focusedContainerColor = Color(0xFF1E1B2E),
                                        unfocusedContainerColor = Color(0xFF1E1B2E),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                    ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                        QuestionType.MATCHING -> {
                            if (question.choices.size >= 2) {
                                val pairs =
                                    question.choices
                                        .filter { !it.matchPairId.isNullOrEmpty() }
                                        .groupBy { it.matchPairId }
                                        .filter { (_, items) -> items.size >= 2 }

                                if (pairs.isNotEmpty()) {
                                    Text(
                                        "Match items on the left with answers on the right:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB4B4C4),
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        // Left column
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                "Questions",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF8B5CF6),
                                                fontWeight = FontWeight.Bold,
                                            )
                                            pairs.values.forEachIndexed { index, items ->
                                                if (items[0].text.isNotEmpty()) {
                                                    Surface(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = Color(0xFF2D2A3E).copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(8.dp),
                                                    ) {
                                                        Text(
                                                            "${index + 1}. ${items[0].text}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(8.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Right column
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                "Answers",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                            )
                                            pairs.values.forEach { items ->
                                                if (items.getOrNull(1)?.text?.isNotEmpty() == true) {
                                                    Surface(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = Color(0xFF2D2A3E).copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(8.dp),
                                                    ) {
                                                        Text(
                                                            "• ${items[1].text}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(8.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        "Add matching pairs to see preview",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6B6B7B),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    )
                                }
                            }
                        }
                        QuestionType.ERROR_CORRECTION -> {
                            if (question.errorText.isNotBlank()) {
                                Text(
                                    "Text with errors:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFEF4444),
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor = Color(0xFFEF4444).copy(alpha = 0.1f),
                                        ),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        question.errorText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }

                            Text(
                                "Students correct the text:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB4B4C4),
                            )
                            OutlinedTextField(
                                value = textAnswer,
                                onValueChange = {
                                    textAnswer = it
                                    showFeedback = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Write the corrected text here...") },
                                minLines = 3,
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF8B5CF6),
                                        unfocusedBorderColor = Color(0xFF3A3147),
                                        focusedContainerColor = Color(0xFF1E1B2E),
                                        unfocusedContainerColor = Color(0xFF1E1B2E),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                    ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }

                    // Check Answer button simulation
                    if (question.type == QuestionType.MULTIPLE_CHOICE && selectedChoice != null) {
                        Button(
                            onClick = { showFeedback = true },
                            enabled = !showFeedback,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8B5CF6),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check Answer")
                        }
                    } else if ((
                            question.type == QuestionType.TEXT_ENTRY ||
                                question.type == QuestionType.ERROR_CORRECTION
                        ) &&
                        textAnswer.isNotBlank()
                    ) {
                        Button(
                            onClick = { showFeedback = true },
                            enabled = !showFeedback,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8B5CF6),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check Answer")
                        }
                    }

                    // Feedback display
                    if (showFeedback) {
                        val isCorrect =
                            when (question.type) {
                                QuestionType.MULTIPLE_CHOICE -> {
                                    question.choices.find { it.id == selectedChoice }?.isCorrect == true
                                }
                                QuestionType.TEXT_ENTRY -> {
                                    textAnswer.trim().equals(question.answerText.trim(), ignoreCase = true)
                                }
                                QuestionType.PARAPHRASING -> true // manual/AI review; treat as pass here
                                else -> false
                            }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCorrect) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    if (isCorrect) {
                                        "Correct! Great job!"
                                    } else {
                                        question.wrongAnswerFeedback.takeIf { it.isNotBlank() }
                                            ?: "Incorrect. Please try again!"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                                )
                            }
                        }

                        if (!isCorrect) {
                            Text(
                                "Next button will remain disabled until correct answer is provided",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B6B7B),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NarrationControlField(
    label: String,
    text: String,
    currentUrl: String?,
    statusKey: String,
    viewModel: AdminLessonContentViewModel,
    onGenerate: () -> Unit,
    onUrlChanged: (String?) -> Unit,
) {
    val narrationStatus by viewModel.narrationStatus
    val status = narrationStatus[statusKey] ?: NarrationStatus.Idle
    val coroutineScope = rememberCoroutineScope()
    val audioPlayer = remember { AudioPlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB4B4C4),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentUrl != null) {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                audioPlayer.stop()
                                isPlaying = false
                            } else {
                                coroutineScope.launch {
                                    isPlaying = true
                                    audioPlayer.setPlaybackFinishedCallback {
                                        isPlaying = false
                                    }
                                    audioPlayer.playAudioFromUrl(currentUrl)
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop preview" else "Preview audio",
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Button(
                    onClick = onGenerate,
                    enabled = text.isNotBlank() && status !is NarrationStatus.Generating,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                        ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    when (status) {
                        is NarrationStatus.Generating -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating...", style = MaterialTheme.typography.labelSmall)
                        }
                        is NarrationStatus.Ready -> {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate", style = MaterialTheme.typography.labelSmall)
                        }
                        is NarrationStatus.Failed -> {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                        else -> {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        when (status) {
            is NarrationStatus.Ready -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Audio ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF10B981),
                        )
                    }
                }
            }
            is NarrationStatus.Failed -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Failed: ${status.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF4444),
                        )
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun LessonCard(
    lesson: org.example.project.domain.model.LessonSummary,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B2E),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (lesson.description != null) {
                    Text(
                        lesson.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB4B4C4),
                    )
                }
                Text(
                    "${lesson.questionCount} questions • ${if (lesson.isPublished) "Published" else "Draft"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B6B7B),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        "Edit",
                        tint = Color(0xFF8B5CF6),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = Color(0xFFEF4444),
                    )
                }
            }
        }
    }
}
