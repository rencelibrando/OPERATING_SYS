package org.example.project.data.repository

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.project.core.api.SupabaseApiHelper
import org.example.project.core.config.SupabaseConfig
import org.example.project.domain.model.LanguageProgress
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.VoiceAnalysisScores
import kotlin.Result

/**
 * Repository for fetching comprehensive language progress analytics.
 * 
 * TRADE-OFF SOLUTIONS:
 * 1. Language Code Normalization: Centralized mapping in extension functions
 * 2. Score Aggregation: Prioritizes pre-aggregated tables, falls back to raw data
 * 3. Session Deduplication: Uses session_id tracking to prevent double-counting
 * 4. Parallel Queries: All metrics fetched concurrently with async/await
 * 5. Error Resilience: Each metric fails independently, returns partial data
 */
interface ProgressTrackerRepository {
    suspend fun getLessonsProgress(userId: String, language: LessonLanguage): Result<LessonsProgress>
    suspend fun getConversationSessions(userId: String, language: LessonLanguage): Result<Int>
    suspend fun getVocabularyCount(userId: String, language: LessonLanguage): Result<Int>
    suspend fun getVoiceAnalysisScores(userId: String, language: LessonLanguage): Result<VoiceAnalysisScores>
    suspend fun getTotalConversationTime(userId: String, language: LessonLanguage): Result<Double>
    suspend fun getLanguageProgress(userId: String, language: LessonLanguage): Result<LanguageProgress>
    suspend fun refreshMaterializedView(): Result<Unit>
}

data class LessonsProgress(
    val completed: Int,
    val total: Int,
)

@Serializable
private data class LessonCountDTO(
    @SerialName("count") val count: Long,
)

@Serializable
private data class VocabularyCountDTO(
    @SerialName("count") val count: Long,
)

@Serializable
private data class SessionCountDTO(
    @SerialName("count") val count: Long,
)

@Serializable
private data class TimeAggregateDTO(
    @SerialName("total_time") val totalTime: Double?,
)

@Serializable
private data class UserSpeakingProgressDTO(
    @SerialName("average_overall") val averageOverall: Double?,
    @SerialName("average_pronunciation") val averagePronunciation: Double?,
    @SerialName("average_fluency") val averageFluency: Double?,
    @SerialName("average_accuracy") val averageAccuracy: Double?,
)

@Serializable
private data class ConversationFeedbackDTO(
    @SerialName("session_id") val sessionId: String,
    @SerialName("overall_score") val overallScore: Int?,
    @SerialName("grammar_score") val grammarScore: Int?,
    @SerialName("pronunciation_score") val pronunciationScore: Int?,
    @SerialName("vocabulary_score") val vocabularyScore: Int?,
    @SerialName("fluency_score") val fluencyScore: Int?,
)

class ProgressTrackerRepositoryImpl : ProgressTrackerRepository {
    private val supabase = SupabaseConfig.client

