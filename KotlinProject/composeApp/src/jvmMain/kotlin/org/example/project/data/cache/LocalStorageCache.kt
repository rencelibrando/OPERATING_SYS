package org.example.project.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.File
import java.io.IOException

/**
 * Persistent local storage cache using JSON files.
 * Provides caching that survives app restarts.
 */
class LocalStorageCache(
    private val cacheDir: File
) {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            polymorphic(CacheableData::class) {
                subclass(CachedLessonTopicList::class)
                subclass(CachedLessonTopic::class)
                subclass(CachedLessonSummaryList::class)
                subclass(CachedLessonSummary::class)
            }
        }
    }

    init {
        // Ensure cache directory exists
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    /**
     * Store data in local cache with timestamp
     */
    suspend fun store(
        key: String,
        data: CacheableData,
        ttlMs: Long = DEFAULT_TTL_MS
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cacheEntry = CacheEntry(
                data = data,
                timestamp = System.currentTimeMillis(),
                ttlMs = ttlMs
            )
            
            val file = File(cacheDir, "$key.json")
            file.writeText(json.encodeToString(cacheEntry))
            
            println("[LocalStorageCache] ✅ Stored cache entry: $key")
            Result.success(Unit)
        } catch (e: IOException) {
            println("[LocalStorageCache] ❌ Failed to store cache entry $key: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Retrieve data from local cache if not expired
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun retrieve(
        key: String
    ): Result<CacheableData?> = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "$key.json")
            if (!file.exists()) {
                return@withContext Result.success(null)
            }

            val content = file.readText()
            val cacheEntry = json.decodeFromString<CacheEntry<CacheableData>>(content)
            
            // Check if cache is expired
            if (cacheEntry.isExpired()) {
                file.delete()
                println("[LocalStorageCache] ⏰ Cache entry expired: $key")
                return@withContext Result.success(null)
            }

            println("[LocalStorageCache] ✅ Cache hit: $key (${cacheEntry.ageMs()}ms old)")
            Result.success(cacheEntry.data)
        } catch (e: Exception) {
            println("[LocalStorageCache] ❌ Failed to retrieve cache entry $key: ${e.message}")
            // Try to delete corrupted file
            try {
                File(cacheDir, "$key.json").delete()
            } catch (deleteException: Exception) {
                println("[LocalStorageCache] Failed to delete corrupted cache file: ${deleteException.message}")
            }
            Result.success(null) // Return null instead of failing
        }
    }

    /**
     * Delete specific cache entry
     */
    suspend fun delete(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "$key.json")
            if (file.exists()) {
                file.delete()
                println("[LocalStorageCache] 🗑️ Deleted cache entry: $key")
            }
            Result.success(Unit)
        } catch (e: IOException) {
            println("[LocalStorageCache] ❌ Failed to delete cache entry $key: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Clear all cache entries
     */
    suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    file.delete()
                }
            }
            println("[LocalStorageCache] 🗑️ Cleared all cache entries")
            Result.success(Unit)
        } catch (e: IOException) {
            println("[LocalStorageCache] ❌ Failed to clear cache: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Clear expired cache entries
     */
    suspend fun clearExpired(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var deletedCount = 0
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    try {
                        val content = file.readText()
                        val cacheEntry = json.decodeFromString<CacheEntry<CacheableData>>(content)
                        
                        if (cacheEntry.isExpired()) {
                            file.delete()
                            deletedCount++
                        }
                    } catch (e: Exception) {
                        // Delete corrupted files
                        file.delete()
                        deletedCount++
                    }
                }
            }
            
            if (deletedCount > 0) {
                println("[LocalStorageCache] 🗑️ Cleared $deletedCount expired cache entries")
            }
            
            Result.success(deletedCount)
        } catch (e: IOException) {
            println("[LocalStorageCache] ❌ Failed to clear expired cache: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles()?.filter { 
                it.isFile && it.name.endsWith(".json") 
            } ?: emptyList()
            
            val totalSizeBytes = files.sumOf { it.length() }
            var expiredCount = 0
            var validCount = 0
            
            files.forEach { file ->
                try {
                    val content = file.readText()
                    val cacheEntry = json.decodeFromString<CacheEntry<CacheableData>>(content)
                    if (cacheEntry.isExpired()) {
                        expiredCount++
                    } else {
                        validCount++
                    }
                } catch (e: Exception) {
                    // Corrupted file, count as expired
                    expiredCount++
                }
            }
            
            CacheStats(
                totalEntries = files.size,
                validEntries = validCount,
                expiredEntries = expiredCount,
                totalSizeBytes = totalSizeBytes
            )
        } catch (e: Exception) {
            println("[LocalStorageCache] ❌ Failed to get cache stats: ${e.message}")
            CacheStats(0, 0, 0, 0)
        }
    }

    @Serializable
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMs: Long
    ) {
        fun isExpired(): Boolean = ageMs() > ttlMs
        fun ageMs(): Long = System.currentTimeMillis() - timestamp
    }

    companion object {
        private const val DEFAULT_TTL_MS = 30 * 60 * 1000L // 30 minutes
    }
}

@Serializable
data class CacheStats(
    val totalEntries: Int,
    val validEntries: Int,
    val expiredEntries: Int,
    val totalSizeBytes: Long
) {
    val totalSizeMB: Double get() = totalSizeBytes / (1024.0 * 1024.0)
}
