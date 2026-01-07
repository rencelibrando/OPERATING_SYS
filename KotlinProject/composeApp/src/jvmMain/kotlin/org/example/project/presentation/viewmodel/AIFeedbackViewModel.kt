package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.core.ai.ConversationAnalysisService
import org.example.project.core.ai.VoiceApiService
import org.example.project.core.audio.AudioPlayer
import org.example.project.domain.model.ConversationFeedback
import org.example.project.domain.model.ConversationSession

class AIFeedbackViewModel : ViewModel() {
    private val voiceApiService = VoiceApiService()
    private val audioPlayer = AudioPlayer()
    private val analysisService = ConversationAnalysisService()

    private val _conversationSessions = mutableStateOf<List<ConversationSession>>(emptyList())
    val conversationSessions: State<List<ConversationSession>> = _conversationSessions

    private val _selectedSession = mutableStateOf<ConversationSession?>(null)
    val selectedSession: State<ConversationSession?> = _selectedSession

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _isPlayingAudio = mutableStateOf(false)
    val isPlayingAudio: State<Boolean> = _isPlayingAudio

    private val _currentFeedback = mutableStateOf<ConversationFeedback?>(null)
    val currentFeedback: State<ConversationFeedback?> = _currentFeedback

    private val _isAnalyzing = mutableStateOf(false)
    val isAnalyzing: State<Boolean> = _isAnalyzing

    private val _analysisError = mutableStateOf<String?>(null)
    val analysisError: State<String?> = _analysisError

    private val _currentRecording = mutableStateOf<org.example.project.domain.model.ConversationRecording?>(null)
    val currentRecording: State<org.example.project.domain.model.ConversationRecording?> = _currentRecording

    private val _audioPlaybackError = mutableStateOf<String?>(null)
    val audioPlaybackError: State<String?> = _audioPlaybackError

    private val _sortOrder = mutableStateOf<SortOrder>(SortOrder.DATE_DESC)
    val sortOrder: State<SortOrder> = _sortOrder

    private val _allSessions = mutableListOf<ConversationSession>()

    enum class SortOrder {
        DATE_DESC,
        DATE_ASC,
        DURATION_DESC,
        DURATION_ASC,
        LANGUAGE_AZ,
        LANGUAGE_ZA,
    }

