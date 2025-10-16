package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.Lesson
import org.example.project.domain.model.LevelProgress
import org.example.project.domain.model.RecentLesson

class LessonsViewModel : ViewModel() {
    private val _lessons = mutableStateOf(Lesson.getSampleLessons())
    private val _levelProgress = mutableStateOf(LevelProgress.getSampleProgress())
    private val _recentLessons = mutableStateOf(RecentLesson.getSampleRecentLessons())
    private val _isLoading = mutableStateOf(false)

    val lessons: State<List<Lesson>> = _lessons
    val levelProgress: State<LevelProgress> = _levelProgress
    val recentLessons: State<List<RecentLesson>> = _recentLessons
    val isLoading: State<Boolean> = _isLoading

    fun onLessonClicked(lessonId: String) {
        // TODO: Navigate to specific lesson or implement lesson logic
        println("Lesson clicked: $lessonId")
    }

    fun onContinueLessonClicked(lessonId: String) {
        _isLoading.value = true
        // TODO: Implement continue lesson logic
        println("Continue lesson clicked: $lessonId")

        _isLoading.value = false
    }

    fun onStartLessonClicked(lessonId: String) {
        _isLoading.value = true
        // TODO: Implement start lesson logic
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
        _isLoading.value = false
    }
}
