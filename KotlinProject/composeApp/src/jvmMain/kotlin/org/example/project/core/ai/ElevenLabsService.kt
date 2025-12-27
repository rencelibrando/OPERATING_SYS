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
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    // Voice IDs for multilingual models
    private val koreanVoiceId = "mWWuFxksGqN2ufDOCo92" // Custom voice from dashboard
    private val chineseVoiceId = "mWWuFxksGqN2ufDOCo92" // Custom voice from dashboard
    
    suspend fun synthesizeSpeech(
        text: String,
        language: String,
        voiceId: String? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val selectedVoiceId = voiceId ?: when (language.lowercase()) {
                "korean", "hangeul" -> koreanVoiceId
                "mandarin", "chinese" -> chineseVoiceId
                else -> koreanVoiceId
            }
            
            println("[ElevenLabs] Synthesizing speech for language: $language")
            println("[ElevenLabs] Text: ${text.take(100)}...")
            
            val requestBody = buildJsonObject {
                put("text", text)
                put("model_id", "eleven_turbo_v2_5")
                put("voice_settings", buildJsonObject {
                    put("stability", 0.75)
                    put("similarity_boost", 0.75)
                    put("style", 0.0)
                    put("use_speaker_boost", true)
                })
                putJsonArray("pronunciation_dictionary_locators") {
                    // Add pronunciation dictionaries if needed for better accent
                }
            }
            
            println("[ElevenLabs] Using voice ID: $selectedVoiceId")
            
            val response = httpClient.post("https://api.elevenlabs.io/v1/text-to-speech/$selectedVoiceId") {
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
    
    suspend fun playAudio(audioBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("[ElevenLabs] Starting MP3 audio playback (${audioBytes.size} bytes)")
            
            // Get audio input stream from MP3 bytes (MP3 SPI handles decoding)
            val inputStream = audioBytes.inputStream()
            var audioInputStream = AudioSystem.getAudioInputStream(inputStream)
            
            // Convert MP3 to PCM if needed
            val baseFormat = audioInputStream.format
            if (baseFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
                println("[ElevenLabs] Converting from ${baseFormat.encoding} to PCM_SIGNED")
                val decodedFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    baseFormat.channels * 2,
                    baseFormat.sampleRate,
                    false
                )
                audioInputStream = AudioSystem.getAudioInputStream(decodedFormat, audioInputStream)
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
            
            sourceDataLine.drain()
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
        voiceId: String? = null
    ): Result<Unit> {
        val synthesisResult = synthesizeSpeech(text, language, voiceId)
        return if (synthesisResult.isSuccess) {
            playAudio(synthesisResult.getOrThrow())
        } else {
            Result.failure(synthesisResult.exceptionOrNull() ?: Exception("Synthesis failed"))
        }
    }
}
