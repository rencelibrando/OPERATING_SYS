package org.example.project.core.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

    private inline fun logError(
        tag: String = "ERROR",
        crossinline message: () -> String,
    ) {
        println("[AgentService][$tag] ${message()}")
    }

    // ==================== STATE MACHINE ====================
    private class StateMachine {
        private val lock = Any()
        private var _state = ConnectionState.DISCONNECTED

        fun transition(
            from: ConnectionState,
            to: ConnectionState,
        ): Boolean =
            synchronized(lock) {
                if (_state == from) {
                    _state = to
                    true
                } else {
                    false
                }
            }

        fun get(): ConnectionState = synchronized(lock) { _state }

        fun set(state: ConnectionState) = synchronized(lock) { _state = state }
    }

    private val stateMachine = StateMachine()

    companion object {
        // Logging configuration
        private const val LOG_ENABLED = true
        private const val LOG_DEBUG_ENABLED = true

        private const val SOCKET_TIMEOUT_MS = 60_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }

    private val mainJob = SupervisorJob()
    private val scope = CoroutineScope(mainJob + Dispatchers.IO)
    private val client =
        HttpClient(CIO) {
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

    private val json =
        Json {
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

    // Use AtomicReference for thread-safe access from the audio capture thread
    private val onUserAudioCapturedCallback = AtomicReference<((ByteArray) -> Unit)?>(null)

    // Audio feedback prevention
    private val isAgentPlayingAudio = AtomicBoolean(false)
    private var audioPlaybackJob: Job? = null

    // Non-blocking audio queue for ElevenLabs - prevents blocking WebSocket message loop
    private val audioPlaybackChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var elevenLabsAudioPlayerJob: Job? = null

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, READY }

    val connectionState = AtomicReference(ConnectionState.DISCONNECTED)

    private val agentReady = AtomicBoolean(false)

    private var audioCaptureJob: Job? = null
    private val isUserSpeaking = AtomicBoolean(false)
    private var keepAliveJob: Job? = null

    private val sessionLock = Any()

    private val reconnectAttempts = AtomicInteger(0)
    private var lastSessionParams: SessionParams? = null

    private data class SessionParams(
        val apiKey: String,
        val language: String,
        val level: String,
        val scenario: String,
        val onMessage: (AgentMessage) -> Unit,
        val onAudioReceived: ((ByteArray) -> Unit)?,
        val onUserAudioCaptured: ((ByteArray) -> Unit)?,
    )

    private data class CustomPipelineConfig(
        val apiKey: String,
        val language: String,
        val level: String,
        val scenario: String,
    )

    private var customPipelineConfig: CustomPipelineConfig? = null

    data class AgentMessage(
        val type: String,
        val event: String? = null,
        val content: String? = null,
        val role: String? = null,
        val error: String? = null,
        val description: String? = null,
        val code: String? = null,
        val isFinal: Boolean = true, // false = interim/streaming transcript
        val turnId: String? = null, // unique ID for updating streaming turns in-place
    )

    suspend fun startConversation(
        apiKey: String,
        language: String,
        level: String,
        scenario: String,
        onMessage: (AgentMessage) -> Unit,
        onAudioReceived: ((ByteArray) -> Unit)? = null,
        onUserAudioCaptured: ((ByteArray) -> Unit)? = null,
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
            // Set callback atomically for thread-safe access from the audio capture thread
            onUserAudioCapturedCallback.set(onUserAudioCaptured)
            logDebug { "User audio callback set: ${onUserAudioCaptured != null}" }

            // Store session params for auto-reconnection on mid-session drops
            lastSessionParams = SessionParams(apiKey, language, level, scenario, onMessage, onAudioReceived, onUserAudioCaptured)
            reconnectAttempts.set(0) // Reset reconnect counter for a new session

// Use ElevenLabs Agent Platform for all languages
            logInfo { "Using ElevenLabs Agent for $language" }
            return startCustomConversation(apiKey, language, level, scenario)
        } catch (e: Exception) {
            logError("StartConversation") { "Failed: ${e.message}" }
            connectionState.set(ConnectionState.DISCONNECTED)
            stateMachine.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            Result.failure(e)
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
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

        // Use buffer pool to avoid allocations in a hot path
        val bufferSize = 2400 // 50ms at 24kHz

        audioCaptureJob =
            scope.launch {
                val buffer = ByteArray(bufferSize)
                val line = targetDataLine ?: return@launch
                var audioBytesSent = 0L

                try {
                    while (isUserSpeaking.get() && line.isOpen) {
                        try {
                            val bytesRead = line.read(buffer, 0, buffer.size)
                            if (bytesRead > 0) {
                                audioBytesSent += bytesRead
                                // Send only the necessary portion - Frame.Binary handles the range
                                currentSession?.send(Frame.Binary(true, buffer.copyOf(bytesRead)))

                                if (audioBytesSent % 120000 == 0L) {
                                    logDebug { "Sent ${audioBytesSent / 1000}KB audio" }
                                }
                            }
                            delay(5)
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
                    // Buffer cleanup isn't needed for ByteArray
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

    suspend fun stopConversation(): Result<Unit> {
        return try {
            logInfo { "Stopping conversation" }

            // For ElevenLabs, send a stop signal before disconnecting
            if (customPipelineConfig != null) {
                stopElevenLabsSession()
            }

            connectionState.set(ConnectionState.DISCONNECTED)
            stateMachine.set(ConnectionState.DISCONNECTED)
            agentReady.set(false)
            isAgentPlayingAudio.set(false)
            isUserSpeaking.set(false)

            // Stop all workers and tracked jobs
            stopKeepAlive()
            stopAudioCapture()
            stopElevenLabsAudioPlayer()

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
            val agentSessionToClose =
                synchronized(sessionLock) {
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
            onUserAudioCapturedCallback.set(null)
            onAudioReceivedCallback = null

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
        scenario: String,
    ): Result<String> {
        return try {
            // Step 1: Start ElevenLabs Agent session via REST API
            logInfo { "Starting ElevenLabs Agent conversation for $language" }

            // Get actual user ID for proper conversation tracking
            val userId = org.example.project.core.api.SupabaseApiHelper.getCurrentUserId() ?: "anonymous-user"

            val requestBody =
                json.encodeToString(
                    mapOf(
                        "user_id" to userId,
                        "language" to language,
                        "level" to level,
                        "scenario" to scenario,
                    ),
                )

            val startResponse =
                client.post("http://localhost:8000/elevenlabs-agent/start") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

            if (!startResponse.status.isSuccess()) {
                val errorBody = startResponse.bodyAsText()
                logError("ElevenLabs") { "Failed to start session: ${startResponse.status} - $errorBody" }
                return Result.failure(Exception("Failed to start ElevenLabs session: $errorBody"))
            }

            val startResult = json.parseToJsonElement(startResponse.bodyAsText()).jsonObject
            val sessionId =
                startResult["session_id"]?.jsonPrimitive?.content
                    ?: return Result.failure(Exception("No session_id in response"))
            startResult["signed_url"]?.jsonPrimitive?.content

            logInfo { "ElevenLabs session started: $sessionId" }

            // Step 2: Connect to ElevenLabs Agent WebSocket via backend proxy
            val elevenLabsWsUrl = "ws://localhost:8000/elevenlabs-agent/ws/$sessionId"

            logInfo { "Connecting to ElevenLabs Agent WebSocket: $elevenLabsWsUrl" }

            currentSession = client.webSocketSession(elevenLabsWsUrl)

            logInfo { "ElevenLabs Agent WebSocket connected" }

            // Initialize audio playback for ElevenLabs (16kHz to match ElevenLabs output)
            val audioFormat =
                AudioFormat(
                    PCM_SIGNED,
                    16000f,
                    16,
                    1,
                    2,
                    16000f,
                    false,
                )
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            sourceDataLine?.open(audioFormat, 65536)
            sourceDataLine?.start()
            logInfo { "Audio playback initialized at 16kHz for ElevenLabs" }

            // Start listening for ElevenLabs Agent responses
            scope.launch {
                logDebug { "Starting ElevenLabs Agent message listener" }
                try {
                    while (true) {
                        val frame = currentSession?.incoming?.receive() ?: break

                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                logDebug { "ElevenLabs message: ${text.take(100)}..." }

                                try {
                                    val jsonMessage = json.parseToJsonElement(text).jsonObject
                                    val type = jsonMessage["type"]?.jsonPrimitive?.content

                                    when (type) {
                                        "connection" -> {
                                            logInfo { "ElevenLabs Agent connection established" }
                                            onMessageCallback?.invoke(
                                                AgentMessage(
                                                    type = "agent_message",
                                                    event = "Welcome",
                                                ),
                                            )
                                        }
                                        "conversation_text" -> {
                                            // ElevenLabs sends transcripts and agent responses
                                            // Normalize to "agent_message" with "ConversationText" event for UI consistency
                                            val content = jsonMessage["content"]?.jsonPrimitive?.content
                                            val role = jsonMessage["role"]?.jsonPrimitive?.content
                                            val isFinal = jsonMessage["is_final"]?.jsonPrimitive?.boolean ?: true
                                            val turnId = jsonMessage["turn_id"]?.jsonPrimitive?.content

                                            logDebug {
                                                "ElevenLabs conversation: role=$role, content='${content?.take(
                                                    50,
                                                )}...', final=$isFinal, turnId=$turnId"
                                            }

                                            if (!content.isNullOrEmpty()) {
                                                // Use agent_message type with ConversationText event for ViewModel compatibility
                                                onMessageCallback?.invoke(
                                                    AgentMessage(
                                                        type = "agent_message",
                                                        event = "ConversationText",
                                                        content = content,
                                                        role = role,
                                                        isFinal = isFinal,
                                                        turnId = turnId,
                                                    ),
                                                )
                                            }
                                        }
                                        "agent_message" -> {
                                            val event = jsonMessage["event"]?.jsonPrimitive?.content

                                            logDebug { "ElevenLabs event: $event" }

                                            when (event) {
                                                "Welcome" -> {
                                                    onMessageCallback?.invoke(
                                                        AgentMessage(
                                                            type = "agent_message",
                                                            event = "Welcome",
                                                        ),
                                                    )
                                                }
                                                "AgentStartedSpeaking" -> {
                                                    isAgentPlayingAudio.set(true)
                                                    onMessageCallback?.invoke(
                                                        AgentMessage(
                                                            type = "agent_message",
                                                            event = "AgentStartedSpeaking",
                                                        ),
                                                    )
                                                }
                                                "AgentAudioDone" -> {
                                                    isAgentPlayingAudio.set(false)
                                                    onMessageCallback?.invoke(
                                                        AgentMessage(
                                                            type = "agent_message",
                                                            event = "AgentAudioDone",
                                                        ),
                                                    )
                                                }
                                                "UserStartedSpeaking" -> {
                                                    onMessageCallback?.invoke(
                                                        AgentMessage(
                                                            type = "agent_message",
                                                            event = "UserStartedSpeaking",
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                        "error" -> {
                                            val errorMsg =
                                                jsonMessage["error"]?.jsonPrimitive?.content
                                                    ?: jsonMessage["message"]?.jsonPrimitive?.content
                                            logError("ElevenLabs") { "Error: $errorMsg" }
                                            onMessageCallback?.invoke(
                                                AgentMessage(
                                                    type = "error",
                                                    error = errorMsg,
                                                ),
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    logError("ElevenLabsMessage") { "Parse error: ${e.message}" }
                                }
                            }
                            is Frame.Binary -> {
                                // ElevenLabs sends TTS audio as binary frames
                                val audioData = frame.readBytes()
                                logDebug { "ElevenLabs audio received: ${audioData.size} bytes" }

                                // Queue audio for non-blocking playback (don't block message loop)
                                audioPlaybackChannel.trySend(audioData)
                                onAudioReceivedCallback?.invoke(audioData)
                            }
                            is Frame.Close -> {
                                logInfo { "ElevenLabs Agent WebSocket closed" }
                                connectionState.set(ConnectionState.DISCONNECTED)
                                stateMachine.set(ConnectionState.DISCONNECTED)
                                break
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        logError("ElevenLabsHandler") { "Error: ${e.message}" }
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
            logInfo { "ElevenLabs Agent pipeline ready for $language" }

            // Start a non-blocking audio player for ElevenLabs
            startElevenLabsAudioPlayer()
            logInfo { "ElevenLabs audio player started (non-blocking)" }

            // Auto-start microphone for continuous listening mode
            // ElevenLabs agents expect continuous audio streaming
            startElevenLabsAudioCapture()
            logInfo { "ElevenLabs microphone auto-started for continuous listening" }

            Result.success("elevenlabs-session-$sessionId")
        } catch (e: Exception) {
            logError("ElevenLabsConversation") { "Error: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Start the non-blocking audio player for ElevenLabs.
     * Consumes audio from the channel and plays it without blocking the WebSocket message loop.
     * This ensures text messages are processed immediately while audio plays in the background.
     */
    private fun startElevenLabsAudioPlayer() {
        elevenLabsAudioPlayerJob?.cancel()
        elevenLabsAudioPlayerJob =
            scope.launch {
                logDebug { "ElevenLabs audio player coroutine started" }
                try {
                    for (audioData in audioPlaybackChannel) {
                        try {
                            // Play audio in this dedicated coroutine (blocking here is OK)
                            if (sourceDataLine?.isOpen == true) {
                                sourceDataLine?.write(audioData, 0, audioData.size)
                            }
                        } catch (e: Exception) {
                            if (e !is kotlinx.coroutines.CancellationException) {
                                logError("ElevenLabsAudioPlayer") { "Error playing audio: ${e.message}" }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    logDebug { "ElevenLabs audio player cancelled" }
                } catch (e: Exception) {
                    logError("ElevenLabsAudioPlayer") { "Channel error: ${e.message}" }
                }
            }
    }

    /**
     * Stop the ElevenLabs audio player.
     */
    private fun stopElevenLabsAudioPlayer() {
        elevenLabsAudioPlayerJob?.cancel()
        elevenLabsAudioPlayerJob = null
        // Clear any pending audio in the channel
        while (audioPlaybackChannel.tryReceive().isSuccess) { /* drain */ }
    }

    fun startCustomManualAudioCapture() {
        // For ElevenLabs continuous mode, mic is already running (auto-started)
        // This is a no-op - user doesn't need to press a button to start
        if (audioCaptureJob?.isActive == true) {
            logInfo { "ElevenLabs continuous mode - mic already active" }
            return
        }
        // If audio capture isn't running for some reason, restart it
        if (customPipelineConfig != null && stateMachine.get() == ConnectionState.READY) {
            logInfo { "Restarting ElevenLabs audio capture" }
            startElevenLabsAudioCapture()
        }
    }

    fun stopCustomManualAudioCapture() {
        // For ElevenLabs continuous mode, this is a NO-OP
        // The microphone stays on continuously - user doesn't need to hold a button
        // Audio capture will be stopped only when the conversation ends via stopConversation()
        logInfo { "ElevenLabs continuous mode - mic stays active (no action needed)" }
    }

    fun stopElevenLabsSession() {
        // This actually stops the ElevenLabs session - called only when ending conversation
        logInfo { "Stopping ElevenLabs session" }
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        targetDataLine?.stop()
        targetDataLine?.flush()

        // Send a stop signal to ElevenLabs
        scope.launch {
            try {
                currentSession?.send(Frame.Text("""{"type":"stop"}"""))
            } catch (e: Exception) {
                logError("ElevenLabs") { "Error sending stop: ${e.message}" }
            }
        }
    }

    private fun startElevenLabsAudioCapture() {
        // ElevenLabs expects 16kHz mono PCM audio
        val audioFormat = AudioFormat(PCM_SIGNED, 16000f, 16, 1, 2, 16000f, false)
        val info = DataLine.Info(TargetDataLine::class.java, audioFormat)

        if (targetDataLine == null || !targetDataLine!!.isOpen) {
            targetDataLine = AudioSystem.getLine(info) as TargetDataLine
            targetDataLine?.open(audioFormat)
        }
        targetDataLine?.start()

        val bufferSize = 1600 // 100ms at 16kHz

        audioCaptureJob =
            scope.launch {
                val buffer = ByteArray(bufferSize)
                try {
                    // Get callback reference once at the start for thread-safe access
                    val userAudioCallback = onUserAudioCapturedCallback.get()
                    logInfo { "Starting audio capture for ElevenLabs Agent (continuous mode), user callback: ${userAudioCallback != null}" }

                    var audioBytesSent = 0L
                    var userAudioChunksSent = 0L

                    // Continuous mode: always capture audio while conversation is active
                    // ElevenLabs will detect when a user speaks and handle interruption
                    while (stateMachine.get() == ConnectionState.READY && targetDataLine?.isOpen == true) {
                        val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            audioBytesSent += bytesRead
                            val audioChunk = buffer.copyOf(bytesRead)

                            // Send audio as a binary frame to ElevenLabs via backend
                            currentSession?.send(Frame.Binary(true, audioChunk))

                            // Send to callback for session recording (user audio)
                            // Re-get the callback each time in case it was set after capture started
                            val callback = onUserAudioCapturedCallback.get()
                            if (callback != null) {
                                try {
                                    callback.invoke(audioChunk)
                                    userAudioChunksSent++
                                } catch (e: Exception) {
                                    logError("UserAudioCallback") { "Error invoking callback: ${e.message}" }
                                }
                            }

                            if (audioBytesSent % 160000 == 0L) {
                                logDebug { "Sent ${audioBytesSent / 1000}KB to ElevenLabs, user chunks: $userAudioChunksSent" }
                            }
                        }
                    }
                    logInfo { "ElevenLabs audio capture ended. Total: ${audioBytesSent / 1000}KB, user chunks: $userAudioChunksSent" }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        logError("ElevenLabsAudioCapture") { "Error: ${e.message}" }
                        onMessageCallback?.invoke(
                            AgentMessage(
                                type = "error",
                                error = "Audio capture failed: ${e.message}",
                            ),
                        )
                    }
                }
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
        elevenLabsAudioPlayerJob?.cancel()

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

        // Cancel the mainJob and wait for children with timeout
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
