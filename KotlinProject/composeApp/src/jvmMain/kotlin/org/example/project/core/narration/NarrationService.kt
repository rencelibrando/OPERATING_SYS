package org.example.project.core.narration

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GenerateNarrationRequest(
    @SerialName("text")
    val text: String,
    @SerialName("language_override")
    val languageOverride: String? = null,
    @SerialName("voice_override")
    val voiceOverride: String? = null,
    @SerialName("use_cache")
    val useCache: Boolean = true
)

@Serializable
data class GenerateNarrationResponse(
    @SerialName("audio_url")
    val audioUrl: String?,
    @SerialName("language_detected")
    val languageDetected: String,
    @SerialName("confidence")
    val confidence: Float,
    @SerialName("voice_used")
    val voiceUsed: String,
    @SerialName("cached")
    val cached: Boolean
)

@Serializable
data class DetectLanguageRequest(
    @SerialName("text")
    val text: String
)

@Serializable
data class DetectLanguageResponse(
    @SerialName("language_code")
    val languageCode: String,
    @SerialName("confidence")
    val confidence: Float,
    @SerialName("supported")
    val supported: Boolean
)

class NarrationService(
    private val baseUrl: String = "http://localhost:8000"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }

    suspend fun generateNarration(
        text: String,
        languageOverride: String? = null,
        voiceOverride: String? = null,
        useCache: Boolean = true
    ): Result<GenerateNarrationResponse> = withContext(Dispatchers.IO) {
        try {
            val request = GenerateNarrationRequest(
                text = text,
                languageOverride = languageOverride,
                voiceOverride = voiceOverride,
                useCache = useCache
            )
            
            val response = client.post("$baseUrl/api/narration/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.value in 200..299) {
                val narrationResponse = response.body<GenerateNarrationResponse>()
                Result.success(narrationResponse)
            } else {
                Result.failure(Exception("Narration generation failed: ${response.status}"))
            }
        } catch (e: Exception) {
            println("[NarrationService] Error generating narration: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun detectLanguage(text: String): Result<DetectLanguageResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val request = DetectLanguageRequest(text = text)
                
                val response = client.post("$baseUrl/api/narration/detect-language") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                
                if (response.status.value in 200..299) {
                    val languageResponse = response.body<DetectLanguageResponse>()
                    Result.success(languageResponse)
                } else {
                    Result.failure(Exception("Language detection failed: ${response.status}"))
                }
            } catch (e: Exception) {
                println("[NarrationService] Error detecting language: ${e.message}")
                Result.failure(e)
            }
        }

    fun close() {
        client.close()
    }
}
