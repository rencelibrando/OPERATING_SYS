package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.data.repository.VocabularyRepository
import org.example.project.data.repository.VocabularyRepositoryImpl
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

    val vocabularyWords: State<List<VocabularyWord>> = _vocabularyWords
    val vocabularyStats: State<VocabularyStats> = _vocabularyStats
    val vocabularyFeatures: State<List<VocabularyFeature>> = _vocabularyFeatures
    val searchQuery: State<String> = _searchQuery
    val selectedFilter: State<VocabularyFilter> = _selectedFilter
    val isLoading: State<Boolean> = _isLoading

    private val _filteredWords = mutableStateOf(emptyList<VocabularyWord>())
    val filteredWords: State<List<VocabularyWord>> = _filteredWords

    init {
        loadAll()
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
        println("Vocabulary word clicked: $wordId")
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
        return words.filter { word ->
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
            matchesFilter && matchesSearch
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
                _vocabularyStats.value = calculateStats(words)
                updateFilteredWords()
                println("Refresh Successful.")
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
            repository.addVocabularyWord(newWord).onSuccess { saved ->
                val updated = _vocabularyWords.value + saved
                _vocabularyWords.value = updated
                _vocabularyStats.value = calculateStats(updated)
                updateFilteredWords()
            }
            _isLoading.value = false
        }
    }

    fun refresh() {
        loadAll()
    }
}
