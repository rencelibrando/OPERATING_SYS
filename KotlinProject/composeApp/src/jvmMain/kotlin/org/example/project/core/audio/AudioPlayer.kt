package org.example.project.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.SupabaseConfig
import org.example.project.core.api.SupabaseApiHelper
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.sound.sampled.*
import io.github.jan.supabase.storage.storage

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
     * For Supabase URLs, uses authenticated download
     */
    suspend fun playAudioFromUrl(audioUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("Playing audio from URL: $audioUrl")
            
            // Validate URL
            if (audioUrl.isBlank()) {
                return@withContext Result.failure(Exception("Audio URL is blank"))
            }
            
            // Stop any currently playing audio
            stop()
            
            // Download the audio file to a temporary location
            val tempFile = File.createTempFile("audio_", ".tmp")
            tempFile.deleteOnExit()
            
            try {
                // Check if this is a Supabase storage URL and handle authentication
                val downloadSuccess = if (audioUrl.contains("supabase.co/storage/v1")) {
                    downloadFromSupabase(audioUrl, tempFile)
                } else {
                    downloadFromDirectUrl(audioUrl, tempFile)
                }
                
                if (!downloadSuccess) {
                    return@withContext Result.failure(Exception("Failed to download audio file"))
                }
                
                println("Downloaded audio file: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                
                if (tempFile.length() == 0L) {
                    return@withContext Result.failure(Exception("Downloaded audio file is empty"))
                }
                
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
                    println("File is MP3 format, will use bundled MP3 decoder...")
                }
                
                // Try to play with AudioSystem (supports WAV and MP3 via mp3spi)
                val playResult = tryPlayWithAudioSystem(tempFile)
                
                if (playResult.isSuccess) {
                    return@withContext playResult
                } else {
                    println("AudioSystem failed to play file")
                    return@withContext Result.failure(
                        Exception("Unable to play audio format. AudioSystem failed: ${playResult.exceptionOrNull()?.message}")
                    )
                }
            } finally {
                // Clean up temp file after a delay (to allow playback to finish)
                // Note: For system player, file might be needed longer, so we use deleteOnExit
            }
        } catch (e: java.net.SocketTimeoutException) {
            println("Timeout downloading audio: ${e.message}")
            Result.failure(Exception("Timeout downloading audio file. Please check your internet connection."))
        } catch (e: java.net.UnknownHostException) {
            println("Unknown host: ${e.message}")
            Result.failure(Exception("Unable to connect to audio server. Please check the URL."))
        } catch (e: java.io.FileNotFoundException) {
            println("Audio file not found: ${e.message}")
            Result.failure(Exception("Audio file not found on server."))
        } catch (e: Exception) {
            println("Error playing audio: ${e.message}")
            e.printStackTrace()
            isPlaying = false
            currentClip = null
            Result.failure(Exception("Failed to play audio: ${e.message}"))
        }
    }
    
    /**
     * Download file from Supabase storage with authentication
     */
    private suspend fun downloadFromSupabase(audioUrl: String, tempFile: File): Boolean {
        return try {
            // Extract bucket and path from URL
            // URL format: https://[project].supabase.co/storage/v1/object/public/[bucket]/[path]
            val urlParts = audioUrl.split("/")
            val bucketIndex = urlParts.indexOf("public") + 1
            val pathIndex = bucketIndex + 1
            
            if (bucketIndex >= urlParts.size || pathIndex >= urlParts.size) {
                println("Invalid Supabase storage URL format")
                return false
            }
            
            val bucket = urlParts[bucketIndex]
            val path = urlParts.subList(pathIndex, urlParts.size).joinToString("/")
            
            println("Downloading from Supabase - Bucket: $bucket, Path: $path")
            
            // Ensure we have a valid session
            if (!SupabaseApiHelper.ensureValidSession()) {
                println("No valid Supabase session for authenticated download")
                return false
            }
            
            // Use Supabase storage client to download
            val storage = SupabaseConfig.client.storage.from(bucket)
            val bytes = try {
                // Try authenticated download first
                storage.downloadAuthenticated(path)
            } catch (e: Exception) {
                println("Authenticated download failed, trying public download: ${e.message}")
                // Fall back to public download
                storage.downloadPublic(path)
            }
            
            // Write to temp file
            tempFile.writeBytes(bytes)
            println("Successfully downloaded ${bytes.size} bytes from Supabase")
            true
            
        } catch (e: Exception) {
            println("Error downloading from Supabase: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Download file from direct URL (non-Supabase)
     */
    private suspend fun downloadFromDirectUrl(audioUrl: String, tempFile: File): Boolean {
        return try {
            println("Downloading audio file...")
            val connection = URL(audioUrl).openConnection()
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 30000 // 30 seconds
            connection.getInputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val bytesCopied = input.copyTo(output)
                    println("Downloaded $bytesCopied bytes")
                }
            }
            true
        } catch (e: Exception) {
            println("Error downloading from direct URL: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Try to play audio using Java AudioSystem (supports WAV, AIFF, etc.)
     * Enhanced with better format detection and error recovery
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
            
            // Read file header for format detection
            val fileHeader = ByteArray(32) // Read more bytes for better detection
            audioFile.inputStream().use { it.read(fileHeader) }
            val headerString = String(fileHeader, Charsets.ISO_8859_1)
            println("File header: ${fileHeader.joinToString(" ") { "%02x".format(it) }}")
            println("File header (ASCII): $headerString")
            
            // Enhanced format detection
            val isWav = headerString.startsWith("RIFF") && 
                      headerString.length > 12 && 
                      headerString.substring(8, 12) == "WAVE"
            val isMp3 = (fileHeader[0] == 0xFF.toByte() && 
                       (fileHeader[1] == 0xFB.toByte() || fileHeader[1] == 0xF3.toByte() || 
                        fileHeader[1] == 0xFA.toByte() || fileHeader[1] == 0xF2.toByte())) ||
                      headerString.startsWith("ID3")
            val isOgg = headerString.startsWith("OggS")
            val isFlac = headerString.startsWith("fLaC")
            val isM4a = headerString.startsWith("ftyp")
            
            // Detect corrupted or proprietary formats
            val isCorruptedWav = when {
                // Check for common corruption patterns
                fileHeader[0] == 0xA4.toByte() && fileHeader[1] == 0x03.toByte() -> {
                    println("Detected corrupted WAV format - attempting repair...")
                    true
                }
                // Add more corruption patterns as needed
                else -> false
            }
            
            when {
                isWav -> println("Detected standard WAV file")
                isMp3 -> println("Detected MP3 file, using mp3spi decoder...")
                isOgg -> println("Detected OGG Vorbis file")
                isFlac -> println("Detected FLAC file")
                isM4a -> println("Detected M4A/AAC file")
                isCorruptedWav -> println("Attempting to repair corrupted WAV format...")
                else -> println("Warning: Unknown audio format, attempting playback anyway...")
            }
            
            // Try to repair corrupted WAV files
            val actualFile = if (isCorruptedWav) {
                repairCorruptedWavFile(audioFile)
            } else {
                audioFile
            }
            
            val audioInputStream = try {
                AudioSystem.getAudioInputStream(actualFile)
            } catch (e: UnsupportedAudioFileException) {
                if (isCorruptedWav && actualFile != audioFile) {
                    // If repair failed, try one more approach
                    tryAlternativeAudioAccess(actualFile)
                } else {
                    throw e
                }
            }
            
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
            
            // Create data line info with fallback formats
            val dataLineInfo = DataLine.Info(Clip::class.java, targetFormat.format)
            val clip: Clip
            
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                println("Direct format not supported, trying alternative formats...")
                // Try common PCM formats as fallback
                val fallbackFormats = listOf(
                    AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false),
                    AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050f, 16, 2, 4, 22050f, false),
                    AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000f, 16, 1, 2, 16000f, false),
                    AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 22050f, 8, 1, 1, 22050f, false)
                )
                
                var fallbackStream: AudioInputStream? = null
                var fallbackClip: Clip? = null
                
                for (fallbackFormat in fallbackFormats) {
                    try {
                        fallbackStream = AudioSystem.getAudioInputStream(fallbackFormat, audioInputStream)
                        val fallbackInfo = DataLine.Info(Clip::class.java, fallbackFormat)
                        if (AudioSystem.isLineSupported(fallbackInfo)) {
                            println("Using fallback format: ${fallbackFormat.sampleRate}Hz, ${fallbackFormat.sampleSizeInBits}bit, ${fallbackFormat.channels}ch")
                            fallbackClip = AudioSystem.getLine(fallbackInfo) as Clip
                            fallbackClip.open(fallbackStream)
                            break
                        }
                    } catch (e: Exception) {
                        println("Fallback format failed: $fallbackFormat")
                        fallbackStream?.close()
                    }
                }
                
                if (fallbackClip == null) {
                    return Result.failure(Exception("No supported audio format found for: ${targetFormat.format}"))
                }
                
                currentClip = fallbackClip
                clip = fallbackClip
            } else {
                clip = AudioSystem.getLine(dataLineInfo) as Clip
                clip.open(targetFormat)
                currentClip = clip
            }
            
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
     * Attempt to repair corrupted WAV files by reconstructing the WAV header
     */
    private fun repairCorruptedWavFile(originalFile: File): File {
        return try {
            println("Attempting to repair corrupted WAV file: ${originalFile.name}")
            
            // Read the original file data
            val originalData = originalFile.readBytes()
            
            // Try to detect if this is raw PCM data by checking for reasonable audio patterns
            // This is a heuristic approach - in a real implementation, you'd want more sophisticated detection
            val isLikelyRawPCM = originalData.size > 1000 && 
                                 originalData.take(100).any { it.toInt() in 0..255 }
            
            if (isLikelyRawPCM) {
                // Create a new WAV file with proper header
                val repairedFile = File.createTempFile("repaired_audio_", ".wav")
                repairedFile.deleteOnExit()
                
                // Assume common PCM parameters (16-bit, mono, 16kHz)
                val sampleRate = 16000
                val channels = 1
                val bitsPerSample = 16
                val bytesPerSample = bitsPerSample / 8
                val blockAlign = channels * bytesPerSample
                val byteRate = sampleRate * blockAlign
                
                // Calculate data size (excluding header)
                val dataSize = originalData.size
                val fileSize = 36 + dataSize
                
                // Create WAV header
                val header = ByteArray(44)
                
                // RIFF header
                header[0] = 'R'.code.toByte()
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                // File size - 8
                header[4] = (fileSize and 0xFF).toByte()
                header[5] = ((fileSize shr 8) and 0xFF).toByte()
                header[6] = ((fileSize shr 16) and 0xFF).toByte()
                header[7] = ((fileSize shr 24) and 0xFF).toByte()
                
                // WAVE format
                header[8] = 'W'.code.toByte()
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()
                
                // fmt chunk
                header[12] = 'f'.code.toByte()
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                // fmt chunk size (16)
                header[16] = 16
                header[17] = 0
                header[18] = 0
                header[19] = 0
                // Audio format (PCM = 1)
                header[20] = 1
                header[21] = 0
                // Number of channels
                header[22] = (channels and 0xFF).toByte()
                header[23] = ((channels shr 8) and 0xFF).toByte()
                // Sample rate
                header[24] = (sampleRate and 0xFF).toByte()
                header[25] = ((sampleRate shr 8) and 0xFF).toByte()
                header[26] = ((sampleRate shr 16) and 0xFF).toByte()
                header[27] = ((sampleRate shr 24) and 0xFF).toByte()
                // Byte rate
                header[28] = (byteRate and 0xFF).toByte()
                header[29] = ((byteRate shr 8) and 0xFF).toByte()
                header[30] = ((byteRate shr 16) and 0xFF).toByte()
                header[31] = ((byteRate shr 24) and 0xFF).toByte()
                // Block align
                header[32] = (blockAlign and 0xFF).toByte()
                header[33] = ((blockAlign shr 8) and 0xFF).toByte()
                // Bits per sample
                header[34] = (bitsPerSample and 0xFF).toByte()
                header[35] = ((bitsPerSample shr 8) and 0xFF).toByte()
                
                // data chunk
                header[36] = 'd'.code.toByte()
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                // Data size
                header[40] = (dataSize and 0xFF).toByte()
                header[41] = ((dataSize shr 8) and 0xFF).toByte()
                header[42] = ((dataSize shr 16) and 0xFF).toByte()
                header[43] = ((dataSize shr 24) and 0xFF).toByte()
                
                // Write repaired file
                repairedFile.writeBytes(header + originalData)
                println("Successfully repaired WAV file: ${repairedFile.absolutePath}")
                repairedFile
            } else {
                println("Could not determine audio format for repair, using original file")
                originalFile
            }
        } catch (e: Exception) {
            println("Failed to repair WAV file: ${e.message}")
            originalFile
        }
    }
    
    /**
     * Try alternative audio access methods for problematic files
     */
    private fun tryAlternativeAudioAccess(audioFile: File): AudioInputStream {
        println("Trying alternative audio access methods...")
        
        // Try different approaches to read the audio
        val approaches = listOf(
            { AudioSystem.getAudioInputStream(audioFile) },
            { audioFile.inputStream().use { AudioSystem.getAudioInputStream(it) } },
            { 
                // Try with a buffered input stream
                val bufferedStream = audioFile.inputStream().buffered()
                AudioSystem.getAudioInputStream(bufferedStream)
            }
        )
        
        for ((index, approach) in approaches.withIndex()) {
            try {
                println("Trying approach ${index + 1}...")
                return approach()
            } catch (e: Exception) {
                println("Approach ${index + 1} failed: ${e.message}")
                if (index == approaches.size - 1) {
                    throw e // Re-throw the last exception
                }
            }
        }
        
        throw Exception("All audio access approaches failed")
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

    /**
     * Play audio from a File with callback when finished
     */
    suspend fun playFile(file: File, onFinished: () -> Unit) {
        setPlaybackFinishedCallback(onFinished)
        playAudioFromFile(file.absolutePath)
    }

    /**
     * Dispose resources
     */
    fun dispose() {
        stop()
        playbackFinishedCallback = null
    }
}

