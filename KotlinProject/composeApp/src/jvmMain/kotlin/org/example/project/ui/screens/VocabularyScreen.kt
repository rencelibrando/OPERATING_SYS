package org.example.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.VocabularyViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.core.auth.User as AuthUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import org.example.project.domain.model.VocabularyStatus
import org.example.project.domain.model.VocabularyWord

@Composable
fun VocabularyScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    viewModel: VocabularyViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val vocabularyWords by viewModel.vocabularyWords
    val vocabularyStats by viewModel.vocabularyStats
    val vocabularyFeatures by viewModel.vocabularyFeatures
    val searchQuery by viewModel.searchQuery
    val selectedFilter by viewModel.selectedFilter
    val filteredWords by viewModel.filteredWords
    val isLoading by viewModel.isLoading
    
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vocabulary Bank",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            UserAvatar(
                initials = authenticatedUser?.initials ?: "U",
                profileImageUrl = authenticatedUser?.profileImageUrl,
                size = 48.dp,
                onClick = onUserAvatarClick
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VocabularyStatsCard(
                title = "Total Words",
                count = vocabularyStats.totalWords,
                icon = "📚",
                backgroundColor = Color(0xFF8B5CF6), // Purple
                modifier = Modifier.weight(1f)
            )
            
            VocabularyStatsCard(
                title = "Mastered",
                count = vocabularyStats.masteredWords,
                icon = "✅",
                backgroundColor = Color(0xFF10B981), // Green
                modifier = Modifier.weight(1f)
            )
            
            VocabularyStatsCard(
                title = "Learning",
                count = vocabularyStats.learningWords,
                icon = "🎯",
                backgroundColor = Color(0xFFF59E0B), // Orange
                modifier = Modifier.weight(1f)
            )
            
            VocabularyStatsCard(
                title = "Need Review",
                count = vocabularyStats.needReviewWords,
                icon = "🔄",
                backgroundColor = Color(0xFFEF4444), // Red
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (vocabularyStats.totalWords == 0) {
            VocabularyEmptyState(
                features = vocabularyFeatures,
                onAddFirstWordClick = viewModel::onAddFirstWordClicked,
                onExploreLessonsClick = viewModel::onExploreLessonsClicked
            )
        } else {
            VocabularySearchBar(
                searchQuery = searchQuery,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                selectedFilter = selectedFilter,
                onFilterSelected = viewModel::onFilterSelected,
                onAddWordClick = { showAddDialog = true }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (filteredWords.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    filteredWords.forEach { word ->
                        VocabularyWordItem(
                            word = word,
                            onClick = viewModel::onVocabularyWordClicked
                        )
                    }
                }
            } else if (searchQuery.isNotEmpty() || selectedFilter.name != "ALL") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔍",
                        style = MaterialTheme.typography.displaySmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "No vocabulary words found",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                    
                    Text(
                        text = "Try adjusting your search or filter criteria",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddWordDialog(
            onDismiss = { showAddDialog = false },
            onSubmit = { word, definition ->
                val newWord = VocabularyWord(
                    id = "word_${System.currentTimeMillis()}",
                    word = word,
                    definition = definition,
                    pronunciation = "",
                    category = "General",
                    difficulty = "Beginner",
                    examples = emptyList(),
                    status = VocabularyStatus.NEW,
                    dateAdded = System.currentTimeMillis(),
                    lastReviewed = null
                )
                viewModel.addWord(newWord)
                showAddDialog = false
            }
        )
    }
}


@Composable
private fun VocabularyWordItem(
    word: VocabularyWord,
    onClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = word.word,
            style = MaterialTheme.typography.titleMedium,
            color = WordBridgeColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = word.definition,
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondary
        )
    }
}


@Composable
private fun AddWordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var word by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Vocabulary Word") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("Word") }
                )
                OutlinedTextField(
                    value = definition,
                    onValueChange = { definition = it },
                    label = { Text("Definition") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(word, definition) },
                enabled = word.isNotBlank() && definition.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
