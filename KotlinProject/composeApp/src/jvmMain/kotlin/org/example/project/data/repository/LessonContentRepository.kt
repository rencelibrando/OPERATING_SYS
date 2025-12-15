package org.example.project.data.repository

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
import kotlinx.serialization.json.Json
import org.example.project.core.utils.ErrorLogger
import org.example.project.domain.model.*
import java.io.File

private const val LOG_TAG = "LessonContentRepository.kt"

/**
 * Repository for lesson content operations.
 * Handles all API calls related to lessons, questions, and user progress.
 */
interface LessonContentRepository {
    suspend fun getLessonsByTopic(topicId: String, publishedOnly: Boolean = true): Result<List<LessonSummary>>
    suspend fun getLessonById(lessonId: String, includeQuestions: Boolean = true): Result<LessonContent>
    suspend fun createLesson(lessonData: LessonCreate): Result<LessonContent>
    suspend fun updateLesson(lessonId: String, title: String?, description: String?, isPublished: Boolean?): Result<LessonContent>
    suspend fun deleteLesson(lessonId: String): Result<Unit>
    
    // Questions
    suspend fun createQuestion(lessonId: String, questionData: QuestionCreate): Result<LessonQuestion>
    suspend fun updateQuestion(questionId: String, questionData: QuestionCreate): Result<LessonQuestion>
    suspend fun deleteQuestion(questionId: String): Result<Unit>
    
    // Choices
    suspend fun createChoice(questionId: String, choiceData: QuestionChoiceCreate): Result<QuestionChoice>
    suspend fun deleteChoice(choiceId: String): Result<Unit>
    
    // User progress
    suspend fun getUserProgress(userId: String, lessonId: String): Result<UserLessonProgress?>
    suspend fun submitLessonAnswers(request: SubmitLessonAnswersRequest): Result<SubmitLessonAnswersResponse>
    
    // Media upload
    suspend fun uploadMedia(file: File, mediaType: String): Result<MediaUploadResponse>
    suspend fun deleteMedia(fileUrl: String): Result<Unit>
}

class LessonContentRepositoryImpl(
    private val baseUrl: String = "http://localhost:8000"
) : LessonContentRepository {
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    // ============================================
    // LESSON OPERATIONS
    // ============================================
    
    override suspend fun getLessonsByTopic(
        topicId: String, 
        publishedOnly: Boolean
    ): Result<List<LessonSummary>> = withContext(Dispatchers.IO) {
        try {
            val response: LessonListResponse = client.get("$baseUrl/api/lessons/topic/$topicId") {
                parameter("published_only", publishedOnly)
            }.body()
            Result.success(response.lessons)
        } catch (e: Exception) {
            ErrorLogger.logException(LOG_TAG, e, "Error fetching lessons")
            Result.failure(e)
        }
    }
    
    override suspend fun getLessonById(
        lessonId: String, 
        includeQuestions: Boolean
    ): Result<LessonContent> = withContext(Dispatchers.IO) {
        try {
            val response: LessonDetailResponse = client.get("$baseUrl/api/lessons/$lessonId") {
                parameter("include_questions", includeQuestions)
            }.body()
            Result.success(response.lesson)
        } catch (e: Exception) {
            ErrorLogger.logException(LOG_TAG, e, "Error fetching lesson")
            Result.failure(e)
        }
    }
    
    override suspend fun createLesson(lessonData: LessonCreate): Result<LessonContent> = 
        withContext(Dispatchers.IO) {
            try {
                val response: LessonDetailResponse = client.post("$baseUrl/api/lessons/") {
                    contentType(ContentType.Application.Json)
                    setBody(lessonData)
                }.body()
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
        isPublished: Boolean?
    ): Result<LessonContent> = withContext(Dispatchers.IO) {
        try {
            val updateData = LessonUpdate(
                title = title,
                description = description,
                isPublished = isPublished
            )
            
            val response: LessonDetailResponse = client.put("$baseUrl/api/lessons/$lessonId") {
                contentType(ContentType.Application.Json)
                setBody(updateData)
            }.body()
            Result.success(response.lesson)
        } catch (e: Exception) {
            println("[LessonContentRepo] Error updating lesson: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteLesson(lessonId: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                client.delete("$baseUrl/api/lessons/$lessonId")
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
        questionData: QuestionCreate
    ): Result<LessonQuestion> = withContext(Dispatchers.IO) {
        try {
            val response: LessonQuestion = client.post("$baseUrl/api/lessons/$lessonId/questions") {
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
        questionData: QuestionCreate
    ): Result<LessonQuestion> = withContext(Dispatchers.IO) {
        try {
            val response: LessonQuestion = client.put("$baseUrl/api/lessons/questions/$questionId") {
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
        choiceData: QuestionChoiceCreate
    ): Result<QuestionChoice> = withContext(Dispatchers.IO) {
        try {
            val response: QuestionChoice = client.post("$baseUrl/api/lessons/questions/$questionId/choices") {
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
        lessonId: String
    ): Result<UserLessonProgress?> = withContext(Dispatchers.IO) {
        try {
            val response: UserLessonProgress = client.get("$baseUrl/api/lessons/progress/$userId/$lessonId").body()
            Result.success(response)
        } catch (e: Exception) {
            // 404 means no progress yet, which is OK
            if (e.message?.contains("404") == true) {
                Result.success(null)
            } else {
                println("[LessonContentRepo] Error fetching progress: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun submitLessonAnswers(
        request: SubmitLessonAnswersRequest
    ): Result<SubmitLessonAnswersResponse> = withContext(Dispatchers.IO) {
        try {
            val response: SubmitLessonAnswersResponse = client.post("$baseUrl/api/lessons/submit-answers") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            println("[LessonContentRepo] Error submitting answers: ${e.message}")
            Result.failure(e)
        }
    }
    
    // ============================================
    // MEDIA OPERATIONS
    // ============================================
    
    override suspend fun uploadMedia(
        file: File, 
        mediaType: String
    ): Result<MediaUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response: MediaUploadResponse = client.submitFormWithBinaryData(
                url = "$baseUrl/api/lessons/media/upload",
                formData = formData {
                    append("file", file.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    })
                    append("media_type", mediaType)
                }
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
}
