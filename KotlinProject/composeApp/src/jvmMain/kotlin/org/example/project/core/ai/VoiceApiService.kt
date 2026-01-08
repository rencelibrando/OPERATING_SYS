package org.example.project.core.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.core.config.AIBackendConfig
import org.example.project.core.utils.ErrorLogger
import java.io.File

private const val LOG_TAG = "VoiceApiService.kt"

@Serializable
data class VoiceTranscriptionResponse(
    val success: Boolean,
    val transcript: String,
    val confidence: Float,
    val words: List<Map<String, String>> = emptyList(),
    val language_detected: String? = null,
    val duration: Float? = null,
)

@Serializable
data class VoiceFeedbackResponse(
    val success: Boolean,
    val scores: Map<String, Float>,
    val overall_score: Float,
    val feedback_messages: List<String>,
    val suggestions: List<String>,
    val corrected_text: String? = null,
)

@Serializable
data class VoiceSessionSaveResponse(
    val success: Boolean,
    val session_id: String,
    val message: String,
)

@Serializable
data class VoiceProgressResponse(
    val success: Boolean,
    val total_sessions: Int,
    val average_scores: Map<String, Float>,
    val sessions_by_language: Map<String, Int>,
    val recent_sessions: List<Map<String, String>>,
    val improvement_trends: Map<String, Float>,
)

@Serializable
data class VoiceScenariosResponse(
    val success: Boolean,
    val scenarios: Map<String, Map<String, String>>,
)

@Serializable
data class VoiceLanguagesResponse(
    val success: Boolean,
    val languages: List<Map<String, String>>,
)

@Serializable
data class LocalVoiceAnalysisResponse(
    val success: Boolean,
    val transcript: String = "",
    val confidence: Float = 0f,
    val language_detected: String? = null,
    val duration: Float = 0f,
    val words: List<Map<String, String>> = emptyList(),
    val scores: Map<String, Float> = emptyMap(),
    val overall_score: Float = 0f,
    val feedback_messages: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val voice_quality: Map<String, Float> = emptyMap(),
    val pronunciation_metrics: Map<String, Float> = emptyMap(),
    val fluency_metrics: Map<String, Float> = emptyMap(),
    val energy_profile: List<Float> = emptyList(),
    val error: String? = null,
)

@Serializable
data class LocalSpeakerAnalysisResponse(
    val success: Boolean,
    val voice_quality: Map<String, Float> = emptyMap(),
    val pronunciation: Map<String, Float> = emptyMap(),
    val fluency: Map<String, Float> = emptyMap(),
    val clarity_score: Float = 0f,
    val energy_profile: List<Float> = emptyList(),
    val has_embedding: Boolean = false,
)

@Serializable
data class LocalHealthResponse(
    val success: Boolean,
    val status: String,
    val device: String = "cpu",
    val cuda_available: Boolean = false,
    val models: Map<String, Map<String, String>> = emptyMap(),
)

