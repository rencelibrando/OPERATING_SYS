package org.example.project.core.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.example.project.data.repository.ProgressTrackerRepository
import org.example.project.data.repository.ProgressTrackerRepositoryImpl
import org.example.project.domain.model.LanguageProgress
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.VoiceAnalysisScores
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for aggregating language progress analytics with caching.
 *
 * PERFORMANCE OPTIMIZATIONS:
 * 1. In-memory cache with configurable TTL (5 minutes default)
 * 2. Parallel metric fetching using coroutines async/await
 * 3. Graceful degradation - returns partial data on individual metric failures
 * 4. Cache invalidation on user actions (lesson completion, new session)
 * 5. Thread-safe concurrent cache for multi-user scenarios
 *
 * SINGLETON: Ensures cache is shared across all ViewModels for consistency
 */
object ProgressAnalyticsService {
    private val repository: ProgressTrackerRepository = ProgressTrackerRepositoryImpl()
    private val cache = ConcurrentHashMap<CacheKey, CachedProgress>()
    private val cacheTTL = 5 * 60 * 1000L // 5 minutes in milliseconds

    /**
     * Fetches comprehensive language progress with caching.
     *
     * OPTIMIZATION: Parallel fetching of all 5 metrics using async
     * - Lessons progress
     * - Conversation sessions
     * - Vocabulary words
     * - Voice analysis scores
     * - Total conversation time
     */
    suspend fun getLanguageProgress(
        userId: String,
        language: LessonLanguage,
        forceRefresh: Boolean = false,
    ): Result<LanguageProgress> =
        runCatching {
            withContext(Dispatchers.IO) {
                val cacheKey = CacheKey(userId, language)
                val currentTime = System.currentTimeMillis()

                // Check cache first (unless force refresh)
                if (!forceRefresh) {
                    val cached = cache[cacheKey]
                    if (cached != null && (currentTime - cached.timestamp) < cacheTTL) {
                        println("[ProgressAnalytics] ✅ Cache hit for ${language.displayName} (user: $userId)")
                        return@withContext cached.progress
                    }
                }

                println("[ProgressAnalytics] 🔄 Fetching fresh data for ${language.displayName}...")

                // Parallel fetch all metrics using async
                val lessonsDeferred =
                    async {
                        repository.getLessonsProgress(userId, language)
                            .getOrElse {
                                println(
                                    "[ProgressAnalytics] ⚠️ Lessons progress failed, using default",
                                )
                                org.example.project.data.repository.LessonsProgress(0, 0)
                            }
                    }

                val sessionsDeferred =
                    async {
                        repository.getConversationSessions(userId, language)
                            .getOrElse {
                                println(
                                    "[ProgressAnalytics] ⚠️ Sessions count failed, using 0",
                                )
                                0
                            }
                    }

                val vocabularyDeferred =
                    async {
                        repository.getVocabularyCount(userId, language)
                            .getOrElse {
                                println(
                                    "[ProgressAnalytics] ⚠️ Vocabulary count failed, using 0",
                                )
                                0
                            }
                    }

                val scoresDeferred =
                    async {
                        repository.getVoiceAnalysisScores(userId, language)
                            .getOrElse {
                                println(
                                    "[ProgressAnalytics] ⚠️ Voice scores failed, using empty",
                                )
                                VoiceAnalysisScores()
                            }
                    }

                val timeDeferred =
                    async {
                        repository.getTotalConversationTime(userId, language)
                            .getOrElse {
                                println(
                                    "[ProgressAnalytics] ⚠️ Conversation time failed, using 0",
                                )
                                0.0
                            }
                    }

                // Await all results
                val lessons = lessonsDeferred.await()
                val sessions = sessionsDeferred.await()
                val vocabulary = vocabularyDeferred.await()
                val scores = scoresDeferred.await()
                val time = timeDeferred.await()

                val progress =
                    LanguageProgress(
                        language = language,
                        lessonsCompleted = lessons.completed,
                        totalLessons = lessons.total,
                        conversationSessions = sessions,
                        vocabularyWords = vocabulary,
                        voiceAnalysis = scores,
                        totalTimeSeconds = time,
                    )

                // Cache the result
                cache[cacheKey] = CachedProgress(progress, currentTime)
                println("[ProgressAnalytics]  Cached progress for ${language.displayName}")
                println(
                    "[ProgressAnalytics]  Stats: ${lessons.completed}/${lessons.total} lessons, " +
                        "$sessions sessions, $vocabulary words, ${String.format("%.1f", time)}s",
                )

                progress
            }
        }

    /**
     * Batch fetch progress for multiple languages in parallel.
     *
     * OPTIMIZATION: Even faster when loading multiple languages at once
     */
    suspend fun getMultiLanguageProgress(
        userId: String,
        languages: List<LessonLanguage>,
        forceRefresh: Boolean = false,
    ): Map<LessonLanguage, Result<LanguageProgress>> =
        withContext(Dispatchers.IO) {
            languages.associateWith { language ->
                async {
                    getLanguageProgress(userId, language, forceRefresh)
                }
            }.mapValues { it.value.await() }
        }

    /**
     * Invalidates cache for a specific user and language.
     * Call this after:
     * - Lesson completion
     * - New conversation session
     * - Vocabulary word added
     * - Voice feedback recorded
     */
    fun invalidateCache(
        userId: String,
        language: LessonLanguage? = null,
    ) {
        if (language != null) {
            val removed = cache.remove(CacheKey(userId, language))
            println("[ProgressAnalytics]  Invalidated cache for ${language.displayName} (user: $userId): ${removed != null}")
        } else {
            // Invalidate all languages for this user
            val keysToRemove = cache.keys.filter { it.userId == userId }
            keysToRemove.forEach { cache.remove(it) }
            println("[ProgressAnalytics]  Invalidated all language caches for user: $userId")
        }
    }

    /**
     * Clears entire cache.
     * Call this on logout or major data changes.
     */
    fun clearAllCache() {
        val size = cache.size
        cache.clear()
        println("[ProgressAnalytics]  Cleared entire cache ($size entries)")
    }

    /**
     * Gets current cache statistics for monitoring.
     */
    fun getCacheStats(): CacheStats {
        val currentTime = System.currentTimeMillis()
        val entries =
            cache.entries.map { (key, cached) ->
                CacheEntry(
                    userId = key.userId,
                    language = key.language.displayName,
                    ageMs = currentTime - cached.timestamp,
                    isExpired = (currentTime - cached.timestamp) > cacheTTL,
                )
            }
        return CacheStats(
            totalEntries = cache.size,
            activeEntries = entries.count { !it.isExpired },
            expiredEntries = entries.count { it.isExpired },
            entries = entries,
        )
    }

    private data class CacheKey(
        val userId: String,
        val language: LessonLanguage,
    )

    private data class CachedProgress(
        val progress: LanguageProgress,
        val timestamp: Long,
    )
}

data class CacheStats(
    val totalEntries: Int,
    val activeEntries: Int,
    val expiredEntries: Int,
    val entries: List<CacheEntry>,
)

data class CacheEntry(
    val userId: String,
    val language: String,
    val ageMs: Long,
    val isExpired: Boolean,
)
