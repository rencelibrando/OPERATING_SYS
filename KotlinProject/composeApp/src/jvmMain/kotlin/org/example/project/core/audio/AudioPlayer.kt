package org.example.project.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent

/**
 * Audio player for playing MP3/WAV files from URLs.
 * Supports async playback and playback state management.
 * Uses MP3 SPI library for MP3 format support.
 */
class AudioPlayer {
    
    private var currentClip: Clip? = null
    private var isPlaying = false
    
    /**
     * Play audio from a URL (async, non-blocking).
     * Stops any currently playing audio before starting new playback.
     */
    suspend fun play(audioUrl: String, onComplete: () -> Unit = {}) = withContext(Dispatchers.IO) {
        try {
            // Stop any currently playing audio
            stop()
            
            println("[AudioPlayer] Loading audio from: $audioUrl")
            
            // Try to play the audio, with fallback to WAV if MP3 fails
            val success = tryPlayAudio(audioUrl, onComplete) || tryPlayWithWavFallback(audioUrl, onComplete)
            
            if (!success) {
                println("[AudioPlayer] All playback attempts failed")
                isPlaying = false
                onComplete()
            }
            
        } catch (e: Exception) {
            println("[AudioPlayer] Error playing audio: ${e.message}")
            e.printStackTrace()
            isPlaying = false
            onComplete()
        }
    }
    
    private fun tryPlayAudio(audioUrl: String, onComplete: () -> Unit): Boolean {
        return try {
            // Open connection to audio URL
            val url = URL(audioUrl)
            val inputStream = BufferedInputStream(url.openStream())
            
            // Get audio input stream (MP3 SPI will handle MP3 format)
            var audioInputStream = AudioSystem.getAudioInputStream(inputStream)
            
            // Convert to PCM if needed (MP3 files need conversion)
            val baseFormat = audioInputStream.format
            if (baseFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
                println("[AudioPlayer] Converting audio format from ${baseFormat.encoding} to PCM_SIGNED")
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
            
            // Get line info and create clip
            val info = DataLine.Info(Clip::class.java, audioInputStream.format)
            val clip = AudioSystem.getLine(info) as Clip
            clip.open(audioInputStream)
            
            // Add listener for when playback completes
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    isPlaying = false
                    clip.close()
                    onComplete()
                    println("[AudioPlayer] Playback completed")
                }
            }
            
            currentClip = clip
            isPlaying = true
            
            // Start playback
            clip.start()
            println("[AudioPlayer] Started playback")
            true
            
        } catch (e: Exception) {
            println("[AudioPlayer] Primary playback failed: ${e.message}")
            false
        }
    }
    
    private fun tryPlayWithWavFallback(audioUrl: String, onComplete: () -> Unit): Boolean {
        return try {
            // Try to replace .mp3 with .wav and attempt playback
            val wavUrl = audioUrl.replace(".mp3", ".wav", ignoreCase = true)
            
            if (wavUrl == audioUrl) {
                // URL doesn't contain .mp3, can't create WAV fallback
                return false
            }
            
            println("[AudioPlayer] Attempting WAV fallback: $wavUrl")
            
            val url = URL(wavUrl)
            val inputStream = BufferedInputStream(url.openStream())
            
            // WAV files should be natively supported
            val audioInputStream = AudioSystem.getAudioInputStream(inputStream)
            
            // Get line info and create clip
            val info = DataLine.Info(Clip::class.java, audioInputStream.format)
            val clip = AudioSystem.getLine(info) as Clip
            clip.open(audioInputStream)
            
            // Add listener for when playback completes
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    isPlaying = false
                    clip.close()
                    onComplete()
                    println("[AudioPlayer] Playback completed (WAV fallback)")
                }
            }
            
            currentClip = clip
            isPlaying = true
            
            // Start playback
            clip.start()
            println("[AudioPlayer] Started playback (WAV fallback successful)")
            true
            
        } catch (e: Exception) {
            println("[AudioPlayer] WAV fallback failed: ${e.message}")
            false
        }
    }
    
    /**
     * Stop currently playing audio.
     */
    fun stop() {
        currentClip?.let { clip ->
            if (clip.isRunning) {
                clip.stop()
            }
            clip.close()
            println("[AudioPlayer] Stopped playback")
        }
        currentClip = null
        isPlaying = false
    }
    
    /**
     * Check if audio is currently playing.
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * Dispose of resources.
     */
    fun dispose() {
        stop()
    }
}
