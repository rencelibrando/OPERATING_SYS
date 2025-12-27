package org.example.project.domain.model

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
        /** Returns an empty list for initial state. */
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
        /** Returns an empty list for initial state. */
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
