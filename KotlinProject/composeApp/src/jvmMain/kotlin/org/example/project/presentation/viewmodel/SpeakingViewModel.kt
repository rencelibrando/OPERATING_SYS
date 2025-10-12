package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.domain.model.VocabularyWord

/**
 * ViewModel for the Speaking Practice feature
 * Manages word practice state, language selection, recording, and feedback
 */
class SpeakingViewModel : ViewModel() {

    // UI State
    private val _currentWord = mutableStateOf<VocabularyWord?>(null)
    val currentWord: State<VocabularyWord?> = _currentWord

    private val _selectedLanguage = mutableStateOf<PracticeLanguage?>(null)
    val selectedLanguage: State<PracticeLanguage?> = _selectedLanguage

    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private val _hasRecording = mutableStateOf(false)
    val hasRecording: State<Boolean> = _hasRecording

    private val _isPlayingRecording = mutableStateOf(false)
    val isPlayingRecording: State<Boolean> = _isPlayingRecording

    private val _feedback = mutableStateOf<PracticeFeedback?>(null)
    val feedback: State<PracticeFeedback?> = _feedback

    private val _isAnalyzing = mutableStateOf(false)
    val isAnalyzing: State<Boolean> = _isAnalyzing

    private val _showLanguageDialog = mutableStateOf(false)
    val showLanguageDialog: State<Boolean> = _showLanguageDialog

    private val _recordingDuration = mutableStateOf(0f) // in seconds
    val recordingDuration: State<Float> = _recordingDuration

    private val _speakingFeatures = mutableStateOf(getSpeakingFeatures())
    val speakingFeatures: State<List<SpeakingFeature>> = _speakingFeatures

    /**
     * Initialize practice session with a vocabulary word
     */
    fun startPracticeSession(word: VocabularyWord) {
        _currentWord.value = word
        _showLanguageDialog.value = true
        resetSession()
    }

    /**
     * User selected a language for practice
     */
    fun onLanguageSelected(language: PracticeLanguage) {
        _selectedLanguage.value = language
        _showLanguageDialog.value = false
    }

    /**
     * Show language selection dialog
     */
    fun showLanguageSelection() {
        _showLanguageDialog.value = true
    }

    /**
     * Hide language selection dialog
     */
    fun hideLanguageDialog() {
        _showLanguageDialog.value = false
    }

    /**
     * Toggle recording state (start/stop)
     */
    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    /**
     * Start recording audio (mock implementation)
     */
    private fun startRecording() {
        _isRecording.value = true
        _hasRecording.value = false
        _feedback.value = null
        _recordingDuration.value = 0f

        // Mock: Simulate recording duration counter
        viewModelScope.launch {
            while (_isRecording.value) {
                delay(100)
                _recordingDuration.value += 0.1f
            }
        }

        println("🎤 Started recording...")
    }

    /**
     * Stop recording and analyze (mock)
     */
    private fun stopRecording() {
        _isRecording.value = false
        _hasRecording.value = true

        println("🎤 Stopped recording. Duration: ${_recordingDuration.value}s")

        // Automatically analyze after recording
        analyzeRecording()
    }

    /**
     * Play back the recorded audio (mock implementation)
     */
    fun playRecording() {
        if (!_hasRecording.value || _isPlayingRecording.value) return

        _isPlayingRecording.value = true

        viewModelScope.launch {
            println("▶️ Playing back recording...")
            // Mock: Simulate playback duration
            delay((_recordingDuration.value * 1000).toLong())
            _isPlayingRecording.value = false
            println("⏸️ Playback finished")
        }
    }

    /**
     * Analyze the recording and generate feedback (mock AI analysis)
     * In production, this would call an AI pronunciation assessment API
     */
    private fun analyzeRecording() {
        _isAnalyzing.value = true

        viewModelScope.launch {
            // Mock: Simulate AI processing delay
            delay(2000)

            // Generate mock feedback based on selected language and word
            val mockFeedback = generateMockFeedback(
                word = _currentWord.value?.word ?: "",
                language = _selectedLanguage.value ?: PracticeLanguage.FRENCH,
                recordingDuration = _recordingDuration.value
            )

            _feedback.value = mockFeedback
            _isAnalyzing.value = false

            println("✅ Analysis complete: ${mockFeedback.overallScore}%")
        }
    }

