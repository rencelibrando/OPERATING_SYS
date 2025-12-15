package org.example.project.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.admin.presentation.AdminLessonTopicsViewModel
import org.example.project.admin.presentation.AdminLessonTopicsViewModel.SortOrder
import org.example.project.admin.presentation.UserManagementViewModel
import org.example.project.admin.ui.UserManagementTab
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.LessonTopic
import org.example.project.ui.theme.WordBridgeTheme

@Composable
fun AdminApp() {
    WordBridgeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AdminMainScreen()
        }
    }
}

@Composable
private fun AdminMainScreen() {
    val topicsViewModel: AdminLessonTopicsViewModel = viewModel()
    val userViewModel: UserManagementViewModel = viewModel()
    
    val topics by topicsViewModel.topics
    val users by userViewModel.users
    
    var selectedTab by remember { mutableStateOf("topics") }
    
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // Sidebar Navigation
        AdminSidebar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            topicsCount = topics.size,
            usersCount = users.size
        )

        // Main Content Area with dark background
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF15121F) // Dark background for main content
        ) {
            when (selectedTab) {
                "topics" -> LessonTopicsTab()
                "users" -> UserManagementTab()
            }
        }
    }
}

@Composable
private fun LessonTopicsTab() {
    val viewModel: AdminLessonTopicsViewModel = viewModel()
    val topics by viewModel.topics
    val selectedLanguage by viewModel.selectedLanguage
    val selectedDifficulty by viewModel.selectedDifficulty
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val successMessage by viewModel.successMessage
    val editingTopic by viewModel.editingTopic
    val searchQuery by viewModel.searchQuery
    val selectedTopics by viewModel.selectedTopics
    
    val filteredTopics = viewModel.getFilteredAndSortedTopics()
    
    var topicToDelete by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTopicForLessons by remember { mutableStateOf<LessonTopic?>(null) }

    // Clear messages after 3 seconds
    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null || successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    // Show edit dialog if editing
    if (editingTopic != null) {
        EditTopicDialog(
            topic = editingTopic,
            onDismiss = { viewModel.cancelEditing() },
            onSave = { updatedTopic ->
                viewModel.updateTopic(updatedTopic)
            }
        )
    }
    
    // Show create dialog
    if (showCreateDialog) {
        CreateTopicDialog(
            language = selectedLanguage,
            difficulty = selectedDifficulty,
            onDismiss = { showCreateDialog = false },
            onSave = { newTopic ->
                viewModel.createTopic(newTopic)
                showCreateDialog = false
            }
        )
    }
    
    // Show lesson management screen if a topic is selected
    if (selectedTopicForLessons != null) {
        AdminLessonContentScreen(
            topicId = selectedTopicForLessons!!.id,
            topicTitle = selectedTopicForLessons!!.title,
            onBack = { selectedTopicForLessons = null }
        )
        return
    }
    
    // Show delete confirmation dialog
    if (topicToDelete != null) {
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            title = { Text("Delete Topic") },
            text = { 
                val topic = topics.find { it.id == topicToDelete }
                Text("Are you sure you want to delete '${topic?.title ?: topicToDelete}'? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTopic(topicToDelete!!)
                        topicToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { topicToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header with title and action button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Topics",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    if (isLoading) {
                        Spacer(modifier = Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF8B5CF6),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selectedLanguage != null && selectedDifficulty != null) {
                        "${filteredTopics.size} topics"
                    } else {
                        "Select language and difficulty to view topics"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB4B4C4)
                )
            }
            
            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTopics.isNotEmpty()) {
                    androidx.compose.material3.Button(
                        onClick = { viewModel.deleteSelectedTopics() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Selected",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete ${selectedTopics.size}")
                    }
                }
                androidx.compose.material3.Button(
                    onClick = { showCreateDialog = true },
                    enabled = selectedLanguage != null && selectedDifficulty != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                        disabledContainerColor = Color(0xFF3A3147)
                    )
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Create Topic",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Topic")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Messages
        if (errorMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }

        if (successMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = successMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filters section
        Column(modifier = Modifier.fillMaxWidth()) {
            // Language label and chips
            Text(
                text = "Language",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFB4B4C4),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LessonLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = selectedLanguage == language,
                        onClick = {
                            viewModel.loadTopics(language, LessonDifficulty.BEGINNER)
                        },
                        label = { 
                            Text(
                                text = language.displayName, 
                                style = MaterialTheme.typography.bodyMedium
                            ) 
                        },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF2D2A3E),
                            selectedContainerColor = Color(0xFF8B5CF6),
                            labelColor = Color(0xFFB4B4C4),
                            selectedLabelColor = Color.White
                        ),
                        border = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Difficulty chips
            if (selectedLanguage != null) {
                Text(
                    text = "Difficulty Level",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFB4B4C4),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LessonDifficulty.entries.forEach { difficulty ->
                        FilterChip(
                            selected = selectedDifficulty == difficulty,
                            onClick = {
                                viewModel.loadTopics(selectedLanguage!!, difficulty)
                            },
                            label = { 
                                Text(
                                    text = difficulty.displayName, 
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF2D2A3E),
                                selectedContainerColor = Color(0xFF8B5CF6),
                                labelColor = Color(0xFFB4B4C4),
                                selectedLabelColor = Color.White
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Search and sort bar
        if (selectedLanguage != null && selectedDifficulty != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { 
                        Text(
                            "Search topics by title...", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6B6B7B)
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search, 
                            contentDescription = null, 
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF6B6B7B)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    Icons.Filled.Close, 
                                    contentDescription = "Clear", 
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF6B6B7B)
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color(0xFF3A3147),
                        focusedContainerColor = Color(0xFF2D2A3E),
                        unfocusedContainerColor = Color(0xFF2D2A3E),
                        cursorColor = Color(0xFF8B5CF6)
                    ),
                    shape = MaterialTheme.shapes.medium
                )
                
                // Sort dropdown
                var expanded by remember { mutableStateOf(false) }
                Box {
                    androidx.compose.material3.Button(
                        onClick = { expanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2D2A3E)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Sort, 
                            contentDescription = "Sort", 
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sort")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.displayName, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Topics list
            if (filteredTopics.isEmpty() && !isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1E1B2E),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📚",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No topics found",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Click \"Add New Topic\" to create your first topic",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB4B4C4)
                            )
                        }
                    }
                }
            } else {
                AdminTopicsList(
                    topics = filteredTopics,
                    isLoading = isLoading,
                    selectedTopics = selectedTopics,
                    onTopicSelect = { viewModel.toggleTopicSelection(it) },
                    onSelectAll = { viewModel.selectAllTopics() },
                    onClearSelection = { viewModel.clearSelection() },
                    onEdit = { topic -> viewModel.startEditing(topic) },
                    onDelete = { topicId -> topicToDelete = topicId },
                    onDuplicate = { topicId -> viewModel.duplicateTopic(topicId) },
                    onManageLessons = { topic -> selectedTopicForLessons = topic }
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1B2E),
                shape = MaterialTheme.shapes.medium
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🌍",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Select Language & Difficulty",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose a language and difficulty level to manage topics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB4B4C4)
                        )
                    }
                }
            }
        }
    }
}
