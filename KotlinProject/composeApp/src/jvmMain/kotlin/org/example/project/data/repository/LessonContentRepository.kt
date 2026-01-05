package org.example.project.data.repository

import io.github.jan.supabase.postgrest.from
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.config.SupabaseConfig
import org.example.project.core.utils.ErrorLogger
import org.example.project.data.cache.CacheableData
import org.example.project.data.cache.CachedLessonSummary
import org.example.project.data.cache.CachedLessonSummaryList
import org.example.project.data.cache.LocalStorageCache
import org.example.project.domain.model.*
import java.io.File

private const val LOG_TAG = "LessonContentRepository.kt"

/**
 * Repository for lesson content operations.
 * Handles all API calls related to lessons, questions, and user progress.
 */
interface LessonContentRepository {
    suspend fun getLessonsByTopic(
        topicId: String,
        publishedOnly: Boolean = true,
        forceRefresh: Boolean = false,
    ): Result<List<LessonSummary>>

    suspend fun getLessonById(
        lessonId: String,
        includeQuestions: Boolean = true,
    ): Result<LessonContent>

    suspend fun createLesson(lessonData: LessonCreate): Result<LessonContent>

    suspend fun updateLesson(
        lessonId: String,
        title: String?,
        description: String?,
        isPublished: Boolean?,
    ): Result<LessonContent>

    suspend fun deleteLesson(lessonId: String): Result<Unit>

    // Questions
    suspend fun createQuestion(
        lessonId: String,
        questionData: QuestionCreate,
    ): Result<LessonQuestion>

    suspend fun updateQuestion(
        questionId: String,
        questionData: QuestionCreate,
    ): Result<LessonQuestion>

    suspend fun deleteQuestion(questionId: String): Result<Unit>

    // Choices
    suspend fun createChoice(
        questionId: String,
        choiceData: QuestionChoiceCreate,
    ): Result<QuestionChoice>

    suspend fun deleteChoice(choiceId: String): Result<Unit>

    // User progress
    suspend fun getUserProgress(
        userId: String,
        lessonId: String,
    ): Result<UserLessonProgress?>

    suspend fun submitLessonAnswers(request: SubmitLessonAnswersRequest): Result<SubmitLessonAnswersResponse>

    // Media upload
    suspend fun uploadMedia(
        file: File,
        mediaType: String,
    ): Result<MediaUploadResponse>

    suspend fun deleteMedia(fileUrl: String): Result<Unit>
}

