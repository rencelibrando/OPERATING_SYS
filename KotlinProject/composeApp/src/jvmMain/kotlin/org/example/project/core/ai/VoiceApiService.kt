package org.example.project.core.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.example.project.core.config.AIBackendConfig
import org.example.project.core.utils.ErrorLogger
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.encodeToString
private const val LOG_TAG = "VoiceApiService.kt"

@Serializable
data class VoiceTranscriptionResponse(
    val success: Boolean,
    val transcript: String,
    val confidence: Float,
    val words: List<Map<String, String>> = emptyList(),
    val language_detected: String? = null,
    val duration: Float? = null
)

@Serializable
data class VoiceFeedbackResponse(
    val success: Boolean,
    val scores: Map<String, Float>,
    val overall_score: Float,
    val feedback_messages: List<String>,
    val suggestions: List<String>,
    val corrected_text: String? = null
)

@Serializable
data class VoiceSessionSaveResponse(
    val success: Boolean,
    val session_id: String,
    val message: String
)

@Serializable
data class VoiceProgressResponse(
    val success: Boolean,
    val total_sessions: Int,
    val average_scores: Map<String, Float>,
    val sessions_by_language: Map<String, Int>,
    val recent_sessions: List<Map<String, String>>,
    val improvement_trends: Map<String, Float>
)

@Serializable
data class VoiceScenariosResponse(
    val success: Boolean,
    val scenarios: Map<String, Map<String, String>>
)

@Serializable
data class VoiceLanguagesResponse(
    val success: Boolean,
    val languages: List<Map<String, String>>
)

class VoiceApiService {
    private val client = HttpClient(CIO) {
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
        model: String = "nova-3"
    ): Result<VoiceTranscriptionResponse> = runCatching {
        val url = AIBackendConfig.getEndpointUrl("/voice/transcribe")
        println("Transcribing audio with language: $language")
        println("   File: ${audioFile.name}, Size: ${audioFile.length()} bytes")

        val response = client.post(url) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("audio_file", audioFile.readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, "audio/wav")
                            append(HttpHeaders.ContentDisposition, "filename=\"${audioFile.name}\"")
                        })
                        append("language", language)
                        append("model", model)
                    }
                )
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
        userId: String
    ): Result<VoiceFeedbackResponse> = runCatching {
        val url = AIBackendConfig.getEndpointUrl("/voice/feedback")
        println("Generating feedback for transcript in $language")

        val response = client.post(url) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("transcript", transcript)
                        expectedText?.let { append("expected_text", it) }
                        append("language", language)
                        append("level", level)
                        append("scenario", scenario)
                        append("user_id", userId)
                    }
                )
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
        feedback: Map<String, Any>,
        sessionDuration: Float
    ): Result<VoiceSessionSaveResponse> = runCatching {
        val url = AIBackendConfig.getEndpointUrl("/voice/session/save")
        println("Saving voice session for user: $userId")

        val response = client.post(url) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("user_id", userId)
                        append("language", language)
                        append("level", level)
                        append("scenario", scenario)
                        append("transcript", transcript)
                        audioUrl?.let { append("audio_url", it) }
                        append("feedback", Json.encodeToString(feedback))
                        append("session_duration", sessionDuration.toString())
                    }
                )
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
        days: Int = 30
    ): Result<VoiceProgressResponse> = runCatching {
        val url = AIBackendConfig.getEndpointUrl("/voice/progress")
        println("Getting voice progress for user: $userId")

        val response = client.post(url) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("user_id", userId)
                        language?.let { append("language", it) }
                        append("days", days.toString())
                    }
                )
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
        level: String
    ): Result<VoiceScenariosResponse> = runCatching {
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

    suspend fun getLanguages(): Result<VoiceLanguagesResponse> = runCatching {
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

    fun close() {
        client.close()
    }
}
