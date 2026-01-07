package org.example.project.admin.presentation

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.data.repository.LessonTopicsRepository
import org.example.project.data.repository.LessonTopicsRepositoryImpl
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.LessonTopic

class AdminLessonTopicsViewModel : ViewModel() {
    private val lessonTopicsRepository: LessonTopicsRepository = LessonTopicsRepositoryImpl.getInstance()

    private val _topics = mutableStateOf<List<LessonTopic>>(emptyList())
    val topics: State<List<LessonTopic>> = _topics

    private val _selectedLanguage = mutableStateOf<LessonLanguage?>(null)
    val selectedLanguage: State<LessonLanguage?> = _selectedLanguage

    private val _selectedDifficulty = mutableStateOf<LessonDifficulty?>(null)
    val selectedDifficulty: State<LessonDifficulty?> = _selectedDifficulty

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _successMessage = mutableStateOf<String?>(null)
    val successMessage: State<String?> = _successMessage

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _selectedTopics = mutableStateOf<Set<String>>(emptySet())
    val selectedTopics: State<Set<String>> = _selectedTopics

    private val _sortOrder = mutableStateOf<SortOrder>(SortOrder.SORT_ORDER)

    enum class SortOrder(val displayName: String) {
        SORT_ORDER("Sort Order"),
        TITLE("Title A-Z"),
        TITLE_DESC("Title Z-A"),
        LESSON_NUMBER("Lesson Number"),
        DURATION("Duration"),
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun toggleTopicSelection(topicId: String) {
        _selectedTopics.value =
            if (_selectedTopics.value.contains(topicId)) {
                _selectedTopics.value - topicId
            } else {
                _selectedTopics.value + topicId
            }
    }

    fun selectAllTopics() {
        _selectedTopics.value = _topics.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedTopics.value = emptySet()
    }

    fun getFilteredAndSortedTopics(): List<LessonTopic> {
        var filtered = _topics.value

        // Apply search filter
        if (_searchQuery.value.isNotEmpty()) {
            val query = _searchQuery.value.lowercase()
            filtered =
                filtered.filter {
                    it.title.lowercase().contains(query) ||
                        it.description.lowercase().contains(query) ||
                        it.id.lowercase().contains(query)
                }
        }

        // Apply sort
        filtered =
            when (_sortOrder.value) {
                SortOrder.SORT_ORDER -> filtered.sortedBy { it.lessonNumber ?: Int.MAX_VALUE }
                SortOrder.TITLE -> filtered.sortedBy { it.title }
                SortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title }
                SortOrder.LESSON_NUMBER -> filtered.sortedBy { it.lessonNumber ?: Int.MAX_VALUE }
                SortOrder.DURATION -> filtered.sortedBy { it.durationMinutes ?: Int.MAX_VALUE }
            }

        return filtered
    }

