package org.example.project.core.audio

import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.*
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

/**
 * Utility for trimming silence from audio files
 */
class AudioTrimmer {
    
    /**
     * Trim silence from the beginning and end of an audio file
     * 
     * @param inputFile The audio file to trim
     * @param outputFile The output file to save the trimmed audio
     * @param energyThreshold Energy threshold for detecting speech (default: 0.02)
     * @param minSilenceDuration Minimum duration of silence to trim (default: 0.3 seconds)
     * @param endBufferSeconds Additional audio to keep after speech ends (default: 0.2 seconds)
     * @return Result indicating success or failure
     */
    fun trimSilence(
        inputFile: File,
        outputFile: File,
        energyThreshold: Double = 0.02,
        minSilenceDuration: Double = 0.3,
        endBufferSeconds: Double = 0.2
    ): Result<File> {
        return try {
            if (!inputFile.exists()) {
                return Result.failure(Exception("Input file does not exist: ${inputFile.absolutePath}"))
            }
            
            println("Trimming silence from: ${inputFile.absolutePath}")
            
            // Read audio file
            val inputAudioStream = AudioSystem.getAudioInputStream(inputFile)
            val audioFormat = inputAudioStream.format
            
            // Convert to PCM if needed
            val pcmFormat = if (audioFormat.encoding != AudioFormat.Encoding.PCM_SIGNED &&
                audioFormat.encoding != AudioFormat.Encoding.PCM_UNSIGNED) {
                // Convert to PCM
                val targetFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    audioFormat.sampleRate,
                    16,
                    audioFormat.channels,
                    audioFormat.channels * 2,
                    audioFormat.sampleRate,
                    false
                )
                AudioSystem.getAudioInputStream(targetFormat, inputAudioStream)
            } else {
                inputAudioStream
            }
            
            val sampleRate = pcmFormat.format.sampleRate.toInt()
            val channels = pcmFormat.format.channels
            val sampleSizeInBytes = pcmFormat.format.sampleSizeInBits / 8
            
            // Read all audio data
            val audioBytes = pcmFormat.readAllBytes()
            pcmFormat.close()
            
            // Convert bytes to samples (assuming 16-bit PCM)
            val samples = if (sampleSizeInBytes == 2) {
                val sampleList = mutableListOf<Float>()
                val bytesPerSample = sampleSizeInBytes * channels
                for (i in 0 until audioBytes.size step bytesPerSample) {
                    if (i + 1 < audioBytes.size) {
                        // Read sample for first channel
                        val sample = (audioBytes[i].toInt() and 0xFF) or ((audioBytes[i + 1].toInt() and 0xFF) shl 8)
                        // Convert to signed 16-bit
                        val signedSample = if (sample > 32767) sample - 65536 else sample
                        sampleList.add(signedSample.toFloat() / 32768f) // Normalize to [-1, 1]
                    }
                }
                sampleList.toFloatArray()
            } else {
                // For other sample sizes, use a simpler approach
                audioBytes.map { (it.toInt() and 0xFF).toFloat() / 128f - 1f }.toFloatArray()
            }
            
            // If stereo, convert to mono by averaging channels
            val monoSignal = if (channels > 1) {
                val monoList = mutableListOf<Float>()
                val bytesPerSample = sampleSizeInBytes * channels
                for (i in 0 until audioBytes.size step bytesPerSample) {
                    if (i + (sampleSizeInBytes * channels) - 1 < audioBytes.size) {
                        var sum = 0f
                        for (ch in 0 until channels) {
                            val byteIndex = i + (ch * sampleSizeInBytes)
                            if (byteIndex + 1 < audioBytes.size) {
                                val sample = (audioBytes[byteIndex].toInt() and 0xFF) or 
                                           ((audioBytes[byteIndex + 1].toInt() and 0xFF) shl 8)
                                val signedSample = if (sample > 32767) sample - 65536 else sample
                                sum += signedSample.toFloat() / 32768f
                            }
                        }
                        monoList.add(sum / channels)
                    }
                }
                monoList.toFloatArray()
            } else {
                samples
            }
            
            // Detect speech boundaries
            val (startSample, endSample) = detectSpeechBoundaries(
                monoSignal,
                sampleRate,
                energyThreshold,
                minSilenceDuration,
                endBufferSeconds
            )
            
            println("Detected speech boundaries: start=$startSample, end=$endSample (out of ${monoSignal.size} samples)")
            
            // Calculate trimmed duration
            val originalDuration = monoSignal.size.toDouble() / sampleRate
            val trimmedDuration = (endSample - startSample).toDouble() / sampleRate
            println("Original duration: ${String.format("%.2f", originalDuration)}s")
            println("Trimmed duration: ${String.format("%.2f", trimmedDuration)}s")
            
            // If no significant trimming needed, just copy the file
            if (startSample == 0 && endSample >= monoSignal.size - 100) {
                println("No significant silence detected, keeping original file")
                inputFile.copyTo(outputFile, overwrite = true)
                return Result.success(outputFile)
            }
            
            // Extract trimmed samples
            val trimmedMono = monoSignal.sliceArray(startSample until endSample)
            
            // Convert back to multi-channel if needed
            val trimmedSamples = if (channels > 1) {
                trimmedMono.flatMap { sample ->
                    List(channels) { sample }
                }.toFloatArray()
            } else {
                trimmedMono
            }
            
            // Convert samples back to bytes
            val trimmedBytes = trimmedSamples.flatMap { sample ->
                val normalized = (sample * 32768f).toInt().coerceIn(-32768, 32767)
                val unsigned = if (normalized < 0) normalized + 65536 else normalized
                listOf((unsigned and 0xFF).toByte(), ((unsigned shr 8) and 0xFF).toByte())
            }.toByteArray()
            
            // Write trimmed audio to output file
            val outputFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate.toFloat(),
                16,
                channels,
                channels * 2,
                sampleRate.toFloat(),
                false
            )
            