    fun loadConversationSessions(userId: String? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                // Use provided userId or get from auth
                val actualUserId = userId ?: org.example.project.core.api.SupabaseApiHelper.getCurrentUserId()

                if (actualUserId == null) {
                    _error.value = "User not authenticated"
                    _isLoading.value = false
                    return@launch
                }

                val result = voiceApiService.getConversationSessions(actualUserId)

                if (result.isSuccess) {
                    val sessions = result.getOrNull() ?: emptyList()
                    _allSessions.clear()
                    _allSessions.addAll(sessions)
                    applySorting()
                    println("[AIFeedback] Loaded ${sessions.size} conversation sessions")
                    sessions.forEach { session ->
                        println("[AIFeedback] Session: ${session.sessionId}, audioUrl: ${session.audioUrl}")
                    }
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to load sessions"
                    println("[AIFeedback] Error loading sessions: ${_error.value}")
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "An unexpected error occurred"
                println("[AIFeedback] Exception loading sessions: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectSession(session: ConversationSession) {
        _selectedSession.value = session
        println("[AIFeedback] Selected session: ${session.sessionId}")
        _currentFeedback.value = null
        _currentRecording.value = null
        loadFeedbackForSession(session)
        loadConversationRecording(session.sessionId)
    }

    fun clearSelectedSession() {
        _selectedSession.value = null
        _currentFeedback.value = null
        _currentRecording.value = null
        _analysisError.value = null
        _audioPlaybackError.value = null
        audioPlayer.setPlaybackFinishedCallback(null)
        audioPlayer.stop()
        _isPlayingAudio.value = false
    }

    private fun loadFeedbackForSession(session: ConversationSession) {
        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                _analysisError.value = null
                println("[AIFeedback] Starting AI analysis for session: ${session.sessionId}")

                // Get current user ID
                val userId = org.example.project.core.api.SupabaseApiHelper.getCurrentUserId()
                if (userId == null) {
                    _analysisError.value = "User not authenticated"
                    _isAnalyzing.value = false
                    return@launch
                }

                val result = analysisService.analyzeConversation(session, userId)

                if (result.isSuccess) {
                    val feedback = result.getOrNull()!!
                    _currentFeedback.value = feedback
                    println("[AIFeedback] Analysis complete: ${feedback.overallScore}/100")
                } else {
                    _analysisError.value = result.exceptionOrNull()?.message
                        ?: "Failed to analyze conversation"
                    println("[AIFeedback] Analysis failed: ${_analysisError.value}")
                }
            } catch (e: Exception) {
                _analysisError.value = e.message ?: "An unexpected error occurred"
                println("[AIFeedback] Analysis exception: ${e.message}")
                e.printStackTrace()
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun loadConversationRecording(sessionId: String) {
        viewModelScope.launch {
            try {
                println("[AIFeedback] Loading conversation recording for session: $sessionId")

                val result = voiceApiService.getConversationRecording(sessionId)

                if (result.isSuccess) {
                    val recording = result.getOrNull()
                    _currentRecording.value = recording
                    println("[AIFeedback] Recording loaded: ${recording?.audioUrl ?: "No audio URL"}")
                } else {
                    println("[AIFeedback] Failed to load recording: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                println("[AIFeedback] Exception loading recording: ${e.message}")
            }
        }
    }

    fun retryAnalysis() {
        val session = _selectedSession.value
        if (session != null) {
            _currentFeedback.value = null
            _analysisError.value = null
            loadFeedbackForSession(session)
        }
    }

    fun playRecording(sessionId: String) {
        val session = _selectedSession.value
        val recording = _currentRecording.value

        // Try session audioUrl first, then recording audioUrl
        val audioUrl = session?.audioUrl ?: recording?.audioUrl

        // Clear previous errors
        _audioPlaybackError.value = null

        if (audioUrl.isNullOrEmpty()) {
            val errorMsg = "No audio recording available for this session"
            println("[AIFeedback] $errorMsg")
            _audioPlaybackError.value = errorMsg
            return
        }

        if (_isPlayingAudio.value) {
            audioPlayer.stop()
            _isPlayingAudio.value = false
            return
        }

        viewModelScope.launch {
            try {
                println("[AIFeedback] Playing audio from: $audioUrl")

                // Set the callback to update playing state when audio finishes
                audioPlayer.setPlaybackFinishedCallback {
                    _isPlayingAudio.value = false
                }

                val result = audioPlayer.playAudioFromUrl(audioUrl)

                if (result.isFailure) {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to play audio"
                    println("[AIFeedback] $errorMsg")
                    _audioPlaybackError.value = errorMsg
                    _isPlayingAudio.value = false
                } else {
                    println("[AIFeedback] Audio playback started successfully")
                    _isPlayingAudio.value = true
                }
            } catch (e: Exception) {
                val errorMsg = "Error playing audio: ${e.message}"
                println("[AIFeedback] $errorMsg")
                _audioPlaybackError.value = errorMsg
                _isPlayingAudio.value = false
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        applySorting()
    }

    private fun applySorting() {
        val sorted =
            when (_sortOrder.value) {
                SortOrder.DATE_DESC -> _allSessions.sortedByDescending { it.createdAt }
                SortOrder.DATE_ASC -> _allSessions.sortedBy { it.createdAt }
                SortOrder.DURATION_DESC -> _allSessions.sortedByDescending { it.duration }
                SortOrder.DURATION_ASC -> _allSessions.sortedBy { it.duration }
                SortOrder.LANGUAGE_AZ -> _allSessions.sortedBy { it.language }
                SortOrder.LANGUAGE_ZA -> _allSessions.sortedByDescending { it.language }
            }
        _conversationSessions.value = sorted
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                println("[AIFeedback] Deleting session: $sessionId")

                val result = voiceApiService.deleteConversationSession(sessionId)

                if (result.isSuccess) {
                    // Remove from local list
                    _allSessions.removeAll { it.sessionId == sessionId }
                    applySorting()

                    // Clear selected session if it was deleted
                    if (_selectedSession.value?.sessionId == sessionId) {
                        clearSelectedSession()
                    }

                    println("[AIFeedback] Session deleted successfully")
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to delete session"
                    println("[AIFeedback] Delete failed: ${_error.value}")
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "An unexpected error occurred"
                println("[AIFeedback] Exception deleting session: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.dispose()
        voiceApiService.close()
    }
}