class VoiceApiService {
    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = false
                        encodeDefaults = true
                    },
                )
            }

            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = AIBackendConfig.REQUEST_TIMEOUT_MS
                connectTimeoutMillis = AIBackendConfig.CONNECTION_TIMEOUT_MS
            }
        }

    suspend fun transcribeAudio(
        audioFile: File,
        language: String,
        model: String = "nova-3",
    ): Result<VoiceTranscriptionResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/transcribe")
            println("Transcribing audio with language: $language")
            println("   File: ${audioFile.name}, Size: ${audioFile.length()} bytes")

            val response =
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "audio_file",
                                    audioFile.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "audio/wav")
                                        append(HttpHeaders.ContentDisposition, "filename=\"${audioFile.name}\"")
                                    },
                                )
                                append("language", language)
                                append("model", model)
                            },
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val transcriptionResponse = response.body<VoiceTranscriptionResponse>()
                println("Transcription successful: ${transcriptionResponse.transcript.take(50)}...")
                println("   Confidence: ${transcriptionResponse.confidence}")
                transcriptionResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Transcription failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Audio transcription failed")
        }

    suspend fun generateFeedback(
        transcript: String,
        expectedText: String? = null,
        language: String,
        level: String,
        scenario: String,
        userId: String,
    ): Result<VoiceFeedbackResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/feedback")
            println("Generating feedback for transcript in $language")

            val response =
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("transcript", transcript)
                                expectedText?.let { append("expected_text", it) }
                                append("language", language)
                                append("level", level)
                                append("scenario", scenario)
                                append("user_id", userId)
                            },
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val feedbackResponse = response.body<VoiceFeedbackResponse>()
                println("Feedback generated successfully")
                println("   Overall score: ${feedbackResponse.overall_score}")
                feedbackResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Feedback generation failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Feedback generation failed")
        }

    suspend fun saveSession(
        userId: String,
        language: String,
        level: String,
        scenario: String,
        transcript: String,
        audioUrl: String? = null,
        feedback: kotlinx.serialization.json.JsonObject,
        sessionDuration: Float,
    ): Result<VoiceSessionSaveResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/session/save")
            println("Saving voice session for user: $userId")

            val response =
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("user_id", userId)
                                append("language", language)
                                append("level", level)
                                append("scenario", scenario)
                                append("transcript", transcript)
                                audioUrl?.let { append("audio_url", it) }
                                append("feedback", feedback.toString())
                                append("session_duration", sessionDuration.toString())
                            },
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val saveResponse = response.body<VoiceSessionSaveResponse>()
                println("Session saved successfully: ${saveResponse.session_id}")
                saveResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Session save failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Session save failed")
        }

    suspend fun getProgress(
        userId: String,
        language: String? = null,
        days: Int = 30,
    ): Result<VoiceProgressResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/progress")
            println("Getting voice progress for user: $userId")

            val response =
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("user_id", userId)
                                language?.let { append("language", it) }
                                append("days", days.toString())
                            },
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val progressResponse = response.body<VoiceProgressResponse>()
                println("Progress retrieved successfully")
                println("   Total sessions: ${progressResponse.total_sessions}")
                progressResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Progress retrieval failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Progress retrieval failed")
        }

    suspend fun getScenarios(
        language: String,
        level: String,
    ): Result<VoiceScenariosResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/scenarios/$language/$level")
            println("Getting scenarios for $language - $level")

            val response = client.get(url)

            if (response.status.value in 200..299) {
                val scenariosResponse = response.body<VoiceScenariosResponse>()
                println("Scenarios retrieved successfully")
                scenariosResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Scenarios retrieval failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Scenarios retrieval failed")
        }

    suspend fun getLanguages(): Result<VoiceLanguagesResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/languages")
            println("Getting supported languages")

            val response = client.get(url)

            if (response.status.value in 200..299) {
                val languagesResponse = response.body<VoiceLanguagesResponse>()
                println("Languages retrieved successfully")
                languagesResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Languages retrieval failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Languages retrieval failed")
        }

    suspend fun getLessonScenarios(
        lessonId: String,
        language: String?,
    ): Result<List<org.example.project.domain.model.SpeakingScenario>> =
        runCatching {
            val baseUrl = "/voice/lesson/$lessonId/scenarios"
            val url =
                if (language != null) {
                    AIBackendConfig.getEndpointUrl("$baseUrl?language=$language")
                } else {
                    AIBackendConfig.getEndpointUrl(baseUrl)
                }
            println("Getting lesson scenarios for lesson: $lessonId, language: $language")

            val response = client.get(url)

            if (response.status.value in 200..299) {
                val scenarios = response.body<List<org.example.project.domain.model.SpeakingScenario>>()
                println("Lesson scenarios retrieved: ${scenarios.size} scenarios")
                scenarios
            } else {
                val errorBody = response.body<String>()
                throw Exception("Lesson scenarios retrieval failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Lesson scenarios retrieval failed")
        }

    suspend fun getConversationSessions(userId: String): Result<List<org.example.project.domain.model.ConversationSession>> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/conversation/sessions/$userId")
            println("Getting conversation sessions for user: $userId")

            val response = client.get(url)

            if (response.status.value in 200..299) {
                val sessions = response.body<List<org.example.project.domain.model.ConversationSession>>()
                println("Conversation sessions retrieved: ${sessions.size} sessions")
                sessions
            } else {
                val errorBody = response.body<String>()
                throw Exception("Conversation sessions retrieval failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Conversation sessions retrieval failed")
        }

    suspend fun getConversationRecording(sessionId: String): Result<org.example.project.domain.model.ConversationRecording> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/conversation/recording/$sessionId")
            println("Getting conversation recording for session: $sessionId")

            val response = client.get(url)

            if (response.status.value in 200..299) {
                val recording = response.body<org.example.project.domain.model.ConversationRecording>()
                println("Conversation recording retrieved successfully")
                recording
            } else {
                val errorBody = response.body<String>()
                throw Exception("Conversation recording retrieval failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Conversation recording retrieval failed")
        }

    suspend fun saveConversationRecording(
        sessionId: String,
        userId: String,
        language: String,
        audioFile: File?,
        transcript: String,
        turnCount: Int,
        duration: Float,
    ): Result<org.example.project.domain.model.ConversationRecording> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/conversation/recording/save")
            println("Saving conversation recording for session: $sessionId")

            val response =
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("session_id", sessionId)
                                append("user_id", userId)
                                append("language", language)
                                append("transcript", transcript)
                                append("turn_count", turnCount.toString())
                                append("duration", duration.toString())
                                audioFile?.let {
                                    append(
                                        "audio_file",
                                        it.readBytes(),
                                        Headers.build {
                                            append(HttpHeaders.ContentType, "audio/wav")
                                            append(HttpHeaders.ContentDisposition, "filename=\"${it.name}\"")
                                        },
                                    )
                                }
                            },
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val recording = response.body<org.example.project.domain.model.ConversationRecording>()
                println("Conversation recording saved successfully")
                recording
            } else {
                val errorBody = response.body<String>()
                throw Exception("Conversation recording save failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Conversation recording save failed")
        }

    suspend fun deleteConversationSession(sessionId: String): Result<Boolean> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/voice/conversation/session/delete/$sessionId")
            println("Deleting conversation session: $sessionId")

            val response = client.post(url)

            if (response.status.value in 200..299) {
                println("Conversation session deleted successfully")
                true
            } else {
                val errorBody = response.body<String>()
                throw Exception("Session delete failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Conversation session delete failed")
        }

    /**
     * Analyze audio using local Whisper STT + SpeechBrain speaker analysis.
     * No API keys required - runs entirely on the backend server.
     */
    suspend fun analyzeVoiceLocal(
        audioFile: File,
        language: String? = null,
        expectedText: String? = null,
        level: String = "intermediate",
        scenario: String = "daily_conversation",
        userId: String,
    ): Result<LocalVoiceAnalysisResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/local-voice/analyze")
            println("[LocalVoice] Analyzing audio with Whisper + SpeechBrain")
            println("   File: ${audioFile.name}, Size: ${audioFile.length()} bytes")
            println("   Language: $language, Level: $level, Scenario: $scenario")

            val response =
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "audio_file",
                                    audioFile.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "audio/wav")
                                        append(HttpHeaders.ContentDisposition, "filename=\"${audioFile.name}\"")
                                    },
                                )
                                language?.let { append("language", it) }
                                expectedText?.let { append("expected_text", it) }
                                append("level", level)
                                append("scenario", scenario)
                                append("user_id", userId)
                            },
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val analysisResponse = response.body<LocalVoiceAnalysisResponse>()
                println("[LocalVoice] Analysis complete:")
                println("   Transcript: ${analysisResponse.transcript.take(50)}...")
                println("   Overall score: ${analysisResponse.overall_score}")
                println("   Scores: ${analysisResponse.scores}")
                analysisResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Local voice analysis failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Local voice analysis failed")
        }

    /**
     * Transcribe audio using local Whisper model only.
     */
    suspend fun transcribeAudioLocal(
        audioFile: File,
        language: String? = null,
    ): Result<VoiceTranscriptionResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/local-voice/transcribe")
            println("[LocalVoice] Transcribing audio with Whisper")
            println("   File: ${audioFile.name}, Size: ${audioFile.length()} bytes")

            val response =
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "audio_file",
                                    audioFile.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "audio/wav")
                                        append(HttpHeaders.ContentDisposition, "filename=\"${audioFile.name}\"")
                                    },
                                )
                                language?.let { append("language", it) }
                            },
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val transcriptionResponse = response.body<VoiceTranscriptionResponse>()
                println("[LocalVoice] Transcription complete: ${transcriptionResponse.transcript.take(50)}...")
                transcriptionResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Local transcription failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Local transcription failed")
        }

    /**
     * Analyze speaker characteristics using local SpeechBrain model.
     */
    suspend fun analyzeSpeakerLocal(
        audioFile: File,
    ): Result<LocalSpeakerAnalysisResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/local-voice/analyze-speaker")
            println("[LocalVoice] Analyzing speaker with SpeechBrain")
            println("   File: ${audioFile.name}, Size: ${audioFile.length()} bytes")

            val response =
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "audio_file",
                                    audioFile.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "audio/wav")
                                        append(HttpHeaders.ContentDisposition, "filename=\"${audioFile.name}\"")
                                    },
                                )
                            },
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val speakerResponse = response.body<LocalSpeakerAnalysisResponse>()
                println("[LocalVoice] Speaker analysis complete:")
                println("   Clarity: ${speakerResponse.clarity_score}")
                println("   Voice quality: ${speakerResponse.voice_quality}")
                speakerResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Speaker analysis failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Speaker analysis failed")
        }

    /**
     * Check if local voice analysis service is available and healthy.
     */
    suspend fun checkLocalVoiceHealth(): Result<LocalHealthResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/local-voice/health")
            println("[LocalVoice] Checking service health")

            val response = client.get(url)

            if (response.status.value in 200..299) {
                val healthResponse = response.body<LocalHealthResponse>()
                println("[LocalVoice] Service status: ${healthResponse.status}")
                println("   Device: ${healthResponse.device}")
                println("   CUDA available: ${healthResponse.cuda_available}")
                healthResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Health check failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Local voice health check failed")
        }

    /**
     * Preload Whisper and SpeechBrain models to reduce first-request latency.
     */
    suspend fun preloadLocalModels(): Result<Boolean> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("/local-voice/preload-models")
            println("[LocalVoice] Preloading models...")

            val response = client.post(url)

            if (response.status.value in 200..299) {
                println("[LocalVoice] Models preloaded successfully")
                true
            } else {
                val errorBody = response.body<String>()
                throw Exception("Model preload failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Model preload failed")
        }

    fun close() {
        client.close()
    }
}
