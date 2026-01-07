package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.data.repository.VocabularyRepository
import org.example.project.data.repository.VocabularyRepositoryImpl
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.VocabularyFeature
import org.example.project.domain.model.VocabularyFilter
import org.example.project.domain.model.VocabularyStats
import org.example.project.domain.model.VocabularyWord

class VocabularyViewModel(
    private val repository: VocabularyRepository = VocabularyRepositoryImpl(),
) : ViewModel() {
    private val _vocabularyWords = mutableStateOf(emptyList<VocabularyWord>())
    private val _vocabularyStats = mutableStateOf(VocabularyStats.getSampleStats())
    private val _vocabularyFeatures = mutableStateOf(VocabularyFeature.getVocabularyFeatures())
    private val _searchQuery = mutableStateOf("")
    private val _selectedFilter = mutableStateOf(VocabularyFilter.ALL)
    private val _isLoading = mutableStateOf(false)
    private val _selectedLanguage = mutableStateOf<LessonLanguage?>(null)
    private val _wordOfTheDay = mutableStateOf<VocabularyWord?>(null)
    private val _dailyGoal = mutableStateOf(10) // Default daily goal: 10 words
    private val _wordsLearnedToday = mutableStateOf(0)
    private val _selectedWordForDetails = mutableStateOf<VocabularyWord?>(null)

    val vocabularyWords: State<List<VocabularyWord>> = _vocabularyWords
    val vocabularyStats: State<VocabularyStats> = _vocabularyStats
    val vocabularyFeatures: State<List<VocabularyFeature>> = _vocabularyFeatures
    val searchQuery: State<String> = _searchQuery
    val selectedFilter: State<VocabularyFilter> = _selectedFilter
    val isLoading: State<Boolean> = _isLoading
    val selectedLanguage: State<LessonLanguage?> = _selectedLanguage
    val wordOfTheDay: State<VocabularyWord?> = _wordOfTheDay
    val dailyGoal: State<Int> = _dailyGoal
    val wordsLearnedToday: State<Int> = _wordsLearnedToday
    val selectedWordForDetails: State<VocabularyWord?> = _selectedWordForDetails

    private val _filteredWords = mutableStateOf(emptyList<VocabularyWord>())
    val filteredWords: State<List<VocabularyWord>> = _filteredWords

    init {
        loadAll()
    }

    /**
     * Set the selected language and reload vocabulary for that language
     */
    fun setSelectedLanguage(language: LessonLanguage) {
        if (_selectedLanguage.value != language) {
            _selectedLanguage.value = language
            loadAll() // Reload vocabulary for the new language
        }
    }

    /**
     * Get words that need review for the selected language
     */
    fun getWordsForReview(limit: Int = 10): List<VocabularyWord> {
        val languageCode = _selectedLanguage.value?.code ?: return emptyList()
        return _vocabularyWords.value
            .filter { it.language == languageCode && it.status.name == "NEED_REVIEW" }
            .take(limit)
    }

    /**
     * Get word of the day for the selected language
     */
    fun updateWordOfTheDay() {
        val languageCode = _selectedLanguage.value?.code ?: return
        val wordsForLanguage = _vocabularyWords.value.filter { it.language == languageCode }
        if (wordsForLanguage.isNotEmpty()) {
            // Select a random word that's not mastered yet
            val candidates = wordsForLanguage.filter { it.status.name != "MASTERED" }
            if (candidates.isNotEmpty()) {
                _wordOfTheDay.value = candidates.random()
            } else {
                _wordOfTheDay.value = wordsForLanguage.random()
            }
        }
    }

    /**
     * Mark a word as learned (increment daily counter)
     */
    fun markWordAsLearned() {
        _wordsLearnedToday.value = _wordsLearnedToday.value + 1
    }

    /**
     * Show word details
     */
    fun showWordDetails(word: VocabularyWord) {
        _selectedWordForDetails.value = word
    }

    /**
     * Hide word details
     */
    fun hideWordDetails() {
        _selectedWordForDetails.value = null
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            updateFilteredWords()
        } else {
            _isLoading.value = true
            viewModelScope.launch {
                repository.searchVocabularyWords(query).onSuccess { words ->
                    _filteredWords.value = applyFilter(words, _selectedFilter.value)
                }.onFailure {
                    updateFilteredWords()
                }
                _isLoading.value = false
            }
        }
    }

    fun onFilterSelected(filter: VocabularyFilter) {
        _selectedFilter.value = filter
        updateFilteredWords()
    }

    fun onAddWordClicked() {
        println("Add word clicked")
    }

    fun onAddFirstWordClicked() {
        println("Add first word clicked")
    }

    fun onVocabularyWordClicked(wordId: String) {
        val word = _vocabularyWords.value.find { it.id == wordId }
        word?.let { showWordDetails(it) }
    }

    /**
     * Update word status (e.g., mark as mastered, learning, etc.)
     */
    fun updateWordStatus(
        wordId: String,
        status: org.example.project.domain.model.VocabularyStatus,
    ) {
        viewModelScope.launch {
            val word = _vocabularyWords.value.find { it.id == wordId }
            word?.let {
                val updatedWord =
                    it.copy(
                        status = status,
                        lastReviewed = System.currentTimeMillis(),
                    )
                repository.updateVocabularyWord(updatedWord).onSuccess {
                    val updated =
                        _vocabularyWords.value.map { w ->
                            if (w.id == wordId) updatedWord else w
                        }
                    _vocabularyWords.value = updated

                    // Recalculate stats for current language
                    val languageCode = _selectedLanguage.value?.code
                    val wordsForLanguage =
                        if (languageCode != null) {
                            updated.filter { it.language == languageCode }
                        } else {
                            updated
                        }
                    _vocabularyStats.value = calculateStats(wordsForLanguage)
                    updateFilteredWords()
                }
            }
        }
    }

    fun onExploreLessonsClicked() {
        println("Explore lessons clicked")
    }

    private fun updateFilteredWords() {
        _filteredWords.value = applyFilter(_vocabularyWords.value, _selectedFilter.value)
    }

    private fun applyFilter(
        words: List<VocabularyWord>,
        filter: VocabularyFilter,
    ): List<VocabularyWord> {
        val query = _searchQuery.value.lowercase()
        val languageCode = _selectedLanguage.value?.code

        return words.filter { word ->
            // Filter by language if a language is selected
            val matchesLanguage = languageCode == null || word.language == languageCode

            val matchesFilter =
                when (filter) {
                    VocabularyFilter.ALL -> true
                    VocabularyFilter.MASTERED -> word.status.name == "MASTERED"
                    VocabularyFilter.LEARNING -> word.status.name == "LEARNING"
                    VocabularyFilter.REVIEW -> word.status.name == "NEED_REVIEW"
                }
            val matchesSearch =
                query.isEmpty() ||
                    word.word.lowercase().contains(query) ||
                    word.definition.lowercase().contains(query)
            matchesLanguage && matchesFilter && matchesSearch
        }
    }

    private fun calculateStats(words: List<VocabularyWord>): VocabularyStats {
        val total = words.size
        val mastered = words.count { it.status.name == "MASTERED" }
        val learning = words.count { it.status.name == "LEARNING" }
        val review = words.count { it.status.name == "NEED_REVIEW" }
        return VocabularyStats(
            totalWords = total,
            masteredWords = mastered,
            learningWords = learning,
            needReviewWords = review,
        )
    }

    fun loadAll() {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getAllVocabularyWords().onSuccess { words ->
                _vocabularyWords.value = words

                // Filter by selected language for stats
                val languageCode = _selectedLanguage.value?.code
                val wordsForLanguage =
                    if (languageCode != null) {
                        words.filter { it.language == languageCode }
                    } else {
                        words
                    }

                _vocabularyStats.value = calculateStats(wordsForLanguage)
                updateFilteredWords()
                updateWordOfTheDay()
                println("Refresh Successful. Language: ${_selectedLanguage.value?.displayName}, Words: ${wordsForLanguage.size}")
            }.onFailure {
                // keep defaults
                updateFilteredWords()
                println("Failed.")
            }
            _isLoading.value = false
        }
    }

    fun addWord(newWord: VocabularyWord) {
        _isLoading.value = true
        viewModelScope.launch {
            // Ensure the word has the correct language
            val wordWithLanguage =
                if (newWord.language == "English" && _selectedLanguage.value != null) {
                    newWord.copy(language = _selectedLanguage.value!!.code)
                } else {
                    newWord
                }

            repository.addVocabularyWord(wordWithLanguage).onSuccess { saved ->
                val updated = _vocabularyWords.value + saved
                _vocabularyWords.value = updated

                // Filter by selected language for stats
                val languageCode = _selectedLanguage.value?.code
                val wordsForLanguage =
                    if (languageCode != null) {
                        updated.filter { it.language == languageCode }
                    } else {
                        updated
                    }

                _vocabularyStats.value = calculateStats(wordsForLanguage)
                updateFilteredWords()
                markWordAsLearned()
                updateWordOfTheDay()
            }
            _isLoading.value = false
        }
    }

    fun refresh() {
        loadAll()
    }
}
