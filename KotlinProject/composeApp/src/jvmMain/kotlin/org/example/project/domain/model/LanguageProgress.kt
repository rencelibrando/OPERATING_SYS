package org.example.project.domain.model

import kotlinx.serialization.Serializable

/**
 * Comprehensive language learning progress analytics.
 * Aggregates data from lessons, conversations, vocabulary, and voice analysis.
 */
@Serializable
data class LanguageProgress(
    val language: LessonLanguage,
    val lessonsCompleted: Int = 0,
    val totalLessons: Int = 0,
    val conversationSessions: Int = 0,
    val vocabularyWords: Int = 0,
    val voiceAnalysis: VoiceAnalysisScores = VoiceAnalysisScores(),
    val totalTimeSeconds: Double = 0.0,
) {
    val lessonsProgressPercentage: Int
        get() =
            if (totalLessons > 0) {
                ((lessonsCompleted.toFloat() / totalLessons.toFloat()) * 100).toInt()
            } else {
                0
            }

    val hasData: Boolean
        get() =
            lessonsCompleted > 0 || conversationSessions > 0 ||
                vocabularyWords > 0 || totalTimeSeconds > 0

    val formattedTime: String
        get() = formatTime(totalTimeSeconds)

    companion object {
        fun empty(language: LessonLanguage) = LanguageProgress(language = language)

        private fun formatTime(seconds: Double): String {
            return when {
                seconds < 1 -> "0s"
                seconds < 60 -> "${seconds.toInt()}s"
                seconds < 3600 -> {
                    val mins = (seconds / 60).toInt()
                    val secs = (seconds % 60).toInt()
                    if (secs > 0) "${mins}m ${secs}s" else "${mins}m"
                }
                seconds < 86400 -> {
                    val hours = (seconds / 3600).toInt()
                    val mins = ((seconds % 3600) / 60).toInt()
                    if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
                }
                else -> {
                    val days = (seconds / 86400).toInt()
                    val hours = ((seconds % 86400) / 3600).toInt()
                    if (hours > 0) "${days}d ${hours}h" else "${days}d"
                }
            }
        }
    }
}

/**
 * Voice and speaking analysis scores.
 * Aggregated from conversation_feedback and user_speaking_progress tables.
 */
@Serializable
data class VoiceAnalysisScores(
    val overall: Double = 0.0,
    val grammar: Double = 0.0,
    val pronunciation: Double = 0.0,
    val vocabulary: Double = 0.0,
    val fluency: Double = 0.0,
    val accuracy: Double = 0.0,
) {
    val hasScores: Boolean
        get() =
            overall > 0 || grammar > 0 || pronunciation > 0 ||
                vocabulary > 0 || fluency > 0 || accuracy > 0

    val averageScore: Double
        get() {
            val scores =
                listOfNotNull(
                    overall.takeIf { it > 0 },
                    grammar.takeIf { it > 0 },
                    pronunciation.takeIf { it > 0 },
                    vocabulary.takeIf { it > 0 },
                    fluency.takeIf { it > 0 },
                    accuracy.takeIf { it > 0 },
                )
            return if (scores.isNotEmpty()) {
                scores.average()
            } else {
                0.0
            }
        }

    fun clampedScores() =
        VoiceAnalysisScores(
            overall = overall.coerceIn(0.0, 100.0),
            grammar = grammar.coerceIn(0.0, 100.0),
            pronunciation = pronunciation.coerceIn(0.0, 100.0),
            vocabulary = vocabulary.coerceIn(0.0, 100.0),
            fluency = fluency.coerceIn(0.0, 100.0),
            accuracy = accuracy.coerceIn(0.0, 100.0),
        )
}

/**
 * UI state for progress tracker screen.
 */
sealed class ProgressTrackerState {
    data object Loading : ProgressTrackerState()

    data class Success(val progress: LanguageProgress) : ProgressTrackerState()

    data class Error(val message: String) : ProgressTrackerState()

    data object Empty : ProgressTrackerState()
}
