package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.SpeakingExercise
import org.example.project.domain.model.SpeakingSession
import org.example.project.domain.model.SpeakingStats
import org.example.project.domain.model.SpeakingFeature
import org.example.project.domain.model.SpeakingFilter


class SpeakingViewModel : ViewModel() {

    private val _speakingExercises = mutableStateOf(SpeakingExercise.getSampleExercises())
    private val _speakingSessions = mutableStateOf(SpeakingSession.getSampleSessions())
    private val _speakingStats = mutableStateOf(SpeakingStats.getSampleStats())
    private val _speakingFeatures = mutableStateOf(SpeakingFeature.getSpeakingFeatures())
    private val _selectedFilter = mutableStateOf(SpeakingFilter.ALL)
    private val _isRecording = mutableStateOf(false)
    private val _currentSession = mutableStateOf<SpeakingSession?>(null)
    private val _isLoading = mutableStateOf(false)

    val speakingExercises: State<List<SpeakingExercise>> = _speakingExercises
    val speakingSessions: State<List<SpeakingSession>> = _speakingSessions
    val speakingStats: State<SpeakingStats> = _speakingStats
    val speakingFeatures: State<List<SpeakingFeature>> = _speakingFeatures
    val selectedFilter: State<SpeakingFilter> = _selectedFilter
    val isRecording: State<Boolean> = _isRecording
    val currentSession: State<SpeakingSession?> = _currentSession
    val isLoading: State<Boolean> = _isLoading

    private val _filteredExercises = mutableStateOf(emptyList<SpeakingExercise>())
    val filteredExercises: State<List<SpeakingExercise>> = _filteredExercises
    
    init {
        updateFilteredExercises()
    }
    

    fun onFilterSelected(filter: SpeakingFilter) {
        _selectedFilter.value = filter
        updateFilteredExercises()
    }

    fun onExerciseClicked(exerciseId: String) {
        // TODO: Navigate to exercise details or start exercise
        println("Exercise clicked: $exerciseId")
    }

    fun onStartExerciseClicked(exerciseId: String) {
        _isLoading.value = true
        
        // TODO: Initialize exercise session
        val newSession = SpeakingSession(
            id = "session_${System.currentTimeMillis()}",
            exerciseId = exerciseId,
            startTime = System.currentTimeMillis(),
            endTime = null,
            accuracyScore = null,
            fluencyScore = null,
            pronunciationScore = null,
            overallScore = null,
            feedback = null,
            recordingPath = null
        )
        
        _currentSession.value = newSession
        println("Starting exercise: $exerciseId")
        
        _isLoading.value = false
    }

    fun onMicrophoneToggle() {
        _isRecording.value = !_isRecording.value
        
        if (_isRecording.value) {
            // TODO: Start audio recording
            println("Starting audio recording...")
        } else {
            // TODO: Stop audio recording and process
            println("Stopping audio recording...")
            processRecording()
        }
    }

    fun onCompleteSession() {
        val session = _currentSession.value ?: return
        
        _isLoading.value = true
        
        // TODO: Process session results and save
        val completedSession = session.copy(
            endTime = System.currentTimeMillis(),
            accuracyScore = (70..95).random(),
            fluencyScore = (65..90).random(),
            pronunciationScore = (75..95).random(),
            overallScore = (70..90).random(),
            feedback = "Great job! Keep practicing to improve your fluency."
        )
        
        _speakingSessions.value = _speakingSessions.value + completedSession
        _currentSession.value = null
        _isRecording.value = false
        
        updateSpeakingStats()
        
        _isLoading.value = false
        println("Session completed: ${completedSession.id}")
    }

    fun onCancelSession() {
        _currentSession.value = null
        _isRecording.value = false
        println("Session cancelled")
    }

    fun onReviewSessionClicked(sessionId: String) {

        println("Review session clicked: $sessionId")
    }

    fun onStartFirstPracticeClicked() {

        println("Start first practice clicked")
    }
    

    fun onExploreExercisesClicked() {

        println("Explore exercises clicked")
    }
    

    fun refreshSpeakingData() {
        _isLoading.value = true

        _speakingExercises.value = SpeakingExercise.getSampleExercises()
        _speakingSessions.value = SpeakingSession.getSampleSessions()
        updateSpeakingStats()
        updateFilteredExercises()
        
        _isLoading.value = false
    }

    private fun processRecording() {
        println("Processing audio recording...")
    }

    private fun updateFilteredExercises() {
        val exercises = _speakingExercises.value
        val filter = _selectedFilter.value
        
        val filtered = exercises.filter { exercise ->
            when (filter) {
                SpeakingFilter.ALL -> true
                SpeakingFilter.PRONUNCIATION -> exercise.type.name == "PRONUNCIATION"
                SpeakingFilter.CONVERSATION -> exercise.type.name == "CONVERSATION"
                SpeakingFilter.ACCENT -> exercise.type.name == "ACCENT_TRAINING"
                SpeakingFilter.FLUENCY -> exercise.category == "Fluency"
            }
        }
        
        _filteredExercises.value = filtered
    }

    private fun updateSpeakingStats() {
        val sessions = _speakingSessions.value.filter { it.endTime != null }
        
        if (sessions.isEmpty()) {
            _speakingStats.value = SpeakingStats.getSampleStats()
            return
        }
        
        val totalMinutes = sessions.sumOf { session ->
            val duration = (session.endTime!! - session.startTime) / 60000 // Convert to minutes
            duration.toInt()
        }
        
        val averageAccuracy = sessions.mapNotNull { it.accuracyScore }.average().toInt()
        val averageFluency = sessions.mapNotNull { it.fluencyScore }.average().toInt()
        val averagePronunciation = sessions.mapNotNull { it.pronunciationScore }.average().toInt()
        
        val stats = SpeakingStats(
            totalSessions = sessions.size,
            totalMinutes = totalMinutes,
            averageAccuracy = averageAccuracy,
            averageFluency = averageFluency,
            averagePronunciation = averagePronunciation,
            currentStreak = calculateCurrentStreak(sessions),
            longestStreak = calculateLongestStreak(sessions),
            exercisesCompleted = sessions.map { it.exerciseId }.distinct().size
        )
        
        _speakingStats.value = stats
    }

    private fun calculateCurrentStreak(sessions: List<SpeakingSession>): Int {

        return if (sessions.isNotEmpty()) 3 else 0
    }

    private fun calculateLongestStreak(sessions: List<SpeakingSession>): Int {
        return if (sessions.isNotEmpty()) 7 else 0
    }
}
