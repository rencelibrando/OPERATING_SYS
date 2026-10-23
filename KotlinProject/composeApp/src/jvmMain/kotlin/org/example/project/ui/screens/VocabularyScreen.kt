package org.example.project.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import org.example.project.core.dictionary.DeepSeekWordValidationService
import org.example.project.core.dictionary.WordNotFoundException
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.VocabularyStatus
import org.example.project.domain.model.VocabularyWord
import org.example.project.presentation.viewmodel.LessonsViewModel
import org.example.project.presentation.viewmodel.SpeakingViewModel
import org.example.project.presentation.viewmodel.VocabularyViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Warning
import org.example.project.core.auth.User as AuthUser

@Composable
fun VocabularyScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    vocabularyViewModel: VocabularyViewModel = viewModel(),
    speakingViewModel: SpeakingViewModel = viewModel(),
    lessonsViewModel: LessonsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val vocabularyWords by vocabularyViewModel.vocabularyWords
    val vocabularyStats by vocabularyViewModel.vocabularyStats
    val vocabularyFeatures by vocabularyViewModel.vocabularyFeatures
    val searchQuery by vocabularyViewModel.searchQuery
    val selectedFilter by vocabularyViewModel.selectedFilter
    val filteredWords by vocabularyViewModel.filteredWords
    val isLoading by vocabularyViewModel.isLoading
    val wordOfTheDay by vocabularyViewModel.wordOfTheDay
    val dailyGoal by vocabularyViewModel.dailyGoal
    val wordsLearnedToday by vocabularyViewModel.wordsLearnedToday
    val selectedWordForDetails by vocabularyViewModel.selectedWordForDetails

    // Get selected language from LessonsViewModel
    val selectedLanguage by lessonsViewModel.selectedLanguage
    val availableLanguages by lessonsViewModel.availableLanguages
    val isLanguageChanging by lessonsViewModel.isLanguageChanging

    var showAddDialog by remember { mutableStateOf(false) }

    // Sync language from LessonsViewModel to VocabularyViewModel
    LaunchedEffect(selectedLanguage) {
        selectedLanguage?.let { language ->
            vocabularyViewModel.setSelectedLanguage(language)
        }
    }

    // Initialize language on first load
    LaunchedEffect(Unit) {
        if (vocabularyViewModel.selectedLanguage.value == null && selectedLanguage != null) {
            vocabularyViewModel.setSelectedLanguage(selectedLanguage)
        }
    }

    val currentWord by speakingViewModel.currentWord
    val practiceLanguage by speakingViewModel.selectedLanguage
    val showLanguageDialog by speakingViewModel.showLanguageDialog

    if (showLanguageDialog && currentWord != null) {
        LanguageSelectionDialog(
            wordToLearn = currentWord!!.word,
            onLanguageSelected = { language ->
                speakingViewModel.onLanguageSelected(language)
            },
            onDismiss = {
                speakingViewModel.hideLanguageDialog()
                speakingViewModel.completePractice()
            },
        )
    }

    if (currentWord != null && practiceLanguage != null && !showLanguageDialog) {
        SpeakingScreen(
            authenticatedUser = authenticatedUser,
            onBackClick = {
                speakingViewModel.completePractice()
            },
            viewModel = speakingViewModel,
        )
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Vocabulary Bank",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )
                    if (selectedLanguage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Learning ${selectedLanguage.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondary,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Language Switcher
                    if (selectedLanguage != null) {
                        LessonLanguageSwitcher(
                            selectedLanguage = selectedLanguage,
                            availableLanguages = availableLanguages,
                            onLanguageSelected = { language ->
                                lessonsViewModel.changeLanguage(language)
                            },
                            enabled = !isLanguageChanging,
                        )
                    }

                    TextButton(
                        onClick = { vocabularyViewModel.refresh() },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = WordBridgeColors.TextSecondary,
                            ),
                    ) {
                        Text(
                            text = "Refresh",
                            style = MaterialTheme.typography.bodyMedium,
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

            Spacer(modifier = Modifier.height(24.dp))

            // Daily Goal Progress Card
            if (selectedLanguage != null) {
                DailyGoalCard(
                    dailyGoal = dailyGoal,
                    wordsLearnedToday = wordsLearnedToday,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Word of the Day Card
            if (wordOfTheDay != null && selectedLanguage != null) {
                WordOfTheDayCard(
                    word = wordOfTheDay!!,
                    onLearnClick = {
                        vocabularyViewModel.showWordDetails(wordOfTheDay!!)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VocabularyStatsCard(
                    title = "Total Words",
                    count = vocabularyStats.totalWords,
                    icon = "books",
                    backgroundColor = WordBridgeColors.PrimaryPurple,
                    modifier = Modifier.weight(1f),
                )

                VocabularyStatsCard(
                    title = "Mastered",
                    count = vocabularyStats.masteredWords,
                    icon = "completed",
                    backgroundColor = WordBridgeColors.AccentGreen,
                    modifier = Modifier.weight(1f),
                )

                VocabularyStatsCard(
                    title = "Learning",
                    count = vocabularyStats.learningWords,
                    icon = "goal",
                    backgroundColor = WordBridgeColors.AccentOrange,
                    modifier = Modifier.weight(1f),
                )

                VocabularyStatsCard(
                    title = "Need Review",
                    count = vocabularyStats.needReviewWords,
                    icon = "review",
                    backgroundColor = WordBridgeColors.AccentRed,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (vocabularyStats.totalWords == 0) {
                VocabularyEmptyState(
                    features = vocabularyFeatures,
                    onAddFirstWordClick = { showAddDialog = true },
                    onExploreLessonsClick = vocabularyViewModel::onExploreLessonsClicked,
                )
            } else {
                VocabularySearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChanged = vocabularyViewModel::onSearchQueryChanged,
                    selectedFilter = selectedFilter,
                    onFilterSelected = vocabularyViewModel::onFilterSelected,
                    onAddWordClick = { showAddDialog = true },
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (filteredWords.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        filteredWords.forEach { word ->
                            VocabularyWordItem(
                                word = word,
                                onPracticeClick = { vocabularyWord ->
                                    if (selectedLanguage != null) {
                                        // Use the app's selected learning language for speaking practice
                                        speakingViewModel.startPracticeSessionForLessonLanguage(
                                            vocabularyWord,
                                            selectedLanguage.code,
                                        )
                                    } else {
                                        // Fallback: show language selection dialog
                                        speakingViewModel.startPracticeSession(vocabularyWord)
                                    }
                                },
                                onClick = { vocabularyViewModel.onVocabularyWordClicked(word.id) },
                                onStatusChange = { status ->
                                    vocabularyViewModel.updateWordStatus(word.id, status)
                                },
                            )
                        }
                    }
                } else if (searchQuery.isNotEmpty() || selectedFilter.name != "ALL") {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = WordBridgeIcons.Search,
                            contentDescription = null,
                            tint = WordBridgeColors.TextMuted,
                            modifier = Modifier.size(36.dp),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No vocabulary words found",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            color = WordBridgeColors.TextPrimary,
                        )

                        Text(
                            text = "Try adjusting your search or filter criteria",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddWordDialog(
            selectedLanguage = selectedLanguage,
            onDismiss = { showAddDialog = false },
            onSubmit = { word, definition, pronunciation, audioUrl, example, category ->
                val languageCode = selectedLanguage?.code ?: "en"
                val newWord =
                    VocabularyWord(
                        id = "word_${System.currentTimeMillis()}",
                        word = word,
                        definition = definition,
                        pronunciation = pronunciation,
                        category = category,
                        audioUrl = audioUrl,
                        difficulty = "Beginner",
                        examples = if (example != null) listOf(example) else emptyList(),
                        status = VocabularyStatus.NEW,
                        dateAdded = System.currentTimeMillis(),
                        lastReviewed = null,
                        language = languageCode,
                    )
                vocabularyViewModel.addWord(newWord)
                showAddDialog = false
            },
        )
    }

    // Word Details Dialog
    selectedWordForDetails?.let { word ->
        WordDetailsDialog(
            word = word,
            onDismiss = { vocabularyViewModel.hideWordDetails() },
            onStatusChange = { status ->
                vocabularyViewModel.updateWordStatus(word.id, status)
            },
            onPracticeClick = {
                vocabularyViewModel.hideWordDetails()
                if (selectedLanguage != null) {
                    speakingViewModel.startPracticeSessionForLessonLanguage(
                        word,
                        selectedLanguage.code,
                    )
                } else {
                    speakingViewModel.startPracticeSession(word)
                }
            },
        )
    }
}

@Composable
private fun VocabularyWordItem(
    word: VocabularyWord,
    onPracticeClick: (VocabularyWord) -> Unit,
    onClick: (String) -> Unit,
    onStatusChange: ((VocabularyStatus) -> Unit)? = null,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick(word.id) },
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.BackgroundWhite,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                hoveredElevation = 4.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = word.word,
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )

                    // Status badge
                    StatusBadge(status = word.status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = word.definition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary,
                    maxLines = 2,
                )

                if (word.pronunciation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = word.pronunciation,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onPracticeClick(word) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.Primary,
                            contentColor = WordBridgeColors.OnPrimary,
                        ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = WordBridgeIcons.Speaking,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Practice",
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: VocabularyStatus) {
    val (color, text) =
        when (status) {
            VocabularyStatus.NEW -> WordBridgeColors.TextSecondary to "New"
            VocabularyStatus.LEARNING -> WordBridgeColors.Warning to "Learning"
            VocabularyStatus.MASTERED -> WordBridgeColors.Primary to "Mastered"
            VocabularyStatus.NEED_REVIEW -> WordBridgeColors.Error to "Review"
        }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AddWordDialog(
    selectedLanguage: LessonLanguage?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String?, String) -> Unit,
) {
    var word by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wordDefinition by remember { mutableStateOf<org.example.project.core.dictionary.WordDefinition?>(null) }
    var validationAttempted by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val deepSeekService = remember { DeepSeekWordValidationService() }

    fun lookupWord() {
        if (word.isBlank()) return

        val languageCode = selectedLanguage?.code ?: "en"
        if (selectedLanguage == null) {
            errorMessage = "Please select a language first"
            validationAttempted = true
            return
        }

        isLoading = true
        errorMessage = null
        wordDefinition = null
        validationAttempted = true

        scope.launch {
            deepSeekService.validateWord(word.trim(), languageCode).onSuccess { definition ->
                wordDefinition = definition
                errorMessage = null
            }.onFailure { error ->
                if (error is WordNotFoundException) {
                    errorMessage = "Invalid Word: '$word' does not exist in ${selectedLanguage?.displayName ?: "the selected language"}. Please check spelling or try a different word."
                } else {
                    errorMessage = "Validation Failed: ${error.message ?: "Unable to validate word. Please try again."}"
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
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (selectedLanguage == null) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = WordBridgeColors.Warning.copy(alpha = 0.12f),
                            ),
                        border = BorderStroke(1.dp, WordBridgeColors.Warning.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = WordBridgeColors.Warning,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Please select a language first to validate words",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WordBridgeColors.Warning,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = word,
                    onValueChange = {
                        word = it
                        if (errorMessage != null || wordDefinition != null || validationAttempted) {
                            errorMessage = null
                            wordDefinition = null
                            validationAttempted = false
                        }
                    },
                    label = {
                        Text(
                            if (selectedLanguage != null) {
                                "Word in ${selectedLanguage.displayName}"
                            } else {
                                "Word"
                            },
                        )
                    },
                    placeholder = { Text("Enter a word...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && validationAttempted,
                    enabled = selectedLanguage != null && !isLoading,
                    trailingIcon = {
                        when {
                            isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            wordDefinition != null -> {
                                Icon(
                                    imageVector = WordBridgeIcons.Check,
                                    contentDescription = null,
                                    tint = WordBridgeColors.AccentGreen,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            errorMessage != null && validationAttempted -> {
                                Icon(
                                    imageVector = Icons.Outlined.Cancel,
                                    contentDescription = null,
                                    tint = WordBridgeColors.AccentRed,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )

                Button(
                    onClick = { lookupWord() },
                    enabled = word.isNotBlank() && !isLoading && selectedLanguage != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.Primary,
                            contentColor = WordBridgeColors.OnPrimary,
                        ),
                ) {
                    Text(
                        text =
                            when {
                                isLoading -> "Validating word..."
                                selectedLanguage == null -> "Select Language First"
                                else -> "Validate & Look Up Word"
                            },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                if (selectedLanguage != null) {
                    Text(
                        text = "Validating word in ${selectedLanguage.displayName} using DeepSeek AI...",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary,
                    )
                }

                if (errorMessage != null && validationAttempted) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = WordBridgeColors.Error.copy(alpha = 0.12f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, WordBridgeColors.Error.copy(alpha = 0.3f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Cancel,
                                    contentDescription = null,
                                    tint = WordBridgeColors.Error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Word Not Valid",
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = WordBridgeColors.Error,
                                )
                            }
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = WordBridgeColors.TextPrimary,
                            )
                            HorizontalDivider(
                                color = WordBridgeColors.Error.copy(alpha = 0.3f),
                                thickness = 1.dp,
                            )
                            Text(
                                text = "This word cannot be added to your vocabulary bank until it is validated.",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = WordBridgeColors.Error,
                            )
                        }
                    }
                }

                if (wordDefinition != null) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = WordBridgeColors.Surface,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, WordBridgeColors.BorderSubtle),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = WordBridgeIcons.Check,
                                    contentDescription = null,
                                    tint = WordBridgeColors.Primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Word Validated",
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = WordBridgeColors.Primary,
                                )
                            }
                            HorizontalDivider(
                                color = WordBridgeColors.BorderSubtle,
                                thickness = 1.dp,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = wordDefinition!!.word,
                                    style =
                                        MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = WordBridgeColors.TextPrimary,
                                )
                                if (wordDefinition!!.pronunciation.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = wordDefinition!!.pronunciation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = WordBridgeColors.TextSecondary,
                                    )
                                }
                            }

                            Text(
                                text = wordDefinition!!.partOfSpeech,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                color = WordBridgeColors.Primary,
                            )

                            HorizontalDivider(color = WordBridgeColors.BorderSubtle, thickness = 1.dp)

                            Text(
                                text = "Definition:",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = WordBridgeColors.TextSecondary,
                            )
                            Text(
                                text = wordDefinition!!.definition,
                                style = MaterialTheme.typography.bodyMedium,
                                color = WordBridgeColors.TextPrimary,
                            )

                            if (wordDefinition!!.example != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Example:",
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    color = WordBridgeColors.TextSecondary,
                                )
                                Text(
                                    text = "\"${wordDefinition!!.example}\"",
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        ),
                                    color = WordBridgeColors.TextPrimary,
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
                            def.audio,
                            def.example,
                            def.partOfSpeech,
                        )
                    }
                },
                enabled = wordDefinition != null && !isLoading,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.Primary,
                        disabledContainerColor = WordBridgeColors.SurfaceVariant,
                        contentColor = WordBridgeColors.OnPrimary,
                        disabledContentColor = WordBridgeColors.TextMuted,
                    ),
            ) {
                Text(
                    text =
                        when {
                            wordDefinition != null -> "Add to Vocabulary"
                            validationAttempted && errorMessage != null -> "Cannot Add - Invalid Word"
                            else -> "Add to Vocabulary"
                        },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WordBridgeColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun DailyGoalCard(
    dailyGoal: Int,
    wordsLearnedToday: Int,
    modifier: Modifier = Modifier,
) {
    val progress = (wordsLearnedToday.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackground,
            ),
        border = BorderStroke(1.dp, WordBridgeColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Daily Goal",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$wordsLearnedToday / $dailyGoal words learned today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary,
                    )
                }

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.Primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                color = WordBridgeColors.Primary,
                trackColor = WordBridgeColors.ProgressBackground,
            )
        }
    }
}

@Composable
private fun WordOfTheDayCard(
    word: VocabularyWord,
    onLearnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackground,
            ),
        border = BorderStroke(1.dp, WordBridgeColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = WordBridgeIcons.Star,
                        contentDescription = null,
                        tint = WordBridgeColors.AccentOrange,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Word of the Day",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = word.word,
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.Primary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = word.definition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary,
                    maxLines = 2,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onLearnClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.Primary,
                        contentColor = WordBridgeColors.OnPrimary,
                    ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Learn",
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        }
    }
}

@Composable
private fun WordDetailsDialog(
    word: VocabularyWord,
    onDismiss: () -> Unit,
    onStatusChange: (VocabularyStatus) -> Unit,
    onPracticeClick: () -> Unit,
) {
    var selectedStatus by remember { mutableStateOf(word.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = word.word,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (word.pronunciation.isNotBlank()) {
                    Text(
                        text = word.pronunciation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WordBridgeColors.TextSecondary,
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Definition",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextSecondary,
                )
                Text(
                    text = word.definition,
                    style = MaterialTheme.typography.bodyLarge,
                    color = WordBridgeColors.TextPrimary,
                )

                if (word.examples.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Example",
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = WordBridgeColors.TextSecondary,
                    )
                    word.examples.forEach { example ->
                        Text(
                            text = "\"$example\"",
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                ),
                            color = WordBridgeColors.TextPrimary,
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Status",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextSecondary,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VocabularyStatus.values().forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = {
                                selectedStatus = status
                                onStatusChange(status)
                            },
                            label = { Text(status.displayName) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPracticeClick,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.Primary,
                            contentColor = WordBridgeColors.OnPrimary,
                        ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = WordBridgeIcons.Speaking,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Practice")
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.SurfaceVariant,
                        ),
                ) {
                    Text("Close", color = WordBridgeColors.TextPrimary)
                }
            }
        },
    )
}
