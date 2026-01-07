package org.example.project.presentation.viewmodel.speaking

import java.util.ArrayDeque

/**
 * Thread-safe bounded audio buffer with LRU eviction to prevent OOM.
 * Automatically evicts oldest chunks when limits are exceeded.
 */
class BoundedAudioBuffer(
    private val maxChunks: Int = SpeakingConfig.MAX_AUDIO_CHUNKS,
    private val maxBytes: Long = SpeakingConfig.MAX_AUDIO_BYTES,
) {
    private val chunks = ArrayDeque<ByteArray>()
    private var totalBytes = 0L
    private val lock = Any()

    /**
     * Add a new audio chunk, evicting oldest chunks if needed.
     */
    fun add(chunk: ByteArray) {
        synchronized(lock) {
            // Evict oldest chunks if limits exceeded
            while (chunks.size >= maxChunks || totalBytes + chunk.size > maxBytes) {
                if (chunks.isEmpty()) break
                val removed = chunks.removeFirst()
                totalBytes -= removed.size
            }
            chunks.addLast(chunk)
            totalBytes += chunk.size
        }
    }

    /**
     * Get all chunks as a list (creates a copy for thread safety).
     */
    fun getAll(): List<ByteArray> = synchronized(lock) { chunks.toList() }

    /**
     * Clear all chunks.
     */
    fun clear() =
        synchronized(lock) {
            chunks.clear()
            totalBytes = 0L
        }

    /**
     * Get current chunk count.
     */
    fun size(): Int = synchronized(lock) { chunks.size }

    /**
     * Get total bytes stored.
     */
    fun totalBytes(): Long = synchronized(lock) { totalBytes }

    /**
     * Check if buffer is empty.
     */
    fun isEmpty(): Boolean = synchronized(lock) { chunks.isEmpty() }

    /**
     * Check if buffer is not empty.
     */
    fun isNotEmpty(): Boolean = synchronized(lock) { chunks.isNotEmpty() }

    /**
     * Combine all chunks into a single ByteArray.
     * Returns null if buffer is empty.
     */
    fun combine(): ByteArray? =
        synchronized(lock) {
            if (chunks.isEmpty()) return@synchronized null

            val combined = ByteArray(totalBytes.toInt())
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(combined, offset)
                offset += chunk.size
            }
            combined
        }

    /**
     * Get statistics for logging.
     */
    fun stats(): String =
        synchronized(lock) {
            "chunks=${chunks.size}, bytes=$totalBytes (${totalBytes / 1024}KB)"
        }
}
