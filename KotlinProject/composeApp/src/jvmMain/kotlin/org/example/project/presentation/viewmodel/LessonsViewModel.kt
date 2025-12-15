package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.core.profile.ProfileService
import org.example.project.data.repository.LessonTopicsRepository
import org.example.project.data.repository.LessonTopicsRepositoryImpl
import org.example.project.data.repository.LessonContentRepository
import org.example.project.data.repository.LessonContentRepositoryImpl
import org.example.project.domain.model.Lesson
import org.example.project.domain.model.LessonCategoryInfo
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.LessonSummary
import org.example.project.domain.model.LevelProgress
import org.example.project.domain.model.RecentLesson
import org.example.project.core.auth.User as AuthUser

class LessonsViewModel : ViewModel() {
    private val profileService = ProfileService()
    private val lessonTopicsRepository: LessonTopicsRepository = LessonTopicsRepositoryImpl()
    private val lessonContentRepository: LessonContentRepository = LessonContentRepositoryImpl()
    
    private val _lessons = mutableStateOf(Lesson.getSampleLessons())
    private val _levelProgress = mutableStateOf(LevelProgress.getSampleProgress())
    private val _recentLessons = mutableStateOf(RecentLesson.getSampleRecentLessons())
    private val _isLoading = mutableStateOf(false)
    private val _userLevel = mutableStateOf(LessonDifficulty.BEGINNER)
    private val _lessonCategories = mutableStateOf<List<LessonCategoryInfo>>(emptyList())
    private val _selectedCategory = mutableStateOf<LessonDifficulty?>(null)
    private val _selectedLanguage = mutableStateOf<LessonLanguage>(LessonLanguage.CHINESE) // Default to Chinese
    private val _categoryLessons = mutableStateOf<List<Lesson>>(emptyList())
    private val _lessonTopics = mutableStateOf<List<org.example.project.domain.model.LessonTopic>>(emptyList())
    private val _isLanguageChanging = mutableStateOf(false)
    
    // Lesson list view state
    private val _selectedTopicForLessons = mutableStateOf<org.example.project.domain.model.LessonTopic?>(null)
    private val _topicLessons = mutableStateOf<List<LessonSummary>>(emptyList())
    private val _isLoadingLessons = mutableStateOf(false)
    
    // Available languages for the switcher
    private val _availableLanguages = mutableStateOf(LessonLanguage.entries.toList())

    val lessons: State<List<Lesson>> = _lessons
    val levelProgress: State<LevelProgress> = _levelProgress
    val recentLessons: State<List<RecentLesson>> = _recentLessons
    val isLoading: State<Boolean> = _isLoading
    val userLevel: State<LessonDifficulty> = _userLevel
    val lessonCategories: State<List<LessonCategoryInfo>> = _lessonCategories
    val selectedCategory: State<LessonDifficulty?> = _selectedCategory
    val selectedLanguage: State<LessonLanguage> = _selectedLanguage
    val categoryLessons: State<List<Lesson>> = _categoryLessons
    val lessonTopics: State<List<org.example.project.domain.model.LessonTopic>> = _lessonTopics
    val isLanguageChanging: State<Boolean> = _isLanguageChanging
    val availableLanguages: State<List<LessonLanguage>> = _availableLanguages
    val selectedTopicForLessons: State<org.example.project.domain.model.LessonTopic?> = _selectedTopicForLessons
    val topicLessons: State<List<LessonSummary>> = _topicLessons
    val isLoadingLessons: State<Boolean> = _isLoadingLessons

    init {
        refreshCategories()
    }

    fun initializeWithAuthenticatedUser(authUser: AuthUser) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                println("Loading lesson data for user: ${authUser.email}")

                val learningProfileResult = profileService.loadLearningProfile()
                val learningProfile = learningProfileResult.getOrNull()

