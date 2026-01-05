package org.example.project.presentation.viewmodel.speaking

/**
 * Centralized configuration for SpeakingViewModel.
 * All constants are grouped here to avoid scattered magic numbers.
 */
object SpeakingConfig {
    // Audio buffer limits
    const val MAX_AUDIO_CHUNKS = 500
    const val MAX_AUDIO_BYTES = 25_000_000L // 25MB
    
    // Audio format
    const val SAMPLE_RATE = 24000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTE_RATE = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
    const val BLOCK_ALIGN = CHANNELS * BITS_PER_SAMPLE / 8
    
    // Timeouts and debouncing
    const val CONVERSATION_TIMEOUT_MS = 300_000L // 5 minutes
    const val CONVERSATION_DEBOUNCE_MS = 500L
    const val RECORDING_UPDATE_INTERVAL_MS = 100L
    
    // Recording limits
    const val RECORDING_MAX_DURATION = 60f // seconds
    const val RECORDING_MIN_DURATION = 0.5f // seconds
    const val RECORDING_MIN_FILE_SIZE = 1000L // bytes
    
    // Transcription
    const val MIN_TRANSCRIPTION_CONFIDENCE = 0.1f
    
    // Logging
    const val AUDIO_CHUNK_LOG_INTERVAL = 500
    
    // Retry configuration
    const val MAX_RETRIES = 3
    const val INITIAL_RETRY_DELAY_MS = 1000L
    const val MAX_RETRY_DELAY_MS = 10000L
    
    // Debug flag - should be tied to BuildConfig in production
    val DEBUG = System.getProperty("debug")?.toBoolean() ?: true
}
