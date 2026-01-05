package org.example.project.data.repository

import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.api.SupabaseApiHelper
import org.example.project.core.config.SupabaseConfig
import org.example.project.data.cache.CacheableData
import org.example.project.data.cache.CachedLessonTopic
import org.example.project.data.cache.CachedLessonTopicList
import org.example.project.data.cache.LocalStorageCache
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.LessonTopic
import java.io.File

interface LessonTopicsRepository {
    suspend fun getTopicsByDifficulty(
        difficulty: LessonDifficulty,
        language: org.example.project.domain.model.LessonLanguage? = null,
    ): Result<List<LessonTopic>>

    suspend fun getTopic(topicId: String): Result<LessonTopic?>

    suspend fun getUserTopicProgress(
        userId: String,
        topicId: String,
    ): Result<LessonTopicProgress?>

    suspend fun updateUserTopicProgress(
        userId: String,
        topicId: String,
        isCompleted: Boolean,
        timeSpent: Int? = null,
    ): Result<Unit>

    // Admin operations
    suspend fun updateTopic(
        topic: LessonTopic,
        difficulty: LessonDifficulty,
        language: org.example.project.domain.model.LessonLanguage,
    ): Result<Unit>

    suspend fun deleteTopic(topicId: String): Result<Unit>

    suspend fun createTopic(
        topic: LessonTopic,
        difficulty: LessonDifficulty,
        language: org.example.project.domain.model.LessonLanguage,
        sortOrder: Int,
    ): Result<Unit>

    suspend fun getTopicsByLanguage(
        languageId: String,
        userId: String,
        forceRefresh: Boolean = false
    ): Result<List<LessonTopic>>
}

@Serializable
data class LessonTopicDTO(
    @SerialName("id")
    val id: String,
    @SerialName("difficulty_level")
    val difficultyLevel: String,
    @SerialName("language")
    val language: String? = null,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("lesson_number")
    val lessonNumber: Int? = null,
    @SerialName("duration_minutes")
    val durationMinutes: Int? = null,
    @SerialName("sort_order")
    val sortOrder: Int = 0,
    @SerialName("is_locked")
    val isLocked: Boolean = false,
    @SerialName("is_published")
    val isPublished: Boolean = true,
) {
    fun toDomain(
        isCompleted: Boolean = false,
        completedLessonsCount: Int = 0,
        totalLessonsCount: Int = 0,
        isLockedOverride: Boolean? = null
    ): LessonTopic {
        val lang = mapDatabaseLanguage(language)
        return LessonTopic(
            id = id,
            title = title,
            description = description ?: "",
            lessonNumber = lessonNumber,
            isCompleted = isCompleted,
            isLocked = isLockedOverride ?: isLocked,
            durationMinutes = durationMinutes,
            language = lang,
            completedLessonsCount = completedLessonsCount,
            totalLessonsCount = totalLessonsCount,
        )
    }
}

private fun mapDatabaseLanguage(rawLanguage: String?): LessonLanguage? {
    if (rawLanguage.isNullOrBlank()) {
        return null
    }

    val normalized = rawLanguage.trim()

    return LessonLanguage.entries.firstOrNull { lessonLanguage ->
        lessonLanguage.displayName.equals(normalized, ignoreCase = true) ||
            lessonLanguage.code.equals(normalized, ignoreCase = true)
    } ?: when (normalized.lowercase()) {
        "mandarin", "mandarin chinese", "zh-cn", "zh-hans", "zh-hant" -> LessonLanguage.CHINESE
        "ko-kr", "hangul" -> LessonLanguage.KOREAN
        "es-es", "es-mx" -> LessonLanguage.SPANISH
        "fr-fr" -> LessonLanguage.FRENCH
        "de-de" -> LessonLanguage.GERMAN
        else -> null
    }
}

@Serializable
data class LessonTopicProgressDTO(
    @SerialName("user_id")
    val userId: String,
    @SerialName("topic_id")
    val topicId: String,
    @SerialName("is_completed")
    val isCompleted: Boolean = false,
    @SerialName("time_spent")
    val timeSpent: Int = 0,
)

