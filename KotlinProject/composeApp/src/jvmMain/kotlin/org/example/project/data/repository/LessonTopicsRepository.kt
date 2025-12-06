package org.example.project.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.api.SupabaseApiHelper
import org.example.project.core.config.SupabaseConfig
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.LessonTopic

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
    suspend fun updateTopic(topic: LessonTopic, difficulty: LessonDifficulty, language: org.example.project.domain.model.LessonLanguage): Result<Unit>
    suspend fun deleteTopic(topicId: String): Result<Unit>
    suspend fun createTopic(topic: LessonTopic, difficulty: LessonDifficulty, language: org.example.project.domain.model.LessonLanguage, sortOrder: Int): Result<Unit>
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
    fun toDomain(isCompleted: Boolean = false): LessonTopic {
        val lang = mapDatabaseLanguage(language)
        return LessonTopic(
            id = id,
            title = title,
            description = description ?: "",
            lessonNumber = lessonNumber,
            isCompleted = isCompleted,
            isLocked = isLocked,
            durationMinutes = durationMinutes,
            language = lang,
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

class LessonTopicsRepositoryImpl : LessonTopicsRepository {
    private val supabase = SupabaseConfig.client

    override suspend fun getTopicsByDifficulty(
        difficulty: LessonDifficulty,
        language: org.example.project.domain.model.LessonLanguage?,
    ): Result<List<LessonTopic>> =
        runCatching {
            if (!SupabaseApiHelper.isReady()) {
                throw Exception("Supabase not configured - will use local fallback")
            }

            SupabaseApiHelper.executeWithRetry {
                withContext(Dispatchers.IO) {
                    if (!SupabaseApiHelper.ensureValidSession()) {
                        
                        println("[LessonTopics] No valid session, fetching topics without progress tracking")
                    }

                    val difficultyString = difficulty.displayName
                    val languageString = language?.displayName

                    println("[LessonTopics] Fetching topics for difficulty: $difficultyString, language: ${languageString ?: "all"}")

                    val response =
                        supabase.postgrest["lesson_topics"].select {
                            filter {
                                eq("difficulty_level", difficultyString)
                                languageString?.let { eq("language", it) }
                                eq("is_published", true)
                            }
                        }

                    val topicDTOs = response.decodeList<LessonTopicDTO>()
                        .sortedWith(compareBy({ it.sortOrder }, { it.lessonNumber ?: Int.MAX_VALUE }))

                    println("[LessonTopics] Fetched ${topicDTOs.size} topics from database")

                    
                    val userId = SupabaseApiHelper.getCurrentUserId()

                    
                    val topics =
                        if (userId != null) {
                            val progressMap = getUserProgressMap(userId, topicDTOs.map { it.id })
                            topicDTOs.map { dto ->
                                val progress = progressMap[dto.id]
                                dto.toDomain(isCompleted = progress?.isCompleted ?: false)
                            }
                        } else {
                            topicDTOs.map { it.toDomain() }
                        }

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

    override suspend fun updateTopic(topic: LessonTopic, difficulty: LessonDifficulty, language: org.example.project.domain.model.LessonLanguage): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                try {
                    val payload = buildJsonObject {
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
                } catch (e: Exception) {
                    println("[LessonTopics] Error deleting topic: ${e.message}")
                    throw e
                }
            }
        }

    override suspend fun createTopic(topic: LessonTopic, difficulty: LessonDifficulty, language: org.example.project.domain.model.LessonLanguage, sortOrder: Int): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                try {
                    val payload = buildJsonObject {
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
                } catch (e: Exception) {
                    println("[LessonTopics] Error creating topic: ${e.message}")
                    throw e
                }
            }
        }

    private suspend fun getUserProgressMap(
        userId: String,
        topicIds: List<String>,
    ): Map<String, LessonTopicProgress> {
        return try {
            if (topicIds.isEmpty()) return emptyMap()

            val response =
                supabase.postgrest["lesson_topic_progress"].select {
                    filter {
                        eq("user_id", userId)
                        isIn("topic_id", topicIds)
                    }
                }

            val progressDTOs = response.decodeList<LessonTopicProgressDTO>()

            progressDTOs.associate { dto ->
                dto.topicId to
                    LessonTopicProgress(
                        userId = dto.userId,
                        topicId = dto.topicId,
                        isCompleted = dto.isCompleted,
                        timeSpent = dto.timeSpent,
                    )
            }
        } catch (e: Exception) {
            // Check if it's a "table not found" error - this is expected if migration hasn't been run
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("Could not find the table", ignoreCase = true) ||
                errorMessage.contains("does not exist", ignoreCase = true)) {
                println("[LessonTopics] Progress table not found - progress tracking will be unavailable until migration 004_lesson_topics.sql is run")
            } else {
                println("[LessonTopics] Error fetching progress map: ${e.message}")
            }
            emptyMap()
        }
    }

}

