package org.example.project.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.sound.sampled.*

/**
 * Audio player for playing audio from URLs or local files directly in the app
 * Uses Java AudioSystem for WAV files (backend converts MP3 to WAV)
 * Falls back to client-side conversion if needed
 */
class AudioPlayer {
    private var currentClip: Clip? = null
    private var isPlaying = false
    private var playbackFinishedCallback: (() -> Unit)? = null
    
    /**
     * Set a callback to be called when playback finishes
     */
    fun setPlaybackFinishedCallback(callback: (() -> Unit)?) {
        playbackFinishedCallback = callback
    }

    /**
     * Play audio from a URL directly in the app
     * Downloads the file and plays it using Java AudioSystem (for WAV files)
     * The backend should convert MP3 to WAV format for compatibility
     */
    suspend fun playAudioFromUrl(audioUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("Playing audio from URL: $audioUrl")
            
            // Stop any currently playing audio
            stop()
            
            // Download the audio file to a temporary location
            val tempFile = File.createTempFile("audio_", ".tmp")
            tempFile.deleteOnExit()
            
            try {
                // Download the file
                URL(audioUrl).openStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                println("Downloaded audio file: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                
                // Try to determine the file format
                val fileExtension = audioUrl.substringAfterLast('.', "").lowercase()
                println("File extension: $fileExtension")
                
                // Check file format first before attempting to play
                val fileHeader = ByteArray(12)
                tempFile.inputStream().use { it.read(fileHeader) }
                val headerBytes = fileHeader[0].toInt() and 0xFF
                val secondByte = fileHeader[1].toInt() and 0xFF
                
                // Detect MP3 format
                val isMp3 = (headerBytes == 0xFF && (secondByte == 0xFB || secondByte == 0xF3 || secondByte == 0xFA || secondByte == 0xF2)) ||
                            String(fileHeader, Charsets.ISO_8859_1).startsWith("ID3")
                
                if (isMp3) {
                    println("File is MP3 format, converting to WAV...")
                    val convertedResult = tryConvertAndPlay(tempFile, "mp3")
                    if (convertedResult.isSuccess) {
                        return@withContext convertedResult
                    }
                }
                
                // Try to play with AudioSystem (works for WAV, which backend should provide)
                val playResult = tryPlayWithAudioSystem(tempFile)
                
                if (playResult.isSuccess) {
                    return@withContext playResult
                } else {
                    // If AudioSystem fails, try conversion as fallback
                    if (!isMp3) {
                        val convertedResult = tryConvertAndPlay(tempFile, fileExtension)
                        if (convertedResult.isSuccess) {
                            return@withContext convertedResult
                        }
                    }
                    
                    // Last resort: show error message
                    println("AudioSystem failed and conversion not possible")
                    return@withContext Result.failure(
                        Exception("Unable to play audio format. File appears to be MP3 but conversion failed. Please ensure ffmpeg is installed.")
                    )
                }
            } finally {
                // Clean up temp file after a delay (to allow playback to finish)
                // Note: For system player, file might be needed longer, so we use deleteOnExit
            }
        } catch (e: Exception) {
            println("Error playing audio: ${e.message}")
            e.printStackTrace()
            isPlaying = false
            currentClip = null
            Result.failure(e)
        }
    }
    
