package org.example.project.core.pronunciation

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.core.config.AIBackendConfig
import java.io.File

/**
 * Service for pronunciation practice - generates reference audio and compares user pronunciation
 */
class PronunciationService {
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
            requestTimeoutMillis = 120000L // 2 minutes for audio processing
            connectTimeoutMillis = AIBackendConfig.CONNECTION_TIMEOUT_MS
        }
    }

    /**
     * Generate reference audio for a word
     */
    suspend fun generateReferenceAudio(
        word: String,
        languageCode: String,
        wordId: String? = null
    ): Result<ReferenceAudioResponse> = runCatching {
        val url = AIBackendConfig.getEndpointUrl(AIBackendConfig.GENERATE_REFERENCE_AUDIO_ENDPOINT)
        println("Generating reference audio for '$word' in $languageCode")

        val request = GenerateReferenceAudioRequest(
            word = word,
            languageCode = languageCode,
            wordId = wordId
        )

        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (response.status.value in 200..299) {
            val audioResponse = response.body<ReferenceAudioResponse>()
            println("Reference audio generated: ${audioResponse.referenceAudioUrl}")
            audioResponse
        } else {
            val errorBody = response.body<String>()
            throw Exception("Failed to generate reference audio: ${response.status}, body: $errorBody")
        }
    }.onFailure { error ->
        println("Failed to generate reference audio: ${error.message}")
        if (error.message?.contains("Connection refused") == true || 
            error.message?.contains("Failed to connect") == true) {
            println("ERROR: Backend server is not running!")
            println("Please start the backend server:")
            println("  1. Open a terminal in the 'backend' folder")
            println("  2. Run: python main.py")
            println("  3. Or run: uvicorn main:app --reload")
            println("The server should start on http://localhost:8000")
        }
        error.printStackTrace()
    }

    /**
     * Compare user's pronunciation with reference audio
     */
    suspend fun comparePronunciation(
        word: String,
        languageCode: String,
        userAudioFile: File,
        referenceAudioUrl: String? = null
    ): Result<PronunciationComparisonResponse> = runCatching {
        val url = AIBackendConfig.getEndpointUrl(AIBackendConfig.COMPARE_PRONUNCIATION_ENDPOINT)
        println("Comparing pronunciation for '$word' in $languageCode")

        // Create multipart form data
        val audioBytes = userAudioFile.readBytes()
        val response = client.post(url) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("word", word)
                        append("language_code", languageCode)
                        if (referenceAudioUrl != null) {
                            append("reference_audio_url", referenceAudioUrl)
                        }
                        append(
                            "user_audio",
                            audioBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "audio/wav")
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"user_audio\"; filename=\"user_audio.wav\"")
                            }
                        )
                    }
                )
            )
        }

        if (response.status.value in 200..299) {
            val comparisonResponse = response.body<PronunciationComparisonResponse>()
            println("Pronunciation comparison complete. Score: ${comparisonResponse.overallScore}%")
            comparisonResponse
        } else {
            val errorBody = response.body<String>()
            throw Exception("Failed to compare pronunciation: ${response.status}, body: $errorBody")
        }
    }.onFailure { error ->
        println("Failed to compare pronunciation: ${error.message}")
        error.printStackTrace()
    }

    fun close() {
        client.close()
    }
}

@Serializable
data class GenerateReferenceAudioRequest(
    val word: String,
    @SerialName("language_code") val languageCode: String,
    @SerialName("word_id") val wordId: String? = null
)

@Serializable
data class ReferenceAudioResponse(
    val success: Boolean,
    @SerialName("reference_audio_url") val referenceAudioUrl: String,
    @SerialName("local_file_path") val localFilePath: String? = null
)

@Serializable
data class PronunciationMetrics(
    @SerialName("mfcc_similarity") val mfccSimilarity: Double,
    @SerialName("pitch_similarity") val pitchSimilarity: Double,
    @SerialName("duration_ratio") val durationRatio: Double,
    @SerialName("energy_ratio") val energyRatio: Double
)

@Serializable
data class PronunciationComparisonResponse(
    val success: Boolean,
    @SerialName("overall_score") val overallScore: Int,
    @SerialName("pronunciation_score") val pronunciationScore: Int,
    @SerialName("clarity_score") val clarityScore: Int,
    @SerialName("fluency_score") val fluencyScore: Int,
    val metrics: PronunciationMetrics,
    @SerialName("feedback_messages") val feedbackMessages: List<String>,
    val suggestions: List<String>
)

