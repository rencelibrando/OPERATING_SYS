package org.example.project.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.example.project.core.audio.AudioPlayer
import org.example.project.core.dictionary.DeepSeekWordValidationService
import org.example.project.core.dictionary.WordNotFoundException
import org.example.project.core.tts.EdgeTTSService
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.VocabularyStatus
import org.example.project.domain.model.VocabularyWord
import org.example.project.presentation.viewmodel.LessonsViewModel
import org.example.project.presentation.viewmodel.SpeakingViewModel
import org.example.project.presentation.viewmodel.VocabularyViewModel
import org.example.project.data.repository.VocabularyPracticeHistoryDTO
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
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
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F23).copy(alpha = 0.95f))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Modern Header with soft elevated design
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1A1625).copy(alpha = 0.95f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Vocabulary Bank",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            color = Color.White.copy(alpha = 0.95f),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Learning ${selectedLanguage.displayName}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    color = Color(0xFF8B5CF6),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Language Switcher
                        LessonLanguageSwitcher(
                            selectedLanguage = selectedLanguage,
                            availableLanguages = availableLanguages,
                            onLanguageSelected = { language ->
                                lessonsViewModel.changeLanguage(language)
                            },
                            enabled = !isLanguageChanging,
                        )

                        TextButton(
                            onClick = { vocabularyViewModel.refresh() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White.copy(alpha = 0.7f),
                            ),
                        ) {
                            Text(
                                text = "↻ Refresh",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
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

            Spacer(modifier = Modifier.height(20.dp))

            // Daily Goal Progress Card
            DailyGoalCard(
                dailyGoal = dailyGoal,
                wordsLearnedToday = vocabularyWords.size,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Word of the Day Card
            if (wordOfTheDay != null) {
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
                    icon = "📚",
                    backgroundColor = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f),
                )

                VocabularyStatsCard(
                    title = "Mastered",
                    count = vocabularyStats.masteredWords,
                    icon = "✅",
                    backgroundColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                )

                VocabularyStatsCard(
                    title = "Learning",
                    count = vocabularyStats.learningWords,
                    icon = "🎯",
                    backgroundColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f),
                )

                VocabularyStatsCard(
                    title = "Need Review",
                    count = vocabularyStats.needReviewWords,
                    icon = "🔄",
                    backgroundColor = Color(0xFFEF4444),
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        filteredWords.forEach { word ->
                            VocabularyWordItem(
                                word = word,
                                onPracticeClick = { vocabularyWord ->
                                    // Use the app's selected learning language for speaking practice
                                    speakingViewModel.startPracticeSessionForLessonLanguage(
                                        vocabularyWord,
                                        selectedLanguage.code,
                                    )
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
                        Text(
                            text = "🔍",
                            style = MaterialTheme.typography.displaySmall,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No vocabulary words found",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            color = WordBridgeColors.TextPrimaryDark,
                        )

                        Text(
                            text = "Try adjusting your search or filter criteria",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextSecondaryDark,
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

    // Word Details Dialog with Practice History
    val practiceHistory by vocabularyViewModel.practiceHistory
    val isLoadingHistory by vocabularyViewModel.isLoadingHistory
    
    selectedWordForDetails?.let { word ->
        WordDetailsDialog(
            word = word,
            practiceHistory = practiceHistory,
            isLoadingHistory = isLoadingHistory,
            onDismiss = { vocabularyViewModel.hideWordDetails() },
            onStatusChange = { status ->
                vocabularyViewModel.updateWordStatus(word.id, status)
            },
            onPracticeClick = {
                vocabularyViewModel.hideWordDetails()
                speakingViewModel.startPracticeSessionForLessonLanguage(
                    word,
                    selectedLanguage.code,
                )
            },
            onRefreshHistory = { vocabularyViewModel.refreshPracticeHistory() },
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
    val scope = rememberCoroutineScope()
    var isPlayingAudio by remember { mutableStateOf(false) }
    val audioPlayer = remember { AudioPlayer() }
    
    // Status color for the glow effect
    val statusColor = when (word.status) {
        VocabularyStatus.NEW -> Color(0xFF94A3B8)
        VocabularyStatus.LEARNING -> Color(0xFFF59E0B)
        VocabularyStatus.MASTERED -> Color(0xFF10B981)
        VocabularyStatus.NEED_REVIEW -> Color(0xFFEF4444)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = statusColor.copy(alpha = 0.2f)
            )
            .clickable { onClick(word.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1625).copy(alpha = 0.95f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                    )

                    // Modern status badge
                    StatusBadge(status = word.status)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = word.definition,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                )

                if (word.pronunciation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = word.pronunciation,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.widthIn(max = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Audio playback button (if audio URL exists)
                if (!word.audioUrl.isNullOrBlank()) {
                    IconButton(
                        onClick = {
                            if (!isPlayingAudio) {
                                isPlayingAudio = true
                                scope.launch {
                                    try {
                                        audioPlayer.setPlaybackFinishedCallback {
                                            isPlayingAudio = false
                                        }
                                        audioPlayer.playAudioFromUrl(word.audioUrl)
                                    } catch (e: Exception) {
                                        isPlayingAudio = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(10.dp),
                                spotColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isPlayingAudio) Color(0xFF3B82F6) 
                                else Color(0xFF3B82F6).copy(alpha = 0.2f)
                            )
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Play pronunciation",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Practice button with gradient
                Button(
                    onClick = { onPracticeClick(word) },
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = 100.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(10.dp),
                            spotColor = Color(0xFF8B5CF6).copy(alpha = 0.4f)
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "🎤 Practice",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: VocabularyStatus) {
    val (color, text, icon) = when (status) {
        VocabularyStatus.NEW -> Triple(Color(0xFF94A3B8), "New", "✨")
        VocabularyStatus.LEARNING -> Triple(Color(0xFFF59E0B), "Learning", "📖")
        VocabularyStatus.MASTERED -> Triple(Color(0xFF10B981), "Mastered", "✅")
        VocabularyStatus.NEED_REVIEW -> Triple(Color(0xFFEF4444), "Review", "🔄")
    }

    Surface(
        modifier = Modifier.shadow(
            elevation = 2.dp,
            shape = RoundedCornerShape(8.dp),
            spotColor = color.copy(alpha = 0.3f)
        ),
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = color,
            )
        }
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
                    errorMessage = " Invalid Word: '$word' does not exist in ${selectedLanguage?.displayName ?: "the selected language"}. This word cannot be added to your vocabulary bank. Please check spelling or try a different word."
                } else {
                    errorMessage = " Validation Failed: ${error.message ?: "Unable to validate word. Please try again."}"
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
                color = WordBridgeColors.TextPrimaryDark,
            )
        },
        containerColor = WordBridgeColors.CardBackgroundDark,
        titleContentColor = WordBridgeColors.TextPrimaryDark,
        textContentColor = WordBridgeColors.TextPrimaryDark,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (selectedLanguage == null) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF3CD), // yellow-50
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "⚠️", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Please select a language first to validate words",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF856404),
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
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                    },
                    placeholder = { Text("Enter a word...", color = WordBridgeColors.TextMutedDark) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && validationAttempted,
                    enabled = selectedLanguage != null && !isLoading,
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = WordBridgeColors.PrimaryPurple,
                            unfocusedIndicatorColor = WordBridgeColors.TextMutedDark,
                            focusedTextColor = WordBridgeColors.TextPrimaryDark,
                            unfocusedTextColor = WordBridgeColors.TextPrimaryDark,
                            focusedLabelColor = WordBridgeColors.PrimaryPurple,
                            unfocusedLabelColor = WordBridgeColors.TextSecondaryDark,
                            cursorColor = WordBridgeColors.PrimaryPurple,
                            focusedContainerColor = WordBridgeColors.CardBackgroundDark,
                            unfocusedContainerColor = WordBridgeColors.CardBackgroundDark,
                        ),
                    trailingIcon = {
                        when {
                            isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = WordBridgeColors.PrimaryPurple,
                                )
                            }
                            wordDefinition != null -> {
                                Text(
                                    text = "✅",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            errorMessage != null && validationAttempted -> {
                                Text(
                                    text = "❌",
                                    style = MaterialTheme.typography.titleMedium,
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
                            containerColor = WordBridgeColors.PrimaryPurple,
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
                        color = WordBridgeColors.TextSecondaryDark,
                    )
                }

                if (errorMessage != null && validationAttempted) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE), // red-50
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                Color(0xFFEF5350), // red-400
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "❌",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Word Not Valid",
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = Color(0xFFC62828),
                                )
                            }
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB71C1C), // red-900
                            )
                            HorizontalDivider(
                                color = Color(0xFFEF5350),
                                thickness = 1.dp,
                            )
                            Text(
                                text = "⚠️ This word cannot be added to your vocabulary bank until it is validated.",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = Color(0xFFC62828),
                            )
                        }
                    }
                }

                if (wordDefinition != null) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = Color(0xFFE8F5E9), // green-50
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                Color(0xFF66BB6A), // green-400
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "✅",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    text = "Word Validated",
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = Color(0xFF2E7D32), // green-800
                                )
                            }
                            HorizontalDivider(
                                color = Color(0xFF66BB6A),
                                thickness = 1.dp,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = wordDefinition!!.word,
                                    style =
                                        MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = Color(0xFF2E7D32),
                                )
                                if (wordDefinition!!.pronunciation.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = wordDefinition!!.pronunciation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF558B2F),
                                    )
                                }
                            }

                            Text(
                                text = wordDefinition!!.partOfSpeech,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                color = Color(0xFF689F38),
                            )

                            HorizontalDivider(color = Color(0xFFA5D6A7), thickness = 1.dp)

                            Text(
                                text = "Definition:",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = Color(0xFF558B2F),
                            )
                            Text(
                                text = wordDefinition!!.definition,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1B5E20),
                            )

                            if (wordDefinition!!.example != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Example:",
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    color = Color(0xFF558B2F),
                                )
                                Text(
                                    text = "\"${wordDefinition!!.example}\"",
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        ),
                                    color = Color(0xFF388E3C),
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
                        containerColor =
                            if (wordDefinition != null) {
                                WordBridgeColors.PrimaryPurple
                            } else {
                                Color(0xFFE0E0E0) // Disabled gray
                            },
                    ),
            ) {
                Text(
                    text =
                        when {
                            wordDefinition != null -> "✅ Add to Vocabulary"
                            validationAttempted && errorMessage != null -> "Cannot Add - Invalid Word"
                            else -> "Add to Vocabulary"
                        },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WordBridgeColors.TextSecondaryDark)
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
    val progressColor = when {
        progress >= 1f -> Color(0xFF10B981) // Green when complete
        progress >= 0.5f -> Color(0xFF3B82F6) // Blue when halfway
        else -> Color(0xFF8B5CF6) // Purple otherwise
    }

    Card(
        modifier = modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = progressColor.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1625).copy(alpha = 0.95f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            progressColor.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🎯",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Daily Goal",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = Color.White.copy(alpha = 0.95f),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$wordsLearnedToday of $dailyGoal words learned today",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp
                            ),
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }

                    // Circular progress indicator
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(60.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(60.dp),
                            color = progressColor,
                            trackColor = Color.White.copy(alpha = 0.1f),
                            strokeWidth = 6.dp,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress bar with gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        progressColor,
                                        progressColor.copy(alpha = 0.7f)
                                    )
                                )
                            )
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(5.dp),
                                spotColor = progressColor.copy(alpha = 0.4f)
                            )
                    )
                }

                if (progress >= 1f) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "🎉 Goal achieved! Great job!",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordOfTheDayCard(
    word: VocabularyWord,
    onLearnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Animated glow effect
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Card(
        modifier = modifier.shadow(
            elevation = 10.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = Color(0xFFF59E0B).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1625).copy(alpha = 0.95f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFF59E0B).copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Glowing star icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    spotColor = Color(0xFFF59E0B).copy(alpha = 0.4f)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFF59E0B),
                                            Color(0xFFD97706)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⭐",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Column {
                            Text(
                                text = "Word of the Day",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = Color.White.copy(alpha = 0.95f),
                            )
                            Text(
                                text = "Expand your vocabulary",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp
                                ),
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }

                    // Learn button with gradient
                    Button(
                        onClick = onLearnClick,
                        modifier = Modifier
                            .height(40.dp)
                            .widthIn(min = 90.dp, max = 120.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = Color(0xFFF59E0B).copy(alpha = 0.4f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Learn →",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Word with pronunciation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = Color(0xFFF59E0B),
                    )
                    if (word.pronunciation.isNotBlank()) {
                        Text(
                            text = word.pronunciation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = word.definition,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 18.sp
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun WordDetailsDialog(
    word: VocabularyWord,
    practiceHistory: List<VocabularyPracticeHistoryDTO> = emptyList(),
    isLoadingHistory: Boolean = false,
    onDismiss: () -> Unit,
    onStatusChange: (VocabularyStatus) -> Unit,
    onPracticeClick: () -> Unit,
    onRefreshHistory: () -> Unit = {},
) {
    var selectedStatus by remember { mutableStateOf(word.status) }
    var showHistoryExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val audioPlayer = remember { AudioPlayer() }
    var playingRecordingId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        title = {
            Text(
                text = word.word,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimaryDark,
            )
        },
        containerColor = WordBridgeColors.CardBackgroundDark,
        titleContentColor = WordBridgeColors.TextPrimaryDark,
        textContentColor = WordBridgeColors.TextPrimaryDark,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (word.pronunciation.isNotBlank()) {
                    Text(
                        text = word.pronunciation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WordBridgeColors.TextSecondaryDark,
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Definition",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextSecondaryDark,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WordBridgeColors.CardBackgroundDark,
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 1.dp,
                        ),
                ) {
                    Text(
                        text = word.definition,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WordBridgeColors.TextPrimaryDark,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                if (word.examples.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Example",
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = WordBridgeColors.TextSecondaryDark,
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = WordBridgeColors.CardBackgroundDark,
                            ),
                        elevation =
                            CardDefaults.cardElevation(
                                defaultElevation = 1.dp,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                        ) {
                            word.examples.forEachIndexed { index, example ->
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Text(
                                    text = "\"$example\"",
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        ),
                                    color = WordBridgeColors.TextPrimaryDark,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Status",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextSecondaryDark,
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

                HorizontalDivider()

                // Practice History Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHistoryExpanded = !showHistoryExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "📊 Practice History",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                        if (practiceHistory.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "${practiceHistory.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = Color(0xFF8B5CF6),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isLoadingHistory) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF8B5CF6),
                            )
                        }
                        IconButton(
                            onClick = onRefreshHistory,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Text(
                                text = "↻",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WordBridgeColors.TextSecondaryDark,
                            )
                        }
                        Text(
                            text = if (showHistoryExpanded) "▲" else "▼",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                    }
                }

                if (showHistoryExpanded) {
                    if (isLoadingHistory) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF8B5CF6),
                            )
                        }
                    } else if (practiceHistory.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1A1625),
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "🎤",
                                    style = MaterialTheme.typography.displaySmall,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No practice sessions yet",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = "Practice this word to see your progress here",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            practiceHistory.take(5).forEach { session ->
                                PracticeHistoryItem(
                                    session = session,
                                    isPlaying = playingRecordingId == session.recordingId,
                                    onPlayClick = {
                                        if (playingRecordingId == session.recordingId) {
                                            audioPlayer.stop()
                                            playingRecordingId = null
                                        } else if (!session.recordingUrl.isNullOrBlank()) {
                                            playingRecordingId = session.recordingId
                                            scope.launch {
                                                try {
                                                    audioPlayer.setPlaybackFinishedCallback {
                                                        playingRecordingId = null
                                                    }
                                                    audioPlayer.playAudioFromUrl(session.recordingUrl)
                                                } catch (e: Exception) {
                                                    playingRecordingId = null
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                            if (practiceHistory.size > 5) {
                                Text(
                                    text = "+ ${practiceHistory.size - 5} more sessions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
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
                            containerColor = WordBridgeColors.PrimaryPurple,
                        ),
                ) {
                    Text("Practice", color = WordBridgeColors.TextPrimaryDark)
                }
                Button(
                    onClick = onDismiss,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple,
                        ),
                ) {
                    Text("Close", color = WordBridgeColors.TextPrimaryDark)
                }
            }
        },
    )
}

@Composable
private fun PracticeHistoryItem(
    session: VocabularyPracticeHistoryDTO,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
) {
    val overallScore = session.overallScore ?: 0
    val scoreColor = when {
        overallScore >= 80 -> Color(0xFF10B981)
        overallScore >= 60 -> Color(0xFFF59E0B)
        overallScore >= 40 -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1625),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Date and time
                Text(
                    text = session.practicedAt?.take(16)?.replace("T", " ") ?: "Unknown date",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                )

                // Overall score badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = scoreColor.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "${overallScore}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Score breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScoreChip("🎯 Pronunciation", session.pronunciationScore ?: 0)
                ScoreChip("✓ Accuracy", session.accuracyScore ?: 0)
                ScoreChip("💬 Fluency", session.fluencyScore ?: 0)
            }

            // Transcript if available
            if (!session.transcript.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"${session.transcript}\"",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 2,
                )
            }

            // Play recording button
            if (!session.recordingUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying) Color(0xFF3B82F6) else Color(0xFF3B82F6).copy(alpha = 0.2f),
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play recording",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "Playing..." else "Play",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                }
            }

            // Suggestions if available
            if (!session.suggestions.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 ${session.suggestions.first()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF59E0B).copy(alpha = 0.8f),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ScoreChip(label: String, score: Int) {
    val color = when {
        score >= 80 -> Color(0xFF10B981)
        score >= 60 -> Color(0xFFF59E0B)
        score >= 40 -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        Text(
            text = "$score%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = color,
        )
    }
}
