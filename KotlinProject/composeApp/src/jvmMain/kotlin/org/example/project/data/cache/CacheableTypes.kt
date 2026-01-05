package org.example.project.data.cache

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.LessonTopic
import org.example.project.domain.model.LessonSummary

/**
 * Base sealed interface for all cacheable data types.
 * This enables polymorphic serialization with kotlinx.serialization.
 */
@Serializable
sealed interface CacheableData

/**
 * Wrapper for caching lists of lesson topics
 */
@Serializable
data class CachedLessonTopicList(
    val topics: List<CachedLessonTopic>
) : CacheableData

/**
 * Cacheable version of LessonTopic
 */
@Serializable
data class CachedLessonTopic(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String,
    @SerialName("lesson_number")
    val lessonNumber: Int? = null,
    @SerialName("is_completed")
    val isCompleted: Boolean = false,
    @SerialName("is_locked")
    val isLocked: Boolean = false,
    @SerialName("duration_minutes")
    val durationMinutes: Int? = null,
    @SerialName("language")
    val language: String? = null,
    @SerialName("completed_lessons_count")
    val completedLessonsCount: Int = 0,
    @SerialName("total_lessons_count")
    val totalLessonsCount: Int = 0,
) : CacheableData {
    fun toDomain(): LessonTopic {
        val lang = mapDatabaseLanguage(language)
        return LessonTopic(
            id = id,
            title = title,
            description = description,
            lessonNumber = lessonNumber,
            isCompleted = isCompleted,
            isLocked = isLocked,
            durationMinutes = durationMinutes,
            language = lang,
            completedLessonsCount = completedLessonsCount,
            totalLessonsCount = totalLessonsCount,
        )
    }
    
    companion object {
        fun fromDomain(topic: LessonTopic): CachedLessonTopic {
            return CachedLessonTopic(
                id = topic.id,
                title = topic.title,
                description = topic.description,
                lessonNumber = topic.lessonNumber,
                isCompleted = topic.isCompleted,
                isLocked = topic.isLocked,
                durationMinutes = topic.durationMinutes,
                language = topic.language?.displayName,
                completedLessonsCount = topic.completedLessonsCount,
                totalLessonsCount = topic.totalLessonsCount,
            )
        }
    }
}

/**
 * Wrapper for caching lists of lesson summaries
 */
@Serializable
data class CachedLessonSummaryList(
    val lessons: List<CachedLessonSummary>
) : CacheableData

/**
 * Cacheable version of LessonSummary
 */
@Serializable
data class CachedLessonSummary(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("topic_id") val topicId: String,
    @SerialName("lesson_order") val lessonOrder: Int = 0,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("question_count") val questionCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) : CacheableData {
    fun toDomain(): LessonSummary {
        return LessonSummary(
            id = id,
            title = title,
            description = description,
            topicId = topicId,
            lessonOrder = lessonOrder,
            isPublished = isPublished,
            questionCount = questionCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
    
    companion object {
        fun fromDomain(lesson: LessonSummary): CachedLessonSummary {
            return CachedLessonSummary(
                id = lesson.id,
                title = lesson.title,
                description = lesson.description,
                topicId = lesson.topicId,
                lessonOrder = lesson.lessonOrder,
                isPublished = lesson.isPublished,
                questionCount = lesson.questionCount,
                createdAt = lesson.createdAt,
                updatedAt = lesson.updatedAt,
            )
        }
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
