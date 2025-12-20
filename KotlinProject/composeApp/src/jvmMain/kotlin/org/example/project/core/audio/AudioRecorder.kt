package org.example.project.core.audio

import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.sound.sampled.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers

/**
 * Audio recorder for capturing microphone input and saving to WAV file
 */
class AudioRecorder {
    private var targetDataLine: TargetDataLine? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private val audioBytes = ByteArrayOutputStream()
    private var audioFormat: AudioFormat? = null

    /**
     * Start recording audio from microphone
     */
    suspend fun startRecording(): Result<Unit> = suspendCoroutine { continuation ->
        try {
            audioFormat = AudioFormat(
                16000.0f,      // Sample rate: 16kHz (standard for speech)
                16,            // Sample size in bits
                1,             // Channels: 1 (mono)
                true,          // Signed
                false           // Big endian
            )

            val dataLineInfo = DataLine.Info(TargetDataLine::class.java, audioFormat)
            
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                continuation.resume(Result.failure(Exception("Audio line not supported")))
                return@suspendCoroutine
            }

            targetDataLine = AudioSystem.getLine(dataLineInfo) as TargetDataLine
            targetDataLine?.open(audioFormat)
            targetDataLine?.start()

            // Clear previous recording
            audioBytes.reset()
            isRecording = true

            // Start continuous reading in background
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(1024)
                while (isRecording && targetDataLine != null) {
                    try {
                        val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            audioBytes.write(buffer, 0, bytesRead)
                        }
                        delay(10) // Small delay to prevent busy waiting
                    } catch (e: Exception) {
                        if (isRecording) {
                            println("Error reading audio data: ${e.message}")
                        }
                        break
                    }
                }
            }

            continuation.resume(Result.success(Unit))
        } catch (e: Exception) {
            println("Error starting audio recording: ${e.message}")
            isRecording = false
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Stop recording and save to WAV file
     */
    suspend fun stopRecordingAndSave(outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording || targetDataLine == null) {
                return@withContext Result.failure(Exception("Not currently recording"))
            }

            isRecording = false
            
            // Wait for recording job to finish
            recordingJob?.join()
            recordingJob = null

            // Stop and close the line
            targetDataLine?.stop()
            targetDataLine?.close()
            targetDataLine = null

            val format = audioFormat ?: run {
                return@withContext Result.failure(Exception("No audio format available"))
            }

            val audioData = audioBytes.toByteArray()
            
            if (audioData.isEmpty()) {
                return@withContext Result.failure(Exception("No audio data recorded"))
            }

            // Save to WAV file
            saveToWavFile(audioData, format, outputFile)
            
            println("Audio saved to: ${outputFile.absolutePath} (${audioData.size} bytes)")
            Result.success(outputFile)
        } catch (e: Exception) {
            println("Error stopping audio recording: ${e.message}")
            targetDataLine?.stop()
            targetDataLine?.close()
            targetDataLine = null
            isRecording = false
            recordingJob?.cancel()
            Result.failure(e)
        }
    }

    /**
     * Stop recording without saving (cleanup)
     */
    fun stopRecording() {
        isRecording = false
        targetDataLine?.stop()
        targetDataLine?.close()
        targetDataLine = null
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Save audio data to WAV file
     */
    private fun saveToWavFile(audioData: ByteArray, audioFormat: AudioFormat, outputFile: File) {
        val audioInputStream = AudioInputStream(
            ByteArrayInputStream(audioData),
            audioFormat,
            (audioData.size / audioFormat.frameSize).toLong()
        )

        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile)
        audioInputStream.close()
    }

    /**
     * Record audio for a specific duration
     */
    suspend fun recordForDuration(durationSeconds: Float, outputFile: File): Result<File> {
        return try {
            startRecording().getOrThrow()
            
            // Record for the specified duration
            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(1024)
            val audioBytes = ByteArrayOutputStream()
            
            val audioFormat = targetDataLine?.format ?: throw Exception("No audio format")
            
            while (System.currentTimeMillis() - startTime < (durationSeconds * 1000).toLong() && isRecording) {
                val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    audioBytes.write(buffer, 0, bytesRead)
                }
                delay(10) // Small delay to prevent busy waiting
            }
            
            targetDataLine?.stop()
            targetDataLine?.close()
            targetDataLine = null
            isRecording = false

            val audioData = audioBytes.toByteArray()
            
            if (audioData.isEmpty()) {
                Result.failure(Exception("No audio data recorded"))
            } else {
                saveToWavFile(audioData, audioFormat, outputFile)
                println("Audio recorded for ${durationSeconds}s, saved to: ${outputFile.absolutePath}")
                Result.success(outputFile)
            }
        } catch (e: Exception) {
            stopRecording()
            Result.failure(e)
        }
    }
}

// Helper class for ByteArrayInputStream
private class ByteArrayInputStream(private val bytes: ByteArray) : java.io.InputStream() {
    private var pos = 0
    
    override fun read(): Int {
        return if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else -1
    }
    
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= bytes.size) return -1
        val available = minOf(len, bytes.size - pos)
        System.arraycopy(bytes, pos, b, off, available)
        pos += available
        return available
    }
    
    override fun available(): Int = bytes.size - pos
}