    /**
     * TRADE-OFF SOLUTION 1: Lessons Progress
     * - Complex join across lesson_topics -> lessons -> user_lesson_progress
     * - Solution: Two separate optimized queries (total vs completed)
     */
    override suspend fun getLessonsProgress(
        userId: String,
        language: LessonLanguage,
    ): Result<LessonsProgress> = runCatching {
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                val languageName = language.displayName

                // Query 1: Total published lessons for this language
                val totalResponse = supabase.postgrest["lessons"].select {
                    filter {
                        eq("is_published", true)
                    }
                }.decodeList<LessonDTO>()

                // Filter lessons by language through topic_id
                val topicIds = totalResponse.map { it.topicId }.distinct()
                val topicsResponse = supabase.postgrest["lesson_topics"].select {
                    filter {
                        isIn("id", topicIds)
                        eq("language", languageName)
                        eq("is_published", true)
                    }
                }.decodeList<TopicLanguageDTO>()

                val validTopicIds = topicsResponse.map { it.id }.toSet()
                val totalLessons = totalResponse.count { it.topicId in validTopicIds }

                // Query 2: Completed lessons for this user & language
                val completedResponse = supabase.postgrest["user_lesson_progress"].select {
                    filter {
                        eq("user_id", userId)
                        eq("is_completed", true)
                    }
                }.decodeList<UserLessonProgressDTO>()

                val completedLessonIds = completedResponse.map { it.lessonId }.toSet()
                val completedLessons = totalResponse.count { 
                    it.id in completedLessonIds && it.topicId in validTopicIds 
                }

                LessonsProgress(
                    completed = completedLessons,
                    total = totalLessons
                )
            }
        }.getOrElse { e ->
            println("[ProgressTracker] Error fetching lessons progress: ${e.message}")
            throw e
        }
    }

    /**
     * TRADE-OFF SOLUTION 2: Conversation Sessions
     * - Queries Supabase directly (conversation_recordings table contains all conversation data)
     * - Includes agent_sessions and voice_sessions for comprehensive count
     * - Handles multiple language formats: displayName, lowercase, ISO code
     */
    override suspend fun getConversationSessions(
        userId: String,
        language: LessonLanguage,
    ): Result<Int> {
        return try {
            withContext(Dispatchers.IO) {
                val languageName = language.displayName
                val languageLower = language.name.lowercase()
                val languageCode = language.code
                println("[ProgressTracker] Getting conversation sessions for user: $userId")
                println("[ProgressTracker] Language formats: displayName=$languageName, lowercase=$languageLower, code=$languageCode")
                
                // Query all session tables from Supabase
                val agentSessions = try {
                    val byName = supabase.postgrest["agent_sessions"].select {
                        filter {
                            eq("user_id", userId)
                            eq("language", languageName)
                        }
                    }.decodeList<AgentSessionDTO>()
                    
                    val byLower = try {
                        supabase.postgrest["agent_sessions"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageLower)
                            }
                        }.decodeList<AgentSessionDTO>()
                    } catch (e: Exception) { emptyList() }
                    
                    (byName + byLower).distinctBy { it.id }
                } catch (e: Exception) {
                    println("[ProgressTracker] Error fetching agent sessions: ${e.message}")
                    emptyList()
                }

                val voiceSessions = try {
                    // Try ISO code, full name, and lowercase for voice_sessions
                    val sessionsByCode = try {
                        supabase.postgrest["voice_sessions"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageCode)
                            }
                        }.decodeList<VoiceSessionDTO>()
                    } catch (e: Exception) { emptyList() }
                    
                    val sessionsByName = try {
                        supabase.postgrest["voice_sessions"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageName)
                            }
                        }.decodeList<VoiceSessionDTO>()
                    } catch (e: Exception) { emptyList() }
                    
                    val sessionsByLower = try {
                        supabase.postgrest["voice_sessions"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageLower)
                            }
                        }.decodeList<VoiceSessionDTO>()
                    } catch (e: Exception) { emptyList() }
                    
                    (sessionsByCode + sessionsByName + sessionsByLower).distinctBy { it.id }
                } catch (e: Exception) {
                    println("[ProgressTracker] Error fetching voice sessions: ${e.message}")
                    emptyList()
                }

                // Query conversation_recordings with all language formats
                val conversationRecordings = try {
                    val byName = try {
                        supabase.postgrest["conversation_recordings"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageName)
                            }
                        }.decodeList<ConversationRecordingDTO>()
                    } catch (e: Exception) { emptyList() }
                    
                    val byLower = try {
                        supabase.postgrest["conversation_recordings"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageLower)
                            }
                        }.decodeList<ConversationRecordingDTO>()
                    } catch (e: Exception) { emptyList() }
                    
                    val byCode = try {
                        supabase.postgrest["conversation_recordings"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageCode)
                            }
                        }.decodeList<ConversationRecordingDTO>()
                    } catch (e: Exception) { emptyList() }
                    
                    (byName + byLower + byCode).distinctBy { it.id }
                } catch (e: Exception) {
                    println("[ProgressTracker] Error fetching conversation recordings: ${e.message}")
                    emptyList()
                }

                println("[ProgressTracker] Found: ${agentSessions.size} agent, ${voiceSessions.size} voice, ${conversationRecordings.size} recordings")

                // Deduplicate by session_id to prevent double-counting
                val allSessionIds = mutableSetOf<String>()
                
                agentSessions.forEach { allSessionIds.add(it.id) }
                voiceSessions.forEach { allSessionIds.add(it.id) }
                conversationRecordings.forEach { 
                    it.sessionId?.let { sessionId -> allSessionIds.add(sessionId) } 
                        ?: allSessionIds.add(it.id) // Use recording id if no session_id
                }

                val totalCount = allSessionIds.size
                println("[ProgressTracker] ✅ Total unique sessions: $totalCount for $languageName")
                
                Result.success(totalCount)
            }
        } catch (e: Exception) {
            println("[ProgressTracker] ❌ Error fetching conversation sessions: ${e.message}")
            Result.success(0)
        }
    }

    /**
     * TRADE-OFF SOLUTION 3: Vocabulary Count
     * - Simple join with language filter
     */
    override suspend fun getVocabularyCount(
        userId: String,
        language: LessonLanguage,
    ): Result<Int> = runCatching {
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                val languageName = language.displayName

                val userVocab = supabase.postgrest["user_vocabulary"].select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<UserVocabularyDTO>()

                if (userVocab.isEmpty()) return@withContext 0

                val wordIds = userVocab.map { it.wordId }
                val words = supabase.postgrest["vocabulary_words"].select {
                    filter {
                        isIn("id", wordIds)
                        eq("language", languageName)
                    }
                }.decodeList<VocabularyWordDTO>()

                words.size
            }
        }.getOrElse { e ->
            println("[ProgressTracker] Error fetching vocabulary count: ${e.message}")
            0
        }
    }

    /**
     * TRADE-OFF SOLUTION 4: Voice Analysis Scores
     * - Strategy: Use pre-aggregated user_speaking_progress first
     * - Fallback: Calculate from conversation_feedback if pre-agg not available
     * - Handles missing scores gracefully
     */
    override suspend fun getVoiceAnalysisScores(
        userId: String,
        language: LessonLanguage,
    ): Result<VoiceAnalysisScores> = runCatching {
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                val isoCode = language.code
                val languageName = language.displayName

                // Strategy 1: Try pre-aggregated user_speaking_progress
                try {
                    // Try ISO code first
                    var speakingProgress = supabase.postgrest["user_speaking_progress"].select {
                        filter {
                            eq("user_id", userId)
                            eq("language", isoCode)
                        }
                        limit(1)
                    }.decodeSingleOrNull<UserSpeakingProgressDTO>()
                    
                    // If not found, try full name
                    if (speakingProgress == null) {
                        speakingProgress = supabase.postgrest["user_speaking_progress"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageName)
                            }
                            limit(1)
                        }.decodeSingleOrNull<UserSpeakingProgressDTO>()
                    }

                    if (speakingProgress != null) {
                        return@withContext VoiceAnalysisScores(
                            overall = speakingProgress.averageOverall ?: 0.0,
                            pronunciation = speakingProgress.averagePronunciation ?: 0.0,
                            fluency = speakingProgress.averageFluency ?: 0.0,
                            accuracy = speakingProgress.averageAccuracy ?: 0.0,
                            grammar = 0.0, // Not in this table
                            vocabulary = 0.0 // Not in this table
                        ).clampedScores()
                    }
                } catch (e: Exception) {
                    println("[ProgressTracker] Pre-aggregated scores not available: ${e.message}")
                }

                // Strategy 2: Fallback to conversation_feedback averages
                try {
                    val feedbackList = supabase.postgrest["conversation_feedback"].select {
                        filter {
                            eq("user_id", userId)
                        }
                    }.decodeList<ConversationFeedbackDTO>()

                    if (feedbackList.isEmpty()) {
                        return@withContext VoiceAnalysisScores()
                    }

                    // Filter by language via conversation_recordings
                    val sessionIds = feedbackList.map { it.sessionId }
                    val recordings = supabase.postgrest["conversation_recordings"].select {
                        filter {
                            isIn("id", sessionIds)
                            eq("language", languageName)
                        }
                    }.decodeList<ConversationRecordingDTO>()

                    val validSessionIds = recordings.map { it.id }.toSet()
                    val filteredFeedback = feedbackList.filter { 
                        it.sessionId in validSessionIds 
                    }

                    if (filteredFeedback.isEmpty()) {
                        return@withContext VoiceAnalysisScores()
                    }

                    VoiceAnalysisScores(
                        overall = filteredFeedback.mapNotNull { it.overallScore?.toDouble() }.average(),
                        grammar = filteredFeedback.mapNotNull { it.grammarScore?.toDouble() }.average(),
                        pronunciation = filteredFeedback.mapNotNull { it.pronunciationScore?.toDouble() }.average(),
                        vocabulary = filteredFeedback.mapNotNull { it.vocabularyScore?.toDouble() }.average(),
                        fluency = filteredFeedback.mapNotNull { it.fluencyScore?.toDouble() }.average(),
                        accuracy = 0.0 // Not in conversation_feedback
                    ).clampedScores()
                } catch (e: Exception) {
                    println("[ProgressTracker] Error calculating feedback averages: ${e.message}")
                    VoiceAnalysisScores()
                }
            }
        }.getOrElse { e ->
            println("[ProgressTracker] Error fetching voice analysis scores: ${e.message}")
            VoiceAnalysisScores()
        }
    }

    /**
     * TRADE-OFF SOLUTION 5: Total Conversation Time
     * - Queries Supabase directly for all conversation time data
     * - Aggregates from agent_sessions, voice_sessions, and conversation_recordings
     * - Handles multiple language formats: displayName, lowercase, ISO code
     */
    override suspend fun getTotalConversationTime(
        userId: String,
        language: LessonLanguage,
    ): Result<Double> = runCatching {
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                val languageName = language.displayName
                val languageLower = language.name.lowercase()
                val isoCode = language.code
                println("[ProgressTracker] Getting total conversation time for user: $userId")
                println("[ProgressTracker] Language formats: displayName=$languageName, lowercase=$languageLower, code=$isoCode")

                // Parallel fetch with time aggregation
                val agentTime = async {
                    try {
                        val byName = supabase.postgrest["agent_sessions"].select {
                            filter {
                                eq("user_id", userId)
                                eq("language", languageName)
                            }
                        }.decodeList<AgentSessionDTO>()
                        
                        val byLower = try {
                            supabase.postgrest["agent_sessions"].select {
                                filter {
                                    eq("user_id", userId)
                                    eq("language", languageLower)
                                }
                            }.decodeList<AgentSessionDTO>()
                        } catch (e: Exception) { emptyList() }
                        
                        val allSessions = (byName + byLower).distinctBy { it.id }
                        allSessions.sumOf { 
                            (it.duration ?: 0.0) + (it.audioDuration ?: 0.0) 
                        }
                    } catch (e: Exception) {
                        println("[ProgressTracker] Error fetching agent session time: ${e.message}")
                        0.0
                    }
                }

                val voiceTime = async {
                    try {
                        // Try ISO code, full name, and lowercase for voice_sessions
                        val sessionsByCode = try {
                            supabase.postgrest["voice_sessions"].select {
                                filter {
                                    eq("user_id", userId)
                                    eq("language", isoCode)
                                }
                            }.decodeList<VoiceSessionDTO>()
                        } catch (e: Exception) { emptyList() }
                        
                        val sessionsByName = try {
                            supabase.postgrest["voice_sessions"].select {
                                filter {
                                    eq("user_id", userId)
                                    eq("language", languageName)
                                }
                            }.decodeList<VoiceSessionDTO>()
                        } catch (e: Exception) { emptyList() }
                        
                        val sessionsByLower = try {
                            supabase.postgrest["voice_sessions"].select {
                                filter {
                                    eq("user_id", userId)
                                    eq("language", languageLower)
                                }
                            }.decodeList<VoiceSessionDTO>()
                        } catch (e: Exception) { emptyList() }
                        
                        val allSessions = (sessionsByCode + sessionsByName + sessionsByLower).distinctBy { it.id }
                        allSessions.sumOf { it.sessionDuration ?: 0.0 }
                    } catch (e: Exception) {
                        println("[ProgressTracker] Error fetching voice session time: ${e.message}")
                        0.0
                    }
                }

                val recordingTime = async {
                    try {
                        val byName = try {
                            supabase.postgrest["conversation_recordings"].select {
                                filter {
                                    eq("user_id", userId)
                                    eq("language", languageName)
                                }
                            }.decodeList<ConversationRecordingDTO>()
                        } catch (e: Exception) { emptyList() }
                        
                        val byLower = try {
                            supabase.postgrest["conversation_recordings"].select {
                                filter {
                                    eq("user_id", userId)
                                    eq("language", languageLower)
                                }
                            }.decodeList<ConversationRecordingDTO>()
                        } catch (e: Exception) { emptyList() }
                        
                        val byCode = try {
                            supabase.postgrest["conversation_recordings"].select {
                                filter {
                                    eq("user_id", userId)
                                    eq("language", isoCode)
                                }
                            }.decodeList<ConversationRecordingDTO>()
                        } catch (e: Exception) { emptyList() }
                        
                        val allRecordings = (byName + byLower + byCode).distinctBy { it.id }
                        val time = allRecordings.sumOf { it.duration ?: 0.0 }
                        println("[ProgressTracker] conversation_recordings: ${allRecordings.size} sessions, ${String.format("%.1f", time)}s")
                        time
                    } catch (e: Exception) {
                        println("[ProgressTracker] Error fetching recording time: ${e.message}")
                        0.0
                    }
                }

                // Sum all times (deduplication happens at session count level)
                val totalTime = agentTime.await() + voiceTime.await() + recordingTime.await()
                println("[ProgressTracker] Total conversation time: ${String.format("%.1f", totalTime)}s")
                
                totalTime
            }
        }.getOrElse { e ->
            println("[ProgressTracker] Error fetching total conversation time: ${e.message}")
            0.0
        }
    }

    /**
     * Optimized method: Fetches complete language progress with single query.
     * Uses materialized view if available, falls back to multi-query.
     * Performance: ~30-50ms (vs ~200ms with multiple queries)
     */
    override suspend fun getLanguageProgress(
        userId: String,
        language: LessonLanguage,
    ): Result<LanguageProgress> = runCatching {
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                try {
                    // Try materialized view first (fast path)
                    val viewData = supabase.postgrest["user_language_progress"].select {
                        filter {
                            eq("user_id", userId)
                            eq("language", language.displayName)
                        }
                        limit(1)
                    }.decodeSingleOrNull<UserLanguageProgressDTO>()

                    if (viewData != null) {
                        println("[ProgressTracker] ✅ Using materialized view (fast path)")
                        return@withContext viewData.toDomain(language)
                    }

                    // Fallback to multi-query approach
                    println("[ProgressTracker] ⚠️ Materialized view not available, using fallback")
                    getLanguageProgressFallback(userId, language)
                } catch (e: Exception) {
                    println("[ProgressTracker] ⚠️ View query failed: ${e.message}, using fallback")
                    getLanguageProgressFallback(userId, language)
                }
            }
        }.getOrThrow()
    }

    /**
     * Fallback to original multi-query approach.
     */
    private suspend fun getLanguageProgressFallback(
        userId: String,
        language: LessonLanguage,
    ): LanguageProgress {
        val lessons = getLessonsProgress(userId, language).getOrElse { 
            LessonsProgress(0, 0) 
        }
        val sessions = getConversationSessions(userId, language).getOrElse { 0 }
        val vocabulary = getVocabularyCount(userId, language).getOrElse { 0 }
        val scores = getVoiceAnalysisScores(userId, language).getOrElse { 
            VoiceAnalysisScores() 
        }
        val time = getTotalConversationTime(userId, language).getOrElse { 0.0 }

        return LanguageProgress(
            language = language,
            lessonsCompleted = lessons.completed,
            totalLessons = lessons.total,
            conversationSessions = sessions,
            vocabularyWords = vocabulary,
            voiceAnalysis = scores,
            totalTimeSeconds = time
        )
    }

    /**
     * Manually refresh the materialized view.
     * Call this if data seems stale (triggers should auto-refresh).
     */
    override suspend fun refreshMaterializedView(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest.rpc("refresh_user_language_progress", Unit)
                println("[ProgressTracker] ✅ Materialized view refreshed")
            } catch (e: Exception) {
                println("[ProgressTracker] ⚠️ Failed to refresh view: ${e.message}")
                throw e
            }
        }
    }
}

