package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.project.core.ai.VoiceApiService
import org.example.project.core.ai.AgentApiService
import org.example.project.core.audio.VoiceRecorder
import org.example.project.core.audio.AudioPlayer
import org.example.project.domain.model.VocabularyWord
import org.example.project.domain.model.PracticeLanguage
import org.example.project.domain.model.PracticeFeedback
import org.example.project.domain.model.SpeakingFeature
import org.example.project.domain.model.SpeakingScenario
import org.example.project.domain.model.ConversationRecording
import org.example.project.models.ConversationTurnUI
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import org.example.project.core.config.SupabaseConfig
import org.example.project.presentation.viewmodel.speaking.BoundedAudioBuffer
import org.example.project.presentation.viewmodel.speaking.Debouncer
import org.example.project.presentation.viewmodel.speaking.SpeakingConfig
import org.example.project.presentation.viewmodel.speaking.SpeakingLogger
import org.example.project.presentation.viewmodel.speaking.TempFileManager
import org.example.project.presentation.viewmodel.speaking.calculateAudioLevel
import org.example.project.presentation.viewmodel.speaking.intToByteArray
import org.example.project.presentation.viewmodel.speaking.shortToByteArray

class SpeakingViewModel(private val userId: String = "current_user") : ViewModel() {
    private val voiceApiService = VoiceApiService()
    private val agentService = AgentApiService()
    private val voiceRecorder = VoiceRecorder()
    private val audioPlayer = AudioPlayer()
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

    private val _voiceTutorLanguage = mutableStateOf<PracticeLanguage?>(null)
    val voiceTutorLanguage: State<PracticeLanguage?> = _voiceTutorLanguage

    private val _voiceTutorLevel = mutableStateOf<String?>(null)
    val voiceTutorLevel: State<String?> = _voiceTutorLevel

    private val _voiceTutorScenario = mutableStateOf<String?>(null)
    val voiceTutorScenario: State<String?> = _voiceTutorScenario

    private val _currentPrompt = mutableStateOf<String?>(null)
    val currentPrompt: State<String?> = _currentPrompt
    
    // Lesson integration
    private val _lessonScenarios = mutableStateOf<List<SpeakingScenario>>(emptyList())
    val lessonScenarios: State<List<SpeakingScenario>> = _lessonScenarios
    
    private val _selectedScenario = mutableStateOf<SpeakingScenario?>(null)
    val selectedScenario: State<SpeakingScenario?> = _selectedScenario
    
    // Conversation recording
    private val _conversationRecording = mutableStateOf<ConversationRecording?>(null)
    val conversationRecording: State<ConversationRecording?> = _conversationRecording
    
    private val _isLoadingRecording = mutableStateOf(false)
    val isLoadingRecording: State<Boolean> = _isLoadingRecording

    // Conversation mode state
    private val _isConversationMode = mutableStateOf(false)
    val isConversationMode: State<Boolean> = _isConversationMode
    
    // Track if the agent is actually connected and ready
    private val _isConversationActive = mutableStateOf(false)
    val isConversationActive: State<Boolean> = _isConversationActive

    private val _conversationTurns = mutableStateOf<List<ConversationTurnUI>>(emptyList())
    val conversationTurns: State<List<ConversationTurnUI>> = _conversationTurns

    private val _isAgentSpeaking = mutableStateOf(false)
    val isAgentSpeaking: State<Boolean> = _isAgentSpeaking

    private val _isAgentThinking = mutableStateOf(false)
    val isAgentThinking: State<Boolean> = _isAgentThinking

    private val _conversationError = mutableStateOf<String?>(null)
    val conversationError: State<String?> = _conversationError
    