    fun loadTopics(
        language: LessonLanguage,
        difficulty: LessonDifficulty,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = lessonTopicsRepository.getTopicsByDifficulty(difficulty, language)
                result.fold(
                    onSuccess = { topics ->
                        _topics.value = topics
                        _selectedLanguage.value = language
                        _selectedDifficulty.value = difficulty
                        _successMessage.value = "Loaded ${topics.size} topics for ${language.displayName} - ${difficulty.displayName}"
                    },
                    onFailure = { e ->
                        _errorMessage.value = "Failed to load topics: ${e.message}"
                        _topics.value = emptyList()
                    },
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error loading topics: ${e.message}"
                _topics.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun duplicateTopic(topicId: String) {
        viewModelScope.launch {
            val topic = _topics.value.find { it.id == topicId } ?: return@launch
            val difficulty = _selectedDifficulty.value ?: return@launch
            val language = _selectedLanguage.value ?: return@launch

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val newTopic =
                    topic.copy(
                        id = "${topic.id}_copy_${System.currentTimeMillis()}",
                        title = "${topic.title} (Copy)",
                    )
                val sortOrder = _topics.value.size
                val result = lessonTopicsRepository.createTopic(newTopic, difficulty, language, sortOrder)
                result.fold(
                    onSuccess = {
                        _successMessage.value = "Topic duplicated successfully!"
                        loadTopics(language, difficulty)
                    },
                    onFailure = { e ->
                        _errorMessage.value = "Failed to duplicate topic: ${e.message}"
                    },
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error duplicating topic: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSelectedTopics() {
        viewModelScope.launch {
            if (_selectedTopics.value.isEmpty()) return@launch

            val difficulty = _selectedDifficulty.value ?: return@launch
            val language = _selectedLanguage.value ?: return@launch

            _isLoading.value = true
            _errorMessage.value = null

            var successCount = 0
            var errorCount = 0

            _selectedTopics.value.forEach { topicId ->
                try {
                    val result = lessonTopicsRepository.deleteTopic(topicId)
                    result.fold(
                        onSuccess = { successCount++ },
                        onFailure = { errorCount++ },
                    )
                } catch (e: Exception) {
                    errorCount++
                }
            }

            if (errorCount == 0) {
                _successMessage.value = "Deleted $successCount topic(s) successfully!"
            } else {
                _errorMessage.value = "Deleted $successCount topic(s), $errorCount failed"
            }

            _selectedTopics.value = emptySet()
            // loadTopics manages its own loading state, so don't set _isLoading = false here
            loadTopics(language, difficulty)
        }
    }

    private val _editingTopic = mutableStateOf<LessonTopic?>(null)
    val editingTopic: State<LessonTopic?> = _editingTopic

    fun startEditing(topic: LessonTopic) {
        _editingTopic.value = topic
    }

    fun cancelEditing() {
        _editingTopic.value = null
    }

    fun updateTopic(updatedTopic: LessonTopic) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val difficulty = _selectedDifficulty.value ?: return@launch
            val language = _selectedLanguage.value ?: return@launch

            try {
                val result = lessonTopicsRepository.updateTopic(updatedTopic, difficulty, language)
                result.fold(
                    onSuccess = {
                        _successMessage.value = "Topic updated successfully!"
                        _editingTopic.value = null
                        loadTopics(language, difficulty) // Reload to get updated data
                    },
                    onFailure = { e ->
                        _errorMessage.value = "Failed to update topic: ${e.message}"
                    },
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error updating topic: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            println("[AdminViewModel] Delete topic called with ID: $topicId")
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            val difficulty = _selectedDifficulty.value
            val language = _selectedLanguage.value

            if (difficulty == null || language == null) {
                _errorMessage.value = "Please select a language and difficulty level first"
                _isLoading.value = false
                println("[AdminViewModel] Cannot delete: language or difficulty not selected")
                return@launch
            }

            println("[AdminViewModel] Deleting topic: $topicId for ${language.displayName} - ${difficulty.displayName}")

            try {
                val result = lessonTopicsRepository.deleteTopic(topicId)
                result.fold(
                    onSuccess = {
                        println("[AdminViewModel] Topic deleted successfully: $topicId")
                        _successMessage.value = "Topic deleted successfully!"
                        loadTopics(language, difficulty) // Reload to get updated data
                    },
                    onFailure = { e ->
                        println("[AdminViewModel] Failed to delete topic: ${e.message}")
                        e.printStackTrace()
                        _errorMessage.value = "Failed to delete topic: ${e.message}"
                    },
                )
            } catch (e: Exception) {
                println("[AdminViewModel] Exception while deleting topic: ${e.message}")
                e.printStackTrace()
                _errorMessage.value = "Error deleting topic: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createTopic(newTopic: LessonTopic) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val difficulty = _selectedDifficulty.value ?: return@launch
            val language = _selectedLanguage.value ?: return@launch

            try {
                val sortOrder = _topics.value.size
                val result = lessonTopicsRepository.createTopic(newTopic, difficulty, language, sortOrder)
                result.fold(
                    onSuccess = {
                        _successMessage.value = "Topic created successfully!"
                        loadTopics(language, difficulty) // Reload to get updated data
                    },
                    onFailure = { e ->
                        _errorMessage.value = "Failed to create topic: ${e.message}"
                    },
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error creating topic: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

}
