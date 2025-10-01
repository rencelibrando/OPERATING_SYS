package org.example.project.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import androidx.compose.foundation.shape.RoundedCornerShape
import org.example.project.domain.model.VocabularyStatus
import org.example.project.domain.model.VocabularyWord
import kotlinx.coroutines.launch
import org.example.project.core.dictionary.DictionaryApiService
import org.example.project.core.dictionary.WordNotFoundException

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

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.refresh() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = WordBridgeColors.TextSecondary
                    )
                ) {
                    Text(
                        text = "Refresh",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                UserAvatar(
                    initials = authenticatedUser?.initials ?: "U",
                    profileImageUrl = authenticatedUser?.profileImageUrl,
                    size = 48.dp,
                    onClick = onUserAvatarClick
                )
            }
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
                backgroundColor = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f)
            )

            VocabularyStatsCard(
                title = "Mastered",
                count = vocabularyStats.masteredWords,
                icon = "✅",
                backgroundColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )

            VocabularyStatsCard(
                title = "Learning",
                count = vocabularyStats.learningWords,
                icon = "🎯",
                backgroundColor = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )

            VocabularyStatsCard(
                title = "Need Review",
                count = vocabularyStats.needReviewWords,
                icon = "🔄",
                backgroundColor = Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (vocabularyStats.totalWords == 0) {
            VocabularyEmptyState(
                features = vocabularyFeatures,
                onAddFirstWordClick = { showAddDialog = true },
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
            onSubmit = { word, definition, pronunciation, example, category ->
                val newWord = VocabularyWord(
                    id = "word_${System.currentTimeMillis()}",
                    word = word,
                    definition = definition,
                    pronunciation = pronunciation,
                    category = category,
                    difficulty = "Beginner",
                    examples = if (example != null) listOf(example) else emptyList(),
                    status = VocabularyStatus.NEW,
                    dateAdded = System.currentTimeMillis(),
                    lastReviewed = null
                )
                viewModel.addWord(newWord)
                viewModel.loadAll()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
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

        TextButton(
            onClick = { println("Practice ${word.word} Clicked.") },
            colors = ButtonDefaults.textButtonColors(
                contentColor = WordBridgeColors.TextPrimary
            ),
            border = BorderStroke(1.dp, WordBridgeColors.PrimaryPurple)
        ) {
            Text(
                text = "Practice",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@Composable
private fun AddWordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String?, String) -> Unit
) {
    var word by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wordDefinition by remember { mutableStateOf<org.example.project.core.dictionary.WordDefinition?>(null) }

    val scope = rememberCoroutineScope()
    val dictionaryService = remember { DictionaryApiService() }

    // Function to lookup word
    fun lookupWord() {
        if (word.isBlank()) return

        isLoading = true
        errorMessage = null
        wordDefinition = null

        scope.launch {
            dictionaryService.lookupWord(word).onSuccess { definition ->
                wordDefinition = definition
                errorMessage = null
            }.onFailure { error ->
                if (error is WordNotFoundException) {
                    errorMessage = "Word not found in dictionary. Please check spelling."
                } else {
                    errorMessage = "Failed to fetch definition. Please try again."
                }
                wordDefinition = null
            }
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Vocabulary Word",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Word input field
                OutlinedTextField(
                    value = word,
                    onValueChange = {
                        word = it
                        if (errorMessage != null || wordDefinition != null) {
                            errorMessage = null
                            wordDefinition = null
                        }
                    },
                    label = { Text("Word") },
                    placeholder = { Text("Enter a word...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                    trailingIcon = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )

                // Lookup button
                Button(
                    onClick = { lookupWord() },
                    enabled = word.isNotBlank() && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.PrimaryPurple
                    )
                ) {
                    Text(
                        text = if (isLoading) "Looking up..." else "Look Up Definition",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Error message
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "❌",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }

                // Definition display
                if (wordDefinition != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Word and pronunciation
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = wordDefinition!!.word,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF2E7D32)
                                )
                                if (wordDefinition!!.pronunciation.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = wordDefinition!!.pronunciation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF558B2F)
                                    )
                                }
                            }

                            // Part of speech
                            Text(
                                text = wordDefinition!!.partOfSpeech,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF689F38)
                            )

                            HorizontalDivider(
                                color = Color(0xFFA5D6A7),
                                thickness = 1.dp
                            )

                            // Definition
                            Text(
                                text = "Definition:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF558B2F)
                            )
                            Text(
                                text = wordDefinition!!.definition,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1B5E20)
                            )

                            // Example
                            if (wordDefinition!!.example != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Example:",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF558B2F)
                                )
                                Text(
                                    text = "\"${wordDefinition!!.example}\"",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    color = Color(0xFF388E3C)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    wordDefinition?.let { def ->
                        onSubmit(
                            def.word,
                            def.definition,
                            def.pronunciation,
                            def.example,
                            def.partOfSpeech
                        )
                    }
                },
                enabled = wordDefinition != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WordBridgeColors.PrimaryPurple
                )
            ) {
                Text("Add to Vocabulary")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = WordBridgeColors.TextSecondary
                )
            }
        }
    )
}