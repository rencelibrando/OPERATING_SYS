package org.example.project.core.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.*
import javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED

class AgentApiService : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 30_000
            // Increase max frame size for better audio streaming
            maxFrameSize = 2_097_152 // 2MB frames for audio chunks
        }
        engine {
            requestTimeout = 0
            endpoint {
                connectTimeout = 15_000 // Faster connection
                socketTimeout = 60_000 // Longer socket timeout for streaming
                connectAttempts = 5 // More retry attempts
            }
        }
    }
    
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false  // Exclude null fields from JSON
    }

    private val deepgramUrl = "wss://agent.deepgram.com/v1/agent/converse"
    private val deepgramSttUrl = "wss://api.deepgram.com/v1/listen"
    private val openAiUrl = "https://api.openai.com/v1/chat/completions"
    
    private var currentSession: DefaultClientWebSocketSession? = null
    private var sttSession: DefaultClientWebSocketSession? = null
    private var sourceDataLine: SourceDataLine? = null
    private var targetDataLine: TargetDataLine? = null
    private var onMessageCallback: ((AgentMessage) -> Unit)? = null
    private var onAudioReceivedCallback: ((ByteArray) -> Unit)? = null
    
    // Audio feedback prevention
    private val isAgentPlayingAudio = AtomicBoolean(false)
    private var audioPlaybackJob: kotlinx.coroutines.Job? = null
    
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
        println("[AgentService] DEBUG: Checking language '$language' (lowercase: '$languageLower')")
        val result = languageLower in listOf("chinese", "mandarin", "zh", "korean", "hangeul", "ko")
        println("[AgentService] DEBUG: Pipeline selection for language '$language' -> $result")
        println("[AgentService] Pipeline selection for language '$language': ${if (result) "EDGE_STT" else "DEEPGRAM"}")
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
        return try {
            println("[AgentService] Starting conversation with language: '$language' (original case)")
            println("[AgentService] Level: '$level', Scenario: '$scenario'")
            
            if (!connectionState.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
                val currentState = connectionState.get()
                println("[AgentService] Already $currentState - ignoring duplicate start call")
                return Result.failure(Exception("Connection already in progress or active"))
            }
            
            agentReady.set(false)
            onMessageCallback = onMessage
            onAudioReceivedCallback = onAudioReceived
            
            if (shouldUseCustomPipeline(language)) {
                println("[AgentService] Using custom pipeline for $language")
                return startCustomConversation(apiKey, language, level, scenario)
            }
            
            println("[AgentService] Connecting to Deepgram Agent V1 API")
            println("[AgentService] API Key: ${apiKey.take(10)}...")
            println("[AgentService] Target: $deepgramUrl")
            
            var lastException: Exception? = null
            val maxRetries = 3
            
            for (attempt in 1..maxRetries) {
                try {
                    println("[AgentService] Connection attempt $attempt/$maxRetries")
                    currentSession = client.webSocketSession(deepgramUrl) {
                        header("Authorization", "Token $apiKey")
                    }
                    println("[AgentService] ✅ WebSocket connected successfully")
                    break
                } catch (e: Exception) {
                    lastException = e
                    println("[AgentService] ⚠️ Attempt $attempt failed: ${e.message}")
                    if (attempt < maxRetries) {
                        val delay = attempt * 2000L
                        println("[AgentService] Retrying in ${delay}ms...")
                        delay(delay)
                    }
                }
            }
            
            if (currentSession == null) {
                throw lastException ?: Exception("Failed to connect after $maxRetries attempts")
            }

            println("[AgentService] WebSocket connected")
            
            val audioFormat = AudioFormat(
                PCM_SIGNED,
                16000f, // Reduced from 24000f to 16000f for slower playback
                16,
                1,
                2,
                16000f, // Reduced from 24000f to 16000f for slower playback
                false
            )
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            // Use much larger buffer for smoother streaming and reduce underruns
            sourceDataLine?.open(audioFormat, 131072) // 128KB buffer for optimal streaming
            sourceDataLine?.start()
            println("[AgentService] Audio playback initialized with 128KB buffer at 16000Hz for slower speech")
            
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
                        sampleRate = 24000
                    ),
                output = AgentV1AudioOutput(
                    encoding = "linear16",
                    sampleRate = 16000, // Reduced from 24000 to 16000 for slower playback
                    container = "none"  // Deepgram sends raw PCM, not WAV containers
                )
                ),
                agent = AgentV1Agent(
                    language = langCode, // Use correct language for STT (zh, ko, en, etc.)
                    listen = AgentV1Listen(
                        provider = AgentV1ListenProvider(
                            type = "deepgram",
                            model = "nova-3"
                        )
                    ),
                    think = AgentV1Think(
                        provider = AgentV1ThinkProvider(
                            type = "open_ai",
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
            println("[AgentService] Sending Settings message")
            println("[AgentService] Configuration: model=nova-3, STT language=$langCode, TTS=$actualTtsModel (useBackendTts=$useBackendTts for ${if (useBackendTts) language else "other languages"})")
            currentSession?.send(Frame.Text(settingsJson))

            connectionState.set(ConnectionState.CONNECTED)
            
            // Start keep-alive job to send silent audio when user isn't speaking
            startKeepAlive()
            
            scope.launch {
                try {
                    // Use a dedicated coroutine for message processing to avoid blocking
                    for (frame in currentSession!!.incoming) {
                        // Skip processing if conversation is stopped
                        if (connectionState.get() == ConnectionState.DISCONNECTED) {
                            break
                        }
                        
                        when (frame) {
                            is Frame.Text -> {
                                // Handle text messages in separate scope to avoid blocking audio
                                scope.launch(Dispatchers.IO) {
                                    handleTextMessage(frame.readText())
                                }
                            }
                            is Frame.Binary -> {
                                // Handle audio frames immediately for real-time playback
                                val audioData = frame.readBytes()
                                handleBinaryAudio(audioData)
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    println("[AgentService] Error receiving message: ${e.message}")
                    connectionState.set(ConnectionState.DISCONNECTED)
                    agentReady.set(false)
                    stopAudioCapture()
                    onMessage(AgentMessage(
                        type = "error",
                        error = "Connection error: ${e.message}"
                    ))
                }
            }

            Result.success("deepgram-session")
        } catch (e: Exception) {
            println("[AgentService] ❌ Failed to start conversation")
            println("[AgentService] Error type: ${e::class.simpleName}")
            println("[AgentService] Error message: ${e.message}")
            
            val errorMsg = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> 
                    "Connection timeout. Please check your internet connection and try again."
                e.message?.contains("authorization", ignoreCase = true) == true -> 
                    "Invalid API key. Please check your Deepgram API key."
                e.message?.contains("refused", ignoreCase = true) == true -> 
                    "Connection refused. Deepgram service may be unavailable."
                else -> "Connection failed: ${e.message}"
            }
            
            connectionState.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            stopAudioCapture()
            e.printStackTrace()
            Result.failure(Exception(errorMsg, e))
        }
    }
    
    private fun openMicrophoneForConversation() {
        try {
            val audioFormat = AudioFormat(
                PCM_SIGNED,
                24000f,
                16,
                1,
                2,
                24000f,
                false
            )
            
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            if (!AudioSystem.isLineSupported(info)) {
                println("[AgentService] Microphone not supported")
                return
            }
            
            targetDataLine = AudioSystem.getLine(info) as TargetDataLine
            targetDataLine?.open(audioFormat)
            println("[AgentService] Microphone opened and ready for push-to-talk")
        } catch (e: Exception) {
            println("[AgentService] Failed to open microphone: ${e.message}")
        }
    }
    
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            val silentAudio = ByteArray(2400) // Silent audio buffer (24000Hz, 100ms chunks)
            
            while (connectionState.get() == ConnectionState.READY || connectionState.get() == ConnectionState.CONNECTED) {
                try {
                    // Only send keep-alive when completely idle to avoid audio interference
                    if (!isUserSpeaking.get() && !isAgentPlayingAudio.get()) {
                        currentSession?.send(Frame.Binary(true, silentAudio))
                    }
                    delay(1000) // Further reduced frequency to minimize network overhead
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        // Silent fail to reduce log noise
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
    
    fun startManualAudioCapture() {
        if (connectionState.get() != ConnectionState.READY || targetDataLine == null || isAgentPlayingAudio.get()) {
            println("[AgentService] Cannot start audio capture - not ready, no mic, or agent speaking")
            println("[AgentService] Connection state: ${connectionState.get()}")
            println("[AgentService] TargetDataLine null: ${targetDataLine == null}")
            println("[AgentService] Agent playing: ${isAgentPlayingAudio.get()}")
            return
        }
        
        println("[AgentService] User started speaking")
        isUserSpeaking.set(true)
        targetDataLine?.start()
        
        audioCaptureJob = scope.launch {
            val buffer = ByteArray(4800) // Larger buffer for better capture efficiency
            val line = targetDataLine ?: return@launch
            var audioBytesSent = 0L
            
            while (isUserSpeaking.get() && line.isOpen) {
                try {
                    val bytesRead = line.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        audioBytesSent += bytesRead
                        currentSession?.send(Frame.Binary(true, buffer.copyOf(bytesRead)))
                        
                        // Log every 5 seconds of audio
                        if (audioBytesSent % 120000 == 0L) {
                            println("[AgentService] Sent ${audioBytesSent / 1000}KB of audio to Deepgram Agent")
                        }
                    }
                    // Optimized delay for balance between responsiveness and performance
                    kotlinx.coroutines.delay(10)
                } catch (e: Exception) {
                    println("[AgentService] Error sending audio: ${e.message}")
                    break
                }
            }
            println("[AgentService] Audio capture ended. Total sent: ${audioBytesSent / 1000}KB")
        }
    }
    
    fun stopManualAudioCapture() {
        println("[AgentService] User stopped speaking")
        isUserSpeaking.set(false)
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        targetDataLine?.stop()
        targetDataLine?.flush()
    }
    
    private fun stopAudioCapture() {
        println("[AgentService] Stopping audio capture")
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
        // Get a random base greeting for the language
        val baseGreeting = greetings.random()
        
        // Add level and scenario context
        val levelContext = when (level) {
            "beginner" -> "I'll help you learn step by step!"
            "intermediate" -> "I'll help you improve your skills!"
            "advanced" -> "I'll challenge you to become fluent!"
            else -> "I'll help you practice!"
        }
        
        val scenarioContext = when (scenario) {
            "conversation_partner" -> "Let's have a natural conversation!"
            "travel" -> "Let's practice travel situations!"
            "work" -> "Let's practice professional conversations!"
            "daily_conversation" -> "Let's practice everyday conversations!"
            "culture" -> "Let's explore cultural topics!"
            else -> "Let's start practicing!"
        }
        
        return "$baseGreeting $levelContext $scenarioContext"
    }
    
    private fun handleTextMessage(text: String) {
        try {
            val preview = if (text.length > 100) text.take(100) + "..." else text
            println("[AgentService] Received: $preview")
            
            val jsonObj = json.parseToJsonElement(text).jsonObject
            val type = jsonObj["type"]?.jsonPrimitive?.content ?: "Unknown"
            
            when (type) {
                "Welcome" -> {
                    println("[AgentService] Agent ready")
                    onMessageCallback?.invoke(AgentMessage(type = "connection", event = "opened"))
                }
                "SettingsApplied" -> {
                    println("[AgentService] Settings applied - agent is now ready")
                    agentReady.set(true)
                    connectionState.set(ConnectionState.READY)
                    openMicrophoneForConversation()
                    println("[AgentService] Ready for push-to-talk recording")
                    onMessageCallback?.invoke(AgentMessage(type = "agent_message", event = "Welcome"))
                }
                "ConversationText" -> {
                    val msg = json.decodeFromString<AgentV1ConversationTextMessage>(text)
                    println("[AgentService] ConversationText - role=${msg.role}, content=${msg.content.take(50)}...")
                    
                    // Backend TTS is handled by Python service, just forward the message
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "ConversationText",
                        role = msg.role,
                        content = msg.content
                    ))
                }
                "UserStartedSpeaking" -> {
                    println("[AgentService] Deepgram detected user started speaking")
                    // Don't stop audio immediately - let it finish naturally to avoid choppy playback
                    isAgentPlayingAudio.set(false)
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "UserStartedSpeaking"
                    ))
                }
                "AgentThinking" -> {
                    println("[AgentService] Agent thinking")
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "AgentThinking"
                    ))
                }
                "AgentStartedSpeaking" -> {
                    println("[AgentService] Agent started speaking - resetting audio state and setting playback flag")
                    // Reset audio state for new response to ensure clean playback
                    resetAudioState()
                    isAgentPlayingAudio.set(true)
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "AgentStartedSpeaking"
                    ))
                }
                "AgentAudioDone" -> {
                    println("[AgentService] Agent audio done")
                    isAgentPlayingAudio.set(false)
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "AgentAudioDone"
                    ))
                }
                "Error" -> {
                    val msg = json.decodeFromString<AgentV1ErrorMessage>(text)
                    println("[AgentService] Error: code=${msg.code}, description=${msg.description}")
                    
                    val errorDesc = msg.description.lowercase()
                    when {
                        "language" in errorDesc -> {
                            println("[AgentService] Language configuration error. Ensure language=multi is supported by nova-3 model.")
                        }
                        "model" in errorDesc -> {
                            println("[AgentService] Model error. Verify nova-3 model is available and supports multilingual transcription.")
                        }
                        "unsupported" in errorDesc -> {
                            println("[AgentService] Unsupported feature error. Check if multilingual code-switching is enabled for your Deepgram account.")
                        }
                    }
                    
                    onMessageCallback?.invoke(AgentMessage(
                        type = "error",
                        error = msg.description,
                        description = msg.description,
                        code = msg.code
                    ))
                }
                "Warning" -> {
                    val msg = json.decodeFromString<AgentV1WarningMessage>(text)
                    println("[AgentService] Warning: code=${msg.code}, description=${msg.description}")
                }
            }
        } catch (e: Exception) {
            println("[AgentService] Error parsing message: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Audio processing state for streaming
    private var audioPrebuffer = mutableListOf<ByteArray>()
    private var isPrebuffering = true
    private val prebufferSize = 3 // Reduced prebuffer size for lower latency (3 chunks ~150ms)
    
    private fun handleBinaryAudio(audioData: ByteArray) {
        try {
            // Don't process audio if conversation is stopped or not ready
            if (connectionState.get() != ConnectionState.READY && connectionState.get() != ConnectionState.CONNECTED) {
                return
            }
            
            // Skip Deepgram audio processing when using backend TTS
            if (useBackendTts) {
                return
            }
            
            // Send audio to callback for recording
            onAudioReceivedCallback?.invoke(audioData)
            
            val line = sourceDataLine
            if (line == null) {
                // Silently return instead of logging errors when conversation is stopped
                return
            }
            if (!line.isOpen) {
                // Silently return instead of logging errors when conversation is stopped
                return
            }
            
            // Deepgram Agent API sends raw linear16 PCM audio chunks
            // Java's SourceDataLine can handle raw PCM directly - no headers needed
            // Pass the audio data directly without any processing
            
            // Prebuffer initial chunks to smooth playback start and reduce underruns
            if (isPrebuffering) {
                audioPrebuffer.add(audioData)
                if (audioPrebuffer.size >= prebufferSize) {
                    // Start playback with prebuffered audio
                    isPrebuffering = false
                    println("[AgentService] Starting playback with ${audioPrebuffer.size} prebuffered chunks")
                    
                    // Ensure continuous playback without interruption
                    if (!line.isActive) {
                        line.start()
                        println("[AgentService] Started audio line")
                    }
                    
                    // Write prebuffered audio first
                    audioPrebuffer.forEach { chunk ->
                        writeAudioChunk(line, chunk)
                    }
                    audioPrebuffer.clear()
                } else {
                    return // Still prebuffering
                }
            }
            
            // Write audio chunk directly to the audio line
            writeAudioChunk(line, audioData)
        } catch (e: Exception) {
            println("[AgentService] ERROR in handleBinaryAudio: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun writeAudioChunk(line: SourceDataLine, audioData: ByteArray) {
        try {
            // Use blocking write - SourceDataLine.write() will block if buffer is full
            // This ensures all audio is played without skipping chunks
            // The 128KB buffer provides enough space to handle network jitter
            var bytesWritten = 0
            while (bytesWritten < audioData.size) {
                val written = line.write(audioData, bytesWritten, audioData.size - bytesWritten)
                if (written < 0) {
                    println("[AgentService] WARNING: Write returned negative value: $written")
                    break
                }
                bytesWritten += written
            }
        } catch (e: Exception) {
            println("[AgentService] ERROR writing audio chunk: ${e.message}")
            // Log error but don't throw - allows audio stream to continue
        }
    }
    
    private fun resetAudioState() {
        audioPrebuffer.clear()
        isPrebuffering = true
    }
    suspend fun sendAudio(audioData: ByteArray): Result<Unit> {
        return try {
            val session = synchronized(sessionLock) { currentSession }
            if (session == null) {
                return Result.failure(Exception("No session"))
            }
            
            session.send(Frame.Binary(true, audioData))
            Result.success(Unit)
        } catch (e: Exception) {
            if (e.message?.contains("cancelled") == true || e.message?.contains("closed") == true) {
                println("[AgentService] Session lost, will reconnect on next use")
                connectionState.set(ConnectionState.DISCONNECTED)
                agentReady.set(false)
            }
            Result.failure(e)
        }
    }

    suspend fun stopConversation(): Result<Unit> {
        return try {
            println("[AgentService] Stopping conversation and clearing all buffers")
            
            connectionState.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            stopKeepAlive()
            isAgentPlayingAudio.set(false)
            isUserSpeaking.set(false)
            
            // Clear audio prebuffer to prevent leftover chunks
            audioPrebuffer.clear()
            isPrebuffering = true
            
            stopAudioCapture()
            
            // Cancel any active audio playback job
            audioPlaybackJob?.cancel()
            audioPlaybackJob = null
            
                // Properly flush and close audio output (speaker)
            try {
                sourceDataLine?.let { line ->
                    if (line.isOpen) {
                        line.stop()
                        line.close()
                        println("[AgentService] Audio output line closed")
                    }
                }
            } catch (e: Exception) {
                println("[AgentService] Error closing audio output line: ${e.message}")
            }
            sourceDataLine = null
            
            // Properly flush and close audio input (microphone)
            try {
                targetDataLine?.let { line ->
                    if (line.isOpen) {
                        line.stop()
                        line.close()
                        println("[AgentService] Audio input line closed")
                    }
                }
            } catch (e: Exception) {
                println("[AgentService] Error closing audio input line: ${e.message}")
            }
            targetDataLine = null
            
            // Close Deepgram Agent session if exists
            val agentSessionToClose = synchronized(sessionLock) {
                val session = currentSession
                currentSession = null
                session
            }
            
            try {
                agentSessionToClose?.close()
                println("[AgentService] Agent WebSocket closed")
            } catch (e: Exception) {
                println("[AgentService] Error closing Agent WebSocket: ${e.message}")
            }
            
            // Close custom STT session if exists
            val sttSessionToClose = sttSession
            sttSession = null
            
            try {
                sttSessionToClose?.close()
                println("[AgentService] STT WebSocket closed")
            } catch (e: Exception) {
                println("[AgentService] Error closing STT WebSocket: ${e.message}")
            }
            
            // Clear custom pipeline config
            customPipelineConfig = null
            
            // Clear callback
            onMessageCallback = null
            
            println("[AgentService] Conversation stopped successfully - all buffers cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[AgentService] Error stopping conversation: ${e.message}")
            connectionState.set(ConnectionState.DISCONNECTED)
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
            
            println("[AgentService] Connecting to Gladia backend: $gladiaUrl")
            
            currentSession = client.webSocketSession(gladiaUrl)
            
            println("[AgentService] Gladia WebSocket connected")
            
            // Initialize audio playback
            val audioFormat = AudioFormat(
                PCM_SIGNED,
                22050f, 16, 1, 2, 22050f, false
            )
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            sourceDataLine?.open(audioFormat, 65536)
            sourceDataLine?.start()
            println("[AgentService] Audio playback initialized at 22050Hz")
            
            // Start listening for Gladia responses
            scope.launch {
                println("[AgentService] Starting message listener coroutine")
                try {
                    while (true) {
                        println("[AgentService] Waiting for next frame...")
                        val frame = currentSession?.incoming?.receive() ?: break
                        println("[AgentService] Received frame: ${frame.javaClass.simpleName}")
                        
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                println("[AgentService] Gladia message: $text")
                                
                                // Parse Gladia response
                                try {
                                    val jsonMessage = json.parseToJsonElement(text).jsonObject
                                    val type = jsonMessage["type"]?.jsonPrimitive?.content
                                    
                                    when (type) {
                                        "connection" -> {
                                            println("[AgentService] Gladia connection established")
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
                                            
                                            println("[AgentService] Final transcript received: '$content' (confidence: $confidence, lang: $detectedLang)")
                                            
                                            if (!content.isNullOrEmpty()) {
                                                // Very low confidence threshold for Chinese/Korean (0.01) since Gladia's scoring is different
                                                if (confidence > 0.01) {
                                                    println("[AgentService] Processing transcript: '$content'")
                                                    
                                                    // Send to UI
                                                    onMessageCallback?.invoke(AgentMessage(
                                                        type = "conversation_text",
                                                        content = content,
                                                        role = role
                                                    ))
                                                    
                                                    // Generate response using DeepSeek
                                                    launch {
                                                        try {
                                                            val response = generateResponse(content, language, level, scenario)
                                                            println("[AgentService] Generated response: $response")
                                                            
                                                            onMessageCallback?.invoke(AgentMessage(
                                                                type = "conversation_text",
                                                                content = response,
                                                                role = "assistant"
                                                            ))
                                                            
                                                            // Generate Edge TTS audio for response (always in English for learning context)
                                                            isAgentPlayingAudio.set(true)
                                                            onMessageCallback?.invoke(AgentMessage(
                                                                type = "agent_message",
                                                                event = "AgentStartedSpeaking"
                                                            ))
                                                            
                                                            println("[AgentService] Generating TTS for: $response")
                                                            // Use English (en) for all responses to provide learning context
                                                            val audioUrl = generateEdgeTtsAudio(response, "en")
                                                            if (audioUrl != null) {
                                                                println("[AgentService] Playing response audio: $audioUrl")
                                                                onMessageCallback?.invoke(AgentMessage(
                                                                    type = "agent_message",
                                                                    event = "AgentAudio",
                                                                    content = response,
                                                                    role = "assistant"
                                                                ))
                                                                
                                                                playBackendAudio(audioUrl)
                                                            } else {
                                                                println("[AgentService] Failed to generate response TTS")
                                                            }
                                                            
                                                            isAgentPlayingAudio.set(false)
                                                            onMessageCallback?.invoke(AgentMessage(
                                                                type = "agent_message",
                                                                event = "AgentAudioDone"
                                                            ))
                                                        } catch (e: Exception) {
                                                            println("[AgentService] Error processing DeepSeek response: ${e.message}")
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                } else {
                                                    println("[AgentService] Transcript confidence too low: $confidence")
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
                                            println("[AgentService] Language detected: $detectedLang")
                                        }
                                        "error" -> {
                                            val errorMsg = jsonMessage["message"]?.jsonPrimitive?.content
                                            println("[AgentService] Gladia error: $errorMsg")
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("[AgentService] Error parsing message: ${e.message}")
                                }
                            }
                            is Frame.Close -> {
                                println("[AgentService] Gladia WebSocket closed")
                                connectionState.set(ConnectionState.DISCONNECTED)
                                break
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    println("[AgentService] Gladia message handling error: ${e.message}")
                    println("[AgentService] Error type: ${e.javaClass.simpleName}")
                    e.printStackTrace()
                    connectionState.set(ConnectionState.DISCONNECTED)
                }
            }
            
            connectionState.set(ConnectionState.CONNECTED)
            agentReady.set(true)
            connectionState.set(ConnectionState.READY)
            
            // Store config for later use
            customPipelineConfig = CustomPipelineConfig(apiKey, language, level, scenario)
            println("[AgentService] Gladia pipeline ready")
            
            // Send greeting after connection is ready
            scope.launch {
                try {
                    val greeting = when (language.lowercase()) {
                        "zh", "chinese", "mandarin" -> "Hello! I'm your Chinese language learning assistant. I'll help you practice Chinese by speaking English and teaching you Chinese phrases. Press the microphone button to start practicing!"
                        "ko", "korean", "hangeul" -> "Hello! I'm your Korean language learning assistant. I'll help you practice Korean by speaking English and teaching you Korean phrases. Press the microphone button to start practicing!"
                        else -> "Hello! I'm your language learning assistant. Press the microphone button to start practicing!"
                    }
                    
                    println("[AgentService] Sending greeting: $greeting")
                    
                    // Display greeting text
                    onMessageCallback?.invoke(AgentMessage(
                        type = "conversation_text",
                        content = greeting,
                        role = "assistant"
                    ))
                    
                    // Generate and play greeting audio using Edge TTS (always in English for learning context)
                    if (language.lowercase() in listOf("chinese", "mandarin", "zh", "korean", "hangeul", "ko")) {
                        println("[AgentService] Generating TTS for greeting")
                        
                        isAgentPlayingAudio.set(true)
                        onMessageCallback?.invoke(AgentMessage(
                            type = "agent_message",
                            event = "AgentStartedSpeaking"
                        ))
                        
                        // Use English (en-US) for all greetings to provide learning context
                        val audioUrl = generateEdgeTtsAudio(greeting, "en")
                        if (audioUrl != null) {
                            println("[AgentService] Playing greeting audio: $audioUrl")
                            onMessageCallback?.invoke(AgentMessage(
                                type = "agent_message",
                                event = "AgentAudio",
                                content = greeting,
                                role = "assistant"
                            ))
                            
                            playBackendAudio(audioUrl)
                        } else {
                            println("[AgentService] Failed to generate greeting TTS")
                        }
                        
                        isAgentPlayingAudio.set(false)
                        onMessageCallback?.invoke(AgentMessage(
                            type = "agent_message",
                            event = "AgentAudioDone"
                        ))
                    }
                    
                    println("[AgentService] Greeting sent successfully")
                } catch (e: Exception) {
                    println("[AgentService] Error sending greeting: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            Result.success("gladia-session")
        } catch (e: Exception) {
            println("[AgentService] Gladia conversation error: ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun playBackendAudio(audioUrl: String) {
        """Download and play audio from backend URL."""
        try {
            println("[AgentService] Downloading audio from: $audioUrl")
            
            val response = client.get(audioUrl)
            if (response.status.value == 200) {
                val audioData = response.readBytes()
                println("[AgentService] Audio downloaded: ${audioData.size} bytes")
                
                // Decode MP3 to PCM using Java's audio system
                val inputStream = java.io.ByteArrayInputStream(audioData)
                val audioInputStream = javax.sound.sampled.AudioSystem.getAudioInputStream(inputStream)
                
                val audioFormat = audioInputStream.format
                println("[AgentService] Audio format: ${audioFormat.sampleRate}Hz, ${audioFormat.sampleSizeInBits}bit, ${audioFormat.channels}ch")
                
                // Check if format is valid
                if (audioFormat.sampleSizeInBits <= 0) {
                    println("[AgentService] Invalid audio format detected, using default conversion")
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
                            println("[AgentService] Audio playback completed")
                        } catch (e: Exception) {
                            println("[AgentService] Error during audio playback: ${e.message}")
                        } finally {
                            convertedStream.close()
                            audioInputStream.close()
                            inputStream.close()
                        }
                    }
                } else {
                    // Convert to PCM if needed
                    val targetFormat = AudioFormat(
                        PCM_SIGNED,
                        22050f, // Match the playback line
                        16,
                        audioFormat.channels,
                        audioFormat.channels * 2,
                        22050f,
                        false
                    )
                    
                    val convertedStream = if (audioFormat != targetFormat) {
                        println("[AgentService] Converting audio format to PCM")
                        javax.sound.sampled.AudioSystem.getAudioInputStream(targetFormat, audioInputStream)
                    } else {
                        audioInputStream
                    }
                    
                    // Play the audio in a separate coroutine to avoid blocking WebSocket
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
                            println("[AgentService] Audio playback completed")
                        } catch (e: Exception) {
                            println("[AgentService] Error during audio playback: ${e.message}")
                        } finally {
                            convertedStream.close()
                            audioInputStream.close()
                            inputStream.close()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("[AgentService] Error playing audio: ${e.message}")
            e.printStackTrace()
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
                println("[AgentService] Edge TTS error: ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("[AgentService] Edge TTS generation error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private suspend fun generateResponse(userMessage: String, language: String, level: String, scenario: String): String {
        // Generate responses in English by default, with Chinese phrases included
        return getAiResponse(userMessage, language, level, scenario)
    }
    
    private suspend fun generateTtsAudio(text: String, language: String): String? {
        // Placeholder until Gladia integration is implemented
        println("[AgentService] TTS generation pending for: $text")
        return null
    }
    
    fun startCustomManualAudioCapture() {
        val config = customPipelineConfig ?: return
        if (connectionState.get() != ConnectionState.READY || audioCaptureJob?.isActive == true) {
            return
        }
        println("[AgentService] Starting custom audio capture")
        startCustomAudioCapture(config.apiKey, config.language, config.level, config.scenario)
    }
    
    fun stopCustomManualAudioCapture() {
        println("[AgentService] Stopping custom audio capture")
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        targetDataLine?.stop()
        targetDataLine?.flush()
        
        scope.launch {
            try {
                sttSession?.send(Frame.Text("{\"type\":\"CloseStream\"}"))
            } catch (e: Exception) {
                println("[AgentService] Error closing STT stream: ${e.message}")
            }
        }
    }
    
    private fun startCustomAudioCapture(apiKey: String, language: String, level: String, scenario: String) {
        val audioFormat = AudioFormat(PCM_SIGNED, 16000f, 16, 1, 2, 16000f, false) // 16kHz little-endian (big-endian not supported)
        val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
        targetDataLine = AudioSystem.getLine(info) as TargetDataLine
        targetDataLine?.open(audioFormat)
        targetDataLine?.start()
        
        audioCaptureJob = scope.launch {
            try {
                println("[AgentService] Starting audio capture for Gladia STT")
                
                val buffer = ByteArray(1600) // 100ms at 16kHz
                var audioBytesSent = 0L
                
                while (connectionState.get() == ConnectionState.READY && targetDataLine?.isOpen == true) {
                    if (!isAgentPlayingAudio.get()) {
                        val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            audioBytesSent += bytesRead
                            // Send audio to Gladia backend WebSocket
                            currentSession?.send(Frame.Binary(true, buffer.copyOf(bytesRead)))
                            
                            // Log every 10 seconds of audio sent
                            if (audioBytesSent % 160000 == 0L) {
                                println("[AgentService] Sent ${audioBytesSent / 1000}KB of audio to Gladia")
                            }
                        }
                    } else {
                        delay(50)
                        targetDataLine?.flush()
                    }
                }
                println("[AgentService] Audio capture ended. Total sent: ${audioBytesSent / 1000}KB")
            } catch (e: Exception) {
                println("[AgentService] Audio capture error: ${e.message}")
                onMessageCallback?.invoke(AgentMessage(
                    type = "Error",
                    description = "Audio capture failed: ${e.message}"
                ))
            }
        }
        
        // Force final transcript after audio capture stops (if no speech_end received)
        scope.launch {
            delay(2000) // Wait 2 seconds after capture starts to check for final transcript
            // This is a fallback in case Gladia doesn't send speech_end
        }
    }

    private suspend fun handleSttResult(result: String, language: String, level: String, scenario: String) {
        try {
            println("[AgentService] STT Raw result: ${result.take(200)}...")
            
            val jsonResult = json.parseToJsonElement(result)
            val isFinal = jsonResult.jsonObject?.get("is_final")?.jsonPrimitive?.boolean == true
            val transcript = jsonResult.jsonObject?.get("channel")?.jsonObject
                ?.get("alternatives")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("transcript")?.jsonPrimitive?.content
            
            val confidence = jsonResult.jsonObject?.get("channel")?.jsonObject
                ?.get("alternatives")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("confidence")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            
            println("[AgentService] STT Parsed - isFinal: $isFinal, transcript: '$transcript', confidence: $confidence")
            
            if (isFinal && !transcript.isNullOrEmpty()) {
                val cleanedTranscript = transcript.trim()
                
                if (cleanedTranscript.isEmpty() || confidence < 0.3) {
                    return
                }
                
                println("[AgentService] User: '$cleanedTranscript' (confidence: $confidence)")
                
                onMessageCallback?.invoke(AgentMessage(
                    type = "conversation_text",
                    content = cleanedTranscript,
                    role = "user"
                ))
                
                val response = getAiResponse(cleanedTranscript, language, level, scenario)
                println("[AgentService] Agent: $response")
                
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
                
                // TTS is handled by backend service for Chinese/Korean
                println("[AgentService] Response generated: $response")
                if (language in listOf("zh", "ko")) {
                    println("[AgentService] TTS handled by backend for $language")
                }
                
                isAgentPlayingAudio.set(false)
                onMessageCallback?.invoke(AgentMessage(
                    type = "agent_message",
                    event = "AgentAudioDone"
                ))
            }
        } catch (e: Exception) {
            println("[AgentService] STT error: ${e.message}")
        }
    }

    private suspend fun getAiResponse(userMessage: String, language: String, level: String, scenario: String): String {
        return try {
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
            
            val response = client.post(openAiUrl) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append(HttpHeaders.Authorization, "Bearer ${System.getenv("OPENAI_API_KEY") ?: ""}")
                }
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                val jsonResponse = json.parseToJsonElement(responseBody)
                jsonResponse.jsonObject?.get("choices")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content ?: "I'm sorry, I couldn't generate a response."
            } else {
                println("[AgentService] OpenAI API error: ${response.status}")
                "I'm sorry, I'm having trouble responding right now."
            }
        } catch (e: Exception) {
            println("[AgentService] AI response error: ${e.message}")
            "I'm sorry, I encountered an error while generating a response."
        }
    }

    fun isActive(): Boolean {
        val state = connectionState.get()
        return state == ConnectionState.CONNECTED || state == ConnectionState.READY
    }

    override fun close() {
        println("[AgentService] Closing agent service")
        
        // Stop all jobs
        audioCaptureJob?.cancel()
        audioPlaybackJob?.cancel()
        
        // Stop conversation first
        scope.launch {
            try {
                stopConversation()
            } catch (e: Exception) {
                println("[AgentService] Error stopping conversation: ${e.message}")
            }
        }
        
        // Properly close and cleanup audio lines
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
            println("[AgentService] Error closing audio lines: ${e.message}")
        }
        
        // Clear references
        sourceDataLine = null
        targetDataLine = null
        isAgentPlayingAudio.set(false)
        
        // Cancel scope and close client
        scope.cancel()
        client.close()
        
        println("[AgentService] Agent service closed")
    }
    
    fun getApiKey(): String? {
        return org.example.project.core.config.ApiKeyConfig.getDeepgramApiKey()
    }
}