    // Session recording for playback (optimized with bounded buffer)
    private var currentSessionId: String? = null
    private var sessionStartTime: Long = 0L
    private val sessionAudioChunks = BoundedAudioBuffer(
        maxChunks = SpeakingConfig.MAX_AUDIO_CHUNKS,
        maxBytes = SpeakingConfig.MAX_AUDIO_BYTES
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
    
    // Mutex for conversation operations
    private val conversationMutex = Mutex()
    
    // Debouncing for API calls
    private val conversationDebouncer = Debouncer(SpeakingConfig.CONVERSATION_DEBOUNCE_MS)
    
    // Recording job reference for cancellation
    private var recordingStreamJob: Job? = null
    
    // Structured coroutine scope for recording operations (cancellable)
    private val recordingJob = SupervisorJob()
    private val recordingScope = CoroutineScope(Dispatchers.IO + recordingJob)
    
    // Audio processing channel for background offloading
    private val audioProcessingChannel = Channel<ByteArray>(Channel.UNLIMITED)
    
    // Temp file manager for automatic cleanup
    private val tempFileManager = TempFileManager()

    init {
        _speakingFeatures.value = getSpeakingFeatures()
        
        // Start audio processing worker for background offloading
        startAudioProcessingWorker()
    }
    
    /**
     * Background worker for processing audio chunks without blocking the main thread.
     */
    private fun startAudioProcessingWorker() {
        viewModelScope.launch(Dispatchers.Default) {
            for (audioData in audioProcessingChannel) {
                try {
                    // Add to bounded buffer (thread-safe)
                    sessionAudioChunks.add(audioData)
                    
                    // Calculate audio level for visualization
                    val level = calculateAudioLevel(audioData)
                    withContext(Dispatchers.Main) {
                        _audioLevel.value = level
                    }
                    
                    // Log periodically
                    if (sessionAudioChunks.size() % SpeakingConfig.AUDIO_CHUNK_LOG_INTERVAL == 0) {
                        SpeakingLogger.debug { "Audio buffer: ${sessionAudioChunks.stats()}" }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SpeakingLogger.error("AudioWorker") { "Error processing chunk: ${e.message}" }
                }
            }
        }
    }

    fun startPracticeSession(word: VocabularyWord) {
        _currentWord.value = word
        _showLanguageDialog.value = true
        resetSession()
    }

    fun startPracticeSessionForLessonLanguage(word: VocabularyWord, language: String) {
        _currentWord.value = word
        // Map lesson language to PracticeLanguage
        val practiceLanguage = when (language.lowercase()) {
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
                    _feedback.value = PracticeFeedback(
                        overallScore = 0,
                        pronunciationScore = 0,
                        clarityScore = 0,
                        fluencyScore = 0,
                        messages = listOf("No audio recording detected. Please record your voice first."),
                        suggestions = listOf("Press the microphone button to start recording")
                    )
                    return@launch
                }

                // Validation: Check if recording duration is sufficient (at least 0.5 seconds)
                if (_recordingDuration.value < 0.5f) {
                    SpeakingLogger.warn("Analysis") { "Recording too short: ${_recordingDuration.value}s" }
                    _feedback.value = PracticeFeedback(
                        overallScore = 0,
                        pronunciationScore = 0,
                        clarityScore = 0,
                        fluencyScore = 0,
                        messages = listOf("Recording is too short. Please record for at least 1 second."),
                        suggestions = listOf("Press and hold the microphone button while speaking")
                    )
                    return@launch
                }

                // Validation: Check if a file exists and has content
                if (!currentRecordingFile!!.exists() || currentRecordingFile!!.length() < 1000) {
                    SpeakingLogger.warn("Analysis") { "Invalid file: size=${currentRecordingFile!!.length()} bytes" }
                    _feedback.value = PracticeFeedback(
                        overallScore = 0,
                        pronunciationScore = 0,
                        clarityScore = 0,
                        fluencyScore = 0,
                        messages = listOf("Recording file is invalid or empty. Please try recording again."),
                        suggestions = listOf("Make sure to speak clearly into your microphone")
                    )
                    return@launch
                }

                // All validations passed - now call Deepgram API
                SpeakingLogger.debug { "Recording validated: ${_recordingDuration.value}s, ${currentRecordingFile!!.length()} bytes" }
                SpeakingLogger.debug { "Calling Deepgram API for transcription" }
                _isAnalyzing.value = true
                
                // Step 1: Transcribe audio using Deepgram
                // For English-only content, use model without a language parameter or use "en-US"
                val languageCode = when (_selectedLanguage.value) {
                    PracticeLanguage.ENGLISH -> null // Use model default for English
                    PracticeLanguage.FRENCH -> "fr"
                    PracticeLanguage.GERMAN -> "de"
                    PracticeLanguage.HANGEUL -> "ko"
                    PracticeLanguage.MANDARIN -> "zh"
                    PracticeLanguage.SPANISH -> "es"
                    null -> null // Use model default
                }
                
                val transcriptionResult = voiceApiService.transcribeAudio(
                    audioFile = currentRecordingFile!!,
                    language = languageCode ?: "en-US", // Use en-US for English or when null
                    model = "nova-3"
                )
                
                if (transcriptionResult.isSuccess) {
                    val transcript = transcriptionResult.getOrNull()
                    if (transcript != null && transcript.success) {
                        SpeakingLogger.debug { "Transcription: ${transcript.transcript}" }
                        SpeakingLogger.debug { "Confidence: ${transcript.confidence}" }
                        
                        // Validate transcription has actual content
                        if (transcript.transcript.isBlank() || transcript.confidence < 0.1f) {
                            SpeakingLogger.warn("Analysis") { "Empty or low-confidence transcription" }
                            _feedback.value = PracticeFeedback(
                                overallScore = 0,
                                pronunciationScore = 0,
                                clarityScore = 0,
                                fluencyScore = 0,
                                messages = listOf(
                                    "Could not detect clear speech in the recording.",
                                    "The audio may be too quiet or contain background noise."
                                ),
                                suggestions = listOf(
                                    "Try speaking louder and closer to the microphone",
                                    "Ensure you're in a quiet environment",
                                    "Check your microphone settings"
                                )
                            )
                            _isAnalyzing.value = false
                            return@launch
                        }
                        
                        // Step 2: Generate AI feedback
                        val expectedText = _currentPrompt.value ?: _currentWord.value?.word ?: ""
                        val level = _voiceTutorLevel.value ?: "intermediate"
                        val scenario = _voiceTutorScenario.value ?: "daily_conversation"
                        val langCode = languageCode ?: "en-US"
                        
                        SpeakingLogger.debug { "Generating AI feedback" }
                        val feedbackResult = voiceApiService.generateFeedback(
                            transcript = transcript.transcript,
                            expectedText = expectedText,
                            language = langCode,
                            level = level,
                            scenario = scenario,
                            userId = userId
                        )
                        
                        if (feedbackResult.isSuccess) {
                            val feedback = feedbackResult.getOrNull()
                            if (feedback != null && feedback.success) {
                                // Convert API response to PracticeFeedback
                                val practiceFeedback = PracticeFeedback(
                                    overallScore = feedback.overall_score.toInt(),
                                    pronunciationScore = feedback.scores["pronunciation"]?.toInt() ?: 0,
                                    clarityScore = feedback.scores["fluency"]?.toInt() ?: 0,
                                    fluencyScore = feedback.scores["accuracy"]?.toInt() ?: 0,
                                    messages = feedback.feedback_messages,
                                    suggestions = feedback.suggestions
                                )
                                
                                _feedback.value = practiceFeedback
                                SpeakingLogger.info("Analysis") { "Complete - score: ${practiceFeedback.overallScore}%" }
                                
                                // Step 3: Save session (optional)
                                saveSessionToDatabase(transcript.transcript, feedback.scores, langCode)
                            }
                        } else {
                            SpeakingLogger.error("Analysis") { "Failed to generate feedback" }
                            _feedback.value = generateErrorFeedback("Failed to generate feedback")
                        }
                    }
                } else {
                    SpeakingLogger.error("Analysis") { "Failed to transcribe audio" }
                    _feedback.value = generateErrorFeedback("Failed to transcribe audio")
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
            suggestions = listOf("Please try recording again")
        )
    }
    
    private fun saveSessionToDatabase(transcript: String, scores: Map<String, Float>, languageCode: String) {
        viewModelScope.launch {
            try {
                // TODO: Get actual user ID from auth service
                val userId = userId
                
                val saveResult = voiceApiService.saveSession(
                    userId = userId,
                    language = languageCode,
                    level = "intermediate",
                    scenario = "daily_conversation",
                    transcript = transcript,
                    audioUrl = null, // TODO: Upload audio file if needed
                    feedback = kotlinx.serialization.json.buildJsonObject {
                        put("scores", kotlinx.serialization.json.JsonPrimitive(scores.toString()))
                        put("overall_score", kotlinx.serialization.json.JsonPrimitive(scores.values.average()))
                    },
                    sessionDuration = _recordingDuration.value
                )
                
                if (saveResult.isSuccess) {
                    SpeakingLogger.debug { "Session saved successfully" }
                } else {
                    SpeakingLogger.error("Session") { "Failed to save" }
                }
            } catch (e: Exception) {
                SpeakingLogger.error("Session") { "Error saving: ${e.message}" }
            }
        }
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
        audioProcessingChannel.close()
        
        // Clean up temp files
        tempFileManager.cleanup()
        
        // Clear audio buffer
        sessionAudioChunks.clear()
        
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
        _conversationTurns.value = emptyList()
        _conversationError.value = null
        
        // Clear any residual audio chunks (BoundedAudioBuffer is thread-safe)
        if (sessionAudioChunks.isNotEmpty()) {
            SpeakingLogger.debug { "Clearing ${sessionAudioChunks.size()} residual audio chunks" }
            sessionAudioChunks.clear()
        }
        
        // Reset session tracking
        currentSessionId = null
        sessionStartTime = 0L
        
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

    fun startVoiceTutorPractice(language: PracticeLanguage, level: String, scenario: String) {
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

    private fun loadPromptForScenario(scenario: String): String {
        // In production, these would come from the backend API
        return when (scenario) {
            "travel" -> "Where is the nearest hotel?"
            "food" -> "Could you recommend a good local restaurant?"
            "daily_conversation" -> "How was your day today?"
            "work" -> "Could we schedule a meeting for next week?"
            "culture" -> "What are the most important holidays in your culture?"
            else -> "Hello, how are you?"
        }
    }

    /**
     * Start conversation mode with optional auto-start of agent connection.
     * Combines UI setup and agent connection for cleaner code.
     */
    fun startConversationMode(autoStartAgent: Boolean = true, preserveData: Boolean = false) {
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
        
        // Check if agent service is already connecting
        if (autoStartAgent && agentService.connectionState.get() != AgentApiService.ConnectionState.DISCONNECTED) {
            SpeakingLogger.warn("Conversation") { "Agent service already connecting/connected - ignoring" }
            isStartingConversation.set(false)
            return
        }
        
        SpeakingLogger.info("Conversation") { "Starting conversation mode${if (autoStartAgent) " with agent" else " (UI only)"}${if (preserveData) " (preserving data)" else ""}" }
        
        // Set up conversation UI state
        _isConversationMode.value = true
        
        // Only clear data if not preserving (for fresh starts vs retries)
        if (!preserveData) {
            _conversationTurns.value = emptyList()
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
     * Used when connection fails but we want to keep the conversation context.
     */
    fun retryConversationConnection() {
        SpeakingLogger.info("Conversation") { "Retrying connection with data preservation" }
        // Always preserve data when retrying - this is specifically for connection failures
        startConversationMode(autoStartAgent = true, preserveData = true)
    }
    
    /**
     * Start the agent connection only (called separately if needed).
     */
    fun startAgentConnection() {
        if (!isStartingConversation.get()) {
            isStartingConversation.set(true)
        }
        
        // Clear any previous error when retrying, but preserve conversation data
        _conversationError.value = null
        
        viewModelScope.launch {
            try {
                conversationMutex.withLock {
                    val languageCode = when (_voiceTutorLanguage.value) {
                        PracticeLanguage.ENGLISH -> "en"
                        PracticeLanguage.FRENCH -> "fr"
                        PracticeLanguage.GERMAN -> "de"
                        PracticeLanguage.HANGEUL -> "ko"
                        PracticeLanguage.MANDARIN -> "zh"
                        PracticeLanguage.SPANISH -> "es"
                        else -> "en"
                    }

                    // Get Deepgram API key from environment
                    val apiKey = agentService.getApiKey() ?: System.getenv("DEEPGRAM_API_KEY")
                    if (apiKey == null) {
                        _conversationError.value = "DEEPGRAM_API_KEY not set in environment"
                        // Don't reset conversation mode - keep UI stable
                        SpeakingLogger.error("Conversation") { "DEEPGRAM_API_KEY not found in environment" }
                        isStartingConversation.set(false)
                        return@withLock
                    }
                    
                    val result = agentService.startConversation(
                        apiKey = apiKey,
                        language = languageCode,
                        level = _voiceTutorLevel.value ?: "intermediate",
                        scenario = _voiceTutorScenario.value ?: "daily_conversation",
                        onMessage = { message: AgentApiService.AgentMessage ->
                        // Update UI state on Main dispatcher for immediate responsiveness
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            when (message.type) {
                                "connection" -> {
                                    if (message.event == "opened") {
                                        SpeakingLogger.info("Conversation") { "Agent connection established" }
                                        _isConversationActive.value = true
                                    }
                                }
                                "ConversationText" -> {
                                    SpeakingLogger.debug { "Text - role=${message.role}, content=${message.content?.take(50)}..." }
                                    message.content?.let { content ->
                                        val currentTurns = _conversationTurns.value
                                        val role = message.role ?: "assistant"
                                        
                                        // If agent is thinking and the last turn is an empty assistant message, replace it
                                        if (_isAgentThinking.value && role == "assistant" && 
                                            currentTurns.isNotEmpty() && 
                                            currentTurns.last().role == "assistant" && 
                                            currentTurns.last().text.isBlank()) {
                                            
                                            // Replace the empty thinking bubble with actual content
                                            _conversationTurns.value = currentTurns.dropLast(1) + ConversationTurnUI(
                                                role = role,
                                                text = content
                                            )
                                        } else {
                                            // Add new conversation turn normally
                                            _conversationTurns.value = currentTurns + ConversationTurnUI(
                                                role = role,
                                                text = content
                                            )
                                        }
                                    }
                                }
                                "agent_message" -> {
                                    when (message.event) {
                                        "Welcome" -> {
                                            SpeakingLogger.info("Conversation") { "Agent ready - activating UI" }
                                            _isConversationActive.value = true
                                        }
                                        "ConversationText" -> {
                                            SpeakingLogger.debug { "Text - role=${message.role}, content=${message.content?.take(50)}..." }
                                            message.content?.let { content ->
                                                val currentTurns = _conversationTurns.value
                                                val role = message.role ?: "assistant"
                                                
                                                // If agent is thinking and the last turn is an empty assistant message, replace it
                                                if (_isAgentThinking.value && role == "assistant" && 
                                                    currentTurns.isNotEmpty() && 
                                                    currentTurns.last().role == "assistant" && 
                                                    currentTurns.last().text.isBlank()) {
                                                    
                                                    // Replace the empty thinking bubble with actual content
                                                    _conversationTurns.value = currentTurns.dropLast(1) + ConversationTurnUI(
                                                        role = role,
                                                        text = content
                                                    )
                                                } else {
                                                    // Add new conversation turn normally
                                                    _conversationTurns.value = currentTurns + ConversationTurnUI(
                                                        role = role,
                                                        text = content
                                                    )
                                                }
                                            }
                                        }
                                        "AgentThinking" -> {
                                            SpeakingLogger.debug { "Agent is thinking" }
                                            _isAgentThinking.value = true
                                            _isAgentSpeaking.value = false
                                            
                                            // Add a temporary thinking bubble
                                            _conversationTurns.value = _conversationTurns.value + ConversationTurnUI(
                                                role = "assistant",
                                                text = ""
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
                                            
                                            // Remove empty thinking bubble if it exists
                                            val currentTurns = _conversationTurns.value
                                            if (currentTurns.isNotEmpty() && 
                                                currentTurns.last().role == "assistant" && 
                                                currentTurns.last().text.isBlank()) {
                                                _conversationTurns.value = currentTurns.dropLast(1)
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
                    },
                    onAudioReceived = { audioData ->
                        // Offload audio processing to background channel (non-blocking)
                        audioProcessingChannel.trySend(audioData)
                    })

                    if (result.isFailure) {
                        val error = result.exceptionOrNull()
                        val errorMsg = when {
                            error?.message?.contains("408", ignoreCase = true) == true -> 
                                "Connection timeout. Deepgram servers may be busy. Please try again in a moment."
                            error?.message?.contains("handshake", ignoreCase = true) == true -> 
                                "WebSocket handshake failed. Please check your internet connection."
                            error?.message?.contains("401", ignoreCase = true) == true -> 
                                "Invalid API key. Please check your Deepgram API key."
                            else -> error?.message ?: "Unknown error occurred"
                        }
                        
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
                // Clear all conversation data for fresh start
                _conversationTurns.value = emptyList()
                _conversationError.value = null
                
                // Clear audio chunks if any remain (BoundedAudioBuffer is thread-safe)
                if (sessionAudioChunks.isNotEmpty()) {
                    SpeakingLogger.debug { "Clearing ${sessionAudioChunks.size()} residual audio chunks" }
                    sessionAudioChunks.clear()
                }
                
                // Reset session tracking
                currentSessionId = null
                sessionStartTime = 0L
            } else {
                // Preserve conversation history but clear error state
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
                
                // Save conversation audio before stopping
                saveConversationAudio()
                
                agentService.stopConversation()
                _isConversationActive.value = false
                _isConversationRecording.value = false
                _isAgentSpeaking.value = false
                _isAgentThinking.value = false
                recordingStreamJob?.cancel()
                
                SpeakingLogger.info("Conversation") { "Stopped" }
            } catch (e: Exception) {
                SpeakingLogger.error("Conversation") { "Error stopping: ${e.message}" }
            }
        }
    }
    
    /**
     * Save conversation audio to file and Supabase storage
     */
    private suspend fun saveConversationAudio() {
        if (sessionAudioChunks.isEmpty()) return
        
        try {
            // Create WAV file from audio chunks using temp file manager
            val tempFile = tempFileManager.createTempFile("conversation_", ".wav")
            val outputStream = FileOutputStream(tempFile)
            
            // Get combined audio data from bounded buffer
            val audioData = sessionAudioChunks.combine() ?: return
            val totalSize = audioData.size + 44
            outputStream.write(byteArrayOf(
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
                ((audioData.size shr 24) and 0xFF).toByte()
            ))
            
            // Write audio data
            outputStream.write(audioData)
            outputStream.close()
            
            // Upload to Supabase storage
            val storage = SupabaseConfig.client.storage.from("conversations")
            val path = "${userId}/${currentSessionId}.wav"
            storage.upload(path, tempFile, upsert = true)
            
            // Save metadata
            voiceApiService.saveSession(
                userId = userId, // Use actual user ID
                language = _voiceTutorLanguage.value?.name ?: "en",
                level = _voiceTutorLevel.value ?: "intermediate",
                scenario = _voiceTutorScenario.value ?: "daily_conversation",
                transcript = _conversationTurns.value.joinToString("\n") { "${it.role}: ${it.text}" },
                audioUrl = "${SupabaseConfig.client.supabaseUrl}/object/public/conversations/$path",
                feedback = kotlinx.serialization.json.buildJsonObject { /* existing feedback */ },
                sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000f
            )
            
            SpeakingLogger.info("Conversation") { "Audio saved: ${tempFile.absolutePath}" }
            
            // Clean up temp file after upload
            tempFileManager.remove(tempFile)
        } catch (e: Exception) {
            SpeakingLogger.error("Conversation") { "Error saving audio: ${e.message}" }
        }
    }
    
    fun sendConversationAudio(audioFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioBytes = audioFile.readBytes()
                val result = agentService.sendAudio(audioBytes)
                if (result.isFailure) {
                    SpeakingLogger.error("ConversationAudio") { "Failed to send audio: ${result.exceptionOrNull()?.message}" }
                }
            } catch (e: Exception) {
                SpeakingLogger.error("ConversationAudio") { "Error sending audio: ${e.message}" }
            }
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
     * Save conversation session to Supabase with audio and transcript.
     */
    private fun saveConversationSession() {
        val sessionId = currentSessionId ?: return
        val language = _voiceTutorLanguage.value ?: return
        val level = _voiceTutorLevel.value ?: "intermediate"
        val scenario = _voiceTutorScenario.value ?: "conversation_partner"
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SpeakingLogger.info("SessionSave") { "Saving conversation session: $sessionId" }
                
                // Calculate session duration
                val duration = (System.currentTimeMillis() - sessionStartTime) / 1000f
                
                // Combine audio chunks into single file using BoundedAudioBuffer
                val audioFile = withContext(Dispatchers.IO) {
                    val audioData = sessionAudioChunks.combine()
                    
                    if (audioData != null && audioData.isNotEmpty()) {
                        // Save to temporary WAV file using temp file manager
                        val tempFile = tempFileManager.createTempFile("session_${sessionId}", ".wav")
                        val audioFormat = javax.sound.sampled.AudioFormat(24000f, 16, 1, true, false)
                        val audioInputStream = javax.sound.sampled.AudioInputStream(
                            java.io.ByteArrayInputStream(audioData),
                            audioFormat,
                            (audioData.size / audioFormat.frameSize).toLong()
                        )
                        javax.sound.sampled.AudioSystem.write(
                            audioInputStream,
                            javax.sound.sampled.AudioFileFormat.Type.WAVE,
                            tempFile
                        )
                        SpeakingLogger.debug { "Created audio file: ${tempFile.length()} bytes" }
                        tempFile
                    } else {
                        null
                    }
                }
                
                // Build transcript from conversation turns
                val transcript = _conversationTurns.value.joinToString("\n") { turn ->
                    "${turn.role}: ${turn.text}"
                }
                
                // Save session via API (which will handle Supabase and storage upload)
                val languageCodeForSave = when (language) {
                    PracticeLanguage.ENGLISH -> "en"
                    PracticeLanguage.FRENCH -> "fr"
                    PracticeLanguage.GERMAN -> "de"
                    PracticeLanguage.HANGEUL -> "ko"
                    PracticeLanguage.MANDARIN -> "zh"
                    PracticeLanguage.SPANISH -> "es"
                }
                val saveResult = voiceApiService.saveSession(
                    userId = userId, // Use actual user ID
                    language = languageCodeForSave,
                    level = level,
                    scenario = scenario,
                    transcript = transcript,
                    audioUrl = null, // Will be set after upload
                    feedback = kotlinx.serialization.json.buildJsonObject {
                        put("turns", kotlinx.serialization.json.JsonPrimitive(_conversationTurns.value.size))
                        put("duration", kotlinx.serialization.json.JsonPrimitive(duration.toDouble()))
                    },
                    sessionDuration = duration
                )
                
                if (saveResult.isSuccess) {
                    val recordingData = saveResult.getOrNull()
                    SpeakingLogger.info("SessionSave") { "Session saved successfully" }
                    
                    // TODO: Upload audio file to Supabase Storage
                    // This would be done through a backend endpoint to get signed URL
                    audioFile?.let { tempFileManager.remove(it) }
                } else {
                    SpeakingLogger.error("SessionSave") { "Failed to save session: ${saveResult.exceptionOrNull()?.message}" }
                    audioFile?.let { tempFileManager.remove(it) }
                }
                
                // Clear session data (BoundedAudioBuffer is thread-safe)
                sessionAudioChunks.clear()
                
            } catch (e: Exception) {
                SpeakingLogger.error("SessionSave") { "Error saving session: ${e.message}" }
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Load speaking scenarios for a specific lesson.
     */
    fun loadLessonScenarios(lessonId: String, language: String? = null) {
        viewModelScope.launch {
            try {
                SpeakingLogger.info("SpeakingVM") { "Loading scenarios for lesson: $lessonId" }
                
                val languageParam = language ?: _voiceTutorLanguage.value?.name?.lowercase()
                val result = voiceApiService.getLessonScenarios(lessonId, languageParam)
                
                if (result.isSuccess) {
                    val scenarios = result.getOrNull()
                    if (scenarios != null && scenarios.isNotEmpty()) {
                        _lessonScenarios.value = scenarios
                        SpeakingLogger.debug { "Loaded ${scenarios.size} scenarios" }
                    } else {
                        SpeakingLogger.warn("SpeakingVM") { "No scenarios found for lesson" }
                        _lessonScenarios.value = emptyList()
                    }
                } else {
                    SpeakingLogger.error("SpeakingVM") { "Failed to load scenarios: ${result.exceptionOrNull()?.message}" }
                    _lessonScenarios.value = emptyList()
                }
            } catch (e: Exception) {
                SpeakingLogger.error("SpeakingVM") { "Error loading scenarios: ${e.message}" }
                _lessonScenarios.value = emptyList()
            }
        }
    }
    
    /**
     * Start practice with a specific scenario from a lesson.
     */
    fun startPracticeWithScenario(scenario: SpeakingScenario) {
        _selectedScenario.value = scenario
        
        // Map scenario to practice language
        val practiceLanguage = when (scenario.language.lowercase()) {
            "ko", "korean" -> PracticeLanguage.HANGEUL
            "zh", "chinese", "mandarin" -> PracticeLanguage.MANDARIN
            "es", "spanish" -> PracticeLanguage.SPANISH
            "fr", "french" -> PracticeLanguage.FRENCH
            "de", "german" -> PracticeLanguage.GERMAN
            else -> PracticeLanguage.ENGLISH
        }
        
        _voiceTutorLanguage.value = practiceLanguage
        _selectedLanguage.value = practiceLanguage
        _voiceTutorLevel.value = scenario.difficultyLevel.lowercase()
        _voiceTutorScenario.value = scenario.scenarioType
        
        // Use first prompt from scenario
        _currentPrompt.value = scenario.prompts.firstOrNull() ?: "Let's practice speaking"
        
        SpeakingLogger.info("SpeakingVM") { "Started practice with scenario: ${scenario.title}" }
    }
    
    /**
     * Load conversation recording for playback.
     */
    fun loadConversationRecording(sessionId: String) {
        viewModelScope.launch {
            try {
                _isLoadingRecording.value = true
                SpeakingLogger.info("SpeakingVM") { "Loading recording for session: $sessionId" }
                
                val result = voiceApiService.getConversationRecording(sessionId)
                
                if (result.isSuccess) {
                    _conversationRecording.value = result.getOrNull()
                    SpeakingLogger.debug { "Recording loaded: ${_conversationRecording.value?.audioUrl}" }
                } else {
                    SpeakingLogger.error("SpeakingVM") { "Failed to load recording: ${result.exceptionOrNull()?.message}" }
                    _conversationRecording.value = null
                }
            } catch (e: Exception) {
                SpeakingLogger.error("SpeakingVM") { "Error loading recording: ${e.message}" }
                _conversationRecording.value = null
            } finally {
                _isLoadingRecording.value = false
            }
        }
    }
    
    /**
     * Play conversation recording audio.
     */
    fun playConversationRecording() {
        val recording = _conversationRecording.value
        if (recording == null || recording.audioUrl.isNullOrEmpty()) {
            SpeakingLogger.warn("SpeakingVM") { "No recording available to play" }
            return
        }
        
        viewModelScope.launch {
            try {
                SpeakingLogger.debug { "Playing recording: ${recording.audioUrl}" }
                // TODO: Implement audio streaming from URL
                // This would download the audio file and play it
                // audioPlayer.playFromUrl(recording.audioUrl)
            } catch (e: Exception) {
                SpeakingLogger.error("SpeakingVM") { "Error playing recording: ${e.message}" }
            }
        }
    }
    
    /**
     * Save current conversation as recording.
     */
    private suspend fun saveConversationAsRecording() {
        if (currentSessionId == null) {
            SpeakingLogger.warn("SpeakingVM") { "Cannot save - no session ID" }
            return
        }
        
        try {
            val transcript = _conversationTurns.value.joinToString("\n") { turn ->
                "${turn.role}: ${turn.text}"
            }
            
            val duration = (System.currentTimeMillis() - sessionStartTime) / 1000f
            val turnCount = _conversationTurns.value.size
            
            SpeakingLogger.info("SpeakingVM") { "========================================" }
            SpeakingLogger.info("SpeakingVM") { "SAVING CONVERSATION RECORDING" }
            SpeakingLogger.info("SpeakingVM") { "Session ID: $currentSessionId" }
            SpeakingLogger.info("SpeakingVM") { "User ID: $userId" }
            SpeakingLogger.info("SpeakingVM") { "Language: ${_voiceTutorLanguage.value?.name?.lowercase()}" }
            SpeakingLogger.info("SpeakingVM") { "Turn count: $turnCount" }
            SpeakingLogger.info("SpeakingVM") { "Duration: ${duration}s" }
            SpeakingLogger.info("SpeakingVM") { "Transcript length: ${transcript.length} chars" }
            SpeakingLogger.info("SpeakingVM") { "========================================" }
            
            if (turnCount == 0) {
                SpeakingLogger.warn("SpeakingVM") { "WARNING: No conversation turns to save" }
                return
            }
            
            // Combine audio chunks if available
            val audioFile = if (sessionAudioChunks.isNotEmpty()) {
                createAudioFileFromChunks()
            } else null
            
            val result = voiceApiService.saveConversationRecording(
                sessionId = currentSessionId!!,
                userId = userId,
                language = _voiceTutorLanguage.value?.name?.lowercase() ?: "en",
                audioFile = audioFile,
                transcript = transcript,
                turnCount = turnCount,
                duration = duration
            )
            
            if (result.isSuccess) {
                val recordingData = result.getOrNull()
                SpeakingLogger.info("SpeakingVM") { "✅ Recording saved successfully!" }
                SpeakingLogger.info("SpeakingVM") { "Recording ID: ${recordingData?.id}" }
                if (recordingData != null) {
                    _conversationRecording.value = recordingData
                }
            } else {
                SpeakingLogger.error("SpeakingVM") { "❌ Failed to save recording" }
                SpeakingLogger.error("SpeakingVM") { "Error: ${result.exceptionOrNull()?.message}" }
                result.exceptionOrNull()?.printStackTrace()
            }
            
            audioFile?.let { tempFileManager.remove(it) }
        } catch (e: Exception) {
            SpeakingLogger.error("SpeakingVM") { "❌ Exception saving recording: ${e.message}" }
            e.printStackTrace()
        }
    }
    
    private fun createAudioFileFromChunks(): File? {
        return try {
            // Use BoundedAudioBuffer.combine() for thread-safe audio data retrieval
            val audioData = sessionAudioChunks.combine() ?: return null
            
            // Create proper WAV file with RIFF header using TempFileManager
            val tempFile = tempFileManager.createTempFile("conversation_", ".wav")
            
            // Define audio format using centralized config
            val sampleRate = SpeakingConfig.SAMPLE_RATE
            val channels = SpeakingConfig.CHANNELS
            val bitsPerSample = SpeakingConfig.BITS_PER_SAMPLE
            val byteRate = SpeakingConfig.BYTE_RATE
            val blockAlign = SpeakingConfig.BLOCK_ALIGN
            
            // Calculate file sizes
            val dataSize = audioData.size
            val headerSize = 44
            val fileSize = headerSize + dataSize
            
            // Create WAV file with proper RIFF header
            tempFile.outputStream().use { output ->
                // RIFF header
                output.write("RIFF".toByteArray()) // ChunkID
                output.write(intToByteArray(fileSize - 8)) // ChunkSize
                output.write("WAVE".toByteArray()) // Format
                
                // fmt subchunk
                output.write("fmt ".toByteArray()) // Subchunk1ID
                output.write(intToByteArray(16)) // Subchunk1Size (16 for PCM)
                output.write(shortToByteArray(1)) // AudioFormat (1 for PCM)
                output.write(shortToByteArray(channels)) // NumChannels
                output.write(intToByteArray(sampleRate)) // SampleRate
                output.write(intToByteArray(byteRate)) // ByteRate
                output.write(shortToByteArray(blockAlign)) // BlockAlign
                output.write(shortToByteArray(bitsPerSample)) // BitsPerSample
                
                // data subchunk
                output.write("data".toByteArray()) // Subchunk2ID
                output.write(intToByteArray(dataSize)) // Subchunk2Size
                output.write(audioData) // Actual audio data
            }
            
            SpeakingLogger.debug { "Created WAV file: ${tempFile.absolutePath} (${tempFile.length()} bytes)" }
            tempFile
        } catch (e: Exception) {
            SpeakingLogger.error("AudioFile") { "Error creating audio file: ${e.message}" }
            null
        }
    }
    
    // intToByteArray and shortToByteArray are now imported from SpeakingUtils
    
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