    /**
     * Generate mock feedback for demonstration
     * TODO: Replace with actual AI pronunciation assessment API
     */
    private fun generateMockFeedback(
        word: String,
        language: PracticeLanguage,
        recordingDuration: Float
    ): PracticeFeedback {
        // Mock scoring (randomized for demo purposes)
        val pronunciationScore = (75..95).random()
        val clarityScore = (70..95).random()
        val fluencyScore = (65..90).random()
        val overallScore = (pronunciationScore + clarityScore + fluencyScore) / 3

        // Generate contextual feedback messages
        val feedbackMessages = mutableListOf<String>()

        when {
            overallScore >= 85 -> {
                feedbackMessages.add("Excellent pronunciation! 🎉")
                feedbackMessages.add("Your ${language.displayName} pronunciation is very clear.")
            }
            overallScore >= 70 -> {
                feedbackMessages.add("Good effort! Keep practicing. 👍")
                feedbackMessages.add("Focus on the stress patterns in '$word'.")
            }
            else -> {
                feedbackMessages.add("Nice try! Let's work on this together. 💪")
                feedbackMessages.add("Try breaking the word into syllables: ${word.chunked(2).joinToString("-")}")
            }
        }

        // Add language-specific tips
        when (language) {
            PracticeLanguage.FRENCH -> {
                feedbackMessages.add("Remember: French vowels are more rounded than English.")
            }
            PracticeLanguage.GERMAN -> {
                feedbackMessages.add("Tip: German consonants are often harder/sharper than English.")
            }
            PracticeLanguage.HANGEUL -> {
                feedbackMessages.add("Focus on the distinct Korean consonant sounds.")
            }
            PracticeLanguage.MANDARIN -> {
                feedbackMessages.add("Pay attention to the tone - it changes the meaning!")
            }
            PracticeLanguage.SPANISH -> {
                feedbackMessages.add("Remember: Spanish 'r' is trilled or tapped.")
            }
        }

        return PracticeFeedback(
            overallScore = overallScore,
            pronunciationScore = pronunciationScore,
            clarityScore = clarityScore,
            fluencyScore = fluencyScore,
            messages = feedbackMessages,
            suggestions = listOf(
                "Practice the word slowly, then gradually increase speed",
                "Record yourself multiple times and compare",
                "Listen to native ${language.displayName} speakers"
            )
        )
    }

    /**
     * Try again with the same word
     */
    fun tryAgain() {
        resetSession()
    }

    /**
     * Reset the practice session state
     */
    private fun resetSession() {
        _isRecording.value = false
        _hasRecording.value = false
        _isPlayingRecording.value = false
        _feedback.value = null
        _isAnalyzing.value = false
        _recordingDuration.value = 0f
    }

    /**
     * Complete practice and return to vocabulary
     */
    fun completePractice() {
        // TODO: Save practice session results to database
        // TODO: Update word progress/mastery
        resetSession()
        _currentWord.value = null
        _selectedLanguage.value = null
    }

    /**
     * Handle start first practice click from empty state
     */
    fun onStartFirstPracticeClicked() {
        // TODO: Show word selection or start with a sample word
        println("Start first practice clicked")
    }

    /**
     * Handle explore exercises click from empty state
     */
    fun onExploreExercisesClicked() {
        // TODO: Navigate to exercises list or show exercise selection
        println("Explore exercises clicked")
    }

    /**
     * Get speaking features for empty state display
     */
    private fun getSpeakingFeatures(): List<SpeakingFeature> {
        return listOf(
            SpeakingFeature(
                id = "ai_feedback",
                title = "AI-Powered Feedback",
                description = "Get instant, detailed feedback on your pronunciation, fluency, and accuracy.",
                icon = "🤖",
                color = "#8B5CF6"
            ),
            SpeakingFeature(
                id = "pronunciation_analysis",
                title = "Pronunciation Analysis",
                description = "Advanced speech recognition analyzes your pronunciation in real-time.",
                icon = "🎙️",
                color = "#10B981"
            ),
            SpeakingFeature(
                id = "multi_language",
                title = "Multi-Language Support",
                description = "Practice pronunciation in French, German, Korean, Mandarin, and Spanish.",
                icon = "🌍",
                color = "#F59E0B"
            ),
            SpeakingFeature(
                id = "progress_tracking",
                title = "Progress Tracking",
                description = "Track your speaking improvement with detailed analytics and scores.",
                icon = "📊",
                color = "#3B82F6"
            ),
            SpeakingFeature(
                id = "recording_playback",
                title = "Recording & Playback",
                description = "Record your sessions and play them back to hear your pronunciation.",
                icon = "▶️",
                color = "#EF4444"
            ),
            SpeakingFeature(
                id = "instant_feedback",
                title = "Instant Feedback",
                description = "Get immediate scores on pronunciation, clarity, and fluency.",
                icon = "⚡",
                color = "#8B5CF6"
            )
        )
    }
}

/**
 * Available languages for practice
 */
enum class PracticeLanguage(
    val displayName: String,
    val flag: String,
    val description: String
) {
    FRENCH(
        displayName = "French",
        flag = "🇫🇷",
        description = "Practice French pronunciation"
    ),
    GERMAN(
        displayName = "German",
        flag = "🇩🇪",
        description = "Practice German pronunciation"
    ),
    HANGEUL(
        displayName = "Korean (Hangeul)",
        flag = "🇰🇷",
        description = "Practice Korean pronunciation"
    ),
    MANDARIN(
        displayName = "Mandarin Chinese",
        flag = "🇨🇳",
        description = "Practice Mandarin pronunciation with tones"
    ),
    SPANISH(
        displayName = "Spanish",
        flag = "🇪🇸",
        description = "Practice Spanish pronunciation"
    )
}

/**
 * Feedback data from pronunciation analysis
 */
data class PracticeFeedback(
    val overallScore: Int, // 0-100
    val pronunciationScore: Int, // 0-100
    val clarityScore: Int, // 0-100
    val fluencyScore: Int, // 0-100
    val messages: List<String>,
    val suggestions: List<String>
)

/**
 * Speaking feature for empty state display
 */
data class SpeakingFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String
)