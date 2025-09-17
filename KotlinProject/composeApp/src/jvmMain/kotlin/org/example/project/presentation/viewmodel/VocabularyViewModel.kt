package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.VocabularyWord
import org.example.project.domain.model.VocabularyStats
import org.example.project.domain.model.VocabularyFeature
import org.example.project.domain.model.VocabularyFilter

/**
 * ViewModel for the Vocabulary screen
 * 
 * Manages the state and business logic for the vocabulary screen,
 * including vocabulary words, statistics, search, and user interactions
 */
class VocabularyViewModel : ViewModel() {
    
    // Private mutable state
    private val _vocabularyWords = mutableStateOf(VocabularyWord.getSampleWords())
    private val _vocabularyStats = mutableStateOf(VocabularyStats.getSampleStats())
    private val _vocabularyFeatures = mutableStateOf(VocabularyFeature.getVocabularyFeatures())
    private val _searchQuery = mutableStateOf("")
    private val _selectedFilter = mutableStateOf(VocabularyFilter.ALL)
    private val _isLoading = mutableStateOf(false)
    
    // Public read-only state
    val vocabularyWords: State<List<VocabularyWord>> = _vocabularyWords
    val vocabularyStats: State<VocabularyStats> = _vocabularyStats
    val vocabularyFeatures: State<List<VocabularyFeature>> = _vocabularyFeatures
    val searchQuery: State<String> = _searchQuery
    val selectedFilter: State<VocabularyFilter> = _selectedFilter
    val isLoading: State<Boolean> = _isLoading
    
    // Computed property for filtered words
    private val _filteredWords = mutableStateOf(emptyList<VocabularyWord>())
    val filteredWords: State<List<VocabularyWord>> = _filteredWords
    
    init {
        updateFilteredWords()
    }
    
    /**
     * Handles search query changes
     * @param query The new search query
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        updateFilteredWords()
    }
    
    /**
     * Handles filter selection changes
     * @param filter The selected filter
     */
    fun onFilterSelected(filter: VocabularyFilter) {
        _selectedFilter.value = filter
        updateFilteredWords()
    }
    
    /**
     * Handles adding a new word
     */
    fun onAddWordClicked() {
        // TODO: Navigate to add word screen or show dialog
        println("Add word clicked")
    }
    
    /**
     * Handles adding the first word (from empty state)
     */
    fun onAddFirstWordClicked() {
        // TODO: Navigate to add word screen or show dialog
        println("Add first word clicked")
    }
    
    /**
     * Handles vocabulary word click
     * @param wordId The ID of the clicked word
     */
    fun onVocabularyWordClicked(wordId: String) {
        // TODO: Navigate to word details or edit screen
        println("Vocabulary word clicked: $wordId")
    }
    
    /**
     * Handles exploring lessons to discover vocabulary
     */
    fun onExploreLessonsClicked() {
        // TODO: Navigate to lessons screen
        println("Explore lessons clicked")
    }
    
    /**
     * Refreshes vocabulary data
     */
    fun refreshVocabulary() {
        _isLoading.value = true
        
        // TODO: Implement actual data refresh from repository
        // For now, simulate refresh
        _vocabularyWords.value = VocabularyWord.getSampleWords()
        updateVocabularyStats()
        updateFilteredWords()
        
        _isLoading.value = false
    }
    
    /**
     * Updates filtered words based on search query and selected filter
     */
    private fun updateFilteredWords() {
        val words = _vocabularyWords.value
        val query = _searchQuery.value.lowercase()
        val filter = _selectedFilter.value
        
        val filtered = words.filter { word ->
            // Apply search filter
            val matchesSearch = query.isEmpty() || 
                word.word.lowercase().contains(query) ||
                word.definition.lowercase().contains(query)
            
            // Apply status filter
            val matchesFilter = when (filter) {
                VocabularyFilter.ALL -> true
                VocabularyFilter.MASTERED -> word.status.name == "MASTERED"
                VocabularyFilter.LEARNING -> word.status.name == "LEARNING"
                VocabularyFilter.REVIEW -> word.status.name == "NEED_REVIEW"
            }
            
            matchesSearch && matchesFilter
        }
        
        _filteredWords.value = filtered
    }
    
    /**
     * Updates vocabulary statistics based on current words
     */
    private fun updateVocabularyStats() {
        val words = _vocabularyWords.value
        
        val stats = VocabularyStats(
            totalWords = words.size,
            masteredWords = words.count { it.status.name == "MASTERED" },
            learningWords = words.count { it.status.name == "LEARNING" },
            needReviewWords = words.count { it.status.name == "NEED_REVIEW" }
        )
        
        _vocabularyStats.value = stats
    }
}