data class LessonTopicProgress(
    val userId: String,
    val topicId: String,
    val isCompleted: Boolean,
    val timeSpent: Int,
)

class LessonTopicsRepositoryImpl private constructor() : LessonTopicsRepository {
    private val supabase = SupabaseConfig.client
    private val baseUrl = "http://localhost:8000"
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    // Persistent local storage cache
    private val localStorageCache = LocalStorageCache(
        cacheDir = File(System.getProperty("user.home"), ".operating_sys_cache/lesson_topics")
    )
    
    // Multi-layer caching with TTL
    private val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutes
    private val SHORT_CACHE_DURATION_MS = 30 * 1000L // 30 seconds for errors
    private val PERSISTENT_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour for persistent cache
    
    // Cache for topics by (difficulty, language) key
    private val topicsCache = mutableMapOf<String, Pair<List<LessonTopic>, Long>>()
    
    // Cache for lesson counts by topic ID
    private val lessonCountsCache = mutableMapOf<String, Pair<LessonCounts, Long>>()
    
    // Cache for user progress by (userId, topicIds hash)
    private val userProgressCache = mutableMapOf<String, Pair<Map<String, LessonTopicProgress>, Long>>()
    
    // Cache for user lesson progress by (userId, topicIds hash)
    private val userLessonProgressCache = mutableMapOf<String, Pair<Map<String, TopicLessonProgress>, Long>>()
    
    companion object {
        @Volatile
        private var instance: LessonTopicsRepositoryImpl? = null
        
        fun getInstance(): LessonTopicsRepositoryImpl {
            return instance ?: synchronized(this) {
                instance ?: LessonTopicsRepositoryImpl().also { instance = it }
            }
        }
    }

