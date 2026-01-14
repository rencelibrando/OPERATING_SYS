package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.project.core.ai.AgentApiService
import org.example.project.core.ai.VoiceApiService
import org.example.project.core.audio.AudioPlayer
import org.example.project.core.audio.VoiceRecorder
import org.example.project.core.config.SupabaseConfig
import org.example.project.data.repository.PracticeRecordingRepository
import org.example.project.data.repository.PracticeRecordingRepositoryImpl
import org.example.project.data.repository.VocabularyRepository
import org.example.project.data.repository.VocabularyRepositoryImpl
import org.example.project.domain.model.PracticeFeedback
import org.example.project.domain.model.PracticeLanguage
import org.example.project.domain.model.SpeakingFeature
import org.example.project.domain.model.VocabularyWord
import org.example.project.models.ConversationTurnUI
import org.example.project.presentation.viewmodel.speaking.BoundedAudioBuffer
import org.example.project.presentation.viewmodel.speaking.Debouncer
import org.example.project.presentation.viewmodel.speaking.SpeakingConfig
import org.example.project.presentation.viewmodel.speaking.SpeakingLogger
import org.example.project.presentation.viewmodel.speaking.TempFileManager
import org.example.project.presentation.viewmodel.speaking.calculateAudioLevel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class SpeakingViewModel(private val userId: String = "current_user") : ViewModel() {
    private val voiceApiService = VoiceApiService()
    private val agentService = AgentApiService()
    private val voiceRecorder = VoiceRecorder()
    private val audioPlayer = AudioPlayer()
    private val vocabularyRepository: VocabularyRepository = VocabularyRepositoryImpl()
    private val practiceRecordingRepository: PracticeRecordingRepository = PracticeRecordingRepositoryImpl()
    private var currentRecordingFile: File? = null

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

    private val _speakingFeatures = mutableStateOf(emptyList<SpeakingFeature>())
    val speakingFeatures: State<List<SpeakingFeature>> = _speakingFeatures

    private val _showVoiceTutorSelection = mutableStateOf(false)
    val showVoiceTutorSelection: State<Boolean> = _showVoiceTutorSelection
    
    // Vocabulary word practice state
    private val _vocabularyWordsForPractice = mutableStateOf<List<VocabularyWord>>(emptyList())
    val vocabularyWordsForPractice: State<List<VocabularyWord>> = _vocabularyWordsForPractice
    
    private val _showWordPractice = mutableStateOf(false)
    val showWordPractice: State<Boolean> = _showWordPractice

    private val _voiceTutorLanguage = mutableStateOf<PracticeLanguage?>(null)
    val voiceTutorLanguage: State<PracticeLanguage?> = _voiceTutorLanguage

    private val _voiceTutorLevel = mutableStateOf<String?>(null)
    val voiceTutorLevel: State<String?> = _voiceTutorLevel

    private val _voiceTutorScenario = mutableStateOf<String?>(null)
    val voiceTutorScenario: State<String?> = _voiceTutorScenario

    private val _currentPrompt = mutableStateOf<String?>(null)
    val currentPrompt: State<String?> = _currentPrompt

    // Lesson integration - removed unused _lessonScenarios property

    // Conversation mode state
    private val _isConversationMode = mutableStateOf(false)
    val isConversationMode: State<Boolean> = _isConversationMode

    // Track if the agent is actually connected and ready
    private val _isConversationActive = mutableStateOf(false)
    val isConversationActive: State<Boolean> = _isConversationActive

    // Use mutableStateListOf for efficient in-place updates without full list recomposition
    private val _conversationTurns = mutableStateListOf<ConversationTurnUI>()
    val conversationTurns: SnapshotStateList<ConversationTurnUI> get() = _conversationTurns

    // O(1) lookup maps for conversation turn management (prevents main thread blocking)
    private val turnIdToIndex = mutableMapOf<String, Int>()
    private val existingTurnIds = mutableSetOf<String>()

    private val _isAgentSpeaking = mutableStateOf(false)
    val isAgentSpeaking: State<Boolean> = _isAgentSpeaking

    private val _isAgentThinking = mutableStateOf(false)
    val isAgentThinking: State<Boolean> = _isAgentThinking

    private val _conversationError = mutableStateOf<String?>(null)
    val conversationError: State<String?> = _conversationError

    // Session recording for playback (optimized with bounded buffer)
    private var currentSessionId: String? = null
    private var sessionStartTime: Long = 0L
    private val sessionAudioChunks =
        BoundedAudioBuffer(
            maxChunks = SpeakingConfig.MAX_AUDIO_CHUNKS,
            maxBytes = SpeakingConfig.MAX_AUDIO_BYTES,
        )

    // Dedicated user audio buffer for reliable capture during conversation
    private val userAudioChunks =
        BoundedAudioBuffer(
            maxChunks = SpeakingConfig.MAX_AUDIO_CHUNKS,
            maxBytes = SpeakingConfig.MAX_AUDIO_BYTES,
        )

    // Push-to-talk microphone state
    private val _isConversationRecording = mutableStateOf(false)
    val isConversationRecording: State<Boolean> = _isConversationRecording

    // Audio level for waveform visualization
    private val _audioLevel = mutableStateOf(0f)
    val audioLevel: State<Float> = _audioLevel

    // Atomic guards for thread-safe operations
    private val isStartingConversation = AtomicBoolean(false)
    private val isStoppingConversation = AtomicBoolean(false)
    private val isRecordingLocked = AtomicBoolean(false)
    private val isRetryingConnection = AtomicBoolean(false)

    // Mutex for conversation operations
    private val conversationMutex = Mutex()

    // Debouncing for API calls
    private val conversationDebouncer = Debouncer(SpeakingConfig.CONVERSATION_DEBOUNCE_MS)

    // Recording job reference for cancellation
    private var recordingStreamJob: Job? = null

    // Structured coroutine scope for recording operations (cancellable)
    private val recordingJob = SupervisorJob()

    // Audio processing channels for background offloading (separate for agent and user)
    private val agentAudioChannel = Channel<ByteArray>(Channel.BUFFERED)
    private val userAudioChannel = Channel<ByteArray>(Channel.BUFFERED)

    // Temp file manager for automatic cleanup
    private val tempFileManager = TempFileManager()

    init {
        _speakingFeatures.value = getSpeakingFeatures()

        // Start audio processing workers for background offloading
        startAgentAudioProcessingWorker()
        startUserAudioProcessingWorker()
        
        // Load vocabulary words for practice
        loadVocabularyWordsForPractice()
    }
    
    /**
     * Load vocabulary words from the user's vocabulary bank for practice
     */
    private fun loadVocabularyWordsForPractice() {
        viewModelScope.launch {
            try {
                vocabularyRepository.getAllVocabularyWords().onSuccess { words ->
                    _vocabularyWordsForPractice.value = words
                    SpeakingLogger.info("WordPractice") { "Loaded ${words.size} vocabulary words for practice" }
                }.onFailure { error ->
                    SpeakingLogger.error("WordPractice") { "Failed to load vocabulary words: ${error.message}" }
                }
            } catch (e: Exception) {
                SpeakingLogger.error("WordPractice") { "Error loading vocabulary words: ${e.message}" }
            }
        }
    }
    
    /**
     * Refresh vocabulary words for practice
     */
    fun refreshVocabularyWords() {
        loadVocabularyWordsForPractice()
    }

    /**
     * Background worker for processing agent audio chunks without blocking the main thread.
     */
    private fun startAgentAudioProcessingWorker() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                for (audioData in agentAudioChannel) {
                    try {
                        // Add to the combined session buffer (thread-safe)
                        sessionAudioChunks.add(audioData)

                        // Calculate audio level for visualization
                        val level = calculateAudioLevel(audioData)
                        withContext(Dispatchers.Main) {
                            _audioLevel.value = level
                        }

                        // Log periodically
                        if (sessionAudioChunks.size() % SpeakingConfig.AUDIO_CHUNK_LOG_INTERVAL == 0) {
                            SpeakingLogger.debug { "Agent audio buffer: ${sessionAudioChunks.stats()}" }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SpeakingLogger.error("AgentAudioWorker") { "Error processing chunk: ${e.message}" }
                    }
                }
            } catch (e: CancellationException) {
                SpeakingLogger.debug { "Agent audio worker cancelled" }
            }
        }
    }

    /**
     * Background worker for processing user audio chunks without blocking capture.
     * Ensures user audio is reliably captured even during agent playback.
     */
    private fun startUserAudioProcessingWorker() {
        viewModelScope.launch(Dispatchers.Default) {
            SpeakingLogger.info("UserAudioWorker") { "Started - waiting for audio chunks" }
            try {
                var chunkCount = 0L
                var totalBytes = 0L
                for (audioData in userAudioChannel) {
                    try {
                        // Add to both user-specific buffer and combined session buffer
                        userAudioChunks.add(audioData)
                        sessionAudioChunks.add(audioData)
                        chunkCount++
                        totalBytes += audioData.size

                        // Log the first chunk to confirm reception
                        if (chunkCount == 1L) {
                            SpeakingLogger.info("UserAudioWorker") { "First user audio chunk received: ${audioData.size} bytes" }
                        }

                        // Log periodically to track capture health
                        if (chunkCount % 100 == 0L) {
                            SpeakingLogger.debug { "User audio captured: $chunkCount chunks, ${totalBytes / 1000}KB, buffer: ${userAudioChunks.stats()}" }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SpeakingLogger.error("UserAudioWorker") { "Error processing chunk: ${e.message}" }
                    }
                }
                SpeakingLogger.info("UserAudioWorker") { "Channel closed - total captured: $chunkCount chunks, ${totalBytes / 1000}KB" }
            } catch (e: CancellationException) {
                SpeakingLogger.debug { "User audio worker cancelled" }
            }
        }
    }

    fun startPracticeSessionForLessonLanguage(
        word: VocabularyWord,
        language: String,
    ) {
        _currentWord.value = word
        // Map lesson language to PracticeLanguage
        val practiceLanguage =
            when (language.lowercase()) {
                "ko", "korean" -> PracticeLanguage.HANGEUL
                "zh", "chinese" -> PracticeLanguage.MANDARIN
                "es", "spanish" -> PracticeLanguage.SPANISH
                "fr", "french" -> PracticeLanguage.FRENCH
                "de", "german" -> PracticeLanguage.GERMAN
                else -> PracticeLanguage.ENGLISH
            }
        _selectedLanguage.value = practiceLanguage
        resetSession()
    }

    fun onLanguageSelected(language: PracticeLanguage) {
        _selectedLanguage.value = language
        _showLanguageDialog.value = false
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

        viewModelScope.launch {
            // Start actual recording
            try {
                val result = voiceRecorder.startRecording()
                if (result.isSuccess) {
                    SpeakingLogger.debug { "Started recording" }

                    while (_isRecording.value) {
                        delay(100)
                        _recordingDuration.value += 0.1f
                    }
                } else {
                    SpeakingLogger.error("Recording") { "Failed to start: ${result.exceptionOrNull()?.message}" }
                    _isRecording.value = false
                }
            } catch (e: Exception) {
                SpeakingLogger.error("Recording") { "Error starting: ${e.message}" }
                _isRecording.value = false
            }
        }
    }

    private fun stopRecording() {
        _isRecording.value = false

        viewModelScope.launch {
            try {
                // Stop actual recording and get a file
                val outputFile = File.createTempFile("recording_", ".wav")
                val result = voiceRecorder.stopRecording(outputFile)
                if (result.isSuccess) {
                    val filePath = result.getOrNull()
                    currentRecordingFile = if (filePath != null) File(filePath) else null
                    _hasRecording.value = currentRecordingFile != null

                    SpeakingLogger.debug { "Recording stopped - duration: ${_recordingDuration.value}s" }

                    if (currentRecordingFile != null) {
                        analyzeRecording()
                    }
                } else {
                    SpeakingLogger.error("Recording") { "Failed to stop: ${result.exceptionOrNull()?.message}" }
                }
            } catch (e: Exception) {
                SpeakingLogger.error("Recording") { "Error stopping: ${e.message}" }
            }
        }
    }

    fun playRecording() {
        if (!_hasRecording.value || _isPlayingRecording.value || currentRecordingFile == null) return

        _isPlayingRecording.value = true

        viewModelScope.launch {
            try {
                SpeakingLogger.debug { "Playing recording" }
                audioPlayer.playFile(currentRecordingFile!!) {
                    _isPlayingRecording.value = false
                    SpeakingLogger.debug { "Playback finished" }
                }
            } catch (e: Exception) {
                SpeakingLogger.error("Playback") { "Error: ${e.message}" }
                _isPlayingRecording.value = false
            }
        }
    }

    private fun analyzeRecording() {
        viewModelScope.launch {
            try {
                // Validation: Check if a recording file exists
                if (currentRecordingFile == null) {
                    SpeakingLogger.warn("Analysis") { "No recording file available" }
                    _feedback.value =
                        PracticeFeedback(
                            overallScore = 0,
                            pronunciationScore = 0,
                            clarityScore = 0,
                            fluencyScore = 0,
                            messages = listOf("No audio recording detected. Please record your voice first."),
                            suggestions = listOf("Press the microphone button to start recording"),
                        )
                    return@launch
                }

                // Validation: Check if recording duration is enough (at least 0.5 seconds)
                if (_recordingDuration.value < 0.5f) {
                    SpeakingLogger.warn("Analysis") { "Recording too short: ${_recordingDuration.value}s" }
                    _feedback.value =
                        PracticeFeedback(
                            overallScore = 0,
                            pronunciationScore = 0,
                            clarityScore = 0,
                            fluencyScore = 0,
                            messages = listOf("Recording is too short. Please record for at least 1 second."),
                            suggestions = listOf("Press and hold the microphone button while speaking"),
                        )
                    return@launch
                }

                // Validation: Check if a file exists and has content
                if (!currentRecordingFile!!.exists() || currentRecordingFile!!.length() < 1000) {
                    SpeakingLogger.warn("Analysis") { "Invalid file: size=${currentRecordingFile!!.length()} bytes" }
                    _feedback.value =
                        PracticeFeedback(
                            overallScore = 0,
                            pronunciationScore = 0,
                            clarityScore = 0,
                            fluencyScore = 0,
                            messages = listOf("Recording file is invalid or empty. Please try recording again."),
                            suggestions = listOf("Make sure to speak clearly into your microphone"),
                        )
                    return@launch
                }

                // All validations passed - now call local Whisper + SpeechBrain analysis
                SpeakingLogger.debug { "Recording validated: ${_recordingDuration.value}s, ${currentRecordingFile!!.length()} bytes" }
                SpeakingLogger.debug { "Calling local Whisper + SpeechBrain for voice analysis" }
                _isAnalyzing.value = true

                // Map language for Whisper
                val languageCode =
                    when (_selectedLanguage.value) {
                        PracticeLanguage.ENGLISH -> "en"
                        PracticeLanguage.FRENCH -> "fr"
                        PracticeLanguage.GERMAN -> "de"
                        PracticeLanguage.HANGEUL -> "ko"
                        PracticeLanguage.MANDARIN -> "zh"
                        PracticeLanguage.SPANISH -> "es"
                        null -> "en"
                    }

                val expectedText = _currentPrompt.value ?: _currentWord.value?.word ?: ""
                val level = _voiceTutorLevel.value ?: "intermediate"
                val scenario = _voiceTutorScenario.value ?: "daily_conversation"

                // Use local voice analysis (Whisper STT + SpeechBrain speaker analysis)
                val analysisResult =
                    voiceApiService.analyzeVoiceLocal(
                        audioFile = currentRecordingFile!!,
                        language = languageCode,
                        expectedText = expectedText,
                        level = level,
                        scenario = scenario,
                        userId = userId,
                    )

                if (analysisResult.isSuccess) {
                    val analysis = analysisResult.getOrNull()
                    if (analysis != null && analysis.success) {
                        SpeakingLogger.debug { "Transcription: ${analysis.transcript}" }
                        SpeakingLogger.debug { "Confidence: ${analysis.confidence}" }
                        SpeakingLogger.debug { "Scores: ${analysis.scores}" }

                        // Validate transcription has actual content
                        if (analysis.transcript.isBlank()) {
                            SpeakingLogger.warn("Analysis") { "Empty transcription - no speech detected" }
                            _feedback.value =
                                PracticeFeedback(
                                    overallScore = 0,
                                    pronunciationScore = 0,
                                    clarityScore = 0,
                                    fluencyScore = 0,
                                    messages =
                                        listOf(
                                            "Could not detect clear speech in the recording.",
                                            "The audio may be too quiet or contain background noise.",
                                        ),
                                    suggestions =
                                        listOf(
                                            "Try speaking louder and closer to the microphone",
                                            "Ensure you're in a quiet environment",
                                            "Check your microphone settings",
                                        ),
                                )
                            _isAnalyzing.value = false
                            return@launch
                        }

                        // Convert local analysis response to PracticeFeedback
                        val practiceFeedback =
                            PracticeFeedback(
                                overallScore = analysis.overall_score.toInt(),
                                pronunciationScore = analysis.scores["pronunciation"]?.toInt() ?: 0,
                                clarityScore = analysis.scores["clarity"]?.toInt() ?: 0,
                                fluencyScore = analysis.scores["fluency"]?.toInt() ?: 0,
                                messages = analysis.feedback_messages,
                                suggestions = analysis.suggestions,
                            )

                        _feedback.value = practiceFeedback
                        SpeakingLogger.info("Analysis") { "Complete - score: ${practiceFeedback.overallScore}%" }

                        // Save session with local analysis scores
                        saveSessionToDatabase(analysis.transcript, analysis.scores, languageCode)
                    } else {
                        SpeakingLogger.error("Analysis") { "Local analysis failed: ${analysis?.error}" }
                        _feedback.value = generateErrorFeedback(analysis?.error ?: "Analysis failed")
                    }
                } else {
                    SpeakingLogger.error("Analysis") { "Failed to analyze audio locally" }
                    _feedback.value = generateErrorFeedback("Failed to analyze audio")
                }
            } catch (e: Exception) {
                SpeakingLogger.error("Analysis") { "Error: ${e.message}" }
                _feedback.value = generateErrorFeedback("Error: ${e.message}")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun generateErrorFeedback(error: String): PracticeFeedback {
        return PracticeFeedback(
            overallScore = 0,
            pronunciationScore = 0,
            clarityScore = 0,
            fluencyScore = 0,
            messages = listOf(error),
            suggestions = listOf("Please try recording again"),
        )
    }

    // Store the uploaded audio URL for the current session
    private var currentRecordingUrl: String? = null
    
    /**
     * Upload recording to Supabase storage
     */
    private suspend fun uploadRecordingToSupabase(audioFile: File): String? {
        return try {
            val timestamp = System.currentTimeMillis()
            val fileName = "recordings/${userId}/${timestamp}_recording.wav"
            
            SpeakingLogger.debug { "Uploading recording to Supabase: $fileName" }
            
            val storage = SupabaseConfig.client.storage.from("voice-recordings")
            val bytes = audioFile.readBytes()
            
            storage.upload(fileName, bytes, upsert = true)
            
            // Get public URL
            val publicUrl = storage.publicUrl(fileName)
            SpeakingLogger.info("Upload") { "Recording uploaded: $publicUrl" }
            
            currentRecordingUrl = publicUrl
            publicUrl
        } catch (e: Exception) {
            SpeakingLogger.error("Upload") { "Failed to upload recording: ${e.message}" }
            null
        }
    }
    
    private fun saveSessionToDatabase(
        transcript: String,
        scores: Map<String, Float>,
        languageCode: String,
    ) {
        viewModelScope.launch {
            try {
                val currentWord = _currentWord.value
                val currentFile = currentRecordingFile
                val expectedText = _currentPrompt.value ?: currentWord?.word ?: ""
                val isVocabularyPractice = currentWord != null
                val isPhraseOrScenarioPractice = _voiceTutorScenario.value != null && currentWord == null

                SpeakingLogger.info("SaveSession") { 
                    "currentWord=${currentWord?.word}, isVocabPractice=$isVocabularyPractice, " +
                    "isPhraseOrScenario=$isPhraseOrScenarioPractice, hasFile=${currentFile != null}"
                }

                if (isVocabularyPractice && currentFile != null) {
                    // Save vocabulary practice recording and AI feedback
                    saveVocabularyPracticeSession(
                        word = currentWord,
                        audioFile = currentFile,
                        transcript = transcript,
                        scores = scores,
                        languageCode = languageCode,
                        expectedText = expectedText
                    )
                } else if (isPhraseOrScenarioPractice && currentFile != null) {
                    // Save phrase practice recording and AI feedback
                    savePhrasePracticeSession(
                        audioFile = currentFile,
                        transcript = transcript,
                        scores = scores,
                        languageCode = languageCode,
                        expectedPhrase = expectedText
                    )
                } else {
                    // Fallback to legacy voice session save
                    saveLegacyVoiceSession(transcript, scores, languageCode)
                }
            } catch (e: Exception) {
                SpeakingLogger.error("Session") { "Error saving: ${e.message}" }
            }
        }
    }

    private suspend fun saveVocabularyPracticeSession(
        word: VocabularyWord,
        audioFile: File,
        transcript: String,
        scores: Map<String, Float>,
        languageCode: String,
        expectedText: String
    ) {
        try {
            SpeakingLogger.info("VocabPractice") { "Starting save for word: ${word.word} (${word.id})" }
            
            // 1. Upload recording and save to vocabulary_practice_recordings
            val recordingResult = practiceRecordingRepository.uploadVocabularyRecording(
                wordId = word.id,
                audioFile = audioFile,
                transcript = transcript,
                language = languageCode,
                expectedText = expectedText,
                durationSeconds = _recordingDuration.value
            )

            if (recordingResult.isSuccess) {
                val recording = recordingResult.getOrThrow()
                SpeakingLogger.info("VocabPractice") { "Recording saved: ${recording.id}" }

                // 2. Save AI feedback to vocabulary_ai_feedback
                val overallScore = scores.values.average().toInt()
                val pronunciationScore = scores["pronunciation"]?.toInt() ?: 0
                val accuracyScore = scores["accuracy"]?.toInt() ?: 0
                val fluencyScore = scores["fluency"]?.toInt() ?: 0
                val clarityScore = scores["clarity"]?.toInt() ?: 0

                val feedbackResult = practiceRecordingRepository.saveVocabularyAIFeedback(
                    recordingId = recording.id,
                    wordId = word.id,
                    overallScore = overallScore,
                    pronunciationScore = pronunciationScore,
                    accuracyScore = accuracyScore,
                    fluencyScore = fluencyScore,
                    clarityScore = clarityScore,
                    detailedAnalysis = _feedback.value?.messages?.joinToString("\n"),
                    strengths = extractStrengths(scores),
                    areasForImprovement = extractAreasForImprovement(scores),
                    suggestions = _feedback.value?.suggestions ?: emptyList(),
                    analysisProvider = "local"
                )

                if (feedbackResult.isSuccess) {
                    SpeakingLogger.info("VocabPractice") { "AI feedback saved for word: ${word.word}" }
                } else {
                    SpeakingLogger.error("VocabPractice") { "Failed to save AI feedback: ${feedbackResult.exceptionOrNull()?.message}" }
                }

                // 3. Also update user_vocabulary with the latest recording URL
                try {
                    vocabularyRepository.updateUserAudioUrl(
                        userId = userId,
                        wordId = word.id,
                        audioUrl = recording.recordingUrl
                    )
                    SpeakingLogger.debug { "Updated user_vocabulary with latest recording" }
                } catch (e: Exception) {
                    SpeakingLogger.warn("VocabPractice") { "Failed to update user_vocabulary: ${e.message}" }
                }
            } else {
                SpeakingLogger.error("VocabPractice") { "Failed to save recording: ${recordingResult.exceptionOrNull()?.message}" }
            }
        } catch (e: Exception) {
            SpeakingLogger.error("VocabPractice") { "Error saving vocabulary practice: ${e.message}" }
            throw e
        }
    }

    private suspend fun savePhrasePracticeSession(
        audioFile: File,
        transcript: String,
        scores: Map<String, Float>,
        languageCode: String,
        expectedPhrase: String
    ) {
        try {
            val scenarioType = _voiceTutorScenario.value
            val difficultyLevel = _voiceTutorLevel.value ?: "intermediate"

            // 1. Upload recording and save to phrase_practice_recordings
            val recordingResult = practiceRecordingRepository.uploadPhraseRecording(
                scenarioId = null, // No specific scenario ID for now
                audioFile = audioFile,
                transcript = transcript,
                language = languageCode,
                expectedPhrase = expectedPhrase,
                scenarioType = scenarioType,
                difficultyLevel = difficultyLevel,
                durationSeconds = _recordingDuration.value
            )

            if (recordingResult.isSuccess) {
                val recording = recordingResult.getOrThrow()
                SpeakingLogger.info("PhrasePractice") { "Recording saved: ${recording.id}" }

                // 2. Save AI feedback to phrase_ai_feedback
                val overallScore = scores.values.average().toInt()
                val pronunciationScore = scores["pronunciation"]?.toInt() ?: 0
                val grammarScore = scores["grammar"]?.toInt() ?: overallScore
                val fluencyScore = scores["fluency"]?.toInt() ?: 0
                val accuracyScore = scores["accuracy"]?.toInt() ?: 0
                val contextualScore = scores["contextual"]?.toInt() ?: overallScore

                val feedbackResult = practiceRecordingRepository.savePhraseAIFeedback(
                    recordingId = recording.id,
                    scenarioId = null,
                    overallScore = overallScore,
                    pronunciationScore = pronunciationScore,
                    grammarScore = grammarScore,
                    fluencyScore = fluencyScore,
                    accuracyScore = accuracyScore,
                    contextualScore = contextualScore,
                    detailedAnalysis = _feedback.value?.messages?.joinToString("\n"),
                    strengths = extractStrengths(scores),
                    areasForImprovement = extractAreasForImprovement(scores),
                    suggestions = _feedback.value?.suggestions ?: emptyList(),
                    analysisProvider = "local"
                )

                if (feedbackResult.isSuccess) {
                    SpeakingLogger.info("PhrasePractice") { "AI feedback saved for phrase" }
                } else {
                    SpeakingLogger.error("PhrasePractice") { "Failed to save AI feedback: ${feedbackResult.exceptionOrNull()?.message}" }
                }
            } else {
                SpeakingLogger.error("PhrasePractice") { "Failed to save recording: ${recordingResult.exceptionOrNull()?.message}" }
            }
        } catch (e: Exception) {
            SpeakingLogger.error("PhrasePractice") { "Error saving phrase practice: ${e.message}" }
            throw e
        }
    }

    private suspend fun saveLegacyVoiceSession(
        transcript: String,
        scores: Map<String, Float>,
        languageCode: String
    ) {
        // Upload recording to Supabase storage first
        val audioUrl = currentRecordingFile?.let { uploadRecordingToSupabase(it) }

        val saveResult =
            voiceApiService.saveSession(
                userId = userId,
                language = languageCode,
                level = _voiceTutorLevel.value ?: "intermediate",
                scenario = _voiceTutorScenario.value ?: "daily_conversation",
                transcript = transcript,
                audioUrl = audioUrl,
                feedback =
                    kotlinx.serialization.json.buildJsonObject {
                        put("scores", kotlinx.serialization.json.JsonPrimitive(scores.toString()))
                        put("overall_score", kotlinx.serialization.json.JsonPrimitive(scores.values.average()))
                    },
                sessionDuration = _recordingDuration.value,
            )

        if (saveResult.isSuccess) {
            SpeakingLogger.info("Session") { "Legacy session saved with audio URL: $audioUrl" }
        } else {
            SpeakingLogger.error("Session") { "Failed to save legacy session" }
        }
    }

    private fun extractStrengths(scores: Map<String, Float>): List<String> {
        val strengths = mutableListOf<String>()
        scores.forEach { (key, value) ->
            if (value >= 70f) {
                strengths.add("Good ${key.replaceFirstChar { it.uppercase() }}: ${value.toInt()}%")
            }
        }
        return strengths.ifEmpty { listOf("Keep practicing!") }
    }

    private fun extractAreasForImprovement(scores: Map<String, Float>): List<String> {
        val improvements = mutableListOf<String>()
        scores.forEach { (key, value) ->
            if (value < 70f) {
                improvements.add("Improve ${key.replaceFirstChar { it.uppercase() }}: currently ${value.toInt()}%")
            }
        }
        return improvements
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
        currentRecordingFile = null
    }

    override fun onCleared() {
        super.onCleared()

        // Cancel structured coroutine scopes
        recordingJob.cancel()
        agentAudioChannel.close()
        userAudioChannel.close()

        // Clean up temp files
        tempFileManager.cleanup()

        // Clear audio buffers
        sessionAudioChunks.clear()
        userAudioChunks.clear()

        // Stop any active recording
        voiceRecorder.cancelRecording()
        voiceRecorder.dispose()
        voiceApiService.close()
        agentService.close()
        audioPlayer.dispose()

        SpeakingLogger.debug { "SpeakingViewModel cleared - all resources released" }
    }

    fun completePractice() {
        resetSession()
        _currentWord.value = null
        _selectedLanguage.value = null
        _voiceTutorLanguage.value = null
        _voiceTutorLevel.value = null
        _voiceTutorScenario.value = null
        _currentPrompt.value = null
        SpeakingLogger.info("Practice") { "Session completed" }
    }

    fun exitVoiceTutorPractice() {
        // Stop conversation if active
        if (_isConversationMode.value) {
            stopConversationMode()
        }

        resetSession()
        _voiceTutorLanguage.value = null
        _voiceTutorLevel.value = null
        _voiceTutorScenario.value = null
        _currentPrompt.value = null
        _selectedLanguage.value = null
        clearConversationData()

        SpeakingLogger.info("VoiceTutor") { "Exited practice - all buffers cleared" }
    }

    fun onStartFirstPracticeClicked() {
        _showVoiceTutorSelection.value = true
        SpeakingLogger.debug { "Starting selection flow" }
    }

    fun onExploreExercisesClicked() {
        _showVoiceTutorSelection.value = true
        SpeakingLogger.debug { "Showing scenarios" }
    }

    fun onStartConversationClicked() {
        // Set default conversation parameters and start in conversation mode
        _voiceTutorLanguage.value = PracticeLanguage.ENGLISH
        _voiceTutorLevel.value = "intermediate"
        _voiceTutorScenario.value = "conversation_partner"
        _selectedLanguage.value = PracticeLanguage.ENGLISH
        _showVoiceTutorSelection.value = false

        // Load a prompt for the conversation
        val prompt = loadPromptForScenario("conversation_partner")
        _currentPrompt.value = prompt

        // Start directly in conversation mode
        _isConversationMode.value = true
        startConversationMode(autoStartAgent = true)

        SpeakingLogger.info("VoiceTutor") { "Started conversation mode with default settings" }
    }

    fun startVoiceTutorPractice(
        language: PracticeLanguage,
        level: String,
        scenario: String,
    ) {
        _voiceTutorLanguage.value = language
        _voiceTutorLevel.value = level
        _voiceTutorScenario.value = scenario
        _selectedLanguage.value = language
        _showVoiceTutorSelection.value = false

        // Load a prompt for this scenario and level
        val prompt = loadPromptForScenario(scenario)
        _currentPrompt.value = prompt

        // Start in practice mode by default (not conversation mode)
        _isConversationMode.value = false

        SpeakingLogger.info("VoiceTutor") { "Started practice: language=$language, level=$level, scenario=$scenario" }
        SpeakingLogger.debug { "Prompt: $prompt" }
    }

    fun hideVoiceTutorSelection() {
        _showVoiceTutorSelection.value = false
    }

    /**
     * Extended phrase library for practice scenarios
     * Each scenario has multiple phrases at different difficulty levels
     */
    private val scenarioPhrases = mapOf(
        "travel" to listOf(
            // Beginner
            "Where is the nearest hotel?",
            "How much does a ticket cost?",
            "Can you help me find the train station?",
            "I need a taxi to the airport.",
            "Where is the bathroom?",
            // Intermediate
            "Could you recommend a good place to stay nearby?",
            "I would like to book a room for two nights, please.",
            "What time does the next bus to downtown leave?",
            "Is there a direct flight to Paris tomorrow?",
            "Could you tell me how to get to the museum from here?",
            // Advanced
            "I'm looking for accommodations that are accessible for wheelchair users.",
            "Would it be possible to arrange a guided tour of the historical district?",
            "I'd appreciate recommendations for authentic local experiences off the beaten path.",
            "Could you explain the public transportation system and the best pass options?",
            "I need to modify my reservation due to an unexpected change in my itinerary."
        ),
        "food" to listOf(
            // Beginner
            "Could you recommend a good local restaurant?",
            "I would like to order this dish, please.",
            "Can I have the menu?",
            "The food is delicious!",
            "How much is the bill?",
            // Intermediate
            "Do you have any vegetarian options on the menu?",
            "I have a food allergy to nuts. Is this dish safe for me?",
            "Could you recommend a traditional dish from this region?",
            "I'd like to make a reservation for four people at eight o'clock.",
            "What ingredients are used in this specialty?",
            // Advanced
            "I'm interested in learning about the culinary traditions of your region.",
            "Could you explain the preparation method for this traditional recipe?",
            "I'd love to try something authentic that tourists rarely discover.",
            "What wine would you pair with this particular dish?",
            "The flavor profile is quite complex. What spices are used?"
        ),
        "daily_conversation" to listOf(
            // Beginner
            "How was your day today?",
            "Nice to meet you!",
            "What is your name?",
            "Where are you from?",
            "What do you like to do for fun?",
            // Intermediate
            "I've been quite busy with work lately. How about you?",
            "What are your plans for the weekend?",
            "Have you seen any good movies recently?",
            "I really enjoy spending time with my family on weekends.",
            "The weather has been lovely these past few days.",
            // Advanced
            "I find it fascinating how different cultures approach work-life balance.",
            "What are your thoughts on the importance of learning multiple languages?",
            "I've been reflecting on my goals and considering some significant changes.",
            "Technology has certainly transformed the way we communicate with each other.",
            "I believe that continuous learning is essential for personal growth."
        ),
        "work" to listOf(
            // Beginner
            "Could we schedule a meeting for next week?",
            "I need help with this project.",
            "When is the deadline?",
            "Can you send me that file?",
            "I have a question about my tasks.",
            // Intermediate
            "I'd like to discuss the progress we've made on the quarterly report.",
            "Could we arrange a conference call with the team for tomorrow?",
            "I think we should reconsider our approach to this problem.",
            "The client has requested some modifications to the proposal.",
            "I appreciate your feedback on my presentation.",
            // Advanced
            "I'd like to propose a more efficient workflow for our department.",
            "We need to align our strategies with the company's long-term objectives.",
            "I've identified some potential risks that we should address proactively.",
            "Could you elaborate on the key performance indicators for this initiative?",
            "I believe cross-functional collaboration would significantly improve our outcomes."
        ),
        "culture" to listOf(
            // Beginner
            "What are the most important holidays in your culture?",
            "What traditional food do you eat?",
            "Do you have any festivals?",
            "What music do you like?",
            "Tell me about your family traditions.",
            // Intermediate
            "How do people in your country typically celebrate New Year?",
            "What customs should I be aware of when visiting your country?",
            "I'm curious about the traditional arts and crafts from your region.",
            "How has modernization affected traditional practices in your culture?",
            "What role does family play in your society?",
            // Advanced
            "I'm fascinated by how cultural identity evolves across generations.",
            "Could you explain the significance of this ceremony in your tradition?",
            "How do younger generations in your country view traditional values?",
            "What aspects of your cultural heritage do you feel most connected to?",
            "I'd love to understand the historical context behind these customs."
        ),
        "shopping" to listOf(
            // Beginner
            "How much does this cost?",
            "Do you have this in a different size?",
            "Can I try this on?",
            "Where is the fitting room?",
            "I'll take this one, please.",
            // Intermediate
            "Do you offer any discounts for students?",
            "I'm looking for something similar but in a different color.",
            "What's your return policy if this doesn't fit properly?",
            "Could you recommend something within my budget?",
            "Is this item available in your other stores?",
            // Advanced
            "I'm interested in locally made products that support artisan communities.",
            "Could you tell me about the materials and craftsmanship of this item?",
            "I appreciate quality over quantity and prefer sustainable options.",
            "What makes this brand stand out from similar products in the market?",
            "I'd like to understand the story behind this handcrafted piece."
        ),
        "health" to listOf(
            // Beginner
            "I don't feel well today.",
            "I have a headache.",
            "Where is the pharmacy?",
            "I need to see a doctor.",
            "Can you help me?",
            // Intermediate
            "I've been experiencing some discomfort in my lower back.",
            "Could you recommend something for seasonal allergies?",
            "I need to schedule a routine checkup with a general practitioner.",
            "Are there any side effects I should be aware of with this medication?",
            "How often should I take this prescription?",
            // Advanced
            "I'd like to discuss preventive measures for maintaining long-term health.",
            "Could you explain the treatment options available for this condition?",
            "I'm interested in incorporating holistic approaches alongside conventional medicine.",
            "What lifestyle modifications would you recommend for my situation?",
            "I'd appreciate a thorough explanation of the procedure and recovery process."
        )
    )

    private fun loadPromptForScenario(scenario: String): String {
        val phrases = scenarioPhrases[scenario] ?: scenarioPhrases["daily_conversation"]!!
        // Select a random phrase from the scenario
        return phrases.random()
    }
    
    /**
     * Get a specific phrase for a scenario at a given index
     */
    fun getPhraseForScenario(scenario: String, index: Int): String {
        val phrases = scenarioPhrases[scenario] ?: scenarioPhrases["daily_conversation"]!!
        return phrases.getOrElse(index % phrases.size) { phrases.first() }
    }
    
    /**
     * Get total number of phrases for a scenario
     */
    fun getPhrasesCountForScenario(scenario: String): Int {
        return scenarioPhrases[scenario]?.size ?: 0
    }
    
    /**
     * Get all phrases for a scenario
     */
    fun getAllPhrasesForScenario(scenario: String): List<String> {
        return scenarioPhrases[scenario] ?: emptyList()
    }
    
    /**
     * Load next phrase for current scenario
     */
    fun loadNextPrompt() {
        val scenario = _voiceTutorScenario.value ?: return
        _currentPrompt.value = loadPromptForScenario(scenario)
        resetSession()
    }
    
    /**
     * Set vocabulary words for practice from the vocabulary bank
     */
    fun setVocabularyWordsForPractice(words: List<VocabularyWord>) {
        _vocabularyWordsForPractice.value = words
        SpeakingLogger.info("WordPractice") { "Loaded ${words.size} words for practice" }
    }
    
    /**
     * Show/hide word practice section
     */
    fun toggleWordPractice() {
        _showWordPractice.value = !_showWordPractice.value
    }
    
    /**
     * Start practicing a specific vocabulary word
     */
    fun startWordPractice(word: VocabularyWord, languageCode: String) {
        _currentWord.value = word
        val practiceLanguage = when (languageCode.lowercase()) {
            "ko", "korean" -> PracticeLanguage.HANGEUL
            "zh", "chinese" -> PracticeLanguage.MANDARIN
            "es", "spanish" -> PracticeLanguage.SPANISH
            "fr", "french" -> PracticeLanguage.FRENCH
            "de", "german" -> PracticeLanguage.GERMAN
            else -> PracticeLanguage.ENGLISH
        }
        _selectedLanguage.value = practiceLanguage
        _currentPrompt.value = word.word
        _showWordPractice.value = false
        resetSession()
        SpeakingLogger.info("WordPractice") { "Started practice for word: ${word.word}" }
    }

    /**
     * Clear all conversation data and reset session tracking
     */
    private fun clearConversationData() {
        _conversationTurns.clear()
        turnIdToIndex.clear()
        existingTurnIds.clear()
        _conversationError.value = null

        // Clear any residual audio chunks (BoundedAudioBuffer is thread-safe)
        if (sessionAudioChunks.isNotEmpty()) {
            SpeakingLogger.debug { "Clearing ${sessionAudioChunks.size()} residual audio chunks" }
            sessionAudioChunks.clear()
        }

        // Reset session tracking
        currentSessionId = null
        sessionStartTime = 0L
    }

    /**
     * Convert PracticeLanguage to language code for API calls
     */
    private fun getLanguageCode(language: PracticeLanguage?): String {
        return when (language) {
            PracticeLanguage.ENGLISH -> "en"
            PracticeLanguage.FRENCH -> "fr"
            PracticeLanguage.GERMAN -> "de"
            PracticeLanguage.HANGEUL -> "ko"
            PracticeLanguage.MANDARIN -> "zh"
            PracticeLanguage.SPANISH -> "es"
            else -> "en"
        }
    }

    /**
     * Generate user-friendly error message from API error
     */
    private fun generateErrorMessage(error: Throwable?): String {
        return when {
            error?.message?.contains("408", ignoreCase = true) == true ->
                "Connection timeout. Servers may be busy. Please try again in a moment."
            error?.message?.contains("handshake", ignoreCase = true) == true ->
                "WebSocket handshake failed. Please check your internet connection."
            error?.message?.contains("401", ignoreCase = true) == true ->
                "Invalid API key. Please check your API key."
            else -> error?.message ?: "Unknown error occurred"
        }
    }

    /**
     * Handle conversation text messages (both direct and agent_message types)
     */
    private fun handleConversationText(message: AgentApiService.AgentMessage) {
        SpeakingLogger.debug {
            "Text - role=${message.role}, content=${message.content?.take(
                50,
            )}..., final=${message.isFinal}, turnId=${message.turnId}"
        }
        message.content?.let { content ->
            handleStreamingConversationText(
                content = content,
                role = message.role ?: "assistant",
                isFinal = message.isFinal,
                turnId = message.turnId,
            )
        }
    }

    /**
     * Handle agent message updates
     */
    private fun handleAgentMessage(message: AgentApiService.AgentMessage) {
        when (message.type) {
            "connection" -> {
                if (message.event == "opened") {
                    SpeakingLogger.info("Conversation") { "Agent connection established" }
                    _isConversationActive.value = true
                }
            }
            // Handle both normalized and raw conversation text from ElevenLabs
            "conversation_text", "ConversationText" -> {
                handleConversationText(message)
            }
            "agent_message" -> {
                when (message.event) {
                    "Welcome" -> {
                        SpeakingLogger.info("Conversation") { "Agent ready - activating UI" }
                        _isConversationActive.value = true
                    }
                    "ConversationText" -> {
                        handleConversationText(message)
                    }
                    "AgentThinking" -> {
                        SpeakingLogger.debug { "Agent is thinking" }
                        _isAgentThinking.value = true
                        _isAgentSpeaking.value = false

                        // Add a temporary thinking bubble with tracked ID
                        val thinkingId = "thinking-${java.util.UUID.randomUUID()}"
                        existingTurnIds.add(thinkingId)
                        turnIdToIndex[thinkingId] = _conversationTurns.size
                        _conversationTurns.add(
                            ConversationTurnUI(
                                id = thinkingId,
                                role = "assistant",
                                text = "",
                            ),
                        )
                    }
                    "AgentStartedSpeaking" -> {
                        SpeakingLogger.debug { "Agent started speaking" }
                        _isAgentThinking.value = false
                        _isAgentSpeaking.value = true
                    }
                    "UserStartedSpeaking" -> {
                        SpeakingLogger.debug { "User started speaking detected" }
                        _isAgentThinking.value = false
                        _isAgentSpeaking.value = false

                        // Remove the empty thinking bubble if it exists (with proper cleanup)
                        if (_conversationTurns.isNotEmpty() &&
                            _conversationTurns.last().role == "assistant" &&
                            _conversationTurns.last().text.isBlank()
                        ) {
                            val removedId = _conversationTurns.last().id
                            existingTurnIds.remove(removedId)
                            turnIdToIndex.remove(removedId)
                            _conversationTurns.removeAt(_conversationTurns.lastIndex)
                        }
                    }
                    "AgentAudioDone" -> {
                        SpeakingLogger.debug { "Agent finished speaking" }
                        _isAgentThinking.value = false
                        _isAgentSpeaking.value = false
                    }
                    "error" -> {
                        SpeakingLogger.error("Conversation") { "Error: ${message.error}" }
                        _conversationError.value = message.error
                    }
                }
            }
            "error" -> {
                SpeakingLogger.error("Conversation") { "Error: ${message.error}" }
                _conversationError.value = message.error
            }
        }
    }

    /**
     * Start agent conversation with common parameters
     */
    private suspend fun startAgentConversation(
        apiKey: String,
        languageCode: String,
        level: String,
        scenario: String,
    ): Result<Unit> {
        // Track user audio reception for debugging
        var userAudioChunksReceived = 0L

        val result =
            agentService.startConversation(
                apiKey = apiKey,
                language = languageCode,
                level = level,
                scenario = scenario,
                onMessage = { message: AgentApiService.AgentMessage ->
                    // Update UI state on Main dispatcher for immediate responsiveness
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        handleAgentMessage(message)
                    }
                },
                onAudioReceived = { audioData ->
                    // Offload agent audio processing to a dedicated channel (non-blocking)
                    viewModelScope.launch(Dispatchers.Default) {
                        try {
                            agentAudioChannel.send(audioData)
                        } catch (e: Exception) {
                            if (e !is CancellationException) {
                                SpeakingLogger.error("AgentAudio") { "Failed to queue chunk: ${e.message}" }
                            }
                        }
                    }
                },
                onUserAudioCaptured = { audioData ->
                    // Capture user audio to a dedicated channel for reliable recording
                    userAudioChunksReceived++
                    if (userAudioChunksReceived == 1L) {
                        SpeakingLogger.info("UserAudioCallback") { "First user audio chunk received from AgentService: ${audioData.size} bytes" }
                    }
                    viewModelScope.launch(Dispatchers.Default) {
                        try {
                            userAudioChannel.send(audioData)
                        } catch (e: Exception) {
                            if (e !is CancellationException) {
                                SpeakingLogger.error("UserAudio") { "Failed to queue chunk: ${e.message}" }
                            }
                        }
                    }
                },
            )
        // Map Result<String> to Result<Unit>
        return result.map { }
    }

    fun startConversationMode(
        autoStartAgent: Boolean = true,
        preserveData: Boolean = false,
    ) {
        // Validate required parameters
        if (_voiceTutorLanguage.value == null || _voiceTutorLevel.value == null || _voiceTutorScenario.value == null) {
            SpeakingLogger.warn("Conversation") { "Cannot start - missing parameters" }
            return
        }

        // Atomic guard to prevent duplicate starts
        if (!isStartingConversation.compareAndSet(false, true)) {
            SpeakingLogger.warn("Conversation") { "Start already in progress - ignoring" }
            return
        }

        // Prevent duplicate calls from debouncing
        if (!conversationDebouncer.shouldProceed()) {
            SpeakingLogger.warn("Conversation") { "Ignoring rapid call (debounced)" }
            isStartingConversation.set(false)
            return
        }

        // Check if already active
        if (_isConversationActive.value) {
            SpeakingLogger.warn("Conversation") { "Already active - ignoring duplicate call" }
            isStartingConversation.set(false)
            return
        }

        // Check if the agent service is already connecting
        if (autoStartAgent && agentService.connectionState.get() != AgentApiService.ConnectionState.DISCONNECTED) {
            SpeakingLogger.warn("Conversation") { "Agent service already connecting/connected - ignoring" }
            isStartingConversation.set(false)
            return
        }

        SpeakingLogger.info("Conversation") {
            "Starting conversation mode${if (autoStartAgent) " with agent" else " (UI only)"}${if (preserveData) " (preserving data)" else ""}"
        }

        // Set up a conversation UI state
        _isConversationMode.value = true

        // Only clear data if not preserving (for fresh starts vs. retries)
        if (!preserveData) {
            _conversationTurns.clear()
            turnIdToIndex.clear()
            existingTurnIds.clear()
            _conversationError.value = null

            // Initialize session recording only for fresh starts
            currentSessionId = java.util.UUID.randomUUID().toString()
            sessionStartTime = System.currentTimeMillis()
            sessionAudioChunks.clear()
            SpeakingLogger.info("Conversation") { "NEW session started with ID: $currentSessionId" }
        } else {
            // Preserve data but clear error for retry
            _conversationError.value = null
            SpeakingLogger.debug { "Preserving existing session data for retry" }
        }

        if (autoStartAgent) {
            startAgentConnection()
        } else {
            isStartingConversation.set(false)
        }
    }

    /**
     * Retry conversation connection while preserving existing conversation data.
     * Used when connection fails, but we want to keep the conversation context.
     * This method bypasses normal guards to force reconnection.
     */
    fun retryConversationConnection() {
        // Atomic guard to prevent duplicate retries
        if (!isRetryingConnection.compareAndSet(false, true)) {
            SpeakingLogger.warn("Conversation") { "Retry already in progress - ignoring" }
            return
        }

        SpeakingLogger.info("Conversation") { "Retrying connection with data preservation" }

        // First, stop any existing connection to clean up the state
        if (_isConversationActive.value || agentService.isActive()) {
            SpeakingLogger.debug { "Stopping existing connection before retry" }
            stopConversationMode()
        }

        // Clear any existing error
        _conversationError.value = null

        // Force to start a new connection with preserved data
        viewModelScope.launch {
            try {
                conversationMutex.withLock {
                    val languageCode = getLanguageCode(_voiceTutorLanguage.value)

                    // Get API key from environment
                    val apiKey = agentService.getApiKey() ?: ""

                    // Start a fresh connection
                    val result =
                        startAgentConversation(
                            apiKey = apiKey,
                            languageCode = languageCode,
                            level = _voiceTutorLevel.value ?: "intermediate",
                            scenario = _voiceTutorScenario.value ?: "daily_conversation",
                        )

                    if (result.isFailure) {
                        val error = result.exceptionOrNull()
                        val errorMsg = generateErrorMessage(error)

                        _conversationError.value = errorMsg
                        SpeakingLogger.error("Conversation") { "Failed to retry: $errorMsg" }
                        SpeakingLogger.error("Conversation") { "Original error: ${error?.message}" }
                    } else {
                        SpeakingLogger.info("Conversation") { "Retry connection successful" }
                    }
                }
            } catch (e: Exception) {
                _conversationError.value = e.message
                SpeakingLogger.error("Conversation") { "Error during retry: ${e.message}" }
            } finally {
                // Always reset the retry guard
                isRetryingConnection.set(false)
            }
        }
    }

    /**
     * Start the agent connection only (called separately if needed).
     */
    fun startAgentConnection() {
        if (!isStartingConversation.get()) {
            isStartingConversation.set(true)
        }

        // Clear any previous error when retrying but preserve conversation data
        _conversationError.value = null

        viewModelScope.launch {
            try {
                conversationMutex.withLock {
                    val languageCode = getLanguageCode(_voiceTutorLanguage.value)

                    // Get API key from environment
                    val apiKey = agentService.getApiKey() ?: ""

                    val result =
                        startAgentConversation(
                            apiKey = apiKey,
                            languageCode = languageCode,
                            level = _voiceTutorLevel.value ?: "intermediate",
                            scenario = _voiceTutorScenario.value ?: "daily_conversation",
                        )

                    if (result.isFailure) {
                        val error = result.exceptionOrNull()
                        val errorMsg = generateErrorMessage(error)

                        _conversationError.value = errorMsg
                        // Keep conversation mode active to show error and allow retry
                        // Don't revert to practice mode on connection failure
                        SpeakingLogger.error("Conversation") { "Failed to start: $errorMsg" }
                        SpeakingLogger.error("Conversation") { "Original error: ${error?.message}" }
                    }
                }
            } catch (e: Exception) {
                _conversationError.value = e.message
                // Keep conversation mode active to show error and allow retry
                // Don't revert to practice mode on exception
                SpeakingLogger.error("Conversation") { "Error: ${e.message}" }
            } finally {
                isStartingConversation.set(false)
            }
        }
    }

    /**
     * Exit conversation mode UI (switches back to practice mode).
     * Also stops any active conversation.
     */
    fun exitConversationMode(preserveHistory: Boolean = false) {
        SpeakingLogger.info("Conversation") { "Exiting conversation mode${if (preserveHistory) " (preserving history)" else ""}" }

        // If conversation is active, stop it first
        if (_isConversationActive.value || agentService.isActive()) {
            stopConversationMode()
        } else {
            // Just reset the UI state
            _isConversationMode.value = false

            if (!preserveHistory) {
                // Clear all conversation data for a fresh start
                clearConversationData()
            } else {
                // Preserve conversation history but clear the error state
                _conversationError.value = null
                SpeakingLogger.debug { "Preserving conversation history for quick re-entry" }
            }
        }
    }

    /**
     * Stop the active conversation agent.
     * Called when the user clicks the "End Conversation" button.
     */
    fun stopConversationMode() {
        // Atomic guard to prevent duplicate stops
        if (!isStoppingConversation.compareAndSet(false, true)) {
            SpeakingLogger.warn("Conversation") { "Stop already in progress - ignoring" }
            return
        }

        if (!_isConversationActive.value && !agentService.isActive()) {
            // Just exit the mode if no active conversation
            _isConversationMode.value = false
            isStoppingConversation.set(false)
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                SpeakingLogger.info("Conversation") { "Stopping conversation agent" }

                // IMPORTANT: Stop the agent FIRST to immediately halt audio capture
                // This prevents the audio loop from continuing while we save
                agentService.stopConversation()
                
                // Update UI state immediately after stopping
                _isConversationActive.value = false
                _isConversationRecording.value = false
                _isAgentSpeaking.value = false
                _isAgentThinking.value = false
                _isConversationMode.value = false
                recordingStreamJob?.cancel()
                
                // Save conversation audio AFTER stopping (in background, non-blocking)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        saveConversationAudio()
                    } catch (e: Exception) {
                        SpeakingLogger.error("SaveAudio") { "Background save failed: ${e.message}" }
                    }
                }

                SpeakingLogger.info("Conversation") { "Stopped" }
            } catch (e: Exception) {
                SpeakingLogger.error("Conversation") { "Error stopping: ${e.message}" }
            } finally {
                // Always reset the stopping guard
                isStoppingConversation.set(false)
            }
        }
    }

    /**
     * Save conversation audio to file and Supabase storage
     */
    private suspend fun saveConversationAudio() {
        if (sessionAudioChunks.isEmpty()) {
            SpeakingLogger.warn("SaveAudio") { "No audio chunks to save" }
            return
        }

        try {
            // Log buffer statistics before save
            SpeakingLogger.info("SaveAudio") {
                "Session buffer: ${sessionAudioChunks.stats()}, User buffer: ${userAudioChunks.stats()}"
            }

            // Create a WAV file from audio chunks using the temp file manager
            val tempFile = tempFileManager.createTempFile("conversation_", ".wav")

            // Move blocking I/O operations to IO dispatcher
            withContext(Dispatchers.IO) {
                val outputStream = FileOutputStream(tempFile)

                // Get combined audio data from a bounded buffer
                val audioData = sessionAudioChunks.combine() ?: return@withContext
                val totalSize = audioData.size + 44
                outputStream.write(
                    byteArrayOf(
                        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
                        (totalSize and 0xFF).toByte(), ((totalSize shr 8) and 0xFF).toByte(),
                        ((totalSize shr 16) and 0xFF).toByte(), ((totalSize shr 24) and 0xFF).toByte(),
                        'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
                        'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
                        16, 0, 0, 0, // Subchunk size
                        1, 0, // Audio format (PCM)
                        1, 0, // Channels
                        0x44, 0xAC.toByte(), 0, 0, // Sample rate (44100)
                        0x88.toByte(), 0x58, 0x01, 0, // Byte rate
                        2, 0, // Block align
                        16, 0, // Bits per sample
                        'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
                        (audioData.size and 0xFF).toByte(),
                        ((audioData.size shr 8) and 0xFF).toByte(),
                        ((audioData.size shr 16) and 0xFF).toByte(),
                        ((audioData.size shr 24) and 0xFF).toByte(),
                    ),
                )

                // Write audio data
                outputStream.write(audioData)
                outputStream.close()
            }

            // Upload to Supabase storage
            val storage = SupabaseConfig.client.storage.from("conversations")
            val path = "$userId/$currentSessionId.wav"
            storage.upload(path, tempFile, upsert = true)

            // Save metadata
            voiceApiService.saveSession(
                userId = userId, // Use actual user ID
                language = _voiceTutorLanguage.value?.name ?: "en",
                level = _voiceTutorLevel.value ?: "intermediate",
                scenario = _voiceTutorScenario.value ?: "daily_conversation",
                transcript = _conversationTurns.joinToString("\n") { "${it.role}: ${it.text}" },
                audioUrl = "${SupabaseConfig.client.supabaseUrl}/object/public/conversations/$path",
                feedback = kotlinx.serialization.json.buildJsonObject { /* existing feedback */ },
                sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000f,
            )

            SpeakingLogger.info("SaveAudio") {
                "Audio saved: ${tempFile.absolutePath} (${tempFile.length()} bytes)"
            }
            SpeakingLogger.info("SaveAudio") {
                "User audio captured: ${userAudioChunks.size()} chunks, ${userAudioChunks.totalBytes()} bytes"
            }

            // Clean up temp file after upload
            tempFileManager.remove(tempFile)
        } catch (e: Exception) {
            SpeakingLogger.error("SaveAudio") { "Error saving audio: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Start conversation recording (push-to-talk).
     * Called when the user presses the mic button.
     */
    fun startConversationRecording() {
        if (!_isConversationActive.value) {
            SpeakingLogger.warn("ConversationRecording") { "Cannot record - conversation not active" }
            return
        }

        if (_isConversationRecording.value) {
            SpeakingLogger.warn("ConversationRecording") { "Already recording" }
            return
        }

        if (!isRecordingLocked.compareAndSet(false, true)) {
            SpeakingLogger.warn("ConversationRecording") { "Recording lock held - ignoring" }
            return
        }

        SpeakingLogger.debug { "Starting push-to-talk recording" }
        _isConversationRecording.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Start manual audio capture on agent service
                if (shouldUseCustomPipeline(_voiceTutorLanguage.value)) {
                    agentService.startCustomManualAudioCapture()
                } else {
                    agentService.startManualAudioCapture()
                }
                SpeakingLogger.debug { "Microphone active - recording started" }
            } catch (e: Exception) {
                SpeakingLogger.error("ConversationRecording") { "Failed to start: ${e.message}" }
                _isConversationRecording.value = false
                isRecordingLocked.set(false)
            }
        }
    }

    /**
     * Stop conversation recording (push-to-talk).
     * Called when a user releases the mic button.
     */
    fun stopConversationRecording() {
        if (!_isConversationRecording.value) {
            SpeakingLogger.warn("ConversationRecording") { "Not currently recording" }
            return
        }

        SpeakingLogger.debug { "Stopping push-to-talk recording" }
        _isConversationRecording.value = false

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Stop manual audio capture on agent service
                if (shouldUseCustomPipeline(_voiceTutorLanguage.value)) {
                    agentService.stopCustomManualAudioCapture()
                } else {
                    agentService.stopManualAudioCapture()
                }
                SpeakingLogger.debug { "Microphone released - recording stopped" }
            } catch (e: Exception) {
                SpeakingLogger.error("ConversationRecording") { "Error stopping: ${e.message}" }
            } finally {
                isRecordingLocked.set(false)
            }
        }
    }

    private fun shouldUseCustomPipeline(language: PracticeLanguage?): Boolean {
        return when (language) {
            PracticeLanguage.HANGEUL, PracticeLanguage.MANDARIN -> true
            else -> false
        }
    }

    /**
     * Handle streaming conversation text updates.
     * Uses O(1) HashMap lookups and efficient in-place mutations on SnapshotStateList
     * to avoid blocking the main thread.
     *
     * @param content The transcript content
     * @param role "user" or "assistant"
     * @param isFinal Whether this is a final transcript or interim
     * @param turnId Unique ID for tracking streaming updates to the same turn
     */
    private fun handleStreamingConversationText(
        content: String,
        role: String,
        isFinal: Boolean,
        turnId: String?,
    ) {
        // If the agent is thinking, and we get assistant content, replace the thinking bubble
        if (_isAgentThinking.value && role == "assistant" &&
            _conversationTurns.isNotEmpty() &&
            _conversationTurns.last().role == "assistant" &&
            _conversationTurns.last().text.isBlank()
        ) {
            // Replace the empty thinking bubble with actual content (in-place update)
            val lastIndex = _conversationTurns.lastIndex
            val uniqueId = turnId ?: java.util.UUID.randomUUID().toString()

            // O(1) duplicate check using Set instead of O(n) list iteration
            val finalId = if (existingTurnIds.contains(uniqueId)) {
                SpeakingLogger.warn("Conversation") { "Duplicate ID detected in thinking bubble replacement: $uniqueId, generating new UUID" }
                java.util.UUID.randomUUID().toString()
            } else {
                uniqueId
            }

            // Remove old ID from tracking if present
            val oldId = _conversationTurns[lastIndex].id
            existingTurnIds.remove(oldId)
            turnIdToIndex.remove(oldId)

            // Add new ID to tracking
            existingTurnIds.add(finalId)
            turnIdToIndex[finalId] = lastIndex

            _conversationTurns[lastIndex] =
                ConversationTurnUI(
                    id = finalId,
                    role = role,
                    text = content,
                    isFinal = isFinal,
                    isStreaming = !isFinal,
                )
            if (isFinal) _isAgentThinking.value = false
            return
        }

        // O(1) lookup for existing turn with same turnId
        if (turnId != null) {
            val existingIndex = turnIdToIndex[turnId]

            if (existingIndex != null && existingIndex < _conversationTurns.size) {
                // Efficient in-place update - only this item recomposes
                _conversationTurns[existingIndex] =
                    _conversationTurns[existingIndex].copy(
                        text = content,
                        isFinal = isFinal,
                        isStreaming = !isFinal,
                    )
                return
            }
        }

        // Check if we should update the last turn of the same role (for streaming without turnId)
        if (!isFinal && _conversationTurns.isNotEmpty()) {
            val lastIndex = _conversationTurns.lastIndex
            val lastTurn = _conversationTurns[lastIndex]
            if (lastTurn.role == role && lastTurn.isStreaming) {
                // Efficient in-place update of the last streaming turn
                _conversationTurns[lastIndex] =
                    lastTurn.copy(
                        text = content,
                        isFinal = false,
                        isStreaming = true,
                    )
                return
            }
        }

        // Add a new conversation turn (efficient append to SnapshotStateList)
        val newTurnId = turnId ?: java.util.UUID.randomUUID().toString()

        // O(1) duplicate check using Set
        val finalTurnId = if (existingTurnIds.contains(newTurnId)) {
            SpeakingLogger.warn("Conversation") { "Duplicate ID detected: $newTurnId, generating new UUID" }
            java.util.UUID.randomUUID().toString()
        } else {
            newTurnId
        }

        // Track the new turn ID for O(1) lookups
        existingTurnIds.add(finalTurnId)
        val newIndex = _conversationTurns.size
        turnIdToIndex[finalTurnId] = newIndex

        _conversationTurns.add(
            ConversationTurnUI(
                id = finalTurnId,
                role = role,
                text = content,
                isFinal = isFinal,
                isStreaming = !isFinal,
            ),
        )
        SpeakingLogger.debug { "Added new turn: role=$role, final=$isFinal, id=$finalTurnId" }
    }

    private fun getSpeakingFeatures(): List<SpeakingFeature> {
        return listOf(
            SpeakingFeature(
                id = "ai_feedback",
                title = "AI-Powered Feedback",
                description = "Get instant, detailed feedback on your pronunciation, fluency, and accuracy.",
                icon = "🤖",
                color = "#8B5CF6",
            ),
            SpeakingFeature(
                id = "pronunciation_analysis",
                title = "Pronunciation Analysis",
                description = "Advanced speech recognition analyzes your pronunciation in real-time.",
                icon = "🎙️",
                color = "#10B981",
            ),
            SpeakingFeature(
                id = "multi_language",
                title = "Multi-Language Support",
                description = "Practice pronunciation in French, German, Korean, Mandarin, and Spanish.",
                icon = "🌍",
                color = "#F59E0B",
            ),
            SpeakingFeature(
                id = "progress_tracking",
                title = "Progress Tracking",
                description = "Track your speaking improvement with detailed analytics and scores.",
                icon = "📊",
                color = "#3B82F6",
            ),
            SpeakingFeature(
                id = "recording_playback",
                title = "Recording & Playback",
                description = "Record your sessions and play them back to hear your pronunciation.",
                icon = "▶️",
                color = "#EF4444",
            ),
            SpeakingFeature(
                id = "instant_feedback",
                title = "Instant Feedback",
                description = "Get immediate scores on pronunciation, clarity, and fluency.",
                icon = "⚡",
                color = "#8B5CF6",
            ),
        )
    }
}
