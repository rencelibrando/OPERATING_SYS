package org.example.project.core.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.Closeable
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.*
import javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED

class AgentApiService : Closeable {
    
    private inline fun logInfo(crossinline message: () -> String) {
        if (LOG_ENABLED) println("[AgentService] ${message()}")
    }
    
    private inline fun logDebug(crossinline message: () -> String) {
        if (LOG_DEBUG_ENABLED) println("[AgentService] ${message()}")
    }
    
    private inline fun logError(tag: String = "ERROR", crossinline message: () -> String) {
        println("[AgentService][$tag] ${message()}")
    }
    
    
    
    
    
    // ==================== STATE MACHINE ====================
    private inner class StateMachine {
        private val lock = Any()
        private var _state = ConnectionState.DISCONNECTED
        
        fun transition(from: ConnectionState, to: ConnectionState): Boolean = synchronized(lock) {
            if (_state == from) {
                _state = to
                true
            } else false
        }
        
        fun get(): ConnectionState = synchronized(lock) { _state }
        fun set(state: ConnectionState) = synchronized(lock) { _state = state }
        
        fun isConnectedOrReady(): Boolean = synchronized(lock) {
            _state == ConnectionState.CONNECTED || _state == ConnectionState.READY
        }
    }
    
    private val stateMachine = StateMachine()
    
    
    
    
    
    companion object {
        // Logging configuration
        private const val LOG_ENABLED = true
        private const val LOG_DEBUG_ENABLED = true
        
        // API Endpoints
        private const val DEEPGRAM_AGENT_URL = "wss://agent.deepgram.com/v1/agent/converse"
        private const val DEEPGRAM_STT_URL = "wss://api.deepgram.com/v1/listen"
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        
        // Connection settings
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val KEEP_ALIVE_INTERVAL_MS = 8000L // 8 seconds - balance between timeout prevention and less interruption
        private const val SOCKET_TIMEOUT_MS = 60_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
        
        // Audio settings
        private const val SAMPLE_RATE = 24000f
        private const val AUDIO_BUFFER_SIZE = 262144 // 256KB
        
        // KeepAlive message (constant to avoid recreation)
        private const val KEEP_ALIVE_MSG = """{"type":"KeepAlive"}"""
        
        // Silent audio buffer for keep-alive (100ms of silence at 24kHz)
        private val SILENT_AUDIO_BUFFER = ByteArray(2400) // 100ms at 24kHz (24000 * 0.1 * 2 bytes/sample)
        
        // Grace period after agent audio done to allow local buffer to drain
        private const val AUDIO_DRAIN_GRACE_PERIOD_MS = 3000L
    }
    
