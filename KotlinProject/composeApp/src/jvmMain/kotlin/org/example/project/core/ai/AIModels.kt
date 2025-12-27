package org.example.project.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class AIProvider {
    @SerialName("gemini")
    GEMINI,
}

@Serializable
enum class MessageRole {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,

    @SerialName("system")
    SYSTEM,
}

@Serializable
data class AIChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long? = null,
)

@Serializable
data class AIUserContext(
    @SerialName("user_id")
    val userId: String,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("native_language")
    val nativeLanguage: String? = null,
    @SerialName("target_languages")
    val targetLanguages: List<String> = emptyList(),
    @SerialName("current_level")
    val currentLevel: String? = null,
    @SerialName("primary_goal")
    val primaryGoal: String? = null,
    @SerialName("learning_style")
    val learningStyle: String? = null,
    @SerialName("focus_areas")
    val focusAreas: List<String> = emptyList(),
    val motivations: List<String> = emptyList(),
    @SerialName("personality_preferences")
    val personalityPreferences: JsonObject? = null,
    val interests: List<String> = emptyList(),
    @SerialName("ai_profile")
    val aiProfile: JsonObject? = null,
    @SerialName("learning_progress")
    val learningProgress: AILearningProgress? = null,
    @SerialName("skill_progress")
    val skillProgress: List<AISkillProgress> = emptyList(),
    @SerialName("vocabulary_stats")
    val vocabularyStats: AIVocabularyStats? = null,
    @SerialName("lesson_progress")
    val lessonProgress: AILessonProgress? = null,
    @SerialName("chat_history")
    val chatHistory: AIChatHistory? = null,
    val achievements: List<String> = emptyList(),
    @SerialName("user_settings")
    val userSettings: AIUserSettings? = null,
)

@Serializable
data class AILearningProgress(
    @SerialName("overall_level")
    val overallLevel: Int,
    @SerialName("xp_points")
    val xpPoints: Int,
    @SerialName("weekly_xp")
    val weeklyXp: Int,
    @SerialName("streak_days")
    val streakDays: Int,
    @SerialName("longest_streak")
    val longestStreak: Int,
    @SerialName("total_study_time")
    val totalStudyTime: Int,
    @SerialName("weekly_study_time")
    val weeklyStudyTime: Int,
)

@Serializable
data class AISkillProgress(
    @SerialName("skill_area")
    val skillArea: String,
    val level: Int,
    @SerialName("xp_points")
    val xpPoints: Int,
    @SerialName("accuracy_percentage")
    val accuracyPercentage: Double,
    @SerialName("time_spent")
    val timeSpent: Int,
    @SerialName("exercises_completed")
    val exercisesCompleted: Int,
)

@Serializable
data class AIVocabularyStats(
    @SerialName("total_words")
    val totalWords: Int,
    @SerialName("new_words")
    val newWords: Int,
    @SerialName("learning_words")
    val learningWords: Int,
    @SerialName("reviewing_words")
    val reviewingWords: Int,
    @SerialName("mastered_words")
    val masteredWords: Int,
    @SerialName("average_correct_rate")
    val averageCorrectRate: Double,
    @SerialName("words")
    val words: List<AIVocabularyWord> = emptyList(),
)

@Serializable
data class AIVocabularyWord(
    @SerialName("word")
    val word: String,
    @SerialName("definition")
    val definition: String,
    @SerialName("pronunciation")
    val pronunciation: String? = null,
    @SerialName("example_sentence")
    val exampleSentence: String? = null,
    @SerialName("difficulty_level")
    val difficultyLevel: String,
    @SerialName("category")
    val category: String,
    @SerialName("status")
    val status: String,
    @SerialName("review_count")
    val reviewCount: Int,
    @SerialName("correct_count")
    val correctCount: Int,
    @SerialName("last_reviewed")
    val lastReviewed: String? = null,
    @SerialName("next_review")
    val nextReview: String? = null,
)

@Serializable
data class AILessonProgress(
    @SerialName("total_lessons")
    val totalLessons: Int,
    @SerialName("completed_lessons")
    val completedLessons: Int,
    @SerialName("in_progress_lessons")
    val inProgressLessons: Int,
    @SerialName("average_score")
    val averageScore: Double,
    @SerialName("total_time_spent")
    val totalTimeSpent: Int,
)

@Serializable
data class AIChatHistory(
    @SerialName("total_sessions")
    val totalSessions: Int,
    @SerialName("total_messages")
    val totalMessages: Int,
    @SerialName("total_duration")
    val totalDuration: Int,
)

@Serializable
data class AIUserSettings(
    @SerialName("ai_preferences")
    val aiPreferences: JsonObject? = null,
)

@Serializable
data class AIChatRequest(
    val message: String,
    @SerialName("user_context")
    val userContext: AIUserContext,
    @SerialName("conversation_history")
    val conversationHistory: List<AIChatMessage> = emptyList(),
    val provider: AIProvider = AIProvider.GEMINI,
    @SerialName("bot_id")
    val botId: String? = null,
    val temperature: Float = 0.7f,
    @SerialName("max_tokens")
    val maxTokens: Int = 1000,
)

@Serializable
data class AIChatResponse(
    val message: String,
    val provider: AIProvider,
    @SerialName("tokens_used")
    val tokensUsed: Int? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class AIHealthResponse(
    val status: String,
    val version: String,
    val providers: Map<String, Boolean>,
)

@Serializable
data class SaveHistoryRequest(
    @SerialName("session_id")
    val sessionId: String,
    val messages: List<AIChatMessage>,
    val compress: Boolean = true,
)

@Serializable
data class SaveHistoryResponse(
    val success: Boolean,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("message_count")
    val messageCount: Int,
    @SerialName("original_size")
    val originalSize: Int,
    @SerialName("compressed_size")
    val compressedSize: Int,
    @SerialName("compression_ratio")
    val compressionRatio: Double,
    @SerialName("compression_type")
    val compressionType: String,
)

@Serializable
data class LoadHistoryRequest(
    @SerialName("session_id")
    val sessionId: String,
)

@Serializable
data class LoadHistoryResponse(
    val success: Boolean,
    @SerialName("session_id")
    val sessionId: String,
    val messages: List<AIChatMessage>,
    @SerialName("message_count")
    val messageCount: Int,
    @SerialName("original_size")
    val originalSize: Int,
    @SerialName("compressed_size")
    val compressedSize: Int,
    @SerialName("compression_ratio")
    val compressionRatio: Double,
)