    override suspend fun getTopicsByDifficulty(
        difficulty: LessonDifficulty,
        language: org.example.project.domain.model.LessonLanguage?,
    ): Result<List<LessonTopic>> =
        runCatching {
            if (!SupabaseApiHelper.isReady()) {
                throw Exception("Supabase not configured - will use local fallback")
            }

            val difficultyString = difficulty.displayName
            val languageString = language?.displayName
            val cacheKey = "${difficultyString}_${languageString ?: "all"}"
            val currentTime = System.currentTimeMillis()
            val userId = SupabaseApiHelper.getCurrentUserId()
            
            // Check persistent cache first
            val persistentCacheResult = localStorageCache.retrieve("topics_$cacheKey")
            
            @Suppress("UNCHECKED_CAST")
            val cachedTopicList = persistentCacheResult.getOrNull() as? CachedLessonTopicList
            if (cachedTopicList != null) {
                println("[LessonTopics] ✅ Persistent cache hit for $cacheKey (${cachedTopicList.topics.size} topics)")
                return@runCatching cachedTopicList.topics.map { it.toDomain() }
            }
            
            // Check in-memory cache second
            val cached = topicsCache[cacheKey]
            if (cached != null && (currentTime - cached.second) < CACHE_DURATION_MS) {
                println("[LessonTopics] ✅ Memory cache hit for $cacheKey (${cached.first.size} topics)")
                return@runCatching cached.first
            }
            
            println("[LessonTopics] 🔄 Cache miss for $cacheKey, fetching from API...")

            SupabaseApiHelper.executeWithRetry {
                withContext(Dispatchers.IO) {
                    if (!SupabaseApiHelper.ensureValidSession()) {
                        println("[LessonTopics] No valid session, fetching topics without progress tracking")
                    }

                    println("[LessonTopics] Fetching topics for difficulty: $difficultyString, language: ${languageString ?: "all"}")

                    val response =
                        supabase.postgrest["lesson_topics"].select {
                            filter {
                                eq("difficulty_level", difficultyString)
                                languageString?.let { eq("language", it) }
                                eq("is_published", true)
                            }
                        }

                    val topicDTOs =
                        response.decodeList<LessonTopicDTO>()
                            .sortedWith(compareBy({ it.sortOrder }, { it.lessonNumber ?: Int.MAX_VALUE }))

                    println("[LessonTopics] Fetched ${topicDTOs.size} topics from database")

                    val userId = SupabaseApiHelper.getCurrentUserId()

                    // Fetch lesson counts and progress for all topics
                    val topicIds = topicDTOs.map { it.id }
                    val lessonCountsMap = getLessonCountsByTopics(topicIds)

                    val topics =
                        if (userId != null) {
                            val progressMap = getUserProgressMap(userId, topicIds)
                            val lessonProgressMap = getUserLessonProgressByTopics(userId, topicIds)

                            // Apply cascading sequential locking logic
                            topicDTOs.mapIndexed { index, dto ->
                                val topicProgress = progressMap[dto.id]
                                val lessonCounts = lessonCountsMap[dto.id]
                                val totalLessons = lessonCounts?.total ?: 0
                                val completedLessons = lessonProgressMap[dto.id]?.completedCount ?: 0

                                // Topic locking logic: 
                                // - Topics with 0 lessons are always unlocked
                                // - Topics with lessons are locked if any previous topic with lessons is incomplete
                                val isLocked = if (totalLessons == 0) {
                                    // Topics with no lessons are always unlocked
                                    false
                                } else if (index == 0) {
                                    // First topic with lessons is never locked
                                    false
                                } else {
                                    // Check if ANY previous topic with lessons is incomplete
                                    topicDTOs.take(index).any { previousDto ->
                                        val previousLessonCounts = lessonCountsMap[previousDto.id]
                                        val previousTotalLessons = previousLessonCounts?.total ?: 0
                                        val previousCompletedLessons = lessonProgressMap[previousDto.id]?.completedCount ?: 0
                                        
                                        // Only lock if previous topic has lessons (>0) but not all are completed
                                        previousTotalLessons > 0 && previousCompletedLessons < previousTotalLessons
                                    }
                                }

                                dto.toDomain(
                                    isCompleted = topicProgress?.isCompleted ?: false,
                                    completedLessonsCount = completedLessons,
                                    totalLessonsCount = totalLessons,
                                    isLockedOverride = isLocked
                                )
                            }
                        } else {
                            // No user, show all topics unlocked with zero progress
                            topicDTOs.map { dto ->
                                val lessonCounts = lessonCountsMap[dto.id]
                                dto.toDomain(
                                    totalLessonsCount = lessonCounts?.total ?: 0
                                )
                            }
                        }

                    // Cache the result in memory
                    topicsCache[cacheKey] = Pair(topics, currentTime)
                    println("[LessonTopics] ✅ Memory cached ${topics.size} topics for $cacheKey")
                    
                    // Cache the result in persistent storage
                    val cachedTopics = topics.map { CachedLessonTopic.fromDomain(it) }
                    val topicListWrapper = CachedLessonTopicList(cachedTopics)
                    localStorageCache.store(
                        key = "topics_$cacheKey",
                        data = topicListWrapper,
                        ttlMs = PERSISTENT_CACHE_TTL_MS
                    )
                    println("[LessonTopics] ✅ Persistent cached ${topics.size} topics for $cacheKey")
                    
                    topics
                }
            }.getOrElse { e ->
                println("[LessonTopics] Failed to fetch topics: ${e.message}")
                throw e
            }
        }

    override suspend fun getTopic(topicId: String): Result<LessonTopic?> =
        runCatching {
            SupabaseApiHelper.executeWithRetry {
                withContext(Dispatchers.IO) {
                    if (!SupabaseApiHelper.isReady()) {
                        return@withContext null
                    }

                    val response =
                        supabase.postgrest["lesson_topics"].select {
                            filter {
                                eq("id", topicId)
                                eq("is_published", true)
                            }
                            limit(1)
                        }

                    val topicDTO = response.decodeSingleOrNull<LessonTopicDTO>()

                    if (topicDTO != null) {
                        val userId = SupabaseApiHelper.getCurrentUserId()
                        val isCompleted =
                            if (userId != null) {
                                getUserTopicProgress(userId, topicId).getOrNull()?.isCompleted
                                    ?: false
                            } else {
                                false
                            }

                        topicDTO.toDomain(isCompleted)
                    } else {
                        null
                    }
                }
            }.getOrElse { e ->
                println("[LessonTopics] Error fetching topic $topicId: ${e.message}")
                null
            }
        }

