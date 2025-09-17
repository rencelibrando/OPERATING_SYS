package org.example.project.domain.model

/**
 * Represents a speaking exercise in the WordBridge application
 * 
 * @Variable id Unique identifier for the exercise
 * @Variable title Display title of the exercise
 * @Variable description Brief description of the exercise
 * @Variable type Type of speaking exercise
 * @Variable difficulty Difficulty level (Beginner, Intermediate, Advanced)
 * @Variable duration Estimated duration in minutes
 * @Variable category Category of the exercise
 * @Variable icon Icon identifier for the exercise
 * @Variable isAvailable Whether this exercise is available to the user
 * @Variable completionRate User's completion rate for this exercise (0-100)
 * @Variable lastAttempt Timestamp of last attempt (null if never attempted)
 */
data class SpeakingExercise(
    val id: String,
    val title: String,
    val description: String,
    val type: SpeakingExerciseType,
    val difficulty: String,
    val duration: Int, // in minutes
    val category: String,
    val icon: String,
    val isAvailable: Boolean = true,
    val completionRate: Int = 0, // 0-100 percentage
    val lastAttempt: Long? = null
) {
    companion object {
        /**
         * Creates sample speaking exercises
         * Returns empty list for template/clean UI
         */
        fun getSampleExercises(): List<SpeakingExercise> = emptyList()
        
        /**
         * Creates demonstration speaking exercises for testing purposes
         */
        fun getDemoExercises(): List<SpeakingExercise> = listOf(
            SpeakingExercise(
                id = "pronunciation_basics",
                title = "Pronunciation Basics",
                description = "Master fundamental English sounds and phonetics",
                type = SpeakingExerciseType.PRONUNCIATION,
                difficulty = "Beginner",
                duration = 15,
                category = "Fundamentals",
                icon = "🔤",
                completionRate = 75,
                lastAttempt = System.currentTimeMillis() - 86400000 // 1 day ago
            ),
            SpeakingExercise(
                id = "conversation_starters",
                title = "Conversation Starters",
                description = "Practice common phrases for starting conversations",
                type = SpeakingExerciseType.CONVERSATION,
                difficulty = "Intermediate",
                duration = 20,
                category = "Social Skills",
                icon = "💬",
                completionRate = 45,
                lastAttempt = System.currentTimeMillis() - 172800000 // 2 days ago
            ),
            SpeakingExercise(
                id = "accent_training",
                title = "Accent Training",
                description = "Improve your accent with native speaker guidance",
                type = SpeakingExerciseType.ACCENT_TRAINING,
                difficulty = "Advanced",
                duration = 25,
                category = "Pronunciation",
                icon = "🎯",
                completionRate = 20
            ),
            SpeakingExercise(
                id = "storytelling",
                title = "Storytelling Practice",
                description = "Learn to tell engaging stories in English",
                type = SpeakingExerciseType.STORYTELLING,
                difficulty = "Advanced",
                duration = 30,
                category = "Fluency",
                icon = "📖",
                completionRate = 0
            )
        )
    }
}

/**
 * Represents different types of speaking exercises
 */
enum class SpeakingExerciseType(val displayName: String) {
    PRONUNCIATION("Pronunciation"),
    CONVERSATION("Conversation"),
    ACCENT_TRAINING("Accent Training"),
    STORYTELLING("Storytelling"),
    READING_ALOUD("Reading Aloud"),
    SHADOWING("Shadowing")
}