    private val mainJob = SupervisorJob()
    private val scope = CoroutineScope(mainJob + Dispatchers.IO)
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 30_000
            maxFrameSize = 2_097_152 // 2MB frames for audio chunks
        }
        engine {
            requestTimeout = 0
            endpoint {
                connectTimeout = CONNECT_TIMEOUT_MS
                socketTimeout = SOCKET_TIMEOUT_MS
                connectAttempts = 5
            }
        }
    }
    
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    
    private var currentSession: DefaultClientWebSocketSession? = null
    private var sttSession: DefaultClientWebSocketSession? = null
    private var sourceDataLine: SourceDataLine? = null
    private var targetDataLine: TargetDataLine? = null
    private var onMessageCallback: ((AgentMessage) -> Unit)? = null
    private var onAudioReceivedCallback: ((ByteArray) -> Unit)? = null
    
    // Audio feedback prevention
    private val isAgentPlayingAudio = AtomicBoolean(false)
    private var audioPlaybackJob: kotlinx.coroutines.Job? = null
    
    // Grace period after agent audio done to allow local buffer to drain
    private val agentAudioDoneTimestamp = java.util.concurrent.atomic.AtomicLong(0L)
    
    // Edge TTS is now handled by the Python backend
    // No need for Edge TTS service in the Kotlin client
    
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, READY }
    val connectionState = AtomicReference(ConnectionState.DISCONNECTED)
    
    private val agentReady = AtomicBoolean(false)

    private var audioCaptureJob: kotlinx.coroutines.Job? = null
    private val isUserSpeaking = AtomicBoolean(false)
    private var keepAliveJob: kotlinx.coroutines.Job? = null
    
    private val sessionLock = Any()
    
    // TTS configuration for current session
    private var currentTtsModel: String = ""
    private var currentLanguage: String = ""
    // Edge TTS is now handled by the Python backend
    private var useBackendTts: Boolean = false
    
    // Auto-reconnection state for mid-session drops (thread-safe)
    private val reconnectAttempts = AtomicInteger(0)
    private var lastSessionParams: SessionParams? = null
    
    private data class SessionParams(
        val apiKey: String,
        val language: String,
        val level: String,
        val scenario: String,
        val onMessage: (AgentMessage) -> Unit,
        val onAudioReceived: ((ByteArray) -> Unit)?
    )
    
    private data class CustomPipelineConfig(
        val apiKey: String,
        val language: String,
        val level: String,
        val scenario: String
    )
    private var customPipelineConfig: CustomPipelineConfig? = null

    data class AgentMessage(
        val type: String,
        val event: String? = null,
        val content: String? = null,
        val role: String? = null,
        val error: String? = null,
        val description: String? = null,
        val code: String? = null
    )

    private fun shouldUseCustomPipeline(language: String): Boolean {
        // Use Edge STT pipeline (backend) for Chinese and Korean
        // All other languages use Deepgram Agent
        val languageLower = language.lowercase()
        logDebug { "Checking language '$language' (lowercase: '$languageLower')" }
        val result = languageLower in listOf("chinese", "mandarin", "zh", "korean", "hangeul", "ko")
        logDebug { "Pipeline selection for '$language': ${if (result) "EDGE_STT" else "DEEPGRAM"}" }
        return result
    }

    suspend fun startConversation(
        apiKey: String,
        language: String,
        level: String,
        scenario: String,
        onMessage: (AgentMessage) -> Unit,
        onAudioReceived: ((ByteArray) -> Unit)? = null
    ): Result<String> {
        // Basic validation
        if (apiKey.isBlank() || language.isBlank() || scenario.isBlank()) {
            return Result.failure(IllegalArgumentException("API key, language, and scenario cannot be blank"))
        }
        
        return try {
            logInfo { "Starting conversation - language: '$language', level: '$level', scenario: '$scenario'" }
            
            // Use state machine for thread-safe state transitions
            if (!stateMachine.transition(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
                val currentState = stateMachine.get()
                logInfo { "Already $currentState - ignoring duplicate start call" }
                return Result.failure(Exception("Connection already in progress or active"))
            }
            
            // Also update the atomic reference for backward compatibility
            connectionState.set(ConnectionState.CONNECTING)
            
            agentReady.set(false)
            onMessageCallback = onMessage
            onAudioReceivedCallback = onAudioReceived
            
            // Store session params for auto-reconnection on mid-session drops
            lastSessionParams = SessionParams(apiKey, language, level, scenario, onMessage, onAudioReceived)
            reconnectAttempts.set(0)  // Reset reconnect counter for new session
            
            if (shouldUseCustomPipeline(language)) {
                logInfo { "Using custom pipeline for $language" }
                return startCustomConversation(apiKey, language, level, scenario)
            }
            
            logInfo { "Connecting to Deepgram Agent V1 API" }
            logDebug { "API Key: ${apiKey.take(10)}..." }
            logDebug { "Target: $DEEPGRAM_AGENT_URL" }
            
            var lastException: Exception? = null
            val maxRetries = 5
            
            for (attempt in 1..maxRetries) {
                try {
                    logInfo { "Connection attempt $attempt/$maxRetries" }
                    currentSession = client.webSocketSession(DEEPGRAM_AGENT_URL) {
                        header("Authorization", "Token $apiKey")
                    }
                    logInfo { "WebSocket connected successfully" }
                    break
                } catch (e: Exception) {
                    lastException = e
                    logError("Connection") { "Attempt $attempt failed: ${e.message}" }
                    if (attempt < maxRetries) {
                        delay(1000L * attempt) // Simple linear backoff
                    }
                }
            }
            
            if (currentSession == null) {
                throw lastException ?: Exception("Failed to connect after $maxRetries attempts")
            }

            logInfo { "WebSocket connected" }
            
            // Initialize audio playback for 24kHz (Deepgram output format)
            val audioFormat = AudioFormat(PCM_SIGNED, 24000f, 16, 1, 2, 24000f, false)
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            sourceDataLine?.open(audioFormat, AUDIO_BUFFER_SIZE)
            sourceDataLine?.start()
            logInfo { "Audio playback initialized with ${AUDIO_BUFFER_SIZE / 1024}KB buffer at 24000Hz" }
            
            // Reset audio state for new conversation
            resetAudioState()
            
            val systemPrompt = createSystemPrompt(language, level, scenario)

            val (langCode, ttsModel, greetings) = getLanguageConfig(language)
            // Use a contextual greeting based on language, level, and scenario
            val welcomeMessage = getContextualGreeting(language, level, scenario, greetings)
            
            // Determine if we need to use backend TTS for TTS
            val shouldUseBackendTts = ttsModel.startsWith("backend_tts_")
            // Use appropriate TTS model based on language
            val actualTtsModel = if (shouldUseBackendTts) {
                // For Chinese/Korean, we'll use backend Edge TTS so no Deepgram TTS model needed
                "aura-2-helena-en" // Fallback, won't be used when useBackendTts=true
            } else {
                ttsModel // Use the configured Deepgram TTS model
            }
            
            // Store TTS configuration for this session
            currentTtsModel = ttsModel
            currentLanguage = language
            useBackendTts = shouldUseBackendTts
            
            // Backend TTS is handled by Python service, no reset needed
            
            val settings = AgentV1SettingsMessage(
                audio = AgentV1AudioConfig(
                    input = AgentV1AudioInput(
                        encoding = "linear16",
                        sampleRate = 24000  // Deepgram default for best quality
                    ),
                    output = AgentV1AudioOutput(
                        encoding = "linear16",
                        sampleRate = 24000, // Deepgram default 24kHz
                        container = "none"  // Deepgram sends raw PCM, not WAV containers
                    )
                ),
                agent = AgentV1Agent(
                    language = langCode, // Use correct language for STT (zh, ko, en, etc.)
                    listen = AgentV1Listen(
                        provider = AgentV1ListenProvider(
                            type = "deepgram",
                            model = "nova-3",
                            smartFormat = true  // Improves transcript readability for UI display
                        )
                    ),
                    think = AgentV1Think(
                        provider = AgentV1ThinkProvider(
                            type = "open_ai",  // Using OpenAI for thinking (with underscore)
                            model = "gpt-4o-mini",
                            temperature = 0.7f
                        ),
                        prompt = systemPrompt
                    ),
                    // Only include speak config if NOT using backend TTS
                    speak = if (!shouldUseBackendTts) {
                        AgentV1SpeakProviderConfig(
                            provider = AgentV1SpeakProvider(
                                type = "deepgram",
                                model = actualTtsModel
                            )
                        )
                    } else {
                        null
                    },
                    greeting = welcomeMessage
                )
            )

            val settingsJson = json.encodeToString(settings)
            logInfo { "Sending Settings message" }
            logDebug { "Configuration: model=nova-3, STT language=$langCode, TTS=$actualTtsModel (useBackendTts=$useBackendTts)" }
            currentSession?.send(Frame.Text(settingsJson))

            connectionState.set(ConnectionState.CONNECTED)
            stateMachine.set(ConnectionState.CONNECTED)
            
            // Start keep-alive job to send silent audio when user isn't speaking
            startKeepAlive()
            
            // Simple message receiving loop
            scope.launch {
                try {
                    for (frame in currentSession!!.incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                handleTextMessage(frame.readText())
                            }
                            is Frame.Binary -> {
                                val audioData = frame.readBytes()
                                if (!useBackendTts && sourceDataLine?.isOpen == true) {
                                    sourceDataLine?.write(audioData, 0, audioData.size)
                                    onAudioReceivedCallback?.invoke(audioData)
                                }
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        logError("MessageReceiver") { "Error: ${e.message}" }
                        connectionState.set(ConnectionState.DISCONNECTED)
                        stateMachine.set(ConnectionState.DISCONNECTED)
                        agentReady.set(false)
                        onMessage(AgentMessage(
                            type = "error",
                            error = "Connection error: ${e.message}"
                        ))
                    }
                }
            }

            Result.success("deepgram-session")
        } catch (e: Exception) {
            logError("StartConversation") { "Failed: ${e.message}" }
            connectionState.set(ConnectionState.DISCONNECTED)
            stateMachine.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            Result.failure(e)
        }
    }
    
    private fun openMicrophoneForConversation() {
        try {
            val audioFormat = AudioFormat(
                PCM_SIGNED,
                24000f,  // Deepgram recommended 24kHz
                16,
                1,
                2,
                24000f,
                false
            )
            
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            if (!AudioSystem.isLineSupported(info)) {
                logError("Microphone") { "Microphone not supported" }
                return
            }
            
            targetDataLine = AudioSystem.getLine(info) as TargetDataLine
            targetDataLine?.open(audioFormat)
            logInfo { "Microphone opened at 24kHz" }
        } catch (e: Exception) {
            logError("Microphone") { "Failed to open: ${e.message}" }
        }
    }
    
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (stateMachine.isConnectedOrReady()) {
                try {
                    delay(KEEP_ALIVE_INTERVAL_MS)
                    
                    // Only send KeepAlive when both user and agent are not speaking
                    if (!isUserSpeaking.get() && !isAgentPlayingAudio.get()) {
                        currentSession?.send(Frame.Text(KEEP_ALIVE_MSG))
                        logDebug { "Sent KeepAlive message (both parties silent)" }
                    } else {
                        logDebug { "Skipping KeepAlive - user speaking: ${isUserSpeaking.get()}, agent speaking: ${isAgentPlayingAudio.get()}" }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        logError("KeepAlive") { "Error: ${e.message}" }
                    }
                    break
                }
            }
        }
    }
    
    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }
    
    /**
     * Attempt to reconnect after a mid-session connection drop.
     * Uses exponential backoff with jitter: 1s, 2s, 4s, 8s (max 5 attempts).
     */
    private suspend fun attemptReconnect() {
        val params = lastSessionParams ?: return
        
        val attempt = reconnectAttempts.incrementAndGet()
        val baseDelay = minOf(1000L * (1L shl (attempt - 1)), 8000L)
        val jitter = (0..500).random().toLong()
        val backoffDelay = baseDelay + jitter
        
        logInfo { "Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS in ${backoffDelay}ms" }
        
        // Clean up current session
        connectionState.set(ConnectionState.DISCONNECTED)
        stateMachine.set(ConnectionState.DISCONNECTED)
        stopKeepAlive()
        try { currentSession?.close() } catch (_: Exception) {}
        currentSession = null
        
        delay(backoffDelay)
        
        params.onMessage(AgentMessage(
            type = "agent_message",
            event = "Reconnecting",
            content = "Reconnecting... (attempt $attempt/$MAX_RECONNECT_ATTEMPTS)"
        ))
        
        val result = startConversation(
            apiKey = params.apiKey,
            language = params.language,
            level = params.level,
            scenario = params.scenario,
            onMessage = params.onMessage,
            onAudioReceived = params.onAudioReceived
        )
        
        if (result.isSuccess) {
            logInfo { "Reconnection successful" }
            reconnectAttempts.set(0)
            params.onMessage(AgentMessage(
                type = "agent_message",
                event = "Reconnected",
                content = "Connection restored"
            ))
        } else {
            logError("Reconnect") { "Failed: ${result.exceptionOrNull()?.message}" }
            if (attempt >= MAX_RECONNECT_ATTEMPTS) {
                params.onMessage(AgentMessage(
                    type = "error",
                    error = "Failed to reconnect after $MAX_RECONNECT_ATTEMPTS attempts. Please restart the conversation."
                ))
                lastSessionParams = null
            }
        }
    }
    
    fun startManualAudioCapture() {
        if (stateMachine.get() != ConnectionState.READY || targetDataLine == null) {
            logDebug { "Cannot start audio capture - state: ${stateMachine.get()}, mic: ${targetDataLine != null}" }
            return
        }
        
        // For push-to-talk, allow user to interrupt agent if needed
        if (isAgentPlayingAudio.get()) {
            logDebug { "User interrupting agent - allowing push-to-talk" }
            isAgentPlayingAudio.set(false) // User takes priority in push-to-talk
        }
        
        logInfo { "User started speaking" }
        isUserSpeaking.set(true)
        targetDataLine?.start()
        
        // Use buffer pool to avoid allocations in hot path
        val bufferSize = 2400 // 50ms at 24kHz
        
        audioCaptureJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            val line = targetDataLine ?: return@launch
            var audioBytesSent = 0L
            
            try {
                while (isUserSpeaking.get() && line.isOpen) {
                    try {
                        val bytesRead = line.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            audioBytesSent += bytesRead
                            // Send only needed portion - Frame.Binary handles the range
                            currentSession?.send(Frame.Binary(true, buffer.copyOf(bytesRead)))
                            
                            if (audioBytesSent % 120000 == 0L) {
                                logDebug { "Sent ${audioBytesSent / 1000}KB audio" }
                            }
                        }
                        kotlinx.coroutines.delay(5)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        logDebug { "Audio capture cancelled normally" }
                        break
                    } catch (e: Exception) {
                        logError("AudioCapture") { "Error: ${e.message}" }
                        break
                    }
                }
                logInfo { "Audio capture ended. Total: ${audioBytesSent / 1000}KB" }
            } catch (e: kotlinx.coroutines.CancellationException) {
                logDebug { "Audio capture job cancelled" }
            } finally {
                // Buffer cleanup not needed for ByteArray
            }
        }
    }
    
    fun stopManualAudioCapture() {
        logInfo { "User stopped speaking" }
        isUserSpeaking.set(false)
        
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        
        targetDataLine?.stop()
        targetDataLine?.flush()
        
        logDebug { "Audio capture stopped cleanly" }
    }
    
    private fun stopAudioCapture() {
        logDebug { "Stopping audio capture" }
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        targetDataLine?.stop()
        targetDataLine?.close()
        targetDataLine = null
    }
    
    private fun getLanguageConfig(language: String): Triple<String, String, List<String>> {
        return when (language.lowercase()) {
            "english", "en" -> Triple(
                "en",
                "aura-2-helena-en", // Using Deepgram TTS for English
                listOf(
                    "Hello! I'm your English tutor. I'll help you learn English in a fun and engaging way!",
                    "Hi there! Ready to learn English? I'm here to guide you through every step!",
                    "Welcome! Let's make learning English enjoyable together. Feel free to ask me anything!"
                )
            )
            "french", "fr" -> Triple(
                "en",
                "aura-2-helena-en", // Using Deepgram TTS for French (taught in English)
                listOf(
                    "Hello! I'm your French language tutor. I'll teach you about French language and culture in English!",
                    "Hi there! Ready to learn French? I'll guide you through French vocabulary, grammar, and phrases!",
                    "Welcome! I'm here to help you understand French. Try speaking some French and I'll help you improve!",
                    "Hey! Let's explore the French language together. I'll explain everything in English!"
                )
            )
            "german", "de" -> Triple(
                "en",
                "aura-2-helena-en", // Using Deepgram TTS for German (taught in English)
                listOf(
                    "Hello! I'm your German language tutor. I'll teach you about German in English!",
                    "Hi! Ready to learn German? I'll explain German grammar, vocabulary, and culture in English!",
                    "Welcome! Let's discover the German language together. I'll guide you every step of the way!",
                    "Hey! I'm here to help you learn German through clear English explanations!"
                )
            )
            "korean", "hangeul", "ko" -> Triple(
                "ko",
                "backend_tts_korean", // Using Python backend Edge TTS for Korean
                listOf(
                    "안녕하세요! 한국어 공부를 도와드릴게요. 편하게 말씀해 주세요!",
                    "환영합니다! 한국어 연습할 준비 되셨나요? 시작해 볼까요?",
                    "안녕! 함께 한국어 배워봐요. 언제든지 말해도 돼요!",
                    "반갑습니다! 저는 한국어 선생님이에요. 대화를 시작해 볼까요?"
                )
            )
            "mandarin", "chinese", "mandarin chinese", "zh" -> Triple(
                "zh",
                "backend_tts_chinese", // Using Python backend Edge TTS for Chinese
                listOf(
                    "你好！我很高兴帮你学中文。随时可以开始说话！",
                    "欢迎！准备好练习中文了吗？我在听！",
                    "嗨！让我们一起学中文吧。开始说吧！",
                    "你好呀！我是你的中文老师。我们现在可以开始了！"
                )
            )
            "spanish", "es" -> Triple(
                "en",
                "aura-2-helena-en", // Using Deepgram TTS for Spanish (taught in English)
                listOf(
                    "Hello! I'm your Spanish language tutor. I'll teach you about Spanish in English!",
                    "Hi! Ready to learn Spanish? I'll explain Spanish vocabulary, grammar, and culture in English!",
                    "Welcome! Let's explore the Spanish language together. I'm here to help!",
                    "Hey! I'll help you learn Spanish through clear and friendly English explanations!"
                )
            )
            else -> Triple(
                "en",
                "aura-2-helena-en", // Using Deepgram TTS for default
                listOf("Hello! I'm ready to help you learn. Just start speaking naturally!")
            )
        }
    }
    
    private fun createSystemPrompt(language: String, level: String, scenario: String): String {
        val languageName = language.replaceFirstChar { it.uppercase() }
        
        val scenarioInstructions = when (scenario) {
            "conversation_partner" -> "Engage in natural, flowing conversation. Ask open-ended questions and share experiences."
            "travel" -> "Help practice travel situations: hotels, restaurants, directions, asking for help."
            "work" -> "Practice professional conversations: meetings, emails, presentations, networking."
            "daily_conversation" -> "Practice everyday conversations about daily life, hobbies, weather, family."
            else -> "Help practice speaking naturally and build confidence."
        }
        
        val levelInstructions = when (level) {
            "beginner" -> "Use very simple vocabulary and short sentences. Speak VERY SLOWLY and clearly with pauses between phrases. Provide lots of encouragement and positive reinforcement. Repeat important words if needed."
            "intermediate" -> "Use everyday vocabulary with some complex structures. Speak slowly and deliberately with clear pronunciation. Correct errors gently and explain briefly. Encourage longer, more detailed responses."
            "advanced" -> "Use natural, complex language with idioms and expressions. Speak at a measured pace with clear articulation. Focus on fluency, nuance, and natural expression. Challenge them with more sophisticated topics."
            else -> "Adjust your language complexity to match the student's responses and level. Speak slowly and clearly for better comprehension."
        }
        
        return """
            You are a friendly, encouraging, and patient $languageName language tutor helping a student who is actively learning $languageName.
            
            CRITICAL INSTRUCTION - LANGUAGE USAGE:
            - ALWAYS speak in ENGLISH when responding to the student
            - You are an English-speaking tutor who TEACHES ABOUT $languageName
            - When the student tries to practice $languageName, respond in English and explain/teach about what they said
            - If they ask questions in $languageName, answer in English while explaining the $languageName context
            
            IMPORTANT CONTEXT:
            - The student is learning $languageName as a foreign language
            - This is a language learning session where you teach ABOUT $languageName IN ENGLISH
            - Your primary goal is to help them understand $languageName concepts through English explanations
            - Encourage them to try speaking in $languageName, but YOU respond in English with helpful feedback
            
            Your role and approach:
            - $scenarioInstructions
            - When they speak in $languageName, praise their effort and explain what they said in English
            - Teach them $languageName vocabulary, grammar, and phrases by explaining in English
            - Give examples of $languageName phrases but explain their meaning in English
            - Correct mistakes by explaining in English: "Good try! In $languageName, we say [correct phrase]. This means [English translation]"
            - Ask follow-up questions in English to encourage more practice
            - Share cultural insights about $languageName-speaking regions in English
            - Be patient and supportive - learning a language is challenging
            
            Student's proficiency level: $level
            Teaching approach for this level: $levelInstructions
            
            Conversation style:
            - Keep responses in ENGLISH, warm, and conversational (2-4 sentences typically)
            - Speak SLOWLY and clearly with natural pauses between phrases
            - Explain $languageName concepts clearly in English
            - Provide $languageName examples with English translations
            - Show genuine interest in their learning progress
            - Make learning feel like a friendly conversation with an English-speaking tutor
            
            Remember: You're an English-speaking tutor helping them learn ABOUT $languageName through English instruction and explanations!
        """.trimIndent()
    }
    
    private fun getContextualGreeting(language: String, level: String, scenario: String, greetings: List<String>): String {
        // Short, tailored greeting based on scenario
        return when (scenario) {
            "travel" -> "Ready for travel practice?"
            "work" -> "Let's practice work conversations."
            "food" -> "Time to talk about food!"
            "culture" -> "Let's explore culture together."
            "daily_conversation" -> "Ready to chat?"
            else -> "Let's practice!"
        }
    }
    
    private fun handleTextMessage(text: String) {
        try {
            logDebug { "Received: ${if (text.length > 100) text.take(100) + "..." else text}" }
            
            val jsonObj = json.parseToJsonElement(text).jsonObject
            val type = jsonObj["type"]?.jsonPrimitive?.content ?: "Unknown"
            
            when (type) {
                "Welcome" -> {
                    logInfo { "Agent ready" }
                    onMessageCallback?.invoke(AgentMessage(type = "connection", event = "opened"))
                }
                "SettingsApplied" -> {
                    logInfo { "Settings applied - agent ready" }
                    agentReady.set(true)
                    connectionState.set(ConnectionState.READY)
                    stateMachine.set(ConnectionState.READY)
                    openMicrophoneForConversation()
                    logInfo { "Ready for push-to-talk" }
                    onMessageCallback?.invoke(AgentMessage(type = "agent_message", event = "Welcome"))
                }
                "ConversationText" -> {
                    val msg = json.decodeFromString<AgentV1ConversationTextMessage>(text)
                    logDebug { "ConversationText - role=${msg.role}, content=${msg.content.take(50)}..." }
                    
                    // Mark agent as speaking when assistant text arrives to prevent keepalive interruption
                    if (msg.role == "assistant") {
                        isAgentPlayingAudio.set(true)
                        logDebug { "Agent started speaking (from ConversationText)" }
                        // Set a timeout to auto-reset agent speaking state if no AgentAudioDone is received
                        scope.launch {
                            delay(5000) // 5 second timeout
                            if (isAgentPlayingAudio.get()) {
                                logDebug { "Agent speaking timeout - force resetting to allow user input" }
                                isAgentPlayingAudio.set(false)
                            }
                        }
                    }
                    
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "ConversationText",
                        role = msg.role,
                        content = msg.content
                    ))
                }
                "UserStartedSpeaking" -> {
                    logDebug { "User started speaking" }
                    isAgentPlayingAudio.set(false)
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "UserStartedSpeaking"
                    ))
                }
                "AgentThinking" -> {
                    logDebug { "Agent thinking" }
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "AgentThinking"
                    ))
                }
                "AgentStartedSpeaking" -> {
                    logDebug { "Agent started speaking" }
                    isAgentPlayingAudio.set(true)
                    agentAudioDoneTimestamp.set(0L) // Reset so grace period only applies after this session
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "AgentStartedSpeaking"
                    ))
                }
                "AgentAudioDone" -> {
                    logDebug { "Agent audio done" }
                    isAgentPlayingAudio.set(false)
                    agentAudioDoneTimestamp.set(System.currentTimeMillis())
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "AgentAudioDone"
                    ))
                }
                "Error" -> {
                    val msg = json.decodeFromString<AgentV1ErrorMessage>(text)
                    logError("API") { "code=${msg.code}, description=${msg.description}" }
                    onMessageCallback?.invoke(AgentMessage(
                        type = "error",
                        error = msg.description,
                        description = msg.description,
                        code = msg.code
                    ))
                }
                "Warning" -> {
                    val msg = json.decodeFromString<AgentV1WarningMessage>(text)
                    logDebug { "Warning: code=${msg.code}, description=${msg.description}" }
                }
            }
        } catch (e: Exception) {
            logError("MessageParsing") { "Error: ${e.message}" }
        }
    }
    
    // Audio playback now handled via audioQueue channel for non-blocking operation
    // This method kept for backward compatibility with direct calls
    private fun handleBinaryAudio(audioData: ByteArray) {
        if (useBackendTts) return
        val line = sourceDataLine ?: return
        if (!line.isOpen) return
        
        try {
            onAudioReceivedCallback?.invoke(audioData)
            if (!line.isActive) line.start()
            line.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            logError("AudioPlayback") { "Error: ${e.message}" }
        }
    }
    
    private fun resetAudioState() {
        logDebug { "Audio state reset" }
    }
    suspend fun sendAudio(audioData: ByteArray): Result<Unit> {
        return try {
            val session = synchronized(sessionLock) { currentSession }
                ?: return Result.failure(Exception("No session"))
            
            session.send(Frame.Binary(true, audioData))
            Result.success(Unit)
        } catch (e: Exception) {
            if (e.message?.contains("cancelled") == true || e.message?.contains("closed") == true) {
                logDebug { "Session lost, will reconnect on next use" }
                connectionState.set(ConnectionState.DISCONNECTED)
                stateMachine.set(ConnectionState.DISCONNECTED)
                agentReady.set(false)
            }
            Result.failure(e)
        }
    }

    suspend fun stopConversation(): Result<Unit> {
        return try {
            logInfo { "Stopping conversation" }
            
            connectionState.set(ConnectionState.DISCONNECTED)
            stateMachine.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            isAgentPlayingAudio.set(false)
            isUserSpeaking.set(false)
            
            // Stop all workers and tracked jobs
            stopKeepAlive()
            stopAudioCapture()
            
            audioPlaybackJob?.cancel()
            audioPlaybackJob = null
            
            // Close audio output
            try {
                sourceDataLine?.let { line ->
                    if (line.isOpen) {
                        line.stop()
                        line.close()
                    }
                }
            } catch (e: Exception) {
                logError("AudioClose") { "Output line: ${e.message}" }
            }
            sourceDataLine = null
            
            // Close audio input
            try {
                targetDataLine?.let { line ->
                    if (line.isOpen) {
                        line.stop()
                        line.close()
                    }
                }
            } catch (e: Exception) {
                logError("AudioClose") { "Input line: ${e.message}" }
            }
            targetDataLine = null
            
            // Close WebSocket sessions
            val agentSessionToClose = synchronized(sessionLock) {
                val session = currentSession
                currentSession = null
                session
            }
            
            try {
                agentSessionToClose?.close()
                logDebug { "Agent WebSocket closed" }
            } catch (e: Exception) {
                logError("WebSocket") { "Agent close error: ${e.message}" }
            }
            
            val sttSessionToClose = sttSession
            sttSession = null
            
            try {
                sttSessionToClose?.close()
                logDebug { "STT WebSocket closed" }
            } catch (e: Exception) {
                logError("WebSocket") { "STT close error: ${e.message}" }
            }
            
            customPipelineConfig = null
            onMessageCallback = null
            
            // Clear references
            
            logInfo { "Conversation stopped successfully" }
            Result.success(Unit)
        } catch (e: Exception) {
            logError("StopConversation") { "Error: ${e.message}" }
            connectionState.set(ConnectionState.DISCONNECTED)
            stateMachine.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            isAgentPlayingAudio.set(false)
            isUserSpeaking.set(false)
            stopAudioCapture()
            Result.failure(e)
        }
    }

    private suspend fun startCustomConversation(
        apiKey: String,
        language: String,
        level: String,
        scenario: String
    ): Result<String> {
        return try {
            // Connect to Gladia backend WebSocket
            val gladiaLanguage = when (language.lowercase()) {
                "chinese", "mandarin", "zh" -> "zh"
                "korean", "hangeul", "ko" -> "ko"
                else -> "auto"
            }
            
            val gladiaUrl = "ws://localhost:8000/gladia/stt?language=$gladiaLanguage"
            
            logInfo { "Connecting to Gladia backend: $gladiaUrl" }
            
            currentSession = client.webSocketSession(gladiaUrl)
            
            logInfo { "Gladia WebSocket connected" }
            
            // Initialize audio playback
            val audioFormat = AudioFormat(
                PCM_SIGNED,
                22050f, 16, 1, 2, 22050f, false
            )
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            sourceDataLine?.open(audioFormat, 65536)
            sourceDataLine?.start()
            logInfo { "Audio playback initialized at 22050Hz" }
            
            // Start listening for Gladia responses
            scope.launch {
                logDebug { "Starting Gladia message listener" }
                try {
                    while (true) {
                        val frame = currentSession?.incoming?.receive() ?: break
                        
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                logDebug { "Gladia message: ${text.take(100)}..." }
                                
                                try {
                                    val jsonMessage = json.parseToJsonElement(text).jsonObject
                                    val type = jsonMessage["type"]?.jsonPrimitive?.content
                                    
                                    when (type) {
                                        "connection" -> {
                                            logInfo { "Gladia connection established" }
                                            onMessageCallback?.invoke(AgentMessage(
                                                type = "agent_message",
                                                event = "Welcome"
                                            ))
                                        }
                                        "conversation_text" -> {
                                            val content = jsonMessage["content"]?.jsonPrimitive?.content
                                            val role = jsonMessage["role"]?.jsonPrimitive?.content
                                            val confidence = jsonMessage["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                                            val detectedLang = jsonMessage["language"]?.jsonPrimitive?.content
                                            
                                            logDebug { "Transcript: '$content' (confidence: $confidence, lang: $detectedLang)" }
                                            
                                            if (!content.isNullOrEmpty()) {
                                                if (confidence > 0.01) {
                                                    logDebug { "Processing transcript: '$content'" }
                                                    
                                                    // Send to UI
                                                    onMessageCallback?.invoke(AgentMessage(
                                                        type = "conversation_text",
                                                        content = content,
                                                        role = role
                                                    ))
                                                    
                                                    // Generate response using DeepSeek
                                                    scope.launch {
                                                        try {
                                                            val response = generateResponse(content, language, level, scenario)
                                                            logDebug { "Generated response: ${response.take(50)}..." }
                                                            
                                                            onMessageCallback?.invoke(AgentMessage(
                                                                type = "conversation_text",
                                                                content = response,
                                                                role = "assistant"
                                                            ))
                                                            
                                                            isAgentPlayingAudio.set(true)
                                                            onMessageCallback?.invoke(AgentMessage(
                                                                type = "agent_message",
                                                                event = "AgentStartedSpeaking"
                                                            ))
                                                            
                                                            logDebug { "Generating TTS" }
                                                            val audioUrl = generateEdgeTtsAudio(response, "en")
                                                            if (audioUrl != null) {
                                                                logDebug { "Playing response audio" }
                                                                onMessageCallback?.invoke(AgentMessage(
                                                                    type = "agent_message",
                                                                    event = "AgentAudio",
                                                                    content = response,
                                                                    role = "assistant"
                                                                ))
                                                                
                                                                playBackendAudio(audioUrl)
                                                            } else {
                                                                logError("TTS") { "Failed to generate response TTS" }
                                                            }
                                                            
                                                            isAgentPlayingAudio.set(false)
                                                            onMessageCallback?.invoke(AgentMessage(
                                                                type = "agent_message",
                                                                event = "AgentAudioDone"
                                                            ))
                                                        } catch (e: Exception) {
                                                            logError("DeepSeek") { "Error: ${e.message}" }
                                                        }
                                                    }
                                                } else {
                                                    logDebug { "Transcript confidence too low: $confidence" }
                                                }
                                            }
                                        }
                                        "agent_message" -> {
                                            val event = jsonMessage["event"]?.jsonPrimitive?.content
                                            val content = jsonMessage["content"]?.jsonPrimitive?.content
                                            
                                            if (event == "PartialTranscript" && !content.isNullOrEmpty()) {
                                                onMessageCallback?.invoke(AgentMessage(
                                                    type = "agent_message",
                                                    event = "PartialTranscript",
                                                    content = content,
                                                    role = "user"
                                                ))
                                            }
                                        }
                                        "language_detected" -> {
                                            val detectedLang = jsonMessage["language"]?.jsonPrimitive?.content
                                            logDebug { "Language detected: $detectedLang" }
                                        }
                                        "error" -> {
                                            val errorMsg = jsonMessage["message"]?.jsonPrimitive?.content
                                            logError("Gladia") { "Error: $errorMsg" }
                                        }
                                    }
                                } catch (e: Exception) {
                                    logError("GladiaMessage") { "Parse error: ${e.message}" }
                                }
                            }
                            is Frame.Close -> {
                                logInfo { "Gladia WebSocket closed" }
                                connectionState.set(ConnectionState.DISCONNECTED)
                                stateMachine.set(ConnectionState.DISCONNECTED)
                                break
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        logError("GladiaHandler") { "Error: ${e.message}" }
                        connectionState.set(ConnectionState.DISCONNECTED)
                        stateMachine.set(ConnectionState.DISCONNECTED)
                    }
                }
            }
            
            connectionState.set(ConnectionState.CONNECTED)
            stateMachine.set(ConnectionState.CONNECTED)
            agentReady.set(true)
            connectionState.set(ConnectionState.READY)
            stateMachine.set(ConnectionState.READY)
            
            customPipelineConfig = CustomPipelineConfig(apiKey, language, level, scenario)
            logInfo { "Gladia pipeline ready" }
            
            // Send greeting after connection is ready
            scope.launch {
                try {
                    val greeting = when (language.lowercase()) {
                        "zh", "chinese", "mandarin" -> "Ready to practice Chinese?"
                        "ko", "korean", "hangeul" -> "Ready to practice Korean?"
                        else -> "Ready to practice?"
                    }
                    
                    logDebug { "Sending greeting" }
                    
                    onMessageCallback?.invoke(AgentMessage(
                        type = "conversation_text",
                        content = greeting,
                        role = "assistant"
                    ))
                    
                    if (language.lowercase() in listOf("chinese", "mandarin", "zh", "korean", "hangeul", "ko")) {
                        logDebug { "Generating TTS for greeting" }
                        
                        isAgentPlayingAudio.set(true)
                        onMessageCallback?.invoke(AgentMessage(
                            type = "agent_message",
                            event = "AgentStartedSpeaking"
                        ))
                        
                        val audioUrl = generateEdgeTtsAudio(greeting, "en")
                        if (audioUrl != null) {
                            logDebug { "Playing greeting audio" }
                            onMessageCallback?.invoke(AgentMessage(
                                type = "agent_message",
                                event = "AgentAudio",
                                content = greeting,
                                role = "assistant"
                            ))
                            
                            playBackendAudio(audioUrl)
                        } else {
                            logError("TTS") { "Failed to generate greeting TTS" }
                        }
                        
                        isAgentPlayingAudio.set(false)
                        onMessageCallback?.invoke(AgentMessage(
                            type = "agent_message",
                            event = "AgentAudioDone"
                        ))
                    }
                    
                    logInfo { "Greeting sent successfully" }
                } catch (e: Exception) {
                    logError("Greeting") { "Error: ${e.message}" }
                }
            }
            
            Result.success("gladia-session")
        } catch (e: Exception) {
            logError("GladiaConversation") { "Error: ${e.message}" }
            Result.failure(e)
        }
    }
    
    private suspend fun playBackendAudio(audioUrl: String) {
        try {
            logDebug { "Downloading audio from backend" }
            
            val response = client.get(audioUrl)
            if (response.status.value == 200) {
                val audioData = response.readBytes()
                logDebug { "Audio downloaded: ${audioData.size} bytes" }
                
                val inputStream = java.io.ByteArrayInputStream(audioData)
                val audioInputStream = javax.sound.sampled.AudioSystem.getAudioInputStream(inputStream)
                
                val audioFormat = audioInputStream.format
                logDebug { "Audio format: ${audioFormat.sampleRate}Hz, ${audioFormat.sampleSizeInBits}bit" }
                
                if (audioFormat.sampleSizeInBits <= 0) {
                    logDebug { "Invalid audio format, using default conversion" }
                    // Try to convert to a known good format
                    val targetFormat = AudioFormat(
                        PCM_SIGNED,
                        22050f,
                        16,
                        1,
                        2,
                        22050f,
                        false
                    )
                    val convertedStream = javax.sound.sampled.AudioSystem.getAudioInputStream(targetFormat, audioInputStream)
                    
                    scope.launch {
                        try {
                            val info = DataLine.Info(SourceDataLine::class.java, targetFormat)
                            val line = AudioSystem.getLine(info) as SourceDataLine
                            line.open(targetFormat, 65536)
                            line.start()
                            
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (convertedStream.read(buffer).also { bytesRead = it } != -1) {
                                line.write(buffer, 0, bytesRead)
                            }
                            
                            line.drain()
                            line.close()
                            logDebug { "Audio playback completed" }
                        } catch (e: Exception) {
                            logError("AudioPlayback") { "Error: ${e.message}" }
                        } finally {
                            convertedStream.close()
                            audioInputStream.close()
                            inputStream.close()
                        }
                    }
                } else {
                    val targetFormat = AudioFormat(
                        PCM_SIGNED,
                        22050f,
                        16,
                        audioFormat.channels,
                        audioFormat.channels * 2,
                        22050f,
                        false
                    )
                    
                    val convertedStream = if (audioFormat != targetFormat) {
                        logDebug { "Converting audio format to PCM" }
                        javax.sound.sampled.AudioSystem.getAudioInputStream(targetFormat, audioInputStream)
                    } else {
                        audioInputStream
                    }
                    
                    scope.launch {
                        try {
                            val info = DataLine.Info(SourceDataLine::class.java, targetFormat)
                            val line = AudioSystem.getLine(info) as SourceDataLine
                            line.open(targetFormat, 65536)
                            line.start()
                            
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (convertedStream.read(buffer).also { bytesRead = it } != -1) {
                                line.write(buffer, 0, bytesRead)
                            }
                            
                            line.drain()
                            line.close()
                            logDebug { "Audio playback completed" }
                        } catch (e: Exception) {
                            logError("AudioPlayback") { "Error: ${e.message}" }
                        } finally {
                            convertedStream.close()
                            audioInputStream.close()
                            inputStream.close()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError("BackendAudio") { "Error: ${e.message}" }
        }
    }
    
    private suspend fun generateEdgeTtsAudio(text: String, language: String): String? {
        // Generate Edge TTS audio via backend
        return try {
            val edgeLanguage = when (language.lowercase()) {
                "chinese", "mandarin", "zh" -> "zh-CN"
                "korean", "hangeul", "ko" -> "ko-KR"
                else -> "en-US"
            }
            
            // Build JSON manually to avoid serialization issues
            val requestBody = """{"text":"$text","language":"$edgeLanguage","use_cache":true}"""
            
            val response = client.post("http://localhost:8000/tts/generate") {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                val jsonResponse = json.parseToJsonElement(responseBody)
                jsonResponse.jsonObject?.get("audio_url")?.jsonPrimitive?.content
            } else {
                logError("EdgeTTS") { "Error: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            logError("EdgeTTS") { "Generation error: ${e.message}" }
            null
        }
    }
    
    private suspend fun generateResponse(userMessage: String, language: String, level: String, scenario: String): String {
        return getAiResponse(userMessage, language, level, scenario)
    }
    
    private suspend fun generateTtsAudio(text: String, language: String): String? {
        logDebug { "TTS generation pending" }
        return null
    }
    
    fun startCustomManualAudioCapture() {
        val config = customPipelineConfig ?: return
        if (stateMachine.get() != ConnectionState.READY || audioCaptureJob?.isActive == true) {
            return
        }
        logInfo { "Starting custom audio capture" }
        startCustomAudioCapture(config.apiKey, config.language, config.level, config.scenario)
    }
    
    fun stopCustomManualAudioCapture() {
        logInfo { "Stopping custom audio capture" }
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        targetDataLine?.stop()
        targetDataLine?.flush()
        
        scope.launch {
            try {
                sttSession?.send(Frame.Text("{\"type\":\"CloseStream\"}"))
            } catch (e: Exception) {
                logError("STT") { "Error closing stream: ${e.message}" }
            }
        }
    }
    
    private fun startCustomAudioCapture(apiKey: String, language: String, level: String, scenario: String) {
        val audioFormat = AudioFormat(PCM_SIGNED, 16000f, 16, 1, 2, 16000f, false)
        val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
        targetDataLine = AudioSystem.getLine(info) as TargetDataLine
        targetDataLine?.open(audioFormat)
        targetDataLine?.start()
        
        val bufferSize = 1600 // 100ms at 16kHz
        
        audioCaptureJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            try {
                logInfo { "Starting audio capture for Gladia STT" }
                var audioBytesSent = 0L
                
                while (stateMachine.get() == ConnectionState.READY && targetDataLine?.isOpen == true) {
                    if (!isAgentPlayingAudio.get()) {
                        val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            audioBytesSent += bytesRead
                            currentSession?.send(Frame.Binary(true, buffer.copyOf(bytesRead)))
                            
                            if (audioBytesSent % 160000 == 0L) {
                                logDebug { "Sent ${audioBytesSent / 1000}KB to Gladia" }
                            }
                        }
                    } else {
                        delay(50)
                        targetDataLine?.flush()
                    }
                }
                logInfo { "Audio capture ended. Total: ${audioBytesSent / 1000}KB" }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    logError("CustomAudioCapture") { "Error: ${e.message}" }
                    onMessageCallback?.invoke(AgentMessage(
                        type = "Error",
                        description = "Audio capture failed: ${e.message}"
                    ))
                }
            } finally {
                // Buffer cleanup not needed for ByteArray
            }
        }
    }

    private suspend fun handleSttResult(result: String, language: String, level: String, scenario: String) {
        try {
            logDebug { "STT Raw result: ${result.take(100)}..." }
            
            val jsonResult = json.parseToJsonElement(result)
            val isFinal = jsonResult.jsonObject?.get("is_final")?.jsonPrimitive?.boolean == true
            val transcript = jsonResult.jsonObject?.get("channel")?.jsonObject
                ?.get("alternatives")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("transcript")?.jsonPrimitive?.content
            
            val confidence = jsonResult.jsonObject?.get("channel")?.jsonObject
                ?.get("alternatives")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("confidence")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            
            logDebug { "STT Parsed - isFinal: $isFinal, confidence: $confidence" }
            
            if (isFinal && !transcript.isNullOrEmpty()) {
                val cleanedTranscript = transcript.trim()
                
                if (cleanedTranscript.isEmpty() || confidence < 0.3) {
                    return
                }
                
                logDebug { "User: '${cleanedTranscript.take(50)}...' (confidence: $confidence)" }
                
                onMessageCallback?.invoke(AgentMessage(
                    type = "conversation_text",
                    content = cleanedTranscript,
                    role = "user"
                ))
                
                val response = getAiResponse(cleanedTranscript, language, level, scenario)
                logDebug { "Agent response: ${response.take(50)}..." }
                
                onMessageCallback?.invoke(AgentMessage(
                    type = "conversation_text",
                    content = response,
                    role = "agent"
                ))
                
                isAgentPlayingAudio.set(true)
                onMessageCallback?.invoke(AgentMessage(
                    type = "agent_message",
                    event = "AgentStartedSpeaking"
                ))
                
                logDebug { "Response generated, TTS handled by backend for $language" }
                
                isAgentPlayingAudio.set(false)
                onMessageCallback?.invoke(AgentMessage(
                    type = "agent_message",
                    event = "AgentAudioDone"
                ))
            }
        } catch (e: Exception) {
            logError("STT") { "Error: ${e.message}" }
        }
    }

    private suspend fun getAiResponse(userMessage: String, language: String, level: String, scenario: String): String {
        return try {
            // Use cached system prompt for better performance
            val systemPrompt = createSystemPrompt(language, level, scenario)
            
            val requestBody = json.encodeToString(mapOf(
                "model" to "gpt-4o-mini",
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userMessage)
                ),
                "temperature" to 0.7,
                "max_tokens" to 200
            ))
            
            val response: HttpResponse = client.post(OPENAI_URL) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${System.getenv("OPENAI_API_KEY") ?: ""}")
                setBody(requestBody)
            }
            
            if (response.status.value in 200..299) {
                val responseBody = response.bodyAsText()
                val jsonResponse = json.parseToJsonElement(responseBody)
                jsonResponse.jsonObject?.get("choices")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content ?: "I'm sorry, I couldn't generate a response."
            } else {
                logError("OpenAI") { "API error: ${response.status.value}" }
                "I'm sorry, I'm having trouble responding right now."
            }
        } catch (e: Exception) {
            logError("OpenAI") { "Response error: ${e.message}" }
            "I'm sorry, I encountered an error while generating a response."
        }
    }

    fun isActive(): Boolean {
        val state = connectionState.get()
        return state == ConnectionState.CONNECTED || state == ConnectionState.READY
    }

    override fun close() {
        logInfo { "Closing agent service" }
        
        // Stop all jobs
        keepAliveJob?.cancel()
        audioCaptureJob?.cancel()
        audioPlaybackJob?.cancel()
        
        // Stop conversation synchronously
        runBlocking {
            try {
                stopConversation()
            } catch (e: Exception) {
                logError("Close") { "Error stopping conversation: ${e.message}" }
            }
        }
        
        // Clean up audio resources
        try {
            sourceDataLine?.let { line ->
                if (line.isOpen) {
                    line.stop()
                    line.flush()
                    line.drain()
                    line.close()
                }
            }
            targetDataLine?.let { line ->
                if (line.isOpen) {
                    line.stop()
                    line.flush()
                    line.close()
                }
            }
        } catch (e: Exception) {
            logError("Close") { "Error closing audio lines: ${e.message}" }
        }
        
        // Clear references and reset state
        sourceDataLine = null
        targetDataLine = null
        isAgentPlayingAudio.set(false)
        lastSessionParams = null
        
        // Clear references
        
        // Cancel mainJob and wait for children with timeout
        mainJob.cancel()
        runBlocking {
            try {
                withTimeout(5000) {
                    mainJob.children.forEach { it.join() }
                }
            } catch (e: Exception) {
                logError("Close") { "Timeout waiting for jobs" }
            }
        }
        
        client.close()
        logInfo { "Agent service closed" }
    }
    
    fun getApiKey(): String? {
        return org.example.project.core.config.ApiKeyConfig.getDeepgramApiKey()
    }
}
