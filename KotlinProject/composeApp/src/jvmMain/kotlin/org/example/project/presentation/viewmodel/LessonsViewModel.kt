package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.core.lessons.LessonTopicsService
import org.example.project.core.profile.ProfileService
import org.example.project.data.repository.LessonTopicsRepository
import org.example.project.data.repository.LessonTopicsRepositoryImpl
import org.example.project.domain.model.Lesson
import org.example.project.domain.model.LessonCategoryInfo
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LevelProgress
import org.example.project.domain.model.RecentLesson
import org.example.project.core.auth.User as AuthUser

class LessonsViewModel : ViewModel() {
    private val profileService = ProfileService()
    private val lessonTopicsRepository: LessonTopicsRepository = LessonTopicsRepositoryImpl()
    private val lessonTopicsService = LessonTopicsService()
    
    private val _lessons = mutableStateOf(Lesson.getSampleLessons())
    private val _levelProgress = mutableStateOf(LevelProgress.getSampleProgress())
    private val _recentLessons = mutableStateOf(RecentLesson.getSampleRecentLessons())
    private val _isLoading = mutableStateOf(false)
    private val _userLevel = mutableStateOf(LessonDifficulty.BEGINNER)
    private val _lessonCategories = mutableStateOf<List<LessonCategoryInfo>>(emptyList())
    private val _selectedCategory = mutableStateOf<LessonDifficulty?>(null)
    private val _categoryLessons = mutableStateOf<List<Lesson>>(emptyList())
    private val _lessonTopics = mutableStateOf<List<org.example.project.domain.model.LessonTopic>>(emptyList())

    val lessons: State<List<Lesson>> = _lessons
    val levelProgress: State<LevelProgress> = _levelProgress
    val recentLessons: State<List<RecentLesson>> = _recentLessons
    val isLoading: State<Boolean> = _isLoading
    val userLevel: State<LessonDifficulty> = _userLevel
    val lessonCategories: State<List<LessonCategoryInfo>> = _lessonCategories
    val selectedCategory: State<LessonDifficulty?> = _selectedCategory
    val categoryLessons: State<List<Lesson>> = _categoryLessons
    val lessonTopics: State<List<org.example.project.domain.model.LessonTopic>> = _lessonTopics

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
            println("[LessonsViewModel] Loading topics for ${difficulty.displayName} from Supabase...")
            
            val result = lessonTopicsRepository.getTopicsByDifficulty(difficulty)
            
            
            val topicsFromDb = result.getOrNull() ?: emptyList()
            
            _lessonTopics.value = if (topicsFromDb.isEmpty()) {
                println("[LessonsViewModel] No topics found in database, falling back to local data...")
                
                
                when (difficulty) {
                    LessonDifficulty.BEGINNER -> org.example.project.domain.model.LessonTopic.getBeginnerTopics()
                    LessonDifficulty.INTERMEDIATE -> org.example.project.domain.model.LessonTopic.getIntermediateTopics()
                    LessonDifficulty.ADVANCED -> org.example.project.domain.model.LessonTopic.getAdvancedTopics()
                }
            } else {
                println("[LessonsViewModel] Successfully loaded ${topicsFromDb.size} topics from Supabase")
                topicsFromDb
            }
            
            println("[LessonsViewModel] Loaded ${_lessonTopics.value.size} topics for ${difficulty.displayName}")
        } catch (e: Exception) {
            println("[LessonsViewModel] Error loading lesson topics: ${e.message}")
            e.printStackTrace()
            
            
            _lessonTopics.value = when (difficulty) {
                LessonDifficulty.BEGINNER -> org.example.project.domain.model.LessonTopic.getBeginnerTopics()
                LessonDifficulty.INTERMEDIATE -> org.example.project.domain.model.LessonTopic.getIntermediateTopics()
                LessonDifficulty.ADVANCED -> org.example.project.domain.model.LessonTopic.getAdvancedTopics()
            }
        } finally {
            _isLoading.value = false
        }
    }
    
    fun onLessonTopicClicked(topicId: String) {
        println("Lesson topic clicked: $topicId")
        
    }
    

    fun seedBeginnerTopicsToSupabase() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                println("[LessonsViewModel] Starting to seed beginner topics to Supabase...")
                val result = lessonTopicsService.seedBeginnerTopics()
                
                result.fold(
                    onSuccess = {
                        println("[LessonsViewModel] ✓ Successfully seeded all beginner topics to Supabase!")
                        
                        val currentCategory = _selectedCategory.value
                        if (currentCategory == LessonDifficulty.BEGINNER) {
                            loadLessonTopics(LessonDifficulty.BEGINNER)
                        }
                    },
                    onFailure = { e ->
                        println("[LessonsViewModel] ✗ Failed to seed topics: ${e.message}")
                        e.printStackTrace()
                    }
                )
            } catch (e: Exception) {
                println("[LessonsViewModel] Error seeding topics: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onBackFromCategory() {
        _selectedCategory.value = null
        _categoryLessons.value = emptyList()
        _lessonTopics.value = emptyList()
    }

    fun onLessonClicked(lessonId: String) {
        println("Lesson clicked: $lessonId")
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
