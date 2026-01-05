package org.example.project.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelAndJoin
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.*

/**
 * Voice recording utility for desktop applications.
 * Captures audio from the microphone and saves to file.
 */
class VoiceRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var targetDataLine: TargetDataLine? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private var audioFormat: AudioFormat? = null
    private val audioData = mutableListOf<ByteArray>()
    private val audioDataLock = Any()
    private var onAudioChunkCallback: AtomicReference<((ByteArray) -> Unit)?> = AtomicReference(null)
    
    // Debounce tracking to prevent rapid start/stop
    private var lastStartTime = 0L
    private val minRecordingIntervalMs = 200L

    /**
     * Start recording audio from the default microphone.
     * Audio is captured but not saved until stopRecording is called.
     * @param onAudioChunk Optional callback for real-time audio chunks (for streaming)
     */
    suspend fun startRecording(onAudioChunk: ((ByteArray) -> Unit)? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Atomic check-and-set to prevent race conditions
                if (!isRecording.compareAndSet(false, true)) {
                    println("[VoiceRecorder] Already recording - ignoring duplicate start")
                    return@withContext Result.failure(Exception("Already recording"))
                }
                
                // Debounce check
                val now = System.currentTimeMillis()
                if (now - lastStartTime < minRecordingIntervalMs) {
                    isRecording.set(false)
                    println("[VoiceRecorder] Start called too quickly - debouncing")
                    return@withContext Result.failure(Exception("Recording started too quickly"))
                }
                lastStartTime = now

                // Deepgram default: 24kHz, 16-bit, mono for best quality
                val format =
                    AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        24000f, // Sample rate (24kHz - Deepgram default)
                        16, // Bits per sample
                        1, // Mono
                        2, // Frame size
                        24000f, // Frame rate
                        false, // Little endian
                    )

                // Get the default microphone
                val info = DataLine.Info(TargetDataLine::class.java, format)

                if (!AudioSystem.isLineSupported(info)) {
                    isRecording.set(false)
                    return@withContext Result.failure(Exception("Audio line not supported"))
                }

                targetDataLine = AudioSystem.getLine(info) as TargetDataLine
                targetDataLine?.open(format)
                targetDataLine?.start()

                audioFormat = format
                synchronized(audioDataLock) {
                    audioData.clear()
                }
                onAudioChunkCallback.set(onAudioChunk)

                // Start recording coroutine for proper lifecycle management
                recordingJob = scope.launch {
                    val buffer = ByteArray(2400) // 50ms chunks at 24kHz for smooth audio
                    val dataLine = targetDataLine
                    
                    while (isActive && isRecording.get() && dataLine?.isOpen == true) {
                        try {
                            val bytesRead = dataLine.read(buffer, 0, buffer.size)
                            if (bytesRead > 0 && isRecording.get()) {
                                val chunk = buffer.copyOf(bytesRead)
                                synchronized(audioDataLock) {
                                    audioData.add(chunk)
                                }
                                // Stream chunk in real-time if callback provided
                                onAudioChunkCallback.get()?.invoke(chunk)
                            }
                        } catch (e: Exception) {
                            if (isRecording.get()) {
                                println("[VoiceRecorder] Error reading audio: ${e.message}")
                            }
                            break
                        }
                    }
                    println("[VoiceRecorder] Recording loop ended")
                }

                val mode = if (onAudioChunk != null) "streaming" else "buffered"
                println("[VoiceRecorder] Started recording ($mode mode) - format: ${format.sampleRate}Hz ${format.sampleSizeInBits}bit ${format.channels}ch")
                Result.success(Unit)
            } catch (e: Exception) {
                isRecording.set(false)
                println("[VoiceRecorder] Error starting recording: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Stop recording and save the audio to a file.
     *
     * @param outputFile The file to save the recording to (WAV format)
     * @return Result containing the file path on success
     */
    suspend fun stopRecording(outputFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!isRecording.compareAndSet(true, false)) {
                    return@withContext Result.failure(Exception("Not currently recording"))
                }
                
                // Clear callback immediately to prevent further chunk sends
                onAudioChunkCallback.set(null)

                // Wait for recording job to finish gracefully
                try {
                    recordingJob?.cancelAndJoin()
                } catch (e: Exception) {
                    println("[VoiceRecorder] Error canceling recording job: ${e.message}")
                }
                recordingJob = null

                // Stop and close the line
                try {
                    targetDataLine?.stop()
                    targetDataLine?.close()
                } catch (e: Exception) {
                    println("[VoiceRecorder] Error closing audio line: ${e.message}")
                }

                // Calculate total audio data size
                val (totalSize, chunkCount, combinedData) = synchronized(audioDataLock) {
                    val size = audioData.sumOf { it.size }
                    val count = audioData.size
                    val combined = if (size > 0) {
                        ByteArray(size).also { arr ->
                            var offset = 0
                            for (chunk in audioData) {
                                chunk.copyInto(arr, offset)
                                offset += chunk.size
                            }
                        }
                    } else null
                    audioData.clear()
                    Triple(size, count, combined)
                }
                
                println("[VoiceRecorder] Captured $totalSize bytes of audio data in $chunkCount chunks")

                if (totalSize == 0 || combinedData == null) {
                    targetDataLine = null
                    return@withContext Result.failure(Exception("No audio data captured"))
                }

                // Create audio input stream from captured data
                val audioInputStream = AudioInputStream(
                    java.io.ByteArrayInputStream(combinedData),
                    audioFormat!!,
                    (totalSize / audioFormat!!.frameSize).toLong()
                )

                // Write to WAV file
                AudioSystem.write(
                    audioInputStream,
                    AudioFileFormat.Type.WAVE,
                    outputFile,
                )

                println("[VoiceRecorder] Saved recording to: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

                targetDataLine = null

                Result.success(outputFile.absolutePath)
            } catch (e: Exception) {
                println("[VoiceRecorder] Error stopping recording: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }

    /**
     * Cancel recording without saving.
     */
    fun cancelRecording() {
        if (isRecording.compareAndSet(true, false)) {
            println("[VoiceRecorder] Canceling recording")
            onAudioChunkCallback.set(null)
            recordingJob?.cancel()
            recordingJob = null
            try {
                targetDataLine?.stop()
                targetDataLine?.close()
            } catch (e: Exception) {
                println("[VoiceRecorder] Error closing audio line: ${e.message}")
            }
            targetDataLine = null
            synchronized(audioDataLock) {
                audioData.clear()
            }
            println("[VoiceRecorder] Recording canceled")
        }
    }

    /**
     * Stop recording without saving to file.
     * Used for streaming mode where chunks are already sent.
     */
    suspend fun stopRecordingNoSave(): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                if (!isRecording.compareAndSet(true, false)) {
                    println("[VoiceRecorder] stopRecordingNoSave called but not recording")
                    return@withContext Result.failure(Exception("Not currently recording"))
                }
                
                // Clear callback immediately to prevent further chunk sends
                onAudioChunkCallback.set(null)
                println("[VoiceRecorder] Stopping recording (no save mode)")

                // Wait for recording job to finish gracefully
                try {
                    recordingJob?.cancelAndJoin()
                } catch (e: Exception) {
                    println("[VoiceRecorder] Error canceling recording job: ${e.message}")
                }
                recordingJob = null

                // Stop and close the line
                try {
                    targetDataLine?.stop()
                    targetDataLine?.close()
                } catch (e: Exception) {
                    println("[VoiceRecorder] Error closing audio line: ${e.message}")
                }

                // Calculate total audio data size
                val (totalSize, chunkCount) = synchronized(audioDataLock) {
                    val size = audioData.sumOf { it.size }
                    val count = audioData.size
                    audioData.clear()
                    Pair(size, count)
                }
                
                println("[VoiceRecorder] Stopped recording - streamed $totalSize bytes in $chunkCount chunks")

                targetDataLine = null

                Result.success(totalSize)
            } catch (e: Exception) {
                println("[VoiceRecorder] Error stopping recording: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }

    /**
     * Check if currently recording.
     */
    fun isCurrentlyRecording(): Boolean = isRecording.get()
    
    /**
     * Clean up resources.
     */
    fun dispose() {
        cancelRecording()
    }

    companion object {
        /**
         * Check if a microphone is available on the system.
         */
        fun isMicrophoneAvailable(): Boolean {
            return try {
                val format = AudioFormat(24000f, 16, 1, true, false) // 24kHz Deepgram default
                val info = DataLine.Info(TargetDataLine::class.java, format)
                AudioSystem.isLineSupported(info)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Get a list of available audio input devices.
         */
        fun getAvailableInputDevices(): List<String> {
            return try {
                val mixerInfos = AudioSystem.getMixerInfo()
                mixerInfos.mapNotNull { mixerInfo ->
                    val mixer = AudioSystem.getMixer(mixerInfo)
                    val lineInfos = mixer.targetLineInfo
                    if (lineInfos.isNotEmpty()) {
                        mixerInfo.name
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

/**
 * Voice recorder state for UI.
 */
enum class RecordingState {
    IDLE,
    RECORDING,
    PROCESSING,
    COMPLETED,
    ERROR,
}