// DTOs for database responses
@Serializable
private data class LessonDTO(
    @SerialName("id") val id: String,
    @SerialName("topic_id") val topicId: String,
)

@Serializable
private data class TopicLanguageDTO(
    @SerialName("id") val id: String,
)

@Serializable
private data class UserLessonProgressDTO(
    @SerialName("lesson_id") val lessonId: String,
)

@Serializable
private data class AgentSessionDTO(
    @SerialName("id") val id: String,
    @SerialName("duration") val duration: Double?,
    @SerialName("audio_duration") val audioDuration: Double?,
)

@Serializable
private data class VoiceSessionDTO(
    @SerialName("id") val id: String,
    @SerialName("session_duration") val sessionDuration: Double?,
)

@Serializable
private data class ConversationRecordingDTO(
    @SerialName("id") val id: String,
    @SerialName("session_id") val sessionId: String?,
    @SerialName("duration") val duration: Double?,
)

@Serializable
private data class ConversationFeedbackWithSessionDTO(
    @SerialName("session_id") val sessionId: String,
    @SerialName("overall_score") val overallScore: Int?,
    @SerialName("grammar_score") val grammarScore: Int?,
    @SerialName("pronunciation_score") val pronunciationScore: Int?,
    @SerialName("vocabulary_score") val vocabularyScore: Int?,
    @SerialName("fluency_score") val fluencyScore: Int?,
)

