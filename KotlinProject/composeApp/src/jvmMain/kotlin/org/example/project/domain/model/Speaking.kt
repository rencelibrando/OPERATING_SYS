package org.example.project.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

enum class PracticeLanguage(
    val displayName: String,
    val flag: String,
    val description: String,
) {
    ENGLISH(
        displayName = "English",
        flag = "🇬🇧",
        description = "Practice English pronunciation",
    ),
    FRENCH(
        displayName = "French",
        flag = "🇫🇷",
        description = "Practice French pronunciation",
    ),
    GERMAN(
        displayName = "German",
        flag = "🇩🇪",
        description = "Practice German pronunciation",
    ),
    HANGEUL(
        displayName = "Korean (Hangeul)",
        flag = "🇰🇷",
        description = "Practice Korean pronunciation",
    ),
    MANDARIN(
        displayName = "Mandarin Chinese",
        flag = "🇨🇳",
        description = "Practice Mandarin pronunciation with tones",
    ),
    SPANISH(
        displayName = "Spanish",
        flag = "🇪🇸",
        description = "Practice Spanish pronunciation",
    ),
}

data class PracticeFeedback(
    val overallScore: Int,
    val pronunciationScore: Int,
    val clarityScore: Int,
    val fluencyScore: Int,
    val messages: List<String>,
    val suggestions: List<String>,
)

data class SpeakingFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String,
)

data class SpeakingScenario(
    val id: String,
    val title: String,
    val language: String,
    val difficultyLevel: String,
    val scenarioType: String,
    val prompts: List<String>,
    val description: String = "",
)

@Serializable
data class ConversationRecording(
    @SerialName("id")
    val id: String,
    @SerialName("sessionId")
    val sessionId: String,
    @SerialName("userId")
    val userId: String,
    @SerialName("language")
    val language: String,
    @SerialName("audioUrl")
    val audioUrl: String?,
    @SerialName("transcript")
    val transcript: String,
    @SerialName("turnCount")
    val turnCount: Int,
    @SerialName("duration")
    val duration: Float,
    @SerialName("createdAt")
    val createdAt: String,
)

@Serializable
data class ConversationSession(
    @SerialName("sessionId")
    val sessionId: String,
    @SerialName("userId")
    val userId: String,
    @SerialName("language")
    val language: String,
    @SerialName("level")
    val level: String,
    @SerialName("scenario")
    val scenario: String,
    @SerialName("transcript")
    val transcript: String,
    @SerialName("audioUrl")
    val audioUrl: String? = null,
    @SerialName("turnCount")
    val turnCount: Int,
    @SerialName("duration")
    val duration: Float,
    @SerialName("createdAt")
    val createdAt: String,
    @SerialName("feedback")
    val feedback: kotlinx.serialization.json.JsonObject? = null
)

data class SpeakingExercise(
    val id: String,
    val title: String,
    val description: String,
    val type: SpeakingExerciseType,
    val difficulty: String,
    val duration: Int,
    val category: String,
    val icon: String,
    val isAvailable: Boolean = true,
    val completionRate: Int = 0,
    val lastAttempt: Long? = null,
) {
    companion object {
        /** Returns an empty list for the initial state. */
        fun getSampleExercises(): List<SpeakingExercise> = emptyList()
    }
}

enum class SpeakingExerciseType(val displayName: String) {
    PRONUNCIATION("Pronunciation"),
    CONVERSATION("Conversation"),
    ACCENT_TRAINING("Accent Training"),
    STORYTELLING("Storytelling"),
    READING_ALOUD("Reading Aloud"),
    SHADOWING("Shadowing"),
}

data class SpeakingSession(
    val id: String,
    val exerciseId: String,
    val startTime: Long,
    val endTime: Long?,
    val accuracyScore: Int?,
    val fluencyScore: Int?,
    val pronunciationScore: Int?,
    val overallScore: Int?,
    val feedback: String?,
    val recordingPath: String?,
) {
    companion object {
        /** Returns an empty list for the initial state. */
        fun getSampleSessions(): List<SpeakingSession> = emptyList()
    }
}

data class SpeakingStats(
    val totalSessions: Int,
    val totalMinutes: Int,
    val averageAccuracy: Int,
    val averageFluency: Int,
    val averagePronunciation: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val exercisesCompleted: Int,
) {
    companion object {
        /** Returns default stats for initial state. */
        fun getSampleStats(): SpeakingStats =
            SpeakingStats(
                totalSessions = 0,
                totalMinutes = 0,
                averageAccuracy = 0,
                averageFluency = 0,
                averagePronunciation = 0,
                currentStreak = 0,
                longestStreak = 0,
                exercisesCompleted = 0,
            )
    }
}


enum class SpeakingFilter(val displayName: String) {
    ALL("All"),
    PRONUNCIATION("Pronunciation"),
    CONVERSATION("Conversation"),
    ACCENT("Accent Training"),
    FLUENCY("Fluency"),
}
