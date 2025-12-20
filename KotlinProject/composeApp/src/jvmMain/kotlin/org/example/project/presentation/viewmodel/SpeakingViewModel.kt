package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jan.supabase.gotrue.auth
import org.example.project.core.audio.AudioPlayer
import org.example.project.core.audio.AudioRecorder
import org.example.project.core.audio.AudioTrimmer
import org.example.project.core.audio.AudioUploadService
import org.example.project.core.pronunciation.PronunciationService
import org.example.project.core.config.SupabaseConfig
import org.example.project.data.repository.VocabularyRepository
import org.example.project.data.repository.VocabularyRepositoryImpl
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.VocabularyWord
import java.io.File
import java.util.UUID
import javax.sound.sampled.AudioSystem


class SpeakingViewModel : ViewModel() {

    private val pronunciationService = PronunciationService()
    private val audioRecorder = AudioRecorder()
    private val audioUploadService = AudioUploadService()
    private val audioPlayer = AudioPlayer()
    private val audioTrimmer = AudioTrimmer()
    private val vocabularyRepository: VocabularyRepository = VocabularyRepositoryImpl()

    
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

    private val _recordingDuration = mutableStateOf(0f) 
    val recordingDuration: State<Float> = _recordingDuration

    private val _speakingFeatures = mutableStateOf(getSpeakingFeatures())
    val speakingFeatures: State<List<SpeakingFeature>> = _speakingFeatures

    // Reference audio URL from backend
    private var _referenceAudioUrl: String? = null
    private var _recordedAudioFile: File? = null
    
    // Loading state for audio generation
    private val _isGeneratingAudio = mutableStateOf(false)
    val isGeneratingAudio: State<Boolean> = _isGeneratingAudio
    
