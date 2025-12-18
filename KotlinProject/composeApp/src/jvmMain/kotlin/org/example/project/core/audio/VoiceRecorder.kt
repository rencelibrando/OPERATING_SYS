package org.example.project.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.*

/**
 * Voice recording utility for desktop applications.
 * Captures audio from the microphone and saves to file.
 */
class VoiceRecorder {
    
    private var targetDataLine: TargetDataLine? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    
    /**
     * Start recording audio from the default microphone.
     * Audio is captured but not saved until stopRecording is called.
     */
    suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                return@withContext Result.failure(Exception("Already recording"))
            }
            
            // Set up audio format (44.1kHz, 16-bit, mono)
            val format = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100f, // Sample rate
                16,     // Bits per sample
                1,      // Mono
                2,      // Frame size
                44100f, // Frame rate
                false   // Big endian
            )
            
            // Get the default microphone
            val info = DataLine.Info(TargetDataLine::class.java, format)
            
            if (!AudioSystem.isLineSupported(info)) {
                return@withContext Result.failure(Exception("Audio line not supported"))
            }
            
            targetDataLine = AudioSystem.getLine(info) as TargetDataLine
            targetDataLine?.open(format)
            targetDataLine?.start()
            
            isRecording = true
            
            Result.success(Unit)
        } catch (e: Exception) {
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
    suspend fun stopRecording(outputFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording || targetDataLine == null) {
                return@withContext Result.failure(Exception("Not currently recording"))
            }
            
            isRecording = false
            
            // Stop and close the line
            targetDataLine?.stop()
            targetDataLine?.close()
            
            // Create the audio input stream
            val audioInputStream = AudioInputStream(
                targetDataLine
            )
            
            // Write to file
            AudioSystem.write(
                audioInputStream,
                AudioFileFormat.Type.WAVE,
                outputFile
            )
            
            targetDataLine = null
            
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            println("[VoiceRecorder] Error stopping recording: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Cancel recording without saving.
     */
    fun cancelRecording() {
        if (isRecording) {
            isRecording = false
            targetDataLine?.stop()
            targetDataLine?.close()
            targetDataLine = null
        }
    }
    
    /**
     * Check if currently recording.
     */
    fun isCurrentlyRecording(): Boolean = isRecording
    
    companion object {
        /**
         * Check if a microphone is available on the system.
         */
        fun isMicrophoneAvailable(): Boolean {
            return try {
                val format = AudioFormat(44100f, 16, 1, true, false)
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
    ERROR
}

/**
 * Audio player for playback of recorded audio files.
 * Separate from AudioPlayer (which handles URL-based TTS narration).
 */
class RecordedAudioPlayer {
    
    private var clip: Clip? = null
    private var isPlaying = false
    
    /**
     * Load an audio file for playback.
     */
    suspend fun loadAudio(audioFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val audioInputStream = AudioSystem.getAudioInputStream(audioFile)
            clip = AudioSystem.getClip()
            clip?.open(audioInputStream)
            Result.success(Unit)
        } catch (e: Exception) {
            println("[RecordedAudioPlayer] Error loading audio: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Play the loaded audio.
     */
    fun play() {
        clip?.let {
            if (isPlaying) {
                it.stop()
                it.framePosition = 0
            }
            it.start()
            isPlaying = true
        }
    }
    
    /**
     * Stop playback.
     */
    fun stop() {
        clip?.stop()
        isPlaying = false
    }
    
    /**
     * Release resources.
     */
    fun release() {
        stop()
        clip?.close()
        clip = null
    }
    
    /**
     * Check if currently playing.
     */
    fun isCurrentlyPlaying(): Boolean = isPlaying && clip?.isRunning == true
}

/**
 * Simple audio visualizer for recording feedback.
 */
class AudioVisualizer {
    
    private var lastVolume: Float = 0f
    
    /**
     * Calculate the current audio level (0.0 to 1.0).
     * This is a simplified version - in production, you'd use FFT or RMS.
     */
    fun getCurrentLevel(dataLine: TargetDataLine?): Float {
        if (dataLine == null || !dataLine.isOpen) return 0f
        
        try {
            val buffer = ByteArray(1024)
            val bytesRead = dataLine.read(buffer, 0, buffer.size)
            
            if (bytesRead <= 0) return 0f
            
            // Calculate simple RMS (root mean square)
            var sum = 0.0
            for (i in 0 until bytesRead step 2) {
                val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                sum += sample * sample
            }
            
            val rms = Math.sqrt(sum / (bytesRead / 2))
            lastVolume = (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
            
            return lastVolume
        } catch (e: Exception) {
            return lastVolume
        }
    }
}