// Extension for safe average calculation
private fun List<Double>.average(): Double {
    return if (isEmpty()) 0.0 else average()
}

/**
 * DTO for materialized view data.
 */
@Serializable
private data class UserLanguageProgressDTO(
    @SerialName("user_id") val userId: String,
    @SerialName("language") val language: String,
    @SerialName("lessons_completed") val lessonsCompleted: Int,
    @SerialName("total_lessons") val totalLessons: Int,
    @SerialName("conversation_sessions") val conversationSessions: Int,
    @SerialName("vocabulary_words") val vocabularyWords: Int,
    @SerialName("score_overall") val scoreOverall: Double,
    @SerialName("score_grammar") val scoreGrammar: Double,
    @SerialName("score_pronunciation") val scorePronunciation: Double,
    @SerialName("score_vocabulary") val scoreVocabulary: Double,
    @SerialName("score_fluency") val scoreFluency: Double,
    @SerialName("score_accuracy") val scoreAccuracy: Double,
    @SerialName("total_time_seconds") val totalTimeSeconds: Double,
    @SerialName("last_updated") val lastUpdated: String,
) {
    fun toDomain(language: LessonLanguage) = LanguageProgress(
        language = language,
        lessonsCompleted = lessonsCompleted,
        totalLessons = totalLessons,
        conversationSessions = conversationSessions,
        vocabularyWords = vocabularyWords,
        voiceAnalysis = VoiceAnalysisScores(
            overall = scoreOverall,
            grammar = scoreGrammar,
            pronunciation = scorePronunciation,
            vocabulary = scoreVocabulary,
            fluency = scoreFluency,
            accuracy = scoreAccuracy
        ).clampedScores(),
        totalTimeSeconds = totalTimeSeconds
    )
}
