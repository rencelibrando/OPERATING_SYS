package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.SpeakingExercise
import org.example.project.domain.model.SpeakingSession
import org.example.project.domain.model.SpeakingStats
import org.example.project.domain.model.SpeakingFeature
import org.example.project.domain.model.SpeakingFilter

/**
 * ViewModel for the Speaking Practice screen
 * 
 * Manages the state and business logic for the speaking practice screen,
 * including exercises, sessions, statistics, and user interactions
 */
class SpeakingViewModel : ViewModel() {
    
    // Private mutable state
    private val _speakingExercises = mutableStateOf(SpeakingExercise.getSampleExercises())
    private val _speakingSessions = mutableStateOf(SpeakingSession.getSampleSessions())
    private val _speakingStats = mutableStateOf(SpeakingStats.getSampleStats())
    private val _speakingFeatures = mutableStateOf(SpeakingFeature.getSpeakingFeatures())
    private val _selectedFilter = mutableStateOf(SpeakingFilter.ALL)
    private val _isRecording = mutableStateOf(false)
    private val _currentSession = mutableStateOf<SpeakingSession?>(null)
    private val _isLoading = mutableStateOf(false)
    
    // Public read-only state
    val speakingExercises: State<List<SpeakingExercise>> = _speakingExercises
    val speakingSessions: State<List<SpeakingSession>> = _speakingSessions
    val speakingStats: State<SpeakingStats> = _speakingStats
    val speakingFeatures: State<List<SpeakingFeature>> = _speakingFeatures
    val selectedFilter: State<SpeakingFilter> = _selectedFilter
    val isRecording: State<Boolean> = _isRecording
    val currentSession: State<SpeakingSession?> = _currentSession
    val isLoading: State<Boolean> = _isLoading
    
    // Computed property for filtered exercises
    private val _filteredExercises = mutableStateOf(emptyList<SpeakingExercise>())
    val filteredExercises: State<List<SpeakingExercise>> = _filteredExercises
    
    init {
        updateFilteredExercises()
    }
    
    /**
     * Handles filter selection changes
     * @param filter The selected filter
     */
    fun onFilterSelected(filter: SpeakingFilter) {
        _selectedFilter.value = filter
        updateFilteredExercises()
    }
    
    /**
     * Handles speaking exercise click
     * @param exerciseId The ID of the clicked exercise
     */
    fun onExerciseClicked(exerciseId: String) {
        // TODO: Navigate to exercise details or start exercise
        println("Exercise clicked: $exerciseId")
    }
    
    /**
     * Handles start exercise button click
     * @param exerciseId The ID of the exercise to start
     */
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
    
    /**
     * Handles microphone recording toggle
     */
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
    
    /**
     * Handles session completion
     */
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
        
        // Add completed session to sessions list
        _speakingSessions.value = _speakingSessions.value + completedSession
        _currentSession.value = null
        _isRecording.value = false
        
        // Update statistics
        updateSpeakingStats()
        
        _isLoading.value = false
        println("Session completed: ${completedSession.id}")
    }
    
    /**
     * Handles session cancellation
     */
    fun onCancelSession() {
        _currentSession.value = null
        _isRecording.value = false
        println("Session cancelled")
    }
    
    /**
     * Handles session review click
     * @param sessionId The ID of the session to review
     */
    fun onReviewSessionClicked(sessionId: String) {
        // TODO: Navigate to session review screen
        println("Review session clicked: $sessionId")
    }
    
    /**
     * Handles start first practice click (from empty state)
     */
    fun onStartFirstPracticeClicked() {
        // TODO: Show getting started guide or create first exercise
        println("Start first practice clicked")
    }
    
    /**
     * Handles explore exercises click (from empty state)
     */
    fun onExploreExercisesClicked() {
        // TODO: Navigate to exercise library or show exercise picker
        println("Explore exercises clicked")
    }
    
    /**
     * Refreshes speaking data
     */
    fun refreshSpeakingData() {
        _isLoading.value = true
        
        // TODO: Implement actual data refresh from repository
        // For now, simulate refresh
        _speakingExercises.value = SpeakingExercise.getSampleExercises()
        _speakingSessions.value = SpeakingSession.getSampleSessions()
        updateSpeakingStats()
        updateFilteredExercises()
        
        _isLoading.value = false
    }
    
    /**
     * Processes audio recording and provides feedback
     */
    private fun processRecording() {
        // TODO: Implement actual audio processing
        // This would typically involve:
        // 1. Send audio to backend for analysis
        // 2. Receive pronunciation, fluency, and accuracy scores
        // 3. Update current session with results
        println("Processing audio recording...")
    }
    
    /**
     * Updates filtered exercises based on selected filter
     */
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
    
    /**
     * Updates speaking statistics based on current sessions
     */
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
    
    /**
     * Calculates current practice streak
     */
    private fun calculateCurrentStreak(sessions: List<SpeakingSession>): Int {
        // TODO: Implement actual streak calculation based on practice days
        return if (sessions.isNotEmpty()) 3 else 0
    }
    
    /**
     * Calculates longest practice streak
     */
    private fun calculateLongestStreak(sessions: List<SpeakingSession>): Int {
        // TODO: Implement actual longest streak calculation
        return if (sessions.isNotEmpty()) 7 else 0
    }
}
