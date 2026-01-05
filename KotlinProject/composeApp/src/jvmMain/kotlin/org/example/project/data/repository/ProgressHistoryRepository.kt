package org.example.project.data.repository

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.project.core.api.SupabaseApiHelper
import org.example.project.core.config.SupabaseConfig
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.ProgressHistorySnapshot
import org.example.project.domain.model.VoiceAnalysisScores
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Repository for historical progress data.
 * Fetches daily snapshots for trend analysis.
 */
class ProgressHistoryRepository {
    private val supabase = SupabaseConfig.client

    /**
     * Fetches progress history for a time range.
     */
    suspend fun getProgressHistory(
        userId: String,
        language: LessonLanguage,
        days: Int = 30
    ): Result<List<ProgressHistorySnapshot>> = runCatching {
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                val startDate = LocalDate.now().minusDays(days.toLong())
                val formattedDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

                val response = supabase.postgrest["user_progress_history"].select {
                    filter {
                        eq("user_id", userId)
                        eq("language", language.displayName)
                        gte("snapshot_date", formattedDate)
                    }
                    order("snapshot_date", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }

                val dtos = response.decodeList<ProgressHistoryDTO>()
                
                println("[ProgressHistory] ✅ Fetched ${dtos.size} snapshots for ${language.displayName} (last $days days)")
                
                dtos.map { it.toDomain(language) }
            }
        }.getOrThrow()
    }

    /**
     * Captures current progress as a snapshot.
     * Called manually or via a scheduled job.
     */
    suspend fun captureSnapshot(
        userId: String,
        language: LessonLanguage,
        date: LocalDate = LocalDate.now()
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            try {
                val formattedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // Call database function to capture snapshot
                val params = mapOf(
                    "p_user_id" to userId,
                    "p_language" to language.displayName,
                    "p_snapshot_date" to formattedDate
                )
                supabase.postgrest.rpc("capture_progress_snapshot", params)
                
                println("[ProgressHistory]  Snapshot captured for ${language.displayName} on $formattedDate")
            } catch (e: Exception) {
                println("[ProgressHistory] ⚠ Failed to capture snapshot: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Gets pre-filtered views for common time ranges.
     */
    suspend fun getLast7Days(
        userId: String,
        language: LessonLanguage
    ): Result<List<ProgressHistorySnapshot>> = getProgressHistory(userId, language, 7)

    suspend fun getLast30Days(
        userId: String,
        language: LessonLanguage
    ): Result<List<ProgressHistorySnapshot>> = getProgressHistory(userId, language, 30)

    suspend fun getLast90Days(
        userId: String,
        language: LessonLanguage
    ): Result<List<ProgressHistorySnapshot>> = getProgressHistory(userId, language, 90)

    /**
     * Gets aggregated stats for a time period.
     */
    suspend fun getHistoryStats(
        userId: String,
        language: LessonLanguage,
        days: Int = 30
    ): Result<HistoryStats> = runCatching {
        val history = getProgressHistory(userId, language, days).getOrThrow()
        
        if (history.isEmpty()) {
            return@runCatching HistoryStats.empty()
        }

        val first = history.first()
        val last = history.last()

        HistoryStats(
            totalDays = history.size,
            lessonsGrowth = last.lessonsCompleted - first.lessonsCompleted,
            sessionsGrowth = last.conversationSessions - first.conversationSessions,
            vocabularyGrowth = last.vocabularyWords - first.vocabularyWords,
            timeGrowth = last.totalTimeSeconds - first.totalTimeSeconds,
            avgScoreImprovement = last.voiceAnalysis.averageScore - first.voiceAnalysis.averageScore,
            mostActiveDay = history.maxByOrNull { it.conversationSessions }?.date ?: first.date,
            currentStreak = calculateStreak(history)
        )
    }

    private fun calculateStreak(history: List<ProgressHistorySnapshot>): Int {
        if (history.isEmpty()) return 0
        
        var streak = 0
        var previousDate = LocalDate.now().plusDays(1) // Start from tomorrow
        
        // Iterate from most recent to oldest
        for (snapshot in history.reversed()) {
            val expectedDate = previousDate.minusDays(1)
            if (snapshot.date == expectedDate) {
                streak++
                previousDate = snapshot.date
            } else {
                break
            }
        }
        
        return streak
    }
}

/**
 * DTO for progress history records.
 */
@Serializable
private data class ProgressHistoryDTO(
    @SerialName("snapshot_date") val snapshotDate: String,
    @SerialName("lessons_completed") val lessonsCompleted: Int,
    @SerialName("total_lessons") val totalLessons: Int,
    @SerialName("conversation_sessions") val conversationSessions: Int,
    @SerialName("vocabulary_words") val vocabularyWords: Int,
    @SerialName("total_time_seconds") val totalTimeSeconds: Double,
    @SerialName("score_overall") val scoreOverall: Double,
    @SerialName("score_grammar") val scoreGrammar: Double,
    @SerialName("score_pronunciation") val scorePronunciation: Double,
    @SerialName("score_vocabulary") val scoreVocabulary: Double,
    @SerialName("score_fluency") val scoreFluency: Double,
    @SerialName("score_accuracy") val scoreAccuracy: Double,
) {
    fun toDomain(language: LessonLanguage) = ProgressHistorySnapshot(
        date = LocalDate.parse(snapshotDate),
        language = language,
        lessonsCompleted = lessonsCompleted,
        totalLessons = totalLessons,
        conversationSessions = conversationSessions,
        vocabularyWords = vocabularyWords,
        totalTimeSeconds = totalTimeSeconds,
        voiceAnalysis = VoiceAnalysisScores(
            overall = scoreOverall,
            grammar = scoreGrammar,
            pronunciation = scorePronunciation,
            vocabulary = scoreVocabulary,
            fluency = scoreFluency,
            accuracy = scoreAccuracy
        ).clampedScores()
    )
}

/**
 * Aggregated statistics for a time period.
 */
data class HistoryStats(
    val totalDays: Int,
    val lessonsGrowth: Int,
    val sessionsGrowth: Int,
    val vocabularyGrowth: Int,
    val timeGrowth: Double,
    val avgScoreImprovement: Double,
    val mostActiveDay: LocalDate,
    val currentStreak: Int
) {
    companion object {
        fun empty() = HistoryStats(
            totalDays = 0,
            lessonsGrowth = 0,
            sessionsGrowth = 0,
            vocabularyGrowth = 0,
            timeGrowth = 0.0,
            avgScoreImprovement = 0.0,
            mostActiveDay = LocalDate.now(),
            currentStreak = 0
        )
    }
}