/**
 * Represents a speaking practice session
 * 
 * @Variable id Unique identifier for the session
 * @Variable exerciseId ID of the associated exercise
 * @Variable startTime When the session started
 * @Variable endTime When the session ended (null if ongoing)
 * @Variable accuracyScore Accuracy score (0-100)
 * @Variable fluencyScore Fluency score (0-100)
 * @Variable pronunciationScore Pronunciation score (0-100)
 * @Variable overallScore Overall score (0-100)
 * @Variable feedback AI-generated feedback
 * @Variable recordingPath Path to the audio recording (if available)
 */
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
    val recordingPath: String?
) {
    companion object {
        /**
         * Creates sample speaking sessions
         * Returns empty list for template/clean UI
         */
        fun getSampleSessions(): List<SpeakingSession> = emptyList()
        
        /**
         * Creates demonstration speaking sessions for testing purposes
         */
        fun getDemoSessions(): List<SpeakingSession> = listOf(
            SpeakingSession(
                id = "session_1",
                exerciseId = "pronunciation_basics",
                startTime = System.currentTimeMillis() - 86400000,
                endTime = System.currentTimeMillis() - 86400000 + 900000, // 15 minutes later
                accuracyScore = 85,
                fluencyScore = 78,
                pronunciationScore = 82,
                overallScore = 82,
                feedback = "Great improvement in vowel sounds! Focus on consonant clusters next.",
                recordingPath = "/recordings/session_1.wav"
            ),
            SpeakingSession(
                id = "session_2",
                exerciseId = "conversation_starters",
                startTime = System.currentTimeMillis() - 172800000,
                endTime = System.currentTimeMillis() - 172800000 + 1200000, // 20 minutes later
                accuracyScore = 75,
                fluencyScore = 70,
                pronunciationScore = 73,
                overallScore = 73,
                feedback = "Good use of intonation. Work on reducing hesitation pauses.",
                recordingPath = "/recordings/session_2.wav"
            )
        )
    }
}

/**
 * Represents speaking practice statistics
 * 
 * @Variable totalSessions Total number of completed sessions
 * @Variable totalMinutes Total minutes of practice
 * @Variable averageAccuracy Average accuracy score across all sessions
 * @Variable averageFluency Average fluency score across all sessions
 * @Variable averagePronunciation Average pronunciation score across all sessions
 * @Variable currentStreak Current practice streak in days
 * @Variable longestStreak Longest practice streak in days
 * @Variable exercisesCompleted Number of exercises completed
 */
data class SpeakingStats(
    val totalSessions: Int,
    val totalMinutes: Int,
    val averageAccuracy: Int,
    val averageFluency: Int,
    val averagePronunciation: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val exercisesCompleted: Int
) {
    companion object {
        fun getSampleStats(): SpeakingStats = SpeakingStats(
            totalSessions = 0,
            totalMinutes = 0,
            averageAccuracy = 0,
            averageFluency = 0,
            averagePronunciation = 0,
            currentStreak = 0,
            longestStreak = 0,
            exercisesCompleted = 0
        )
        
        fun getDemoStats(): SpeakingStats = SpeakingStats(
            totalSessions = 15,
            totalMinutes = 320,
            averageAccuracy = 82,
            averageFluency = 78,
            averagePronunciation = 80,
            currentStreak = 5,
            longestStreak = 12,
            exercisesCompleted = 8
        )
    }
}

/**
 * Represents a speaking feature for the empty state
 */
data class SpeakingFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String
) {
    companion object {
        /**
         * Creates speaking features for the empty state
         */
        fun getSpeakingFeatures(): List<SpeakingFeature> = listOf(
            SpeakingFeature(
                id = "ai_feedback",
                title = "AI-Powered Feedback",
                description = "Get instant, detailed feedback on your pronunciation, fluency, and accuracy.",
                icon = "🤖",
                color = "#8B5CF6"
            ),
            SpeakingFeature(
                id = "pronunciation_analysis",
                title = "Pronunciation Analysis",
                description = "Advanced speech recognition analyzes your pronunciation in real-time.",
                icon = "🎙️",
                color = "#10B981"
            ),
            SpeakingFeature(
                id = "conversation_practice",
                title = "Conversation Practice",
                description = "Practice real-world conversations with AI-powered conversation partners.",
                icon = "💬",
                color = "#F59E0B"
            ),
            SpeakingFeature(
                id = "progress_tracking",
                title = "Progress Tracking",
                description = "Track your speaking improvement with detailed analytics and scores.",
                icon = "📊",
                color = "#3B82F6"
            ),
            SpeakingFeature(
                id = "accent_coaching",
                title = "Accent Coaching",
                description = "Personalized accent training with native speaker audio examples.",
                icon = "🎯",
                color = "#EF4444"
            ),
            SpeakingFeature(
                id = "recording_playback",
                title = "Recording & Playback",
                description = "Record your sessions and compare with native speaker examples.",
                icon = "🔊",
                color = "#8B5CF6"
            )
        )
    }
}

/**
 * Represents speaking practice filter options
 */
enum class SpeakingFilter(val displayName: String) {
    ALL("All"),
    PRONUNCIATION("Pronunciation"),
    CONVERSATION("Conversation"),
    ACCENT("Accent Training"),
    FLUENCY("Fluency")
}
