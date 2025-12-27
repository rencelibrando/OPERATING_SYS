package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.project.core.ai.VoiceApiService
import org.example.project.core.ai.AgentApiService
import org.example.project.core.audio.VoiceRecorder
import org.example.project.core.audio.AudioPlayer
import org.example.project.domain.model.VocabularyWord
import org.example.project.models.PracticeLanguage
import org.example.project.models.PracticeFeedback
import org.example.project.models.SpeakingFeature
import org.example.project.models.ConversationTurnUI
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SpeakingViewModel : ViewModel() {
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

    // Conversation mode state
    private val _isConversationMode = mutableStateOf(false)
    val isConversationMode: State<Boolean> = _isConversationMode
    
    // Track if agent is actually connected and ready
    private val _isConversationActive = mutableStateOf(false)
    val isConversationActive: State<Boolean> = _isConversationActive

    private val _conversationTurns = mutableStateOf<List<ConversationTurnUI>>(emptyList())
    val conversationTurns: State<List<ConversationTurnUI>> = _conversationTurns

    private val _isAgentSpeaking = mutableStateOf(false)
    val isAgentSpeaking: State<Boolean> = _isAgentSpeaking

    private val _conversationError = mutableStateOf<String?>(null)
    val conversationError: State<String?> = _conversationError
    
    // Session recording for playback
    private var currentSessionId: String? = null
    private var sessionStartTime: Long = 0L
    private val sessionAudioChunks = mutableListOf<ByteArray>()
    
    // Push-to-talk microphone state
    private val _isConversationRecording = mutableStateOf(false)
    val isConversationRecording: State<Boolean> = _isConversationRecording
    
    // Atomic guards for thread-safe operations
    private val isStartingConversation = AtomicBoolean(false)
    private val isStoppingConversation = AtomicBoolean(false)
    private val isRecordingLocked = AtomicBoolean(false)
    
    // Mutex for conversation operations
    private val conversationMutex = Mutex()
    
    // Debouncing for API calls
    private var lastConversationStartTime = 0L
    private val conversationStartDebounceMs = 500L
    
    // Recording job reference for cancellation
    private var recordingStreamJob: Job? = null

    init {
        _speakingFeatures.value = getSpeakingFeatures()
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
                    println("[PracticeRecording] Started recording")
                    
                    while (_isRecording.value) {
                        delay(100)
                        _recordingDuration.value += 0.1f
                    }
                } else {
                    println("[PracticeRecording] Failed to start: ${result.exceptionOrNull()?.message}")
                    _isRecording.value = false
                }
            } catch (e: Exception) {
                println("[PracticeRecording] Error starting: ${e.message}")
                _isRecording.value = false
            }
        }
    }

    private fun stopRecording() {
        _isRecording.value = false
        
        viewModelScope.launch {
            try {
                // Stop actual recording and get file
                val outputFile = File.createTempFile("recording_", ".wav")
                val result = voiceRecorder.stopRecording(outputFile)
                if (result.isSuccess) {
                    val filePath = result.getOrNull()
                    currentRecordingFile = if (filePath != null) File(filePath) else null
                    _hasRecording.value = currentRecordingFile != null
                    
                    println("[PracticeRecording] Stopped - duration: ${_recordingDuration.value}s")
                    
                    if (currentRecordingFile != null) {
                        analyzeRecording()
                    }
                } else {
                    println("[PracticeRecording] Failed to stop: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                println("[PracticeRecording] Error stopping: ${e.message}")
            }
        }
    }

    fun playRecording() {
        if (!_hasRecording.value || _isPlayingRecording.value || currentRecordingFile == null) return

        _isPlayingRecording.value = true

        viewModelScope.launch {
            try {
                println("[PracticePlayback] Playing recording")
                audioPlayer.playFile(currentRecordingFile!!) {
                    _isPlayingRecording.value = false
                    println("[PracticePlayback] Finished")
                }
            } catch (e: Exception) {
                println("[PracticePlayback] Error: ${e.message}")
                _isPlayingRecording.value = false
            }
        }
    }

    private fun analyzeRecording() {
        viewModelScope.launch {
            try {
                // Validation: Check if recording file exists
                if (currentRecordingFile == null) {
                    println("[PracticeAnalysis] No recording file available")
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
                    println("[PracticeAnalysis] Recording too short: ${_recordingDuration.value}s")
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

                // Validation: Check if file exists and has content
                if (!currentRecordingFile!!.exists() || currentRecordingFile!!.length() < 1000) {
                    println("[PracticeAnalysis] Invalid file: size=${currentRecordingFile!!.length()} bytes")
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
                println("[PracticeAnalysis] Recording validated: ${_recordingDuration.value}s, ${currentRecordingFile!!.length()} bytes")
                println("[PracticeAnalysis] Calling Deepgram API for transcription")
                _isAnalyzing.value = true
                
                // Step 1: Transcribe audio using Deepgram
                // For English-only content, use model without language parameter or use "en-US"
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
                        println("[PracticeAnalysis] Transcription: ${transcript.transcript}")
                        println("[PracticeAnalysis] Confidence: ${transcript.confidence}")
                        
                        // Validate transcription has actual content
                        if (transcript.transcript.isBlank() || transcript.confidence < 0.1f) {
                            println("[PracticeAnalysis] Empty or low-confidence transcription")
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
                        
                        println("[PracticeAnalysis] Generating AI feedback")
                        val feedbackResult = voiceApiService.generateFeedback(
                            transcript = transcript.transcript,
                            expectedText = expectedText,
                            language = langCode,
                            level = level,
                            scenario = scenario,
                            userId = "current_user" // TODO: Get actual user ID from auth
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
                                println("[PracticeAnalysis] Complete - score: ${practiceFeedback.overallScore}%")
                                
                                // Step 3: Save session (optional)
                                saveSessionToDatabase(transcript.transcript, feedback.scores, langCode)
                            }
                        } else {
                            println("[PracticeAnalysis] Failed to generate feedback")
                            _feedback.value = generateErrorFeedback("Failed to generate feedback")
                        }
                    }
                } else {
                    println("[PracticeAnalysis] Failed to transcribe audio")
                    _feedback.value = generateErrorFeedback("Failed to transcribe audio")
                }
                
            } catch (e: Exception) {
                println("[PracticeAnalysis] Error: ${e.message}")
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
                val userId = "current_user"
                
                val saveResult = voiceApiService.saveSession(
                    userId = userId,
                    language = languageCode,
                    level = "intermediate",
                    scenario = "daily_conversation",
                    transcript = transcript,
                    audioUrl = null, // TODO: Upload audio file if needed
                    feedback = mapOf(
                        "scores" to scores,
                        "overall_score" to (scores.values.average())
                    ),
                    sessionDuration = _recordingDuration.value
                )
                
                if (saveResult.isSuccess) {
                    println("[PracticeSession] Saved successfully")
                } else {
                    println("[PracticeSession] Failed to save")
                }
            } catch (e: Exception) {
                println("[PracticeSession] Error saving: ${e.message}")
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
        // Stop any active recording
        voiceRecorder.cancelRecording()
        voiceRecorder.dispose()
        voiceApiService.close()
        agentService.close()
        audioPlayer.dispose()
    }

    fun completePractice() {
        resetSession()
        _currentWord.value = null
        _selectedLanguage.value = null
        _voiceTutorLanguage.value = null
        _voiceTutorLevel.value = null
        _voiceTutorScenario.value = null
        _currentPrompt.value = null
        println("[Practice] Session completed")
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
        println("[VoiceTutor] Exited practice")
    }

    fun onStartFirstPracticeClicked() {
        _showVoiceTutorSelection.value = true
        println("[VoiceTutor] Starting selection flow")
    }

    fun onExploreExercisesClicked() {
        _showVoiceTutorSelection.value = true
        println("[VoiceTutor] Showing scenarios")
    }

    fun onStartConversationClicked() {
        // Set default conversation parameters and start in conversation mode
        _voiceTutorLanguage.value = PracticeLanguage.ENGLISH
        _voiceTutorLevel.value = "intermediate"
        _voiceTutorScenario.value = "conversation_partner"
        _selectedLanguage.value = PracticeLanguage.ENGLISH
        _showVoiceTutorSelection.value = false
        
        // Load a prompt for the conversation
        val prompt = loadPromptForScenario(PracticeLanguage.ENGLISH, "intermediate", "conversation_partner")
        _currentPrompt.value = prompt
        
        // Start directly in conversation mode
        _isConversationMode.value = true
        startConversationMode()
        
        println("[VoiceTutor] Started conversation mode with default settings")
    }

    fun startVoiceTutorPractice(language: PracticeLanguage, level: String, scenario: String) {
        _voiceTutorLanguage.value = language
        _voiceTutorLevel.value = level
        _voiceTutorScenario.value = scenario
        _selectedLanguage.value = language
        _showVoiceTutorSelection.value = false
        
        // Load a prompt for this scenario and level
        val prompt = loadPromptForScenario(language, level, scenario)
        _currentPrompt.value = prompt
        
        // Start in practice mode by default (not conversation mode)
        _isConversationMode.value = false
        
        println("[VoiceTutor] Started practice: language=$language, level=$level, scenario=$scenario")
        println("[VoiceTutor] Prompt: $prompt")
    }

    fun hideVoiceTutorSelection() {
        _showVoiceTutorSelection.value = false
    }

    private fun loadPromptForScenario(language: PracticeLanguage, level: String, scenario: String): String {
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
     * Enter conversation mode UI without starting the agent connection.
     * User must explicitly click "Start Conversation" to begin.
     */
    fun enterConversationMode() {
        if (_voiceTutorLanguage.value == null || _voiceTutorLevel.value == null || _voiceTutorScenario.value == null) {
            println("[Conversation] Cannot enter mode - missing parameters")
            return
        }
        
        if (_isConversationMode.value) {
            println("[Conversation] Already in conversation mode")
            return
        }
        
        println("[Conversation] Entering conversation mode UI")
        _isConversationMode.value = true
        _conversationTurns.value = emptyList()
        _conversationError.value = null
        // Note: Does NOT start the agent connection - user must click Start Conversation
    }
    
    /**
     * Start the conversation agent connection.
     * Called when user explicitly clicks "Start Conversation" button.
     */
    fun startConversationMode() {
        if (_voiceTutorLanguage.value == null || _voiceTutorLevel.value == null || _voiceTutorScenario.value == null) {
            println("[Conversation] Cannot start - missing parameters")
            return
        }
        
        // Atomic guard to prevent duplicate starts
        if (!isStartingConversation.compareAndSet(false, true)) {
            println("[Conversation] Start already in progress - ignoring")
            return
        }
        
        // Prevent duplicate calls with debouncing
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastConversationStartTime < conversationStartDebounceMs) {
            println("[Conversation] Ignoring rapid call (debounced)")
            isStartingConversation.set(false)
            return
        }
        
        // Check if already active
        if (_isConversationActive.value) {
            println("[Conversation] Already active - ignoring duplicate call")
            isStartingConversation.set(false)
            return
        }
        
        lastConversationStartTime = currentTime

        println("[Conversation] Starting conversation agent")
        // Ensure we're in conversation mode
        _isConversationMode.value = true
        _conversationTurns.value = emptyList()
        _conversationError.value = null
        
        // Initialize session recording
        currentSessionId = java.util.UUID.randomUUID().toString()
        sessionStartTime = System.currentTimeMillis()
        sessionAudioChunks.clear()

        viewModelScope.launch {
            try {
                conversationMutex.withLock {
                    val languageCode = when (_voiceTutorLanguage.value) {
                        PracticeLanguage.ENGLISH -> "english"
                        PracticeLanguage.FRENCH -> "french"
                        PracticeLanguage.GERMAN -> "german"
                        PracticeLanguage.HANGEUL -> "korean"
                        PracticeLanguage.MANDARIN -> "mandarin"
                        PracticeLanguage.SPANISH -> "spanish"
                        else -> "english"
                    }

                    // Get Deepgram API key from environment
                    val apiKey = agentService.getApiKey() ?: System.getenv("DEEPGRAM_API_KEY")
                    if (apiKey == null) {
                        _conversationError.value = "DEEPGRAM_API_KEY not set in environment"
                        // Don't reset conversation mode - keep UI stable
                        println("[Conversation] DEEPGRAM_API_KEY not found in environment")
                        isStartingConversation.set(false)
                        return@withLock
                    }
                    
                    val result = agentService.startConversation(
                        apiKey = apiKey,
                        language = languageCode,
                        level = _voiceTutorLevel.value ?: "intermediate",
                        scenario = _voiceTutorScenario.value ?: "daily_conversation"
                    ) { message: AgentApiService.AgentMessage ->
                        // Update UI state on Main dispatcher for immediate responsiveness
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            when (message.type) {
                                "connection" -> {
                                    if (message.event == "opened") {
                                        println("[Conversation] Agent connection established")
                                        _isConversationActive.value = true
                                    }
                                }
                                "ConversationText" -> {
                                    println("[Conversation] Text - role=${message.role}, content=${message.content?.take(50)}...")
                                    message.content?.let { content ->
                                        _conversationTurns.value = _conversationTurns.value + ConversationTurnUI(
                                            role = message.role ?: "assistant",
                                            text = content
                                        )
                                    }
                                }
                                "agent_message" -> {
                                    when (message.event) {
                                        "Welcome" -> {
                                            println("[Conversation] Agent ready - activating UI")
                                            _isConversationActive.value = true
                                        }
                                        "ConversationText" -> {
                                            println("[Conversation] Text - role=${message.role}, content=${message.content?.take(50)}...")
                                            message.content?.let { content ->
                                                _conversationTurns.value = _conversationTurns.value + ConversationTurnUI(
                                                    role = message.role ?: "assistant",
                                                    text = content
                                                )
                                            }
                                        }
                                        "AgentThinking" -> {
                                            println("[Conversation] Agent is thinking")
                                            _isAgentSpeaking.value = true
                                        }
                                        "AgentAudioDone" -> {
                                            println("[Conversation] Agent finished speaking")
                                            _isAgentSpeaking.value = false
                                        }
                                        "error" -> {
                                            println("[Conversation] Error: ${message.error}")
                                            _conversationError.value = message.error
                                        }
                                    }
                                }
                                "error" -> {
                                    println("[Conversation] Error: ${message.error}")
                                    _conversationError.value = message.error
                                }
                            }
                        }
                    }

                    if (result.isFailure) {
                        _conversationError.value = result.exceptionOrNull()?.message
                        _isConversationMode.value = false
                        println("[Conversation] Failed to start: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                _conversationError.value = e.message
                _isConversationMode.value = false
                println("[Conversation] Error: ${e.message}")
            } finally {
                isStartingConversation.set(false)
            }
        }
    }


    /**
     * Exit conversation mode UI (switches back to practice mode).
     * Also stops any active conversation.
     */
    fun exitConversationMode() {
        println("[Conversation] Exiting conversation mode")
        
        // If conversation is active, stop it first
        if (_isConversationActive.value || agentService.isActive()) {
            stopConversationMode()
        } else {
            // Just reset UI state
            _isConversationMode.value = false
            _conversationTurns.value = emptyList()
            _conversationError.value = null
        }
    }
    
    /**
     * Stop the active conversation agent.
     * Called when user clicks "End Conversation" button.
     */
    fun stopConversationMode() {
        // Atomic guard to prevent duplicate stops
        if (!isStoppingConversation.compareAndSet(false, true)) {
            println("[Conversation] Stop already in progress - ignoring")
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
                println("[Conversation] Stopping conversation agent")
                
                // Stop recording first if active
                if (_isConversationRecording.value || isRecordingLocked.get()) {
                    println("[Conversation] Stopping active recording first")
                    recordingStreamJob?.cancel()
                    recordingStreamJob = null
                    voiceRecorder.cancelRecording()
                    _isConversationRecording.value = false
                    isRecordingLocked.set(false)
                }
                
                conversationMutex.withLock {
                    val result = agentService.stopConversation()
                    if (result.isSuccess) {
                        println("[Conversation] Agent stopped successfully")
                    } else {
                        println("[Conversation] Failed to stop agent: ${result.exceptionOrNull()?.message}")
                    }
                }
                
                // Save session to database and storage
                saveConversationSession()
            } catch (e: Exception) {
                println("[Conversation] Error stopping: ${e.message}")
            } finally {
                // Reset conversation state but stay in conversation mode
                _isConversationActive.value = false
                _isAgentSpeaking.value = false
                _isConversationRecording.value = false
                isRecordingLocked.set(false)
                isStoppingConversation.set(false)
                println("[Conversation] Agent stopped - ready to restart")
            }
        }
    }

    fun sendConversationAudio(audioFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioBytes = audioFile.readBytes()
                val result = agentService.sendAudio(audioBytes)
                if (result.isFailure) {
                    println("[ConversationAudio] Failed to send audio: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                println("[ConversationAudio] Error sending audio: ${e.message}")
            }
        }
    }
    
    /**
     * Start conversation recording (no-op - audio is now continuous and automatic).
     * Kept for backward compatibility with UI.
     */
    fun startConversationRecording() {
        println("[ConversationRecording] Audio capture is automatic - no action needed")
    }
    
    /**
     * Stop conversation recording (no-op - audio is now continuous and automatic).
     * Kept for backward compatibility with UI.
     */
    fun stopConversationRecording() {
        println("[ConversationRecording] Audio capture is automatic - no action needed")
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
                println("[SessionSave] Saving conversation session: $sessionId")
                
                // Calculate session duration
                val duration = (System.currentTimeMillis() - sessionStartTime) / 1000f
                
                // Combine audio chunks into single file
                val audioFile = withContext(Dispatchers.IO) {
                    val audioData = synchronized(sessionAudioChunks) {
                        if (sessionAudioChunks.isEmpty()) {
                            null
                        } else {
                            val totalSize = sessionAudioChunks.sumOf { it.size }
                            ByteArray(totalSize).also { combined ->
                                var offset = 0
                                for (chunk in sessionAudioChunks) {
                                    chunk.copyInto(combined, offset)
                                    offset += chunk.size
                                }
                            }
                        }
                    }
                    
                    if (audioData != null && audioData.isNotEmpty()) {
                        // Save to temporary WAV file
                        val tempFile = File.createTempFile("session_${sessionId}", ".wav")
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
                        println("[SessionSave] Created audio file: ${tempFile.length()} bytes")
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
                val saveResult = voiceApiService.saveSession(
                    userId = "current_user", // TODO: Get from auth
                    language = language.name.lowercase(),
                    level = level,
                    scenario = scenario,
                    transcript = transcript,
                    audioUrl = null, // Will be set after upload
                    feedback = mapOf(
                        "turns" to _conversationTurns.value.size,
                        "duration" to duration
                    ),
                    sessionDuration = duration
                )
                
                if (saveResult.isSuccess) {
                    println("[SessionSave] Session saved successfully")
                    
                    // TODO: Upload audio file to Supabase Storage
                    // This would be done through a backend endpoint to get signed URL
                    audioFile?.delete()
                } else {
                    println("[SessionSave] Failed to save session: ${saveResult.exceptionOrNull()?.message}")
                    audioFile?.delete()
                }
                
                // Clear session data
                synchronized(sessionAudioChunks) {
                    sessionAudioChunks.clear()
                }
                
            } catch (e: Exception) {
                println("[SessionSave] Error saving session: ${e.message}")
                e.printStackTrace()
            }
        }
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