            // Create AudioInputStream from byte array
            val byteArrayInputStream = ByteArrayInputStream(trimmedBytes)
            // Calculate frame length: bytes / (channels * sampleSizeInBytes)
            val frameLength = trimmedBytes.size / (channels * sampleSizeInBytes)
            val audioInputStream = AudioInputStream(byteArrayInputStream, outputFormat, frameLength.toLong())
            
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile)
            audioInputStream.close()
            byteArrayInputStream.close()
            
            println("Trimmed audio saved to: ${outputFile.absolutePath}")
            println("File size: ${outputFile.length()} bytes (original: ${inputFile.length()} bytes)")
            
            Result.success(outputFile)
        } catch (e: Exception) {
            println("Error trimming silence: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Detect speech boundaries in an audio signal
     * 
     * @param signal Audio signal as float array (normalized to [-1, 1])
     * @param sampleRate Sample rate in Hz
     * @param energyThreshold Energy threshold for detecting speech
     * @param minSilenceDuration Minimum duration of silence to trim
     * @param endBufferSeconds Additional audio samples to keep after speech ends
     * @return Pair of (startSample, endSample)
     */
    private fun detectSpeechBoundaries(
        signal: FloatArray,
        sampleRate: Int,
        energyThreshold: Double,
        minSilenceDuration: Double,
        endBufferSeconds: Double = 0.2
    ): Pair<Int, Int> {
        // Calculate energy in small windows
        val frameSize = (0.02 * sampleRate).toInt() // 20ms frames
        val hopSize = (0.01 * sampleRate).toInt()    // 10ms hop
        
        val energy = mutableListOf<Double>()
        for (i in 0 until signal.size - frameSize step hopSize) {
            val frame = signal.sliceArray(i until min(i + frameSize, signal.size))
            val frameEnergy = sqrt(frame.map { it * it }.average())
            energy.add(frameEnergy)
        }
        
        if (energy.isEmpty()) {
            return Pair(0, signal.size)
        }
        
        // Find frames above threshold
        val speechFrames = BooleanArray(energy.size) { energy[it] > energyThreshold }
        
        if (!speechFrames.any { it }) {
            // No speech detected, return full signal
            return Pair(0, signal.size)
        }
        
        // Find first speech frame
        val firstSpeech = speechFrames.indexOfFirst { it }
        if (firstSpeech == -1) {
            return Pair(0, signal.size)
        }
        
        // Find last speech frame
        val lastSpeech = speechFrames.indexOfLast { it }
        if (lastSpeech == -1) {
            return Pair(0, signal.size)
        }
        
        // Convert frame indices to sample indices
        val startSample = max(0, firstSpeech * hopSize - frameSize)
        val detectedEndSample = (lastSpeech + 1) * hopSize + frameSize
        
        // Add a small buffer after the end of speech (e.g., 0.2 seconds)
        val endBufferSamples = (endBufferSeconds * sampleRate).toInt()
        val endSample = min(signal.size, detectedEndSample + endBufferSamples)
        
        return Pair(startSample, endSample)
    }
}