    // Temporary directory for audio files
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "wordbridge_audio")


    fun startPracticeSession(word: VocabularyWord) {
        _currentWord.value = word
        _showLanguageDialog.value = true
        resetSession()
    }

    /**
     * Start a practice session using the app's selected learning language
     * (from Lessons tab / LessonLanguage).
     * This bypasses the language selection dialog and directly sets the practice language.
     */
    fun startPracticeSessionForLessonLanguage(word: VocabularyWord, lessonLanguage: LessonLanguage) {
        _currentWord.value = word
        _selectedLanguage.value =
            when (lessonLanguage) {
                LessonLanguage.KOREAN -> PracticeLanguage.HANGEUL
                LessonLanguage.CHINESE -> PracticeLanguage.MANDARIN
                LessonLanguage.FRENCH -> PracticeLanguage.FRENCH
                LessonLanguage.GERMAN -> PracticeLanguage.GERMAN
                LessonLanguage.SPANISH -> PracticeLanguage.SPANISH
            }
        _showLanguageDialog.value = false
        resetSession()
        
        // Generate reference audio
        viewModelScope.launch {
            generateReferenceAudio(word.word, lessonLanguage.code)
        }
    }
    
    /**
     * Generate reference audio for the word
     */
    private suspend fun generateReferenceAudio(word: String, languageCode: String) {
        _isGeneratingAudio.value = true
        try {
            val wordId = _currentWord.value?.id
            val currentWord = _currentWord.value
            
            // Only generate if audioUrl is missing
            if (currentWord?.audioUrl.isNullOrBlank()) {
                println("Generating reference audio for '$word' in $languageCode (wordId: $wordId)")
                val result = pronunciationService.generateReferenceAudio(word, languageCode, wordId)
                result.onSuccess { response ->
                    _referenceAudioUrl = response.referenceAudioUrl
                    println("Reference audio URL: ${response.referenceAudioUrl}")
                    
                    // Update the current word with the new audio URL
                    if (currentWord != null && response.referenceAudioUrl.isNotBlank()) {
                        _currentWord.value = currentWord.copy(audioUrl = response.referenceAudioUrl)
                        println("Updated current word with audio URL: ${response.referenceAudioUrl}")
                    }
                }.onFailure { error ->
                    println("Failed to generate reference audio: ${error.message}")
                    error.printStackTrace()
                }
            } else {
                // Use existing audio URL
                _referenceAudioUrl = currentWord?.audioUrl
                println("Using existing audio URL: ${currentWord?.audioUrl}")
            }
        } catch (e: Exception) {
            println("Error generating reference audio: ${e.message}")
            e.printStackTrace()
        } finally {
            _isGeneratingAudio.value = false
        }
    }
    
    /**
     * Manually generate reference audio (called from UI button)
     */
    fun generateReferenceAudioManually() {
        val currentWord = _currentWord.value
        val selectedLanguage = _selectedLanguage.value
        
        if (currentWord == null || selectedLanguage == null) {
            println("Cannot generate audio: missing word or language")
            return
        }
        
        val languageCode = getLanguageCode(selectedLanguage)
        viewModelScope.launch {
            generateReferenceAudio(currentWord.word, languageCode)
        }
    }
    
    /**
     * Set the recorded audio file (called from UI when recording is saved)
     */
    fun setRecordedAudioFile(file: File) {
        _recordedAudioFile = file
    }

    fun onLanguageSelected(language: PracticeLanguage) {
        _selectedLanguage.value = language
        _showLanguageDialog.value = false
    }

    fun showLanguageSelection() {
        _showLanguageDialog.value = true
    }

    fun hideLanguageDialog() {
        _showLanguageDialog.value = false
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _isRecording.value = true
        _hasRecording.value = false
        _feedback.value = null
        _recordingDuration.value = 0f
        _recordedAudioFile = null

        viewModelScope.launch {
            // Start actual audio recording
            val startResult = audioRecorder.startRecording()
            if (startResult.isFailure) {
                println("Failed to start audio recording: ${startResult.exceptionOrNull()?.message}")
                _isRecording.value = false
                return@launch
            }

            println("Started recording...")

            // Update duration while recording
            val startTime = System.currentTimeMillis()
            while (_isRecording.value && audioRecorder.isRecording()) {
                delay(100)
                _recordingDuration.value = ((System.currentTimeMillis() - startTime) / 1000f)
            }
        }
    }

    private fun stopRecording() {
        _isRecording.value = false

        viewModelScope.launch {
            try {
                // Ensure temp directory exists
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }

                // Create output file
                val outputFile = File(tempDir, "recording_${UUID.randomUUID()}.wav")
                
                println("Stopping recording and saving to: ${outputFile.absolutePath}")

                // Stop recording and save to file
                val result = audioRecorder.stopRecordingAndSave(outputFile)
                
                result.onSuccess { file ->
                    // Verify file exists and has content
                    if (!file.exists() || file.length() == 0L) {
                        println("Warning: Audio file is empty or doesn't exist: ${file.absolutePath}")
                        _hasRecording.value = false
                        analyzeRecording()
                        return@launch
                    }
                    
                    println("Recording saved successfully. Duration: ${_recordingDuration.value}s")
                    println("Original audio file size: ${file.length()} bytes, path: ${file.absolutePath}")
                    
                    // Trim silence from the recording
                    val trimmedFile = File(tempDir, "recording_trimmed_${UUID.randomUUID()}.wav")
                    val trimResult = audioTrimmer.trimSilence(file, trimmedFile)
                    
                    val finalFile = if (trimResult.isSuccess) {
                        println("Silence trimmed successfully")
                        // Delete original file and use trimmed version
                        file.delete()
                        trimmedFile
                    } else {
                        println("Failed to trim silence: ${trimResult.exceptionOrNull()?.message}, using original file")
                        file
                    }
                    
                    // Update duration based on trimmed audio
                    try {
                        val audioInputStream = AudioSystem.getAudioInputStream(finalFile)
                        val frameLength = audioInputStream.frameLength
                        val frameRate = audioInputStream.format.frameRate
                        val duration = if (frameLength > 0 && frameRate > 0) {
                            (frameLength / frameRate).toFloat()
                        } else {
                            _recordingDuration.value
                        }
                        audioInputStream.close()
                        _recordingDuration.value = duration
                        println("Updated duration after trimming: ${duration}s")
                    } catch (e: Exception) {
                        println("Could not calculate trimmed duration: ${e.message}")
                    }
                    
                    _recordedAudioFile = finalFile
                    _hasRecording.value = true
                    println("Final audio file size: ${finalFile.length()} bytes, path: ${finalFile.absolutePath}")
                    
                    // Upload to Supabase and save URL (non-blocking, continues even if upload fails)
                    viewModelScope.launch {
                        uploadAudioToSupabase(finalFile)
                    }
                    
                    // Analyze the recording (don't wait for upload)
                    analyzeRecording()
                }.onFailure { error ->
                    println("Failed to save recording: ${error.message}")
                    _hasRecording.value = false
                    // Still try to analyze (will use mock feedback)
                    analyzeRecording()
                }
            } catch (e: Exception) {
                println("Error stopping recording: ${e.message}")
                _hasRecording.value = false
                analyzeRecording()
            }
        }
    }
    
    /**
     * Upload audio to Supabase Storage and save URL to database
     */
    private suspend fun uploadAudioToSupabase(audioFile: File) {
        val currentWord = _currentWord.value
        val user = SupabaseConfig.client.auth.currentUserOrNull()
        val userId = user?.id as? String
        
        if (currentWord == null || userId == null) {
            println("Cannot upload audio: missing word or user ID")
            return
        }
        
        try {
            println("Uploading audio to Supabase Storage for word: ${currentWord.word}")
            
            val uploadResult = audioUploadService.uploadPronunciationAudio(
                audioFile = audioFile,
                userId = userId,
                wordId = currentWord.id
            )
            
            uploadResult.onSuccess { audioUrl ->
                println("Audio uploaded successfully: $audioUrl")
                
                // Save URL to user_vocabulary table
                val saveResult = vocabularyRepository.updateUserAudioUrl(
                    userId = userId,
                    wordId = currentWord.id,
                    audioUrl = audioUrl
                )
                
                saveResult.onSuccess {
                    println("Audio URL saved to database successfully")
                }.onFailure { error ->
                    println("Failed to save audio URL to database: ${error.message}")
                }
            }.onFailure { error ->
                println("Failed to upload audio to Supabase: ${error.message}")
            }
        } catch (e: Exception) {
            println("Error uploading audio to Supabase: ${e.message}")
            e.printStackTrace()
        }
    }

    fun playRecording() {
        if (!_hasRecording.value || _isPlayingRecording.value) return
        
        val audioFile = _recordedAudioFile
        if (audioFile == null || !audioFile.exists()) {
            println("No recorded audio file available to play")
            return
        }

        _isPlayingRecording.value = true

        viewModelScope.launch {
            try {
                println("Playing back recording from: ${audioFile.absolutePath}")
                
                // Set callback to be notified when playback finishes
                audioPlayer.setPlaybackFinishedCallback {
                    _isPlayingRecording.value = false
                    println("Recording playback finished (via callback)")
                }
                
                // Play the audio file using AudioPlayer
                val result = audioPlayer.playAudioFromFile(audioFile.absolutePath)
                
                result.onSuccess {
                    println("Recording playback started successfully")
                    // The AudioPlayer will call the callback when playback finishes
                }.onFailure { error ->
                    println("Failed to play recording: ${error.message}")
                    error.printStackTrace()
                    _isPlayingRecording.value = false
                    audioPlayer.setPlaybackFinishedCallback(null)
                }
            } catch (e: Exception) {
                println("Error playing recording: ${e.message}")
                e.printStackTrace()
                _isPlayingRecording.value = false
                audioPlayer.setPlaybackFinishedCallback(null)
            }
        }
    }
    
    /**
     * Play reference audio from URL
     */
    fun playReferenceAudio(audioUrl: String) {
        if (audioUrl.isBlank()) {
            println("No audio URL provided")
            return
        }
        
        viewModelScope.launch {
            println("Playing reference audio from: $audioUrl")
            val result = audioPlayer.playAudioFromUrl(audioUrl)
            result.onFailure { error ->
                println("Failed to play reference audio: ${error.message}")
            }
        }
    }

    private fun analyzeRecording() {
        _isAnalyzing.value = true

        viewModelScope.launch {
            val word = _currentWord.value?.word ?: ""
            val language = _selectedLanguage.value
            val audioFile = _recordedAudioFile
            
            if (word.isBlank() || language == null) {
                println("Cannot analyze: missing word or language")
                _isAnalyzing.value = false
                return@launch
            }
            
            // If we have a recorded audio file, use real pronunciation comparison
            if (audioFile != null && audioFile.exists() && _referenceAudioUrl != null) {
                try {
                    val languageCode = getLanguageCode(language)
                    println("Comparing pronunciation for '$word' in $languageCode")
                    
                    val result = pronunciationService.comparePronunciation(
                        word = word,
                        languageCode = languageCode,
                        userAudioFile = audioFile,
                        referenceAudioUrl = _referenceAudioUrl
                    )
                    
                    result.onSuccess { comparison ->
                        _feedback.value = PracticeFeedback(
                            overallScore = comparison.overallScore,
                            pronunciationScore = comparison.pronunciationScore,
                            clarityScore = comparison.clarityScore,
                            fluencyScore = comparison.fluencyScore,
                            messages = comparison.feedbackMessages,
                            suggestions = comparison.suggestions
                        )
                        println("Pronunciation analysis complete: ${comparison.overallScore}%")
                    }.onFailure { error ->
                        println("Failed to compare pronunciation: ${error.message}")
                        // Fall back to mock feedback
                        _feedback.value = generateMockFeedback(
                            word = word,
                            language = language,
                            recordingDuration = _recordingDuration.value
                        )
                    }
                } catch (e: Exception) {
                    println("Error during pronunciation analysis: ${e.message}")
                    // Fall back to mock feedback
                    _feedback.value = generateMockFeedback(
                        word = word,
                        language = language,
                        recordingDuration = _recordingDuration.value
                    )
                }
            } else {
                // No audio file yet, use mock feedback
                println("No audio file available, using mock feedback")
                delay(2000) // Simulate analysis delay
                _feedback.value = generateMockFeedback(
                    word = word,
                    language = language,
                    recordingDuration = _recordingDuration.value
                )
            }
            
            _isAnalyzing.value = false
        }
    }
    
    /**
     * Convert PracticeLanguage to language code string
     */
    private fun getLanguageCode(language: PracticeLanguage): String {
        return when (language) {
            PracticeLanguage.HANGEUL -> "ko"
            PracticeLanguage.MANDARIN -> "zh"
            PracticeLanguage.FRENCH -> "fr"
            PracticeLanguage.GERMAN -> "de"
            PracticeLanguage.SPANISH -> "es"
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
        
        val pronunciationScore = (75..95).random()
        val clarityScore = (70..95).random()
        val fluencyScore = (65..90).random()
        val overallScore = (pronunciationScore + clarityScore + fluencyScore) / 3

        
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

    fun tryAgain() {
        resetSession()
    }

    private fun resetSession() {
        _isRecording.value = false
        _hasRecording.value = false
        _isPlayingRecording.value = false
        _feedback.value = null
        _isAnalyzing.value = false
        _recordingDuration.value = 0f
    }

    fun completePractice() {
        
        
        resetSession()
        _currentWord.value = null
        _selectedLanguage.value = null
        _referenceAudioUrl = null
        _recordedAudioFile = null
    }
    
    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        pronunciationService.close()
        
        // Clean up temp audio files
        try {
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            println("Error cleaning up temp audio files: ${e.message}")
        }
    }

    fun onStartFirstPracticeClicked() {
        
        println("Start first practice clicked")
    }

    fun onExploreExercisesClicked() {
        
        println("Explore exercises clicked")
    }
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

data class PracticeFeedback(
    val overallScore: Int, 
    val pronunciationScore: Int, 
    val clarityScore: Int, 
    val fluencyScore: Int, 
    val messages: List<String>,
    val suggestions: List<String>
)

data class SpeakingFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String
)