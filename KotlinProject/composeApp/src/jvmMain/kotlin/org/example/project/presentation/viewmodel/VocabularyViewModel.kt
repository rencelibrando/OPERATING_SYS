package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.VocabularyWord
import org.example.project.domain.model.VocabularyStats
import org.example.project.domain.model.VocabularyFeature
import org.example.project.domain.model.VocabularyFilter


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

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        updateFilteredWords()
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
}
