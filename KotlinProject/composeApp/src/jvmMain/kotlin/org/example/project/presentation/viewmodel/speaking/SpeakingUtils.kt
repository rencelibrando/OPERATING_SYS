package org.example.project.presentation.viewmodel.speaking

import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.sqrt

/**
 * Lazy logging utilities for SpeakingViewModel.
 * Uses inline functions to avoid string concatenation when logging is disabled.
 */
object SpeakingLogger {
    private const val LOG_TAG = "SpeakingVM"

    fun debug(message: () -> String) {
        if (SpeakingConfig.DEBUG) {
            println("[$LOG_TAG] ${message()}")
        }
    }

    fun info(
        tag: String,
        message: () -> String,
    ) {
        println("[$LOG_TAG][$tag] ${message()}")
    }

    fun error(
        tag: String,
        message: () -> String,
    ) {
        println("[$LOG_TAG][$tag] ERROR: ${message()}")
    }

    fun warn(
        tag: String,
        message: () -> String,
    ) {
        if (SpeakingConfig.DEBUG) {
            println("[$LOG_TAG][$tag] WARN: ${message()}")
        }
    }
}

/**
 * Retry utility with exponential backoff.
 */
suspend fun <T> withRetry(
    maxRetries: Int = SpeakingConfig.MAX_RETRIES,
    initialDelay: Long = SpeakingConfig.INITIAL_RETRY_DELAY_MS,
    maxDelay: Long = SpeakingConfig.MAX_RETRY_DELAY_MS,
    operation: suspend () -> Result<T>,
): Result<T> {
    var currentDelay = initialDelay
    var lastError: Exception? = null

    repeat(maxRetries) { attempt ->
        val result = operation()
        if (result.isSuccess) return result

        lastError = result.exceptionOrNull() as? Exception
        if (attempt < maxRetries - 1) {
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
        }
    }

    return Result.failure(lastError ?: Exception("Operation failed after $maxRetries attempts"))
}

/**
 * Temp file manager for automatic cleanup.
 */
class TempFileManager {
    private val tempFiles = mutableListOf<File>()
    private val lock = Any()

    fun createTempFile(
        prefix: String,
        suffix: String,
    ): File {
        return File.createTempFile(prefix, suffix).also { file ->
            synchronized(lock) {
                tempFiles.add(file)
            }
        }
    }

    fun cleanup() {
        synchronized(lock) {
            tempFiles.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    SpeakingLogger.warn("TempFile") { "Failed to delete ${file.name}: ${e.message}" }
                }
            }
            tempFiles.clear()
        }
    }

    fun remove(file: File) {
        synchronized(lock) {
            tempFiles.remove(file)
        }
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            SpeakingLogger.warn("TempFile") { "Failed to delete ${file.name}: ${e.message}" }
        }
    }
}

/**
 * Optimized audio level calculation using RMS.
 * Processes every 4th sample for performance.
 */
fun calculateAudioLevel(audioData: ByteArray): Float {
    if (audioData.isEmpty()) return 0f

    var sum = 0.0
    var count = 0

    // Process every 4th sample for performance
    for (i in audioData.indices step 4) {
        val sample = audioData[i].toInt() and 0xFF
        sum += sample * sample
        count++
    }

    val rms = if (count > 0) sqrt(sum / count) else 0.0
    return (rms / 128.0).toFloat().coerceIn(0f, 1f)
}

/**
 * Helper to convert int to little-endian byte array for WAV files.
 */
fun intToByteArray(value: Int): ByteArray {
    return byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}

/**
 * Helper to convert short to little-endian byte array for WAV files.
 */
fun shortToByteArray(value: Int): ByteArray {
    return byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )
}

/**
 * Debouncer for preventing rapid API calls.
 */
class Debouncer(private val debounceMs: Long) {
    private var lastCallTime = 0L

    /**
     * Returns true if the call should proceed, false if it should be debounced.
     */
    fun shouldProceed(): Boolean {
        val currentTime = System.currentTimeMillis()
        return if (currentTime - lastCallTime >= debounceMs) {
            lastCallTime = currentTime
            true
        } else {
            false
        }
    }

    fun reset() {
        lastCallTime = 0L
    }
}
