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

data class SpeakingFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String,
) {
    companion object {
        fun getSpeakingFeatures(): List<SpeakingFeature> =
            listOf(
                SpeakingFeature(
                    id = "ai_feedback",
                    title = "AI-Powered Feedback",
                    description = "Get instant, detailed feedback on your pronunciation, fluency, and accuracy.",
                    icon = "🤖",
                    color = "#8B5CF6",
                ),
                SpeakingFeature(
                    id = "pronunciation_analysis",
                    title = "Pronunciation Analysis",
                    description = "Advanced speech recognition analyzes your pronunciation in real-time.",
                    icon = "🎙️",
                    color = "#10B981",
                ),
                SpeakingFeature(
                    id = "conversation_practice",
                    title = "Conversation Practice",
                    description = "Practice real-world conversations with AI-powered conversation partners.",
                    icon = "💬",
                    color = "#F59E0B",
                ),
                SpeakingFeature(
                    id = "progress_tracking",
                    title = "Progress Tracking",
                    description = "Track your speaking improvement with detailed analytics and scores.",
                    icon = "📊",
                    color = "#3B82F6",
                ),
                SpeakingFeature(
                    id = "accent_coaching",
                    title = "Accent Coaching",
                    description = "Personalized accent training with native speaker audio examples.",
                    icon = "🎯",
                    color = "#EF4444",
                ),
                SpeakingFeature(
                    id = "recording_playback",
                    title = "Recording & Playback",
                    description = "Record your sessions and compare with native speaker examples.",
                    icon = "🔊",
                    color = "#8B5CF6",
                ),
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