    /**
     * Try to play audio using Java AudioSystem (supports WAV, AIFF, etc.)
     */
    private fun tryPlayWithAudioSystem(audioFile: File): Result<Unit> {
        return try {
            // Check file size and existence
            if (!audioFile.exists()) {
                return Result.failure(Exception("Audio file does not exist: ${audioFile.absolutePath}"))
            }
            if (audioFile.length() == 0L) {
                return Result.failure(Exception("Audio file is empty: ${audioFile.absolutePath}"))
            }
            
            println("Attempting to read audio file: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
            
            // Try to detect the actual file format by reading the header
            val fileHeader = ByteArray(12)
            audioFile.inputStream().use { it.read(fileHeader) }
            val headerString = String(fileHeader, Charsets.ISO_8859_1)
            println("File header: ${fileHeader.joinToString(" ") { "%02x".format(it) }}")
            println("File header (ASCII): $headerString")
            
            // Check if it's actually a WAV file (should start with "RIFF")
            // MP3 files start with "ID3" (ID3v2) or 0xFF 0xFB/0xF3 (MPEG frame sync)
            val isMp3 = fileHeader[0] == 0xFF.toByte() && 
                       (fileHeader[1] == 0xFB.toByte() || fileHeader[1] == 0xF3.toByte() || fileHeader[1] == 0xFA.toByte() || fileHeader[1] == 0xF2.toByte())
            val isId3 = headerString.startsWith("ID3")
            
            if (isMp3 || isId3) {
                println("Detected MP3 file (not WAV), attempting client-side conversion...")
                // Close the file and try to convert MP3 to WAV
                return tryConvertAndPlay(audioFile, "mp3")
            }
            
            if (!headerString.startsWith("RIFF")) {
                println("Warning: File does not appear to be a valid WAV file (missing RIFF header)")
                // Try to convert it anyway, might still work
            }
            
            val audioInputStream = AudioSystem.getAudioInputStream(audioFile)
            
            // Get audio format
            val audioFormat = audioInputStream.format
            println("Audio format detected:")
            println("  Encoding: ${audioFormat.encoding}")
            println("  Sample rate: ${audioFormat.sampleRate}Hz")
            println("  Channels: ${audioFormat.channels}")
            println("  Sample size: ${audioFormat.sampleSizeInBits} bits")
            println("  Frame size: ${audioFormat.frameSize} bytes")
            println("  Frame rate: ${audioFormat.frameRate}Hz")
            println("  Big endian: ${audioFormat.isBigEndian}")
            
            // If the format is not PCM, try to convert it
            val targetFormat = if (audioFormat.encoding != AudioFormat.Encoding.PCM_SIGNED &&
                audioFormat.encoding != AudioFormat.Encoding.PCM_UNSIGNED) {
                println("Converting audio format to PCM...")
                // Convert to PCM format
                val pcmFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    audioFormat.sampleRate,
                    16,  // 16-bit
                    audioFormat.channels,
                    audioFormat.channels * 2,  // 2 bytes per sample
                    audioFormat.sampleRate,
                    false  // Little endian
                )
                AudioSystem.getAudioInputStream(pcmFormat, audioInputStream)
            } else {
                audioInputStream
            }
            
            // Create data line info
            val dataLineInfo = DataLine.Info(Clip::class.java, targetFormat.format)
            
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                return Result.failure(Exception("Audio format not supported by AudioSystem: ${targetFormat.format}"))
            }
            
            // Get and open clip
            val clip = AudioSystem.getLine(dataLineInfo) as Clip
            clip.open(targetFormat)
            
            currentClip = clip
            isPlaying = true
            
            // Play audio
            clip.start()
            println("Audio playback started")
            
            // Wait for playback to finish
            clip.addLineListener(object : LineListener {
                override fun update(event: LineEvent) {
                    if (event.type == LineEvent.Type.STOP) {
                        isPlaying = false
                        clip.close()
                        currentClip = null
                        println("Audio playback finished")
                        // Call the callback if set
                        playbackFinishedCallback?.invoke()
                    }
                }
            })
            
            Result.success(Unit)
        } catch (e: UnsupportedAudioFileException) {
            println("Unsupported audio format: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        } catch (e: Exception) {
            println("Error with AudioSystem: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Try to convert MP3 to WAV and play (fallback if backend didn't convert)
     * This uses ffmpeg if available on the system
     */
    private fun tryConvertAndPlay(audioFile: File, originalExtension: String): Result<Unit> {
        if (originalExtension != "mp3") {
            return Result.failure(Exception("Conversion only supported for MP3"))
        }
        
        return try {
            // Try to use ffmpeg to convert MP3 to WAV
            val wavFile = File(audioFile.parent, audioFile.nameWithoutExtension + ".wav")
            
            val processBuilder = ProcessBuilder(
                "ffmpeg",
                "-y",  // Overwrite output file
                "-i", audioFile.absolutePath,
                "-ac", "1",  // Mono
                "-ar", "44100",  // Sample rate
                wavFile.absolutePath
            )
            
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && wavFile.exists()) {
                println("Converted MP3 to WAV: ${wavFile.absolutePath}")
                // Try playing the converted WAV file
                val playResult = tryPlayWithAudioSystem(wavFile)
                // Clean up converted file after playback
                wavFile.deleteOnExit()
                return playResult
            } else {
                val errorOutput = process.errorStream.bufferedReader().readText()
                println("ffmpeg conversion failed: $errorOutput")
                return Result.failure(Exception("ffmpeg conversion failed"))
            }
        } catch (e: Exception) {
            println("Error converting audio: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Play audio from a local file
     */
    suspend fun playAudioFromFile(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("Playing audio from file: $filePath")
            
            // Stop any currently playing audio
            stop()
            
            val audioInputStream = AudioSystem.getAudioInputStream(
                java.io.File(filePath)
            )
            
            val audioFormat = audioInputStream.format
            val dataLineInfo = DataLine.Info(Clip::class.java, audioFormat)
            
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                return@withContext Result.failure(Exception("Audio format not supported"))
            }
            
            val clip = AudioSystem.getLine(dataLineInfo) as Clip
            clip.open(audioInputStream)
            
            currentClip = clip
            isPlaying = true
            
            clip.start()
            
            clip.addLineListener(object : LineListener {
                override fun update(event: LineEvent) {
                    if (event.type == LineEvent.Type.STOP) {
                        isPlaying = false
                        clip.close()
                        currentClip = null
                        // Call the callback if set
                        playbackFinishedCallback?.invoke()
                    }
                }
            })
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("Error playing audio file: ${e.message}")
            isPlaying = false
            currentClip = null
            Result.failure(e)
        }
    }

    /**
     * Stop currently playing audio
     */
    fun stop() {
        currentClip?.let { clip ->
            if (clip.isRunning) {
                clip.stop()
            }
            clip.close()
            currentClip = null
            isPlaying = false
        }
    }

    /**
     * Check if audio is currently playing
     */
    fun isPlaying(): Boolean = isPlaying
}