    override suspend fun getUserTopicProgress(
        userId: String,
        topicId: String,
    ): Result<LessonTopicProgress?> =
        runCatching {
            SupabaseApiHelper.executeWithRetry {
                withContext(Dispatchers.IO) {
                    val response =
                        supabase.postgrest["lesson_topic_progress"].select {
                            filter {
                                eq("user_id", userId)
                                eq("topic_id", topicId)
                            }
                            limit(1)
                        }

                    val progressDTO = response.decodeSingleOrNull<LessonTopicProgressDTO>()

                    progressDTO?.let {
                        LessonTopicProgress(
                            userId = it.userId,
                            topicId = it.topicId,
                            isCompleted = it.isCompleted,
                            timeSpent = it.timeSpent,
                        )
                    }
                }
            }.getOrElse { e ->
                println("[LessonTopics] Error fetching progress: ${e.message}")
                null
            }
        }

    override suspend fun updateUserTopicProgress(
        userId: String,
        topicId: String,
        isCompleted: Boolean,
        timeSpent: Int?,
    ): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                try {
                    val now = java.time.Instant.now().toString()
                    val payload =
                        buildJsonObject {
                            put("user_id", userId)
                            put("topic_id", topicId)
                            put("is_completed", isCompleted)
                            timeSpent?.let { put("time_spent", it) }
                            if (isCompleted) {
                                put("completed_at", now)
                            } else {
                                put("started_at", now)
                            }
                        }

                    supabase.postgrest["lesson_topic_progress"].upsert(payload)

                    println("[LessonTopics] Updated progress for topic $topicId")
                } catch (e: Exception) {
                    println("[LessonTopics] Error updating progress: ${e.message}")
                    throw e
                }
            }
        }

    override suspend fun updateTopic(
        topic: LessonTopic,
        difficulty: LessonDifficulty,
        language: org.example.project.domain.model.LessonLanguage,
    ): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                try {
                    val payload =
                        buildJsonObject {
                            put("id", topic.id)
                            put("difficulty_level", difficulty.displayName)
                            put("language", language.displayName)
                            put("title", topic.title)
                            put("description", topic.description)
                            topic.lessonNumber?.let { put("lesson_number", it) }
                            topic.durationMinutes?.let { put("duration_minutes", it) }
                            put("is_locked", topic.isLocked)
                            put("is_published", true)
                        }

                    supabase.postgrest["lesson_topics"].update(payload) {
                        filter {
                            eq("id", topic.id)
                        }
                    }

                    println("[LessonTopics] Updated topic: ${topic.id}")
                    
                    // Clear cache for this topic
                    clearTopicsCache(difficulty, language)
                    
                } catch (e: Exception) {
                    println("[LessonTopics] Error updating topic: ${e.message}")
                    throw e
                }
            }
        }

    override suspend fun deleteTopic(topicId: String): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                try {
                    // First, delete all related progress records
                    try {
                        supabase.postgrest["lesson_topic_progress"].delete {
                            filter {
                                eq("topic_id", topicId)
                            }
                        }
                        println("[LessonTopics] Deleted progress records for topic: $topicId")
                    } catch (e: Exception) {
                        println("[LessonTopics] Warning: Could not delete progress records: ${e.message}")
                        // Continue with topic deletion even if progress deletion fails
                    }

                    // Then delete the topic itself
                    supabase.postgrest["lesson_topics"].delete {
                        filter {
                            eq("id", topicId)
                        }
                    }

                    println("[LessonTopics] Deleted topic: $topicId")
                    
                    // Clear all cache since we don't know the difficulty/language of deleted topic
                    clearCache()
                    
                } catch (e: Exception) {
                    println("[LessonTopics] Error deleting topic: ${e.message}")
                    throw e
                }
            }
        }

    override suspend fun createTopic(
        topic: LessonTopic,
        difficulty: LessonDifficulty,
        language: org.example.project.domain.model.LessonLanguage,
        sortOrder: Int,
    ): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                try {
                    val payload =
                        buildJsonObject {
                            put("id", topic.id)
                            put("difficulty_level", difficulty.displayName)
                            put("language", language.displayName)
                            put("title", topic.title)
                            put("description", topic.description)
                            topic.lessonNumber?.let { put("lesson_number", it) }
                            topic.durationMinutes?.let { put("duration_minutes", it) }
                            put("sort_order", sortOrder)
                            put("is_locked", topic.isLocked)
                            put("is_published", true)
                        }

                    supabase.postgrest["lesson_topics"].insert(payload)

                    println("[LessonTopics] Created topic: ${topic.id}")
                    
                    // Clear cache for this difficulty/language combination
                    clearTopicsCache(difficulty, language)
                    
                } catch (e: Exception) {
                    println("[LessonTopics] Error creating topic: ${e.message}")
                    throw e
                }
            }
        }

    override suspend fun getTopicsByLanguage(
        languageId: String,
        userId: String,
        forceRefresh: Boolean,
    ): Result<List<LessonTopic>> =
        runCatching {
            if (!SupabaseApiHelper.isReady()) {
                throw Exception("Supabase not configured - will use local fallback")
            }

            val cacheKey = "language_${languageId}"
            val currentTime = System.currentTimeMillis()

            if (!forceRefresh) {
                // Check persistent cache first
                val persistentCacheResult = localStorageCache.retrieve("topics_$cacheKey")
                
                @Suppress("UNCHECKED_CAST")
                val cachedTopicList = persistentCacheResult.getOrNull() as? CachedLessonTopicList
                if (cachedTopicList != null) {
                    println("[LessonTopics] Persistent cache hit for language $languageId (${cachedTopicList.topics.size} topics)")
                    return@runCatching cachedTopicList.topics.map { it.toDomain() }
                }
                
                // Check in-memory cache second
                val cached = topicsCache[cacheKey]
                if (cached != null && (currentTime - cached.second) < CACHE_DURATION_MS) {
                    println("[LessonTopics]  Memory cache hit for language $languageId (${cached.first.size} topics)")
                    return@runCatching cached.first
                }
            } else {
                println("[LessonTopics] Force refresh requested - bypassing cache for language $languageId")
            }

            println("[LessonTopics] 🔄 Fetching topics for language $languageId from API...")

            SupabaseApiHelper.executeWithRetry {
                withContext(Dispatchers.IO) {
                    if (!SupabaseApiHelper.ensureValidSession()) {
                        println("[LessonTopics] No valid session, fetching topics without progress tracking")
                    }

                    println("[LessonTopics] Fetching topics for language: $languageId")

                    val response =
                        supabase.postgrest["lesson_topics"].select {
                            filter {
                                eq("language", languageId)
                                eq("is_published", true)
                            }
                        }

                    val topicDTOs =
                        response.decodeList<LessonTopicDTO>()
                            .sortedWith(compareBy({ it.sortOrder }, { it.lessonNumber ?: Int.MAX_VALUE }))

                    println("[LessonTopics] Fetched ${topicDTOs.size} topics from database")

                    // Fetch lesson counts and progress for all topics
                    val topicIds = topicDTOs.map { it.id }
                    val lessonCountsMap = getLessonCountsByTopics(topicIds)

                    val topics =
                        if (userId.isNotEmpty()) {
                            val progressMap = getUserProgressMap(userId, topicIds)
                            val lessonProgressMap = getUserLessonProgressByTopics(userId, topicIds)

                            // Apply cascading sequential locking logic
                            topicDTOs.mapIndexed { index, dto ->
                                val topicProgress = progressMap[dto.id]
                                val lessonCounts = lessonCountsMap[dto.id]
                                val totalLessons = lessonCounts?.total ?: 0
                                val completedLessons = lessonProgressMap[dto.id]?.completedCount ?: 0

                                // Topic locking logic: 
                                // - Topics with 0 lessons are always unlocked
                                // - Topics with lessons are locked if any previous topic with lessons is incomplete
                                val isLocked = if (totalLessons == 0) {
                                    // Topics with no lessons are always unlocked
                                    false
                                } else if (index == 0) {
                                    // First topic with lessons is never locked
                                    false
                                } else {
                                    // Check if ANY previous topic with lessons is incomplete
                                    topicDTOs.take(index).any { previousDto ->
                                        val previousLessonCounts = lessonCountsMap[previousDto.id]
                                        val previousTotalLessons = previousLessonCounts?.total ?: 0
                                        val previousCompletedLessons = lessonProgressMap[previousDto.id]?.completedCount ?: 0
                                        
                                        // Only lock if previous topic has lessons (>0) but not all are completed
                                        previousTotalLessons > 0 && previousCompletedLessons < previousTotalLessons
                                    }
                                }

                                dto.toDomain(
                                    isCompleted = topicProgress?.isCompleted ?: false,
                                    completedLessonsCount = completedLessons,
                                    totalLessonsCount = totalLessons,
                                    isLockedOverride = isLocked
                                )
                            }
                        } else {
                            // No user, show all topics unlocked with zero progress
                            topicDTOs.map { dto ->
                                val lessonCounts = lessonCountsMap[dto.id]
                                dto.toDomain(
                                    totalLessonsCount = lessonCounts?.total ?: 0
                                )
                            }
                        }

                    // Cache the result in memory only if not forced refresh
                    if (!forceRefresh) {
                        topicsCache[cacheKey] = Pair(topics, currentTime)
                        println("[LessonTopics] ✅ Memory cached ${topics.size} topics for language $languageId")
                        
                        // Cache the result in persistent storage
                        val cachedTopics = topics.map { CachedLessonTopic.fromDomain(it) }
                        val topicListWrapper = CachedLessonTopicList(cachedTopics)
                        localStorageCache.store(
                            key = "topics_$cacheKey",
                            data = topicListWrapper,
                            ttlMs = PERSISTENT_CACHE_TTL_MS
                        )
                        println("[LessonTopics] ✅ Persistent cached ${topics.size} topics for language $languageId")
                    }
                    
                    topics
                }
            }.getOrElse { e ->
                println("[LessonTopics] Failed to fetch topics for language $languageId: ${e.message}")
                throw e
            }
        }

    private suspend fun getUserProgressMap(
        userId: String,
        topicIds: List<String>,
    ): Map<String, LessonTopicProgress> {
        return try {
            if (topicIds.isEmpty()) return emptyMap()
            
            // Check cache
            val cacheKey = "${userId}_${topicIds.sorted().hashCode()}"
            val currentTime = System.currentTimeMillis()
            val cached = userProgressCache[cacheKey]
            if (cached != null && (currentTime - cached.second) < CACHE_DURATION_MS) {
                println("[LessonTopics] ✅ User progress cache hit for user $userId")
                return cached.first
            }
            
            println("[LessonTopics] 🔄 Fetching user progress for ${topicIds.size} topics...")

            val response =
                supabase.postgrest["lesson_topic_progress"].select {
                    filter {
                        eq("user_id", userId)
                        isIn("topic_id", topicIds)
                    }
                }

            val progressDTOs = response.decodeList<LessonTopicProgressDTO>()

            val progressMap = progressDTOs.associate { dto ->
                dto.topicId to
                    LessonTopicProgress(
                        userId = dto.userId,
                        topicId = dto.topicId,
                        isCompleted = dto.isCompleted,
                        timeSpent = dto.timeSpent,
                    )
            }
            
            // Cache the result
            userProgressCache[cacheKey] = Pair(progressMap, currentTime)
            println("[LessonTopics] ✅ Cached progress for ${progressMap.size} topics")
            
            progressMap
        } catch (e: Exception) {
            // Check if it's a "table not found" error - this is expected if migration hasn't been run
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("Could not find the table", ignoreCase = true) ||
                errorMessage.contains("does not exist", ignoreCase = true)
            ) {
                println(
                    "[LessonTopics] Progress table not found - progress tracking will be unavailable until migration 004_lesson_topics.sql is run",
                )
            } else {
                println("[LessonTopics] Error fetching progress map: ${e.message}")
            }
            emptyMap()
        }
    }

    private suspend fun getLessonCountsByTopics(topicIds: List<String>): Map<String, LessonCounts> {
        return try {
            if (topicIds.isEmpty()) return emptyMap()

            val currentTime = System.currentTimeMillis()
            val countsMap = mutableMapOf<String, LessonCounts>()
            val topicsToFetch = mutableListOf<String>()
            
            // Check cache first
            topicIds.forEach { topicId ->
                val cached = lessonCountsCache[topicId]
                if (cached != null && (currentTime - cached.second) < CACHE_DURATION_MS) {
                    // Cache hit
                    countsMap[topicId] = cached.first
                    println("[LessonTopics] Cache hit for topic $topicId: ${cached.first.total} lessons")
                } else {
                    // Cache miss or expired
                    topicsToFetch.add(topicId)
                }
            }
            
            // Fetch missing topics from API
            if (topicsToFetch.isNotEmpty()) {
                println("[LessonTopics] Fetching lesson counts for ${topicsToFetch.size} topics from backend")
            }
            
            topicsToFetch.forEach { topicId ->
                try {
                    val response: LessonListResponse = httpClient.get("$baseUrl/api/lessons/topic/$topicId") {
                        parameter("published_only", true)
                    }.body()
                    
                    val count = LessonCounts(total = response.total)
                    countsMap[topicId] = count
                    // Store in cache
                    lessonCountsCache[topicId] = Pair(count, currentTime)
                    println("[LessonTopics] Topic $topicId has ${response.total} lessons (cached)")
                } catch (e: Exception) {
                    println("[LessonTopics] Error fetching count for topic $topicId: ${e.message}")
                    val emptyCount = LessonCounts(total = 0)
                    countsMap[topicId] = emptyCount
                    // Cache error result for shorter duration
                    lessonCountsCache[topicId] = Pair(emptyCount, currentTime - CACHE_DURATION_MS + SHORT_CACHE_DURATION_MS)
                }
            }
            
            countsMap
        } catch (e: Exception) {
            println("[LessonTopics] Error fetching lesson counts: ${e.message}")
            emptyMap()
        }
    }

    private suspend fun getUserLessonProgressByTopics(
        userId: String,
        topicIds: List<String>
    ): Map<String, TopicLessonProgress> {
        return try {
            if (topicIds.isEmpty()) return emptyMap()
            
            // Check cache
            val cacheKey = "${userId}_${topicIds.sorted().hashCode()}"
            val currentTime = System.currentTimeMillis()
            val cached = userLessonProgressCache[cacheKey]
            if (cached != null && (currentTime - cached.second) < CACHE_DURATION_MS) {
                println("[LessonTopics] ✅ User lesson progress cache hit for user $userId")
                return cached.first
            }
            
            println("[LessonTopics] 🔄 Fetching user lesson progress...")

            // Build a map of lesson_id -> topic_id by fetching lessons via API
            val lessonToTopicMap = mutableMapOf<String, String>()
            
            topicIds.forEach { topicId ->
                try {
                    println("[LessonTopics] Fetching lessons for progress tracking, topic: $topicId")
                    val response: LessonListResponse = httpClient.get("$baseUrl/api/lessons/topic/$topicId") {
                        parameter("published_only", true)
                    }.body()
                    
                    println("[LessonTopics] Found ${response.lessons.size} lessons for topic $topicId")
                    response.lessons.forEach { lesson ->
                        lessonToTopicMap[lesson.id] = lesson.topicId
                    }
                } catch (e: Exception) {
                    println("[LessonTopics] Error fetching lessons for topic $topicId: ${e.message}")
                    e.printStackTrace()
                }
            }

            val lessonIds = lessonToTopicMap.keys.toList()
            if (lessonIds.isEmpty()) {
                return topicIds.associateWith { TopicLessonProgress(completedCount = 0) }
            }

            // Get user progress from Supabase
            val progressResponse =
                supabase.postgrest["user_lesson_progress"].select {
                    filter {
                        eq("user_id", userId)
                        isIn("lesson_id", lessonIds)
                        eq("is_completed", true)
                    }
                }

            @Serializable
            data class UserLessonProgressDTO(
                @SerialName("lesson_id") val lessonId: String,
                @SerialName("is_completed") val isCompleted: Boolean,
            )

            val completedProgress = progressResponse.decodeList<UserLessonProgressDTO>()

            // Count completed lessons per topic
            val completedByTopic = completedProgress
                .mapNotNull { progress -> lessonToTopicMap[progress.lessonId] }
                .groupingBy { it }
                .eachCount()

            val progressMap = topicIds.associateWith { topicId ->
                TopicLessonProgress(completedCount = completedByTopic[topicId] ?: 0)
            }
            
            // Cache the result
            userLessonProgressCache[cacheKey] = Pair(progressMap, currentTime)
            println("[LessonTopics] ✅ Cached lesson progress for ${progressMap.size} topics")
            
            progressMap
        } catch (e: Exception) {
            println("[LessonTopics] Error fetching user lesson progress: ${e.message}")
            topicIds.associateWith { TopicLessonProgress(completedCount = 0) }
        }
    }
    
    /**
     * Clear all caches - call this when user logs out or when manual refresh is needed
     */
    fun clearCache() {
        topicsCache.clear()
        lessonCountsCache.clear()
        userProgressCache.clear()
        userLessonProgressCache.clear()
        
        // Clear persistent cache
        runCatching {
            kotlinx.coroutines.runBlocking {
                localStorageCache.clear()
            }
        }.onFailure { e ->
            println("[LessonTopics] Failed to clear persistent cache: ${e.message}")
        }
        
        println("[LessonTopics] 🗑️ All caches cleared")
    }
    
    /**
     * Clear cache for specific user - call this when user completes a lesson
     */
    fun clearUserCache(userId: String) {
        // Clear user progress caches
        userProgressCache.keys.removeAll { it.startsWith("${userId}_") }
        userLessonProgressCache.keys.removeAll { it.startsWith("${userId}_") }
        
        // CRITICAL: Also clear topics cache since it contains computed locking status
        topicsCache.clear()
        lessonCountsCache.clear()
        
        // Clear persistent cache
        runCatching {
            kotlinx.coroutines.runBlocking {
                localStorageCache.clear()
            }
        }.onFailure { e ->
            println("[LessonTopics] Failed to clear persistent cache for user: ${e.message}")
        }
        
        println("[LessonTopics] 🗑️ User cache cleared for $userId (including topics cache)")
    }
    
    /**
     * Clear cache for specific difficulty/language - call this when content is updated
     */
    fun clearTopicsCache(difficulty: LessonDifficulty? = null, language: LessonLanguage? = null) {
        if (difficulty != null && language != null) {
            val cacheKey = "${difficulty.displayName}_${language.displayName}"
            topicsCache.remove(cacheKey)
            
            // Clear specific persistent cache
            runCatching {
                kotlinx.coroutines.runBlocking {
                    localStorageCache.delete("topics_$cacheKey")
                }
            }.onFailure { e ->
                println("[LessonTopics] Failed to clear persistent cache for $cacheKey: ${e.message}")
            }
            
            println("[LessonTopics] 🗑️ Topics cache cleared for $cacheKey")
        } else {
            topicsCache.clear()
            
            // Clear all topics persistent cache
            runCatching {
                kotlinx.coroutines.runBlocking {
                    localStorageCache.clear()
                }
            }.onFailure { e ->
                println("[LessonTopics] Failed to clear all persistent topics cache: ${e.message}")
            }
            
            println("[LessonTopics] 🗑️ All topics cache cleared")
        }
    }
}

data class LessonCounts(
    val total: Int
)

data class TopicLessonProgress(
    val completedCount: Int
)

@Serializable
private data class LessonListResponse(
    @SerialName("lessons") val lessons: List<LessonSummaryDTO>,
    @SerialName("total") val total: Int
)

@Serializable
private data class LessonSummaryDTO(
    @SerialName("id") val id: String,
    @SerialName("topic_id") val topicId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("question_count") val questionCount: Int = 0
)