class LessonContentRepositoryImpl(
    private val baseUrl: String = "http://localhost:8000",
) : LessonContentRepository {
    private val client =
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

    private val supabase = SupabaseConfig.client
    
    // Persistent local storage cache
    private val localStorageCache = LocalStorageCache(
        cacheDir = File(System.getProperty("user.home"), ".operating_sys_cache/lesson_content")
    )
    
    // Multi-layer caching with TTL
    private val CACHE_DURATION_MS = 2 * 60 * 1000L // 2 minutes
    private val SHORT_CACHE_DURATION_MS = 30 * 1000L // 30 seconds for user progress
    private val PERSISTENT_CACHE_TTL_MS = 30 * 1000L // 30 seconds for persistent cache (auto-expires quickly)
    
    // Cache for lessons by topic
    private val lessonsCache = mutableMapOf<String, Pair<List<LessonSummary>, Long>>()
    
    // Cache for lesson details by ID
    private val lessonDetailsCache = mutableMapOf<String, Pair<LessonContent, Long>>()
    
    // Cache for user progress by (userId, lessonId)
    private val userProgressCache = mutableMapOf<String, Pair<UserLessonProgress?, Long>>()

    // ============================================
    // CACHE DATA CLASSES
    // ============================================

    // ============================================
    // LESSON OPERATIONS
    // ============================================

    override suspend fun getLessonsByTopic(
        topicId: String,
        publishedOnly: Boolean,
        forceRefresh: Boolean,
    ): Result<List<LessonSummary>> =
        withContext(Dispatchers.IO) {
            try {
                val cacheKey = "${topicId}_$publishedOnly"
                val currentTime = System.currentTimeMillis()

                // Skip cache if forceRefresh is true
                if (!forceRefresh) {
                    // Check persistent cache first
                    val persistentCacheResult = localStorageCache.retrieve("lessons_$cacheKey")

                    @Suppress("UNCHECKED_CAST")
                    val cachedLessonList = persistentCacheResult.getOrNull() as? CachedLessonSummaryList
                    if (cachedLessonList != null) {
                        println("[LessonContent] ✅ Persistent cache hit for topic $topicId (${cachedLessonList.lessons.size} lessons)")
                        return@withContext Result.success(cachedLessonList.lessons.map { it.toDomain() })
                    }

                    // Check in-memory cache second
                    val cached = lessonsCache[cacheKey]
                    if (cached != null && (currentTime - cached.second) < CACHE_DURATION_MS) {
                        println("[LessonContent] ✅ Memory cache hit for topic $topicId (${cached.first.size} lessons)")
                        return@withContext Result.success(cached.first)
                    }
                } else {
                    println("[LessonContent] 🔄 Force refresh requested - skipping cache for topic $topicId")
                }

                println("[LessonContent] 🔄 Fetching lessons for topic $topicId from API...")
                val response: LessonListResponse =
                    client.get("$baseUrl/api/lessons/topic/$topicId") {
                        parameter("published_only", publishedOnly)
                    }.body()

                // Cache the result in memory
                lessonsCache[cacheKey] = Pair(response.lessons, currentTime)
                println("[LessonContent] ✅ Memory cached ${response.lessons.size} lessons for topic $topicId")

                // Cache the result in persistent storage
                val cachedLessons = response.lessons.map { CachedLessonSummary.fromDomain(it) }
                val lessonListWrapper = CachedLessonSummaryList(cachedLessons)
                localStorageCache.store(
                    key = "lessons_$cacheKey",
                    data = lessonListWrapper,
                    ttlMs = PERSISTENT_CACHE_TTL_MS
                )
                println("[LessonContent] ✅ Persistent cached ${response.lessons.size} lessons for topic $topicId")

                Result.success(response.lessons)
            } catch (e: Exception) {
                ErrorLogger.logException(LOG_TAG, e, "Error fetching lessons")
                Result.failure(e)
            }
        }

    override suspend fun getLessonById(
        lessonId: String,
        includeQuestions: Boolean,
    ): Result<LessonContent> =
        withContext(Dispatchers.IO) {
            try {
                // Only cache when questions are included (most common use case)
                if (includeQuestions) {
                    val currentTime = System.currentTimeMillis()
                    val cached = lessonDetailsCache[lessonId]
                    if (cached != null && (currentTime - cached.second) < CACHE_DURATION_MS) {
                        println("[LessonContent] ✅ Cache hit for lesson $lessonId")
                        return@withContext Result.success(cached.first)
                    }
                }
                
                println("[LessonContent] 🔄 Fetching lesson $lessonId from API...")
                val response: LessonDetailResponse =
                    client.get("$baseUrl/api/lessons/$lessonId") {
                        parameter("include_questions", includeQuestions)
                    }.body()
                
                // Cache if questions included
                if (includeQuestions) {
                    lessonDetailsCache[lessonId] = Pair(response.lesson, System.currentTimeMillis())
                    println("[LessonContent] ✅ Cached lesson $lessonId")
                }
                
                Result.success(response.lesson)
            } catch (e: Exception) {
                ErrorLogger.logException(LOG_TAG, e, "Error fetching lesson")
                Result.failure(e)
            }
        }

    override suspend fun createLesson(lessonData: LessonCreate): Result<LessonContent> =
        withContext(Dispatchers.IO) {
            try {
                val response: LessonDetailResponse =
                    client.post("$baseUrl/api/lessons/") {
                        contentType(ContentType.Application.Json)
                        setBody(lessonData)
                    }.body()
                
                // Clear cache for the topic
                invalidateTopicCache(lessonData.topicId)
                
                Result.success(response.lesson)
            } catch (e: Exception) {
                ErrorLogger.logException(LOG_TAG, e, "Error creating lesson")
                Result.failure(e)
            }
        }

    override suspend fun updateLesson(
        lessonId: String,
        title: String?,
        description: String?,
        isPublished: Boolean?,
    ): Result<LessonContent> =
        withContext(Dispatchers.IO) {
            try {
                val updateData =
                    LessonUpdate(
                        title = title,
                        description = description,
                        isPublished = isPublished,
                    )

                val response: LessonDetailResponse =
                    client.put("$baseUrl/api/lessons/$lessonId") {
                        contentType(ContentType.Application.Json)
                        setBody(updateData)
                    }.body()
                
                // Clear cache for this lesson and its topic
                invalidateLessonCache(lessonId)
                invalidateTopicCache(response.lesson.topicId)
                
                Result.success(response.lesson)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error updating lesson: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun deleteLesson(lessonId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // First get the lesson to know its topic for cache invalidation
                val lessonResponse: LessonDetailResponse = 
                    client.get("$baseUrl/api/lessons/$lessonId").body()
                
                client.delete("$baseUrl/api/lessons/$lessonId")
                
                // Clear cache for this lesson and its topic
                invalidateLessonCache(lessonId)
                invalidateTopicCache(lessonResponse.lesson.topicId)
                
                Result.success(Unit)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error deleting lesson: ${e.message}")
                Result.failure(e)
            }
        }

    // ============================================
    // QUESTION OPERATIONS
    // ============================================

    override suspend fun createQuestion(
        lessonId: String,
        questionData: QuestionCreate,
    ): Result<LessonQuestion> =
        withContext(Dispatchers.IO) {
            try {
                val response: LessonQuestion =
                    client.post("$baseUrl/api/lessons/$lessonId/questions") {
                        contentType(ContentType.Application.Json)
                        setBody(questionData)
                    }.body()
                Result.success(response)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error creating question: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun updateQuestion(
        questionId: String,
        questionData: QuestionCreate,
    ): Result<LessonQuestion> =
        withContext(Dispatchers.IO) {
            try {
                val response: LessonQuestion =
                    client.put("$baseUrl/api/lessons/questions/$questionId") {
                        contentType(ContentType.Application.Json)
                        setBody(questionData)
                    }.body()
                Result.success(response)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error updating question: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun deleteQuestion(questionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                client.delete("$baseUrl/api/lessons/questions/$questionId")
                Result.success(Unit)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error deleting question: ${e.message}")
                Result.failure(e)
            }
        }

    // ============================================
    // CHOICE OPERATIONS
    // ============================================

    override suspend fun createChoice(
        questionId: String,
        choiceData: QuestionChoiceCreate,
    ): Result<QuestionChoice> =
        withContext(Dispatchers.IO) {
            try {
                val response: QuestionChoice =
                    client.post("$baseUrl/api/lessons/questions/$questionId/choices") {
                        contentType(ContentType.Application.Json)
                        setBody(choiceData)
                    }.body()
                Result.success(response)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error creating choice: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun deleteChoice(choiceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                client.delete("$baseUrl/api/lessons/choices/$choiceId")
                Result.success(Unit)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error deleting choice: ${e.message}")
                Result.failure(e)
            }
        }

    // ============================================
    // USER PROGRESS OPERATIONS
    // ============================================

    override suspend fun getUserProgress(
        userId: String,
        lessonId: String,
    ): Result<UserLessonProgress?> =
        withContext(Dispatchers.IO) {
            try {
                val cacheKey = "${userId}_$lessonId"
                val currentTime = System.currentTimeMillis()
                
                // Check cache first (shorter TTL for progress)
                val cached = userProgressCache[cacheKey]
                if (cached != null && (currentTime - cached.second) < SHORT_CACHE_DURATION_MS) {
                    println("[LessonContent] ✅ User progress cache hit for lesson $lessonId")
                    return@withContext Result.success(cached.first)
                }
                
                println("[LessonContent] 🔄 Fetching user progress for lesson $lessonId...")
                val response: UserLessonProgress = client.get("$baseUrl/api/lessons/progress/$userId/$lessonId").body()
                
                // Cache the result
                userProgressCache[cacheKey] = Pair(response, currentTime)
                println("[LessonContent] ✅ Cached user progress for lesson $lessonId")
                
                Result.success(response)
            } catch (e: Exception) {
                // 404 means no progress yet, which is OK
                if (e.message?.contains("404") == true) {
                    val cacheKey = "${userId}_$lessonId"
                    userProgressCache[cacheKey] = Pair(null, System.currentTimeMillis())
                    Result.success(null)
                } else {
                    println("[LessonContentRepo] Error fetching progress: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    override suspend fun submitLessonAnswers(request: SubmitLessonAnswersRequest): Result<SubmitLessonAnswersResponse> =
        withContext(Dispatchers.IO) {
            try {
                println("[LessonContentRepo] ===== LOCAL QUIZ SUBMISSION =====")
                println("[LessonContentRepo] User ID: ${request.userId}")
                println("[LessonContentRepo] Lesson ID: ${request.lessonId}")
                println("[LessonContentRepo] Answers count: ${request.answers.size}")

                // Step 1: Save all answers to database
                println("[LessonContentRepo] Saving answers to database...")
                for ((index, answer) in request.answers.withIndex()) {
                    println(
                        "[LessonContentRepo] Saving answer ${index + 1}: questionId=${answer.questionId}, isCorrect=${answer.isCorrect}",
                    )

                    val answerData =
                        buildJsonObject {
                            put("user_id", request.userId)
                            put("lesson_id", request.lessonId)
                            put("question_id", answer.questionId)
                            put("selected_choice_id", answer.selectedChoiceId)
                            put("answer_text", answer.answerText)
                            put("voice_recording_url", answer.voiceRecordingUrl)
                            put("is_correct", answer.isCorrect)
                        }

                    supabase.from("user_question_answers")
                        .update(answerData) {
                            filter {
                                eq("user_id", request.userId)
                                eq("question_id", answer.questionId)
                            }
                        }
                }
                println("[LessonContentRepo] All answers saved successfully")

                // Step 2: Calculate score locally
                val correctCount = request.answers.count { it.isCorrect == true }
                val totalQuestions = request.answers.size
                val score =
                    if (totalQuestions > 0) {
                        (correctCount.toFloat() / totalQuestions.toFloat()) * 100f
                    } else {
                        0f
                    }
                val isPassed = score >= 70f // 70% passing threshold

                println("[LessonContentRepo] Score calculated: $correctCount/$totalQuestions = $score%")
                println("[LessonContentRepo] Passed: $isPassed")

                // Step 3: Update lesson progress
                println("[LessonContentRepo] Updating lesson progress...")
                val progressData =
                    buildJsonObject {
                        put("user_id", request.userId)
                        put("lesson_id", request.lessonId)
                        put("is_completed", true)
                        put("score", score)
                        put("time_spent_seconds", 0) // Could be calculated from actual time
                        put("completed_at", kotlinx.datetime.Clock.System.now().toString())
                    }

                // First try to update existing record, if no rows affected then insert new one
                val updateResult = supabase.from("user_lesson_progress")
                    .update(progressData) {
                        filter {
                            eq("user_id", request.userId)
                            eq("lesson_id", request.lessonId)
                        }
                    }
                
                // Check if update affected any rows by looking at the response data
                // If update returned data, it means a record was updated
                // If update returned empty data, it means no record was found to update
                val updateAffectedRows = updateResult.data?.isNotEmpty() == true
                
                if (!updateAffectedRows) {
                    println("[LessonContentRepo] No existing record found, inserting new progress record")
                    supabase.from("user_lesson_progress")
                        .insert(progressData)
                } else {
                    println("[LessonContentRepo] Existing record updated successfully")
                }

                println("[LessonContentRepo] Lesson progress updated successfully")

                // Step 4: Create response
                val now = kotlinx.datetime.Clock.System.now().toString()
                val response =
                    SubmitLessonAnswersResponse(
                        score = score,
                        totalQuestions = totalQuestions,
                        correctAnswers = correctCount,
                        isPassed = isPassed,
                        completedAt = now,
                    )

                println("[LessonContentRepo] ===== SUBMISSION COMPLETED =====")
                println("[LessonContentRepo] Response: score=$score, total=$totalQuestions, correct=$correctCount, passed=$isPassed")

                Result.success(response)
            } catch (e: Exception) {
                println("[LessonContentRepo] ===== SUBMISSION FAILED =====")
                println("[LessonContentRepo] Error: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }

    // ============================================
    // MEDIA OPERATIONS
    // ============================================

    override suspend fun uploadMedia(
        file: File,
        mediaType: String,
    ): Result<MediaUploadResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response: MediaUploadResponse =
                    client.submitFormWithBinaryData(
                        url = "$baseUrl/api/lessons/media/upload",
                        formData =
                            formData {
                                append(
                                    "file",
                                    file.readBytes(),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "application/octet-stream")
                                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                    },
                                )
                                append("media_type", mediaType)
                            },
                    ).body()
                Result.success(response)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error uploading media: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun deleteMedia(fileUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                client.delete("$baseUrl/api/lessons/media") {
                    parameter("file_url", fileUrl)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                println("[LessonContentRepo] Error deleting media: ${e.message}")
                Result.failure(e)
            }
        }
    
    // ============================================
    // CACHE MANAGEMENT
    // ============================================
    
    /**
     * Clear all caches - call this when user logs out or when manual refresh is needed
     */
    fun clearCache() {
        lessonsCache.clear()
        lessonDetailsCache.clear()
        userProgressCache.clear()
        
        // Clear persistent cache
        runCatching {
            kotlinx.coroutines.runBlocking {
                localStorageCache.clear()
            }
        }.onFailure { e ->
            println("[LessonContent] Failed to clear persistent cache: ${e.message}")
        }
        
        println("[LessonContent] 🗑️ All caches cleared")
    }
    
    /**
     * Clear cache for specific user - call this when user completes a lesson
     */
    fun clearUserCache(userId: String) {
        userProgressCache.keys.removeAll { it.startsWith("${userId}_") }
        
        // Clear user-specific persistent cache
        runCatching {
            kotlinx.coroutines.runBlocking {
                localStorageCache.clear()
            }
        }.onFailure { e ->
            println("[LessonContent] Failed to clear persistent cache for user: ${e.message}")
        }
        
        println("[LessonContent] 🗑️ User cache cleared for $userId")
    }
    
    /**
     * Invalidate cache for specific topic - call this when lessons are updated
     */
    fun invalidateTopicCache(topicId: String) {
        lessonsCache.keys.removeAll { it.startsWith("${topicId}_") }
        
        // Clear topic-specific persistent cache
        runCatching {
            kotlinx.coroutines.runBlocking {
                localStorageCache.delete("lessons_${topicId}_true")
                localStorageCache.delete("lessons_${topicId}_false")
            }
        }.onFailure { e ->
            println("[LessonContent] Failed to clear persistent cache for topic $topicId: ${e.message}")
        }
        
        println("[LessonContent] 🗑️ Topic cache cleared for $topicId")
    }
    
    /**
     * Invalidate cache for specific lesson - call this when lesson is updated
     */
    fun invalidateLessonCache(lessonId: String) {
        lessonDetailsCache.remove(lessonId)
        
        // Clear lesson-specific persistent cache
        runCatching {
            kotlinx.coroutines.runBlocking {
                localStorageCache.delete("lesson_$lessonId")
            }
        }.onFailure { e ->
            println("[LessonContent] Failed to clear persistent cache for lesson $lessonId: ${e.message}")
        }
        
        println("[LessonContent] 🗑️ Lesson cache cleared for $lessonId")
    }
}
