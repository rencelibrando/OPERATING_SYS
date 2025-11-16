package org.example.project.core.lessons

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.api.SupabaseApiHelper
import org.example.project.core.config.SupabaseConfig
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LessonTopic

class LessonTopicsService {
    private val supabase = SupabaseConfig.client

    suspend fun seedBeginnerTopics(): Result<Unit> =
        runCatching {
            withContext(Dispatchers.IO) {
                if (!SupabaseApiHelper.isReady()) {
                    throw Exception("Supabase not configured - please check your .env file")
                }

                
                val hasSession = SupabaseApiHelper.ensureValidSession()
                if (!hasSession) {
                    println("[LessonTopicsService] No active session - attempting to seed anyway (may require admin access)")
                }

                println("[LessonTopicsService] Starting to seed beginner topics...")
                println("[LessonTopicsService] Preparing ${LessonTopic.getBeginnerTopics().size} topics...")

                val topics = LessonTopic.getBeginnerTopics()
                val difficulty = LessonDifficulty.BEGINNER.displayName
                var successCount = 0
                var errorCount = 0

                topics.forEachIndexed { index, topic ->
                    try {
                        val payload =
                            buildJsonObject {
                                put("id", topic.id)
                                put("difficulty_level", difficulty)
                                put("title", topic.title)
                                put("description", topic.description)
                                topic.lessonNumber?.let { put("lesson_number", it) }
                                topic.durationMinutes?.let { put("duration_minutes", it) }
                                put("sort_order", index)
                                put("is_locked", topic.isLocked)
                                put("is_published", true)
                            }

                        supabase.postgrest["lesson_topics"].upsert(payload)
                        successCount++
                        println("[LessonTopicsService] ✓ Upserted (${index + 1}/${topics.size}): ${topic.title}")
                    } catch (e: Exception) {
                        errorCount++
                        println("[LessonTopicsService] ✗ Error upserting topic ${topic.id} (${topic.title}): ${e.message}")
                        e.printStackTrace()
                        
                    }
                }

                println("[LessonTopicsService] =========================================")
                println("[LessonTopicsService] Seeding completed!")
                println("[LessonTopicsService] Success: $successCount, Errors: $errorCount")
                println("[LessonTopicsService] =========================================")

                if (errorCount > 0) {
                    throw Exception("Failed to seed some topics: $errorCount errors out of ${topics.size} total")
                }
            }
        }

    suspend fun topicsExistForDifficulty(difficulty: LessonDifficulty): Boolean =
        runCatching {
            withContext(Dispatchers.IO) {
                if (!SupabaseApiHelper.isReady()) {
                    return@withContext false
                }

                val response =
                    supabase.postgrest["lesson_topics"].select {
                        filter {
                            eq("difficulty_level", difficulty.displayName)
                            eq("is_published", true)
                        }
                        limit(1)
                    }

                    response.decodeList<Any>().isNotEmpty()
            }
        }.getOrElse { false }
}

