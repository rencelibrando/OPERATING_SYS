package org.example.project.core.tts

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Service for generating audio narration using Edge TTS via the backend API.
 * Uses the same TTS service as the admin panel for consistent voice quality.
 */
class EdgeTTSService {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = 30000 // 30 seconds
            connectTimeoutMillis = 10000 // 10 seconds
            socketTimeoutMillis = 30000 // 30 seconds
        }
    }
    
    companion object {
        // Backend API URL - adjust based on your deployment
        private const val BASE_URL = "http://127.0.0.1:8000"
        
        // Voice mapping for different languages
        val VOICE_MAPPING = mapOf(
            "ko" to "ko-KR-SunHiNeural",      // Korean - Female
            "de" to "de-DE-KatjaNeural",      // German - Female
            "zh" to "zh-CN-XiaoNeural",       // Chinese - Female
            "es" to "es-ES-ElviraNeural",     // Spanish - Female
            "fr" to "fr-FR-DeniseNeural",     // French - Female
            "en" to "en-US-JennyNeural",      // English - Female (Default)
        )
    }
    
    /**
     * Generate audio narration for a word or text.
     * 
     * @param text The text to convert to speech
     * @param languageCode The language code (ko, de, zh, es, fr, en)
     * @param useCache Whether to use cached audio if available
     * @return Result containing the audio URL or error
     */
    suspend fun generateAudio(
        text: String,
        languageCode: String,
        useCache: Boolean = true
    ): Result<TTSResponse> = withContext(Dispatchers.IO) {
        try {
            println("[EdgeTTS] Generating audio for: '$text' (language: $languageCode)")
            
            val request = TTSRequest(
                text = text,
                languageOverride = languageCode,
                voiceOverride = VOICE_MAPPING[languageCode],
                useCache = useCache
            )
            
            val response = httpClient.post("$BASE_URL/api/narration/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val ttsResponse = response.body<TTSResponse>()
                println("[EdgeTTS] Audio generated successfully: ${ttsResponse.audioUrl}")
                Result.success(ttsResponse)
            } else {
                val errorBody = response.body<String>()
                println("[EdgeTTS] Failed to generate audio: ${response.status} - $errorBody")
                Result.failure(Exception("TTS generation failed: ${response.status}"))
            }
        } catch (e: Exception) {
            println("[EdgeTTS] Error generating audio: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get the appropriate voice for a language code.
     */
    fun getVoiceForLanguage(languageCode: String): String {
        return VOICE_MAPPING[languageCode] ?: VOICE_MAPPING["en"]!!
    }
    
    /**
     * Check if a language is supported for TTS.
     */
    fun isLanguageSupported(languageCode: String): Boolean {
        return VOICE_MAPPING.containsKey(languageCode)
    }
    
    fun close() {
        httpClient.close()
    }
}

@Serializable
data class TTSRequest(
    val text: String,
    @SerialName("language_override")
    val languageOverride: String? = null,
    @SerialName("voice_override")
    val voiceOverride: String? = null,
    @SerialName("use_cache")
    val useCache: Boolean = true
)

@Serializable
data class TTSResponse(
    @SerialName("audio_url")
    val audioUrl: String?,
    @SerialName("language_detected")
    val languageDetected: String,
    val confidence: Float,
    @SerialName("voice_used")
    val voiceUsed: String,
    val cached: Boolean
)
