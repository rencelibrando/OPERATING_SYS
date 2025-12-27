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
import kotlinx.serialization.decodeFromString
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
            maxFrameSize = Long.MAX_VALUE
        }
        engine {
            requestTimeout = 0
        }
    }
    
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val deepgramUrl = "wss://agent.deepgram.com/v1/agent/converse"
    private val deepgramSttUrl = "wss://api.deepgram.com/v1/listen"
    private val openAiUrl = "https://api.openai.com/v1/chat/completions"
    
    private var currentSession: DefaultClientWebSocketSession? = null
    private var sttSession: DefaultClientWebSocketSession? = null
    private val audioBuffer = ByteArrayOutputStream()
    private var sourceDataLine: SourceDataLine? = null
    private var targetDataLine: TargetDataLine? = null
    private var onMessageCallback: ((AgentMessage) -> Unit)? = null
    
    private val elevenLabsService by lazy {
        val apiKey = org.example.project.core.config.ApiKeyConfig.getElevenLabsApiKey() 
            ?: System.getenv("ELEVEN_LABS_API_KEY") 
            ?: ""
        if (apiKey.isEmpty()) {
            println("[AgentService] WARNING: ELEVEN_LABS_API_KEY not found")
        }
        ElevenLabsService(apiKey = apiKey, httpClient = client)
    }
    
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, READY }
    private val connectionState = AtomicReference(ConnectionState.DISCONNECTED)
    
    private val agentReady = AtomicBoolean(false)
    
    private var keepAliveJob: kotlinx.coroutines.Job? = null
    private var audioCaptureJob: kotlinx.coroutines.Job? = null
    private val keepAliveIntervalMs = 15000L
    
    private val sessionLock = Any()

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
        return when (language.lowercase()) {
            "korean", "hangeul" -> true
            "mandarin", "chinese", "mandarin chinese" -> true
            else -> false
        }
    }

    suspend fun startConversation(
        apiKey: String,
        language: String,
        level: String,
        scenario: String,
        onMessage: (AgentMessage) -> Unit
    ): Result<String> {
        return try {
            if (!connectionState.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
                val currentState = connectionState.get()
                println("[AgentService] Already $currentState - ignoring duplicate start call")
                return Result.failure(Exception("Connection already in progress or active"))
            }
            
            agentReady.set(false)
            onMessageCallback = onMessage
            
            if (shouldUseCustomPipeline(language)) {
                println("[AgentService] Using custom pipeline for $language")
                return startCustomConversation(apiKey, language, level, scenario, onMessage)
            }
            
            println("[AgentService] Connecting to Deepgram Agent V1 API")
            
            currentSession = client.webSocketSession(deepgramUrl) {
                header("Authorization", "Token $apiKey")
            }

            println("[AgentService] WebSocket connected")
            
            val audioFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                24000f,
                16,
                1,
                2,
                24000f,
                false
            )
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            sourceDataLine?.open(audioFormat, 65536)
            sourceDataLine?.start()
            println("[AgentService] Audio playback initialized - 64KB buffer for low latency")
            
            val systemPrompt = createSystemPrompt(language, level, scenario)
            
            val (langCode, ttsModel, greetings) = getLanguageConfig(language)
            val randomGreeting = greetings.random()
            
            val settings = AgentV1SettingsMessage(
                audio = AgentV1AudioConfig(
                    input = AgentV1AudioInput(
                        encoding = "linear16",
                        sampleRate = 24000
                    ),
                    output = AgentV1AudioOutput(
                        encoding = "linear16",
                        sampleRate = 24000,
                        container = "wav"
                    )
                ),
                agent = AgentV1Agent(
                    language = langCode,
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
                    speak = AgentV1SpeakProviderConfig(
                        provider = AgentV1SpeakProvider(
                            type = "deepgram",
                            model = ttsModel
                        )
                    ),
                    greeting = randomGreeting
                )
            )

            val settingsJson = json.encodeToString(settings)
            println("[AgentService] Sending Settings message")
            println("[AgentService] Configuration: model=nova-3, language=$langCode (nova-3 auto-detects multilingual)")
            currentSession?.send(Frame.Text(settingsJson))

            connectionState.set(ConnectionState.CONNECTED)
            
            scope.launch {
                try {
                    for (frame in currentSession!!.incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                handleTextMessage(frame.readText())
                            }
                            is Frame.Binary -> {
                                handleBinaryAudio(frame.readBytes())
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
            println("[AgentService] Error starting conversation: ${e.message}")
            connectionState.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            stopAudioCapture()
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    private fun startAudioCapture() {
        println("[AgentService] Starting continuous audio capture")
        stopAudioCapture()
        
        try {
            val audioFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
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
            targetDataLine?.start()
            println("[AgentService] Microphone opened - streaming continuous audio")
            
            audioCaptureJob = scope.launch {
                val buffer = ByteArray(1200) // 25ms chunks for real-time streaming
                val line = targetDataLine
                
                while (connectionState.get() == ConnectionState.READY && line?.isOpen == true) {
                    try {
                        val bytesRead = line.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            val session = synchronized(sessionLock) { currentSession }
                            session?.send(Frame.Binary(true, buffer.copyOf(bytesRead)))
                        }
                    } catch (e: Exception) {
                        if (connectionState.get() == ConnectionState.READY) {
                            println("[AgentService] Audio capture error: ${e.message}")
                        }
                        break
                    }
                }
                println("[AgentService] Audio capture loop ended")
            }
        } catch (e: Exception) {
            println("[AgentService] Failed to start audio capture: ${e.message}")
        }
    }
    
    private fun stopAudioCapture() {
        println("[AgentService] Stopping audio capture")
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        
        try {
            targetDataLine?.stop()
            targetDataLine?.close()
        } catch (e: Exception) {
            println("[AgentService] Error closing microphone: ${e.message}")
        }
        targetDataLine = null
    }
    
    private fun getLanguageConfig(language: String): Triple<String, String, List<String>> {
        return when (language.lowercase()) {
            "english" -> Triple(
                "en",
                "aura-asteria-en",
                listOf(
                    "Hello! I'm your English tutor. I'll help you learn English in a fun and engaging way!",
                    "Hi there! Ready to learn English? I'm here to guide you through every step!",
                    "Welcome! Let's make learning English enjoyable together. Feel free to ask me anything!"
                )
            )
            "french" -> Triple(
                "en",
                "aura-asteria-en",
                listOf(
                    "Hello! I'm your French language tutor. I'll teach you about French language and culture in English!",
                    "Hi there! Ready to learn French? I'll guide you through French vocabulary, grammar, and phrases!",
                    "Welcome! I'm here to help you understand French. Try speaking some French and I'll help you improve!",
                    "Hey! Let's explore the French language together. I'll explain everything in English!"
                )
            )
            "german" -> Triple(
                "en",
                "aura-asteria-en",
                listOf(
                    "Hello! I'm your German language tutor. I'll teach you about German in English!",
                    "Hi! Ready to learn German? I'll explain German grammar, vocabulary, and culture in English!",
                    "Welcome! Let's discover the German language together. I'll guide you every step of the way!",
                    "Hey! I'm here to help you learn German through clear English explanations!"
                )
            )
            "korean", "hangeul" -> Triple(
                "ko",
                "aura-asteria-en",
                listOf(
                    "안녕하세요! 한국어 공부를 도와드릴게요. 편하게 말씀해 주세요!",
                    "환영합니다! 한국어 연습할 준비 되셨나요? 시작해 볼까요?",
                    "안녕! 함께 한국어 배워봐요. 언제든지 말해도 돼요!",
                    "반갑습니다! 저는 한국어 선생님이에요. 대화를 시작해 볼까요?"
                )
            )
            "mandarin" -> Triple(
                "zh",
                "aura-asteria-en",
                listOf(
                    "你好！我很高兴帮你学中文。随时可以开始说话！",
                    "欢迎！准备好练习中文了吗？我在听！",
                    "嗨！让我们一起学中文吧。开始说吧！",
                    "你好呀！我是你的中文老师。我们现在可以开始了！"
                )
            )
            "spanish" -> Triple(
                "en",
                "aura-asteria-en",
                listOf(
                    "Hello! I'm your Spanish language tutor. I'll teach you about Spanish in English!",
                    "Hi! Ready to learn Spanish? I'll explain Spanish vocabulary, grammar, and culture in English!",
                    "Welcome! Let's explore the Spanish language together. I'm here to help!",
                    "Hey! I'll help you learn Spanish through clear and friendly English explanations!"
                )
            )
            else -> Triple(
                "en",
                "aura-asteria-en",
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
            "beginner" -> "Use very simple vocabulary and short sentences. Speak slowly and clearly. Provide lots of encouragement and positive reinforcement. Repeat important words if needed."
            "intermediate" -> "Use everyday vocabulary with some complex structures. Correct errors gently and explain briefly. Encourage longer, more detailed responses."
            "advanced" -> "Use natural, complex language with idioms and expressions. Focus on fluency, nuance, and natural expression. Challenge them with more sophisticated topics."
            else -> "Adjust your language complexity to match the student's responses and level."
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
            - Explain $languageName concepts clearly in English
            - Provide $languageName examples with English translations
            - Show genuine interest in their learning progress
            - Make learning feel like a friendly conversation with an English-speaking tutor
            
            Remember: You're an English-speaking tutor helping them learn ABOUT $languageName through English instruction and explanations!
        """.trimIndent()
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
                    startAudioCapture()
                    onMessageCallback?.invoke(AgentMessage(type = "agent_message", event = "Welcome"))
                }
                "ConversationText" -> {
                    val msg = json.decodeFromString<AgentV1ConversationTextMessage>(text)
                    println("[AgentService] ConversationText - role=${msg.role}, content=${msg.content.take(50)}...")
                    
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "ConversationText",
                        role = msg.role,
                        content = msg.content
                    ))
                }
                "UserStartedSpeaking" -> {
                    println("[AgentService] User started speaking")
                    // Stop agent audio playback immediately to prevent feedback
                    sourceDataLine?.stop()
                    sourceDataLine?.flush()
                    audioBuffer.reset()
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
                    println("[AgentService] Agent started speaking - muting microphone")
                    // Stop sending user audio to prevent feedback loop
                    targetDataLine?.stop()
                    targetDataLine?.flush()
                    audioBuffer.reset()
                    onMessageCallback?.invoke(AgentMessage(
                        type = "agent_message",
                        event = "AgentStartedSpeaking"
                    ))
                }
                "AgentAudioDone" -> {
                    println("[AgentService] Agent audio done - resuming microphone")
                    // Resume microphone after agent finishes speaking
                    scope.launch {
                        delay(500) // Brief delay to ensure audio playback finished
                        if (connectionState.get() == ConnectionState.READY && targetDataLine?.isOpen == true) {
                            targetDataLine?.flush() // Clear any residual agent audio
                            targetDataLine?.start()
                            // Restart audio playback line for next agent response
                            sourceDataLine?.start()
                            println("[AgentService] Microphone and playback resumed")
                        }
                    }
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
    
    private fun handleBinaryAudio(audioData: ByteArray) {
        try {
            sourceDataLine?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            println("[AgentService] Error playing audio: ${e.message}")
        }
    }

    fun isAgentReady(): Boolean = agentReady.get() && connectionState.get() == ConnectionState.READY
    
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
    
    suspend fun streamAudioChunk(audioChunk: ByteArray): Result<Unit> {
        return sendAudio(audioChunk)
    }

    suspend fun sendTextMessage(content: String): Result<Unit> {
        return try {
            if (currentSession == null) {
                return Result.failure(Exception("No active conversation session"))
            }

            val message = AgentV1InjectUserMessage(
                content = content
            )

            val messageJson = json.encodeToString(message)
            currentSession?.send(Frame.Text(messageJson))
            println("[AgentService] Sent text message: $content")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[AgentService] Error sending text message: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun sendKeepAlive(): Result<Unit> {
        return try {
            if (currentSession == null) {
                return Result.failure(Exception("No active conversation session"))
            }

            val message = AgentV1KeepAliveMessage()
            val messageJson = json.encodeToString(message)
            currentSession?.send(Frame.Text(messageJson))
            println("[AgentService] Sent manual KeepAlive")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[AgentService] Error sending KeepAlive: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun stopConversation(): Result<Unit> {
        return try {
            println("[AgentService] Stopping conversation")
            
            connectionState.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            
            stopAudioCapture()
            
            try {
                sourceDataLine?.stop()
                sourceDataLine?.drain()
                sourceDataLine?.close()
            } catch (e: Exception) {
                println("[AgentService] Error closing audio line: ${e.message}")
            }
            sourceDataLine = null
            
            // Close Deepgram Agent session if exists
            val agentSessionToClose = synchronized(sessionLock) {
                val session = currentSession
                currentSession = null
                session
            }
            
            try {
                agentSessionToClose?.close()
            } catch (e: Exception) {
                println("[AgentService] Error closing Agent WebSocket: ${e.message}")
            }
            
            // Close custom STT session if exists
            val sttSessionToClose = sttSession
            sttSession = null
            
            try {
                sttSessionToClose?.close()
            } catch (e: Exception) {
                println("[AgentService] Error closing STT WebSocket: ${e.message}")
            }
            
            onMessageCallback = null
            
            println("[AgentService] Conversation stopped successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[AgentService] Error stopping conversation: ${e.message}")
            connectionState.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            stopAudioCapture()
            Result.failure(e)
        }
    }

    private suspend fun startCustomConversation(
        apiKey: String,
        language: String,
        level: String,
        scenario: String,
        onMessage: (AgentMessage) -> Unit
    ): Result<String> {
        return try {
            // Initialize audio playback for ElevenLabs TTS
            val audioFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                24000f, 16, 1, 2, 24000f, false
            )
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            sourceDataLine?.open(audioFormat, 65536)
            sourceDataLine?.start()
            println("[AgentService] Custom pipeline audio playback initialized")
            
            // Send welcome message with greeting
            val (_, _, greetings) = getLanguageConfig(language)
            val randomGreeting = greetings.random()
            onMessage(AgentMessage(type = "agent_message", event = "Welcome"))
            onMessage(AgentMessage(type = "conversation_text", content = randomGreeting, role = "agent"))
            
            // Synthesize and play greeting with ElevenLabs
            elevenLabsService.synthesizeAndPlay(randomGreeting, language)
                .onFailure { e -> println("[AgentService] Greeting TTS failed: ${e.message}") }
            
            connectionState.set(ConnectionState.CONNECTED)
            agentReady.set(true)
            connectionState.set(ConnectionState.READY)
            
            // Start custom audio capture and processing
            startCustomAudioCapture(apiKey, language, level, scenario)
            
            Result.success("Custom conversation started")
        } catch (e: Exception) {
            println("[AgentService] Failed to start custom conversation: ${e.message}")
            connectionState.set(ConnectionState.DISCONNECTED)
            Result.failure(e)
        }
    }

    private fun startCustomAudioCapture(apiKey: String, language: String, level: String, scenario: String) {
        val audioFormat = AudioFormat(PCM_SIGNED, 24000f, 16, 1, 2, 24000f, false)
        val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
        targetDataLine = AudioSystem.getLine(info) as TargetDataLine
        targetDataLine?.open(audioFormat)
        targetDataLine?.start()
        
        audioCaptureJob = scope.launch {
            try {
                // Connect to Deepgram STT
                sttSession = client.webSocketSession(deepgramSttUrl) {
                    header("Authorization", "Token $apiKey")
                }
                
                // Configure STT for the language
                val langCode = when (language.lowercase()) {
                    "korean", "hangeul" -> "ko"
                    "mandarin", "chinese" -> "zh"
                    else -> "en"
                }
                
                val sttConfig = buildJsonObject {
                    put("model", "nova-3")
                    put("language", langCode)
                    put("encoding", "linear16")
                    put("sample_rate", 24000)
                    put("channels", 1)
                    put("smart_format", true)
                    put("punctuate", true)
                }.toString()
                sttSession?.send(Frame.Text(sttConfig))
                
                // Listen for STT results
                launch {
                    for (frame in sttSession!!.incoming) {
                        if (frame is Frame.Text) {
                            handleSttResult(frame.readText(), language, level, scenario)
                        }
                    }
                }
                
                // Stream audio to STT with optimized chunk size
                val buffer = ByteArray(1200) // 25ms at 24kHz for real-time performance
                while (connectionState.get() == ConnectionState.READY && targetDataLine?.isOpen == true) {
                    val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        sttSession?.send(Frame.Binary(true, buffer.copyOf(bytesRead)))
                    }
                }
            } catch (e: Exception) {
                println("[AgentService] Custom audio capture error: ${e.message}")
                onMessageCallback?.invoke(AgentMessage(
                    type = "Error",
                    description = "Audio capture failed: ${e.message}"
                ))
            }
        }
    }

    private suspend fun handleSttResult(result: String, language: String, level: String, scenario: String) {
        try {
            val jsonResult = json.parseToJsonElement(result)
            val isFinal = jsonResult.jsonObject["is_final"]?.jsonPrimitive?.boolean == true
            val transcript = jsonResult.jsonObject["channel"]?.jsonObject
                ?.jsonObject["alternatives"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("transcript")?.jsonPrimitive?.content
            
            if (isFinal && !transcript.isNullOrEmpty()) {
                println("[AgentService] User said: $transcript")
                onMessageCallback?.invoke(AgentMessage(
                    type = "conversation_text",
                    content = transcript,
                    role = "user"
                ))
                
                // Get AI response
                val response = getAiResponse(transcript, language, level, scenario)
                println("[AgentService] AI response: $response")
                
                onMessageCallback?.invoke(AgentMessage(
                    type = "conversation_text",
                    content = response,
                    role = "agent"
                ))
                
                // Synthesize and play response with ElevenLabs
                elevenLabsService.synthesizeAndPlay(response, language)
                    .onFailure { e -> println("[AgentService] TTS failed: ${e.message}") }
            }
        } catch (e: Exception) {
            println("[AgentService] STT result parsing error: ${e.message}")
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
                jsonResponse.jsonObject["choices"]?.jsonArray?.firstOrNull()
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
    
    fun getConnectionState(): ConnectionState = connectionState.get()

    override fun close() {
        scope.launch {
            stopConversation()
        }
        scope.cancel()
        client.close()
    }
    
    fun getApiKey(): String? {
        return org.example.project.core.config.ApiKeyConfig.getDeepgramApiKey()
    }
}
