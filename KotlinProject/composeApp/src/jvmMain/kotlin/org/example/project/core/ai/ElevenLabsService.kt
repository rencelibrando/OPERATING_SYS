package org.example.project.core.ai

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.sound.sampled.*

class ElevenLabsService(
    private val apiKey: String,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Enhanced voice IDs for better quality
    private val koreanVoiceId = "mWWuFxksGqN2ufDOCo92" // Custom voice from dashboard
    private val chineseVoiceId = "mWWuFxksGqN2ufDOCo92" // Custom voice from dashboard
    private val englishVoiceId = "rachel" // High-quality English voice
    private val frenchVoiceId = "emily" // Natural French voice
    private val spanishVoiceId = "bella" // Clear Spanish voice
    private val germanVoiceId = "hans" // Natural German voice
    private val italianVoiceId = "isabella" // Melodic Italian voice
    private val japaneseVoiceId = "mizuki" // Natural Japanese voice

    // Model selection based on use case
    private val highQualityModel = "eleven_multilingual_v2" // Best quality
    private val fastModel = "eleven_turbo_v2_5" // Faster generation
    private val lowLatencyModel = "eleven_flash_v2" // Lowest latency

    suspend fun synthesizeSpeech(
        text: String,
        language: String,
        voiceId: String? = null,
        quality: String = "high", // "high", "fast", "low_latency"
        prosody: Boolean = true,
        optimizeLatency: Boolean = false,
    ): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val selectedVoiceId =
                    voiceId ?: when (language.lowercase()) {
                        "korean", "hangeul" -> koreanVoiceId
                        "mandarin", "chinese" -> chineseVoiceId
                        "english", "en" -> englishVoiceId
                        "french", "fr" -> frenchVoiceId
                        "spanish", "es" -> spanishVoiceId
                        "german", "de" -> germanVoiceId
                        "italian", "it" -> italianVoiceId
                        "japanese", "ja" -> japaneseVoiceId
                        else -> koreanVoiceId
                    }

                val selectedModel =
                    when {
                        optimizeLatency -> lowLatencyModel
                        quality == "fast" -> fastModel
                        quality == "low_latency" -> lowLatencyModel
                        else -> highQualityModel
                    }

                println("[ElevenLabs] Synthesizing speech for language: $language")
                println("[ElevenLabs] Using model: $selectedModel")
                println("[ElevenLabs] Text: ${text.take(100)}...")

                // Enhanced voice settings for better quality - reduced deep voice effect
                val voiceSettings =
                    buildJsonObject {
                        // Stability: 0.0-1.0 (higher = more consistent, lower = more expressive)
                        put("stability", if (optimizeLatency) 0.85 else 0.65) // Reduced for more natural voice
                        // Similarity boost: 0.0-1.0 (higher = closer to original voice)
                        put("similarity_boost", if (optimizeLatency) 0.85 else 0.65) // Reduced for less artificial sound
                        // Style: 0.0-1.0 (higher = more style/expression)
                        put("style", if (prosody && !optimizeLatency) 0.5 else 0.2) // Increased for more natural expression
                        // Speaker boost: enhances voice clarity
                        put("use_speaker_boost", true)
                        // Add subtle emotion for better engagement
                        if (prosody && !optimizeLatency) {
                            put(
                                "emotion",
                                buildJsonObject {
                                    put("happiness", 0.2) // Slightly increased for warmer tone
                                    put("sadness", 0.0)
                                    put("anger", 0.0)
                                    put("surprise", 0.1)
                                },
                            )
                        }
                    }

                val requestBody =
                    buildJsonObject {
                        put("text", text)
                        put("model_id", selectedModel)
                        put("voice_settings", voiceSettings)
                        // Add pronunciation optimization for better clarity
                        putJsonArray("pronunciation_dictionary_locators") {
                            // Could add language-specific dictionaries here
                        }
                        // Optimize for speech clarity
                        if (!optimizeLatency) {
                            put("optimize_streaming_latency", if (quality == "low_latency") 3 else 2)
                        }
                    }

                println("[ElevenLabs] Using voice ID: $selectedVoiceId")

                val response =
                    httpClient.post("https://api.elevenlabs.io/v1/text-to-speech/$selectedVoiceId") {
                        headers {
                            append(HttpHeaders.Accept, "audio/mpeg")
                            append(HttpHeaders.ContentType, ContentType.Application.Json)
                            append("xi-api-key", apiKey)
                        }
                        setBody(requestBody.toString())
                    }

                if (response.status.isSuccess()) {
                    val audioBytes = response.readBytes()
                    println("[ElevenLabs] Synthesized ${audioBytes.size} bytes of audio")
                    Result.success(audioBytes)
                } else {
                    val errorBody = response.bodyAsText()
                    println("[ElevenLabs] Error: ${response.status}")
                    println("[ElevenLabs] Error body: $errorBody")
                    Result.failure(Exception("ElevenLabs API error: ${response.status} - $errorBody"))
                }
            } catch (e: Exception) {
                println("[ElevenLabs] Exception: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun playAudio(audioBytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                println("[ElevenLabs] Starting MP3 audio playback (${audioBytes.size} bytes)")

                // Get audio input stream from MP3 bytes (MP3 SPI handles decoding)
                val inputStream = audioBytes.inputStream()
                var audioInputStream = AudioSystem.getAudioInputStream(inputStream)

                // Convert MP3 to PCM if needed with better quality settings
                val baseFormat = audioInputStream.format
                if (baseFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
                    println("[ElevenLabs] Converting from ${baseFormat.encoding} to PCM_SIGNED")
                    println(
                        "[ElevenLabs] Original format: ${baseFormat.sampleRate}Hz, ${baseFormat.sampleSizeInBits}bit, ${baseFormat.channels}ch",
                    )

                    // Use higher quality PCM format for better voice reproduction
                    val decodedFormat =
                        AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            22050f, // Better than 16kHz for voice clarity
                            16,
                            baseFormat.channels,
                            baseFormat.channels * 2,
                            22050f,
                            false,
                        )
                    audioInputStream = AudioSystem.getAudioInputStream(decodedFormat, audioInputStream)
                    println(
                        "[ElevenLabs] Converted to: ${decodedFormat.sampleRate}Hz, ${decodedFormat.sampleSizeInBits}bit, ${decodedFormat.channels}ch",
                    )
                }

                val format = audioInputStream.format
                val info = DataLine.Info(SourceDataLine::class.java, format)
                val sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
                sourceDataLine.open(format, 131072) // 128KB buffer for smooth playback
                sourceDataLine.start()

                println("[ElevenLabs] Streaming audio: ${format.sampleRate}Hz ${format.sampleSizeInBits}bit ${format.channels}ch")

                val buffer = ByteArray(8192) // 8KB chunks for smooth streaming
                var bytesRead: Int
                while (audioInputStream.read(buffer).also { bytesRead = it } != -1) {
                    sourceDataLine.write(buffer, 0, bytesRead)
                }

                // Use non-blocking approach instead of drain() to prevent hanging
                // Wait for audio to finish with timeout, then force cleanup if needed
                val startTime = System.currentTimeMillis()
                val maxWaitTime = 5000L // 5 seconds max wait

                while (sourceDataLine.available() > 0 && (System.currentTimeMillis() - startTime) < maxWaitTime) {
                    kotlinx.coroutines.delay(100) // Small delay to prevent busy waiting
                }

                // Force stop if still playing after timeout
                if (sourceDataLine.available() > 0) {
                    println("[ElevenLabs] Audio still playing after timeout, forcing stop")
                    sourceDataLine.stop()
                }

                sourceDataLine.close()
                audioInputStream.close()

                println("[ElevenLabs] Audio playback completed successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                println("[ElevenLabs] Playback error: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }

    suspend fun synthesizeAndPlay(
        text: String,
        language: String,
        voiceId: String? = null,
        quality: String = "high",
        prosody: Boolean = true,
        optimizeLatency: Boolean = false,
    ): Result<Unit> {
        val synthesisResult = synthesizeSpeech(text, language, voiceId, quality, prosody, optimizeLatency)
        return if (synthesisResult.isSuccess) {
            playAudio(synthesisResult.getOrThrow())
        } else {
            Result.failure(synthesisResult.exceptionOrNull() ?: Exception("Synthesis failed"))
        }
    }

    /**
     * Synthesize speech with enhanced quality for educational content
     */
    suspend fun synthesizeEducationalContent(
        text: String,
        language: String,
        pace: String = "normal", // "slow", "normal", "fast"
        clarity: Boolean = true,
    ): Result<ByteArray> {
        // Adjust text based on pace
        val adjustedText =
            when (pace) {
                "slow" -> text.replace("(?<=\\.\\s)".toRegex(), "\n\n") // Add pauses
                "fast" -> text.replace("\n\n+".toRegex(), " ") // Remove extra pauses
                else -> text
            }

        return synthesizeSpeech(
            text = adjustedText,
            language = language,
            quality = "high",
            prosody = clarity,
            optimizeLatency = false,
        )
    }

    /**
     * Get available voice information for a language
     */
    fun getVoiceInfo(language: String): VoiceInfo {
        return when (language.lowercase()) {
            "korean", "hangeul" -> VoiceInfo(koreanVoiceId, "Korean", "Custom trained voice")
            "mandarin", "chinese" -> VoiceInfo(chineseVoiceId, "Chinese", "Custom trained voice")
            "english", "en" -> VoiceInfo(englishVoiceId, "English", "High-quality Rachel")
            "french", "fr" -> VoiceInfo(frenchVoiceId, "French", "Natural Emily")
            "spanish", "es" -> VoiceInfo(spanishVoiceId, "Spanish", "Clear Bella")
            "german", "de" -> VoiceInfo(germanVoiceId, "German", "Natural Hans")
            "italian", "it" -> VoiceInfo(italianVoiceId, "Italian", "Melodic Isabella")
            "japanese", "ja" -> VoiceInfo(japaneseVoiceId, "Japanese", "Natural Mizuki")
            else -> VoiceInfo(koreanVoiceId, "Default", "Custom trained voice")
        }
    }

    data class VoiceInfo(
        val voiceId: String,
        val language: String,
        val description: String,
    )
}
