package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.Lesson
import org.example.project.domain.model.LevelProgress
import org.example.project.domain.model.RecentLesson

/**
 * ViewModel for the Lessons screen
 * 
 * Manages the state and business logic for the lessons screen,
 * including lesson data, level progress, and user interactions
 */
class LessonsViewModel : ViewModel() {
    
    // Private mutable state
    private val _lessons = mutableStateOf(Lesson.getSampleLessons())
    private val _levelProgress = mutableStateOf(LevelProgress.getSampleProgress())
    private val _recentLessons = mutableStateOf(RecentLesson.getSampleRecentLessons())
    private val _isLoading = mutableStateOf(false)
    
    // Public read-only state
    val lessons: State<List<Lesson>> = _lessons
    val levelProgress: State<LevelProgress> = _levelProgress
    val recentLessons: State<List<RecentLesson>> = _recentLessons
    val isLoading: State<Boolean> = _isLoading
    
    /**
     * Handles lesson card click
     * @param lessonId The ID of the clicked lesson
     */
    fun onLessonClicked(lessonId: String) {
        // TODO: Navigate to specific lesson or implement lesson logic
        println("Lesson clicked: $lessonId")
    }
    
    /**
     * Handles continue learning button click for a specific lesson
     * @param lessonId The ID of the lesson to continue
     */
    fun onContinueLessonClicked(lessonId: String) {
        _isLoading.value = true
        // TODO: Implement continue lesson logic
        println("Continue lesson clicked: $lessonId")
        
        // Simulate loading
        _isLoading.value = false
    }
    
    /**
     * Handles start lesson button click for a specific lesson
     * @param lessonId The ID of the lesson to start
     */
    fun onStartLessonClicked(lessonId: String) {
        _isLoading.value = true
        // TODO: Implement start lesson logic
        println("Start lesson clicked: $lessonId")
        
        // Simulate loading
        _isLoading.value = false
    }
    
    /**
     * Handles recent lesson item click
     * @param recentLessonId The ID of the recent lesson clicked
     */
    fun onRecentLessonClicked(recentLessonId: String) {
        // TODO: Navigate to recent lesson or resume progress
        println("Recent lesson clicked: $recentLessonId")
    }
    
    /**
     * Refreshes lesson data
     */
    fun refreshLessons() {
        _isLoading.value = true
        // TODO: Implement data refresh logic
        
        // Simulate loading and refresh
        _lessons.value = Lesson.getSampleLessons()
        _levelProgress.value = LevelProgress.getSampleProgress()
        _recentLessons.value = RecentLesson.getSampleRecentLessons()
        _isLoading.value = false
    }
}