                if (learningProfile != null) {
                    println("User level from profile: ${learningProfile.currentLevel}")
                    setUserLevel(learningProfile.currentLevel)
                } else {
                    println("No learning profile found, defaulting to Beginner")
                    setUserLevel("Beginner")
                }
                
                // Load personal info to get target languages
                val personalInfoResult = profileService.loadPersonalInfo()
                val personalInfo = personalInfoResult.getOrNull()
                
                if (personalInfo != null && personalInfo.targetLanguages.isNotEmpty()) {
                    val targetLang = personalInfo.targetLanguages.first()
                    val language = LessonLanguage.entries.find { 
                        it.displayName.equals(targetLang, ignoreCase = true) || 
                        it.code.equals(targetLang, ignoreCase = true)
                    } ?: LessonLanguage.CHINESE
                    _selectedLanguage.value = language
                    println("User target language: ${language.displayName}")
                }

                _isLoading.value = false
            } catch (e: Exception) {
                println("Failed to load user profile: ${e.message}")
                e.printStackTrace()
                setUserLevel("Beginner")
                _isLoading.value = false
            }
        }
    }

    fun setUserLevel(level: String) {
        _userLevel.value =
            when (level) {
                "Beginner" -> LessonDifficulty.BEGINNER
                "Intermediate" -> LessonDifficulty.INTERMEDIATE
                "Advanced" -> LessonDifficulty.ADVANCED
                else -> LessonDifficulty.BEGINNER
            }
        refreshCategories()
    }

    private fun refreshCategories() {
        _lessonCategories.value = LessonCategoryInfo.getSampleCategories(_userLevel.value)
    }

    fun onCategoryClicked(difficulty: LessonDifficulty) {
        _selectedCategory.value = difficulty
        _categoryLessons.value = emptyList()
        _lessonTopics.value = emptyList()
        println("Category clicked: ${difficulty.displayName}")
        
        viewModelScope.launch {
            
            loadLessonTopics(difficulty)
        }
    }
    
    private suspend fun loadLessonTopics(difficulty: LessonDifficulty) {
        _isLoading.value = true
        try {
            val language = _selectedLanguage.value
            println("[LessonsViewModel] ========================================")
            println("[LessonsViewModel] 🔍 Loading topics for ${language.displayName} - ${difficulty.displayName} from Supabase...")
            
            val result = lessonTopicsRepository.getTopicsByDifficulty(difficulty, language)
            
            result.fold(
                onSuccess = { topicsFromDb ->
                    println("[LessonsViewModel] ✅ Successfully loaded ${topicsFromDb.size} topics from Supabase")
                    
                    // Debug: Print each topic's language to verify filtering
                    if (topicsFromDb.isNotEmpty()) {
                        println("[LessonsViewModel] Topics loaded:")
                        topicsFromDb.take(5).forEach { topic ->
                            println("[LessonsViewModel]   - ${topic.id}: ${topic.title} (language: ${topic.language?.displayName ?: "NULL"})")
                        }
                        if (topicsFromDb.size > 5) {
                            println("[LessonsViewModel]   ... and ${topicsFromDb.size - 5} more")
                        }
                        
                        // Warn if any topics don't match the selected language
                        val mismatchedTopics = topicsFromDb.filter { it.language != language }
                        if (mismatchedTopics.isNotEmpty()) {
                            println("[LessonsViewModel] ⚠️ WARNING: ${mismatchedTopics.size} topics don't match selected language!")
                            mismatchedTopics.take(3).forEach { topic ->
                                println("[LessonsViewModel]   ⚠️ ${topic.id}: language=${topic.language?.displayName ?: "NULL"} (expected: ${language.displayName})")
                            }
                            println("[LessonsViewModel] 💡 Fix: Run migration 009_fix_chinese_lesson_language.sql to update lesson language tags")
                        }
                    } else {
                        println("[LessonsViewModel] 📭 No topics found for ${language.displayName} - ${difficulty.displayName}")
                        println("[LessonsViewModel] 💡 Use Admin Panel to create lessons for this language")
                    }
                    
                    val filteredTopics =
                        topicsFromDb.filter { topic ->
                            topic.language == null || topic.language == language
                        }

                    val removedCount = topicsFromDb.size - filteredTopics.size
                    if (removedCount > 0) {
                        println("[LessonsViewModel] ⚠️ Removed $removedCount topic(s) that were tagged with a different language than ${language.displayName}")
                    }

                    val unknownLanguageCount = filteredTopics.count { it.language == null }
                    if (unknownLanguageCount > 0) {
                        println("[LessonsViewModel] ⚠️ $unknownLanguageCount topic(s) are missing a language tag. They will default to ${language.displayName}.")
                    }

                    _lessonTopics.value = filteredTopics
                },
                onFailure = { e ->
                    println("[LessonsViewModel] ❌ Error loading lesson topics: ${e.message}")
                    e.printStackTrace()
                    // Show empty list instead of hardcoded data
                    _lessonTopics.value = emptyList()
                }
            )
            
            println("[LessonsViewModel] Final count: ${_lessonTopics.value.size} topics for ${language.displayName} - ${difficulty.displayName}")
            println("[LessonsViewModel] ========================================")
        } catch (e: Exception) {
            println("[LessonsViewModel] ❌ Exception loading lesson topics: ${e.message}")
            e.printStackTrace()
            // Show empty list instead of hardcoded data
            _lessonTopics.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }
    
    fun onLessonTopicClicked(topicId: String) {
        println("[LessonsViewModel] Lesson topic clicked: $topicId")
        
        // Find the topic in the list
        val topic = _lessonTopics.value.find { it.id == topicId }
        if (topic != null) {
            _selectedTopicForLessons.value = topic
            loadLessonsForTopic(topicId)
        } else {
            println("[LessonsViewModel] ⚠️ Topic not found: $topicId")
        }
    }
    
    private fun loadLessonsForTopic(topicId: String) {
        viewModelScope.launch {
            _isLoadingLessons.value = true
            try {
                println("[LessonsViewModel] 🔍 Loading lessons for topic: $topicId")
                
                val result = lessonContentRepository.getLessonsByTopic(topicId, publishedOnly = true)
                
                result.fold(
                    onSuccess = { lessons ->
                        println("[LessonsViewModel] ✅ Successfully loaded ${lessons.size} lessons")
                        _topicLessons.value = lessons
                    },
                    onFailure = { error ->
                        println("[LessonsViewModel] ❌ Error loading lessons: ${error.message}")
                        error.printStackTrace()
                        _topicLessons.value = emptyList()
                    }
                )
            } catch (e: Exception) {
                println("[LessonsViewModel] ❌ Exception loading lessons: ${e.message}")
                e.printStackTrace()
                _topicLessons.value = emptyList()
            } finally {
                _isLoadingLessons.value = false
            }
        }
    }
    
    fun onBackFromLessonList() {
        _selectedTopicForLessons.value = null
        _topicLessons.value = emptyList()
    }
    
    // Note: Lesson navigation is handled via callback passed from HomeScreen
    // The onLessonSelected callback is passed to LessonListView which calls it when a lesson is clicked

    fun onBackFromCategory() {
        // If we're in lesson list view, go back to topic list
        if (_selectedTopicForLessons.value != null) {
            onBackFromLessonList()
        } else {
            // Otherwise, go back to category selection
            _selectedCategory.value = null
            _categoryLessons.value = emptyList()
            _lessonTopics.value = emptyList()
        }
    }
    
    /**
     * Change the active learning language.
     * This will:
     * 1. Update the selected language state
     * 2. Clear current lesson topics
     * 3. Optionally persist to user profile (for next session)
     * 4. Reload lessons for the new language if a category is selected
     * 
     * @param language The new language to switch to
     * @param persistToProfile Whether to save this preference to the user's profile (default: true)
     */
    fun changeLanguage(language: LessonLanguage, persistToProfile: Boolean = true) {
        if (language == _selectedLanguage.value) {
            println("[LessonsViewModel] Language already set to ${language.displayName}, skipping")
            return
        }
        
        viewModelScope.launch {
            _isLanguageChanging.value = true
            println("[LessonsViewModel] Changing language from ${_selectedLanguage.value.displayName} to ${language.displayName}")
            
            try {
                // Update the selected language
                _selectedLanguage.value = language
                
                // Clear current topics to show loading state
                _lessonTopics.value = emptyList()
                
                // Persist to profile if requested
                if (persistToProfile) {
                    persistLanguagePreference(language)
                }
                
                // Reload lessons if a category is currently selected
                val currentCategory = _selectedCategory.value
                if (currentCategory != null) {
                    println("[LessonsViewModel] Reloading ${currentCategory.displayName} lessons for ${language.displayName}")
                    loadLessonTopics(currentCategory)
                }
                
                println("[LessonsViewModel] Successfully changed language to ${language.displayName}")
            } catch (e: Exception) {
                println("[LessonsViewModel] Error changing language: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLanguageChanging.value = false
            }
        }
    }
    
    /**
     * Persist the language preference to the user's profile.
     * Updates the targetLanguages in PersonalInfo to put the new language first.
     */
    private suspend fun persistLanguagePreference(language: LessonLanguage) {
        try {
            // Load current personal info
            val currentInfoResult = profileService.loadPersonalInfo()
            val currentInfo = currentInfoResult.getOrNull()
            
            if (currentInfo != null) {
                // Update target languages: put selected language first, keep others
                val existingLanguages = currentInfo.targetLanguages.toMutableList()
                existingLanguages.remove(language.displayName)
                val updatedLanguages = listOf(language.displayName) + existingLanguages
                
                // Create updated personal info
                val updatedInfo = currentInfo.copy(targetLanguages = updatedLanguages)
                
                // Save to profile
                val saveResult = profileService.updatePersonalInfo(updatedInfo)
                if (saveResult.isSuccess) {
                    println("[LessonsViewModel] Language preference saved to profile: ${language.displayName}")
                } else {
                    println("[LessonsViewModel] Failed to save language preference: ${saveResult.exceptionOrNull()?.message}")
                }
            }
        } catch (e: Exception) {
            println("[LessonsViewModel] Error persisting language preference: ${e.message}")
            // Don't fail the language change if persistence fails - it's just a preference
        }
    }
    
    /**
     * Get the flag emoji for the current language
     */
    fun getLanguageFlag(language: LessonLanguage = _selectedLanguage.value): String {
        return when (language) {
            LessonLanguage.KOREAN -> "🇰🇷"
            LessonLanguage.CHINESE -> "🇨🇳"
            LessonLanguage.FRENCH -> "🇫🇷"
            LessonLanguage.GERMAN -> "🇩🇪"
            LessonLanguage.SPANISH -> "🇪🇸"
        }
    }

    fun onContinueLessonClicked(lessonId: String) {
        _isLoading.value = true
        println("Continue lesson clicked: $lessonId")
        _isLoading.value = false
    }

    fun onStartLessonClicked(lessonId: String) {
        _isLoading.value = true
        println("Start lesson clicked: $lessonId")
        _isLoading.value = false
    }

    fun onRecentLessonClicked(
        @Suppress("UNUSED_PARAMETER") recentLessonId: String,
    ) {
    }

    fun refreshLessons() {
        _isLoading.value = true
        _lessons.value = Lesson.getSampleLessons()
        _levelProgress.value = LevelProgress.getSampleProgress()
        _recentLessons.value = RecentLesson.getSampleRecentLessons()
        refreshCategories()
        _isLoading.value = false
    }
}
