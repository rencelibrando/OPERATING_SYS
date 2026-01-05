package org.example.project.core.ai

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.example.project.core.config.SupabaseConfig

class UserDataService {
    private val supabase = SupabaseConfig.client

    suspend fun fetchCompleteUserData(userId: String): Result<ComprehensiveUserData> =
        withContext(Dispatchers.IO) {
            runCatching {
                println("Fetching comprehensive user data for: $userId")

                val userProfile = fetchUserProfile(userId)

                val firstName = userProfile?.personalInfo?.get("firstName")?.toString()?.trim('"')
                val lastName = userProfile?.personalInfo?.get("lastName")?.toString()?.trim('"')

                ComprehensiveUserData(
                    userId = userId,
                    firstName = firstName,
                    lastName = lastName,
                    userProfile = userProfile,
                    learningProgress = fetchLearningProgress(userId),
                    skillProgress = fetchSkillProgress(userId),
                    vocabularyStats = fetchVocabularyStats(userId),
                    lessonProgress = fetchLessonProgress(userId),
                    chatHistory = fetchRecentChatSummary(userId),
                    achievements = fetchAchievements(userId),
                    userSettings = fetchUserSettings(userId),
                )
            }
        }

    private suspend fun fetchUserProfile(userId: String): UserProfileData? {
        return try {
            val response =
                supabase.postgrest["user_profiles"].select {
                    filter {
                        eq("id", userId)
                    }
                    limit(1)
                }

            response.decodeSingle<UserProfileRow>().let {
                UserProfileData(
                    personalInfo = it.personal_info,
                    learningProfile = it.learning_profile,
                    accountInfo = it.account_info,
                    profileStats = it.profile_stats,
                )
            }
        } catch (e: Exception) {
            println("Failed to fetch user profile: ${e.message}")
            null
        }
    }

    private suspend fun fetchLearningProgress(userId: String): LearningProgressData? {
        return try {
            val response =
                supabase.postgrest["learning_progress"].select {
                    filter {
                        eq("user_id", userId)
                    }
                    limit(1)
                }

            response.decodeSingle<LearningProgressRow>().let {
                LearningProgressData(
                    overallLevel = it.overall_level,
                    xpPoints = it.xp_points,
                    weeklyXp = it.weekly_xp,
                    monthlyXp = it.monthly_xp,
                    streakDays = it.streak_days,
                    longestStreak = it.longest_streak,
                    totalStudyTime = it.total_study_time,
                    weeklyStudyTime = it.weekly_study_time,
                )
            }
        } catch (e: Exception) {
            println("Failed to fetch learning progress: ${e.message}")
            null
        }
    }

    private suspend fun fetchSkillProgress(userId: String): List<SkillProgressData> {
        return try {
            val response =
                supabase.postgrest["skill_progress"].select {
                    filter {
                        eq("user_id", userId)
                    }
                }

            response.decodeList<SkillProgressRow>().map {
                SkillProgressData(
                    skillArea = it.skill_area,
                    level = it.level,
                    xpPoints = it.xp_points,
                    accuracyPercentage = it.accuracy_percentage,
                    timeSpent = it.time_spent,
                    exercisesCompleted = it.exercises_completed,
                )
            }
        } catch (e: Exception) {
            println("Failed to fetch skill progress: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchVocabularyStats(userId: String): VocabularyStatsData? {
        return try {
            val userVocabResponse =
                supabase.postgrest["user_vocabulary"].select {
                    filter {
                        eq("user_id", userId)
                    }
                }

            val userVocab = userVocabResponse.decodeList<UserVocabularyRow>()

            val vocabularyWords = mutableListOf<UserVocabularyWord>()
            if (userVocab.isNotEmpty()) {
                val wordIds = userVocab.map { it.word_id }

                val wordsResponse =
                    supabase.postgrest["vocabulary_words"].select {
                        filter {
                            isIn("id", wordIds)
                        }
                    }

                val words = wordsResponse.decodeList<VocabularyWordRow>()

                vocabularyWords.addAll(
                    userVocab.mapNotNull { userWord ->
                        val wordDetails = words.find { it.id == userWord.word_id }
                        wordDetails?.let {
                            UserVocabularyWord(
                                word = it.word,
                                definition = it.definition,
                                pronunciation = it.pronunciation,
                                exampleSentence = it.example_sentence,
                                difficultyLevel = it.difficulty_level,
                                category = it.category,
                                status = userWord.status,
                                reviewCount = userWord.review_count,
                                correctCount = userWord.correct_count,
                                lastReviewed = userWord.last_reviewed,
                                nextReview = userWord.next_review,
                            )
                        }
                    },
                )
            }

            VocabularyStatsData(
                totalWords = userVocab.size,
                newWords = userVocab.count { it.status == "new" },
                learningWords = userVocab.count { it.status == "learning" },
                reviewingWords = userVocab.count { it.status == "reviewing" },
                masteredWords = userVocab.count { it.status == "mastered" },
                averageReviewCount = if (userVocab.isNotEmpty()) userVocab.map { it.review_count }.average() else 0.0,
                averageCorrectRate =
                    if (userVocab.isNotEmpty()) {
                        val reviewedWords = userVocab.filter { it.review_count > 0 }
                        if (reviewedWords.isNotEmpty()) {
                            reviewedWords.map { it.correct_count.toDouble() / it.review_count }.average()
                        } else {
                            0.0
                        }
                    } else {
                        0.0
                    },
                words = vocabularyWords,
            )
        } catch (e: Exception) {
            println("Failed to fetch vocabulary stats: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchLessonProgress(userId: String): LessonProgressData? {
        return try {
            val response =
                supabase.postgrest["user_lesson_progress"].select {
                    filter {
                        eq("user_id", userId)
                    }
                }

            val lessons = response.decodeList<UserLessonProgressRow>()

            LessonProgressData(
                totalLessons = lessons.size,
                completedLessons = lessons.count { it.status == "completed" },
                inProgressLessons = lessons.count { it.status == "in_progress" },
                averageScore =
                    if (lessons.isNotEmpty()) {
                        val scores = lessons.mapNotNull { it.score }
                        if (scores.isNotEmpty()) scores.average() else 0.0
                    } else {
                        0.0
                    },
                totalTimeSpent = lessons.sumOf { it.time_spent },
            )
        } catch (e: Exception) {
            println("Failed to fetch lesson progress: ${e.message}")
            null
        }
    }

    private suspend fun fetchRecentChatSummary(userId: String): ChatHistoryData? {
        return try {
            val response =
                supabase.postgrest["chat_sessions"].select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                    limit(10)
                }

            val sessions = response.decodeList<ChatSessionRow>()

            ChatHistoryData(
                totalSessions = sessions.size,
                totalMessages = sessions.sumOf { it.message_count },
                totalDuration = sessions.sumOf { it.total_duration },
            )
        } catch (e: Exception) {
            println("Failed to fetch chat history: ${e.message}")
            null
        }
    }

    private suspend fun fetchAchievements(userId: String): List<String> {
        return try {
            val response =
                supabase.postgrest["user_achievements"].select {
                    filter {
                        eq("user_id", userId)
                    }
                }

            response.decodeList<UserAchievementRow>().map { it.achievement_id }
        } catch (e: Exception) {
            println("Failed to fetch achievements: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchUserSettings(userId: String): UserSettingsData? {
        return try {
            val response =
                supabase.postgrest["user_settings"].select {
                    filter {
                        eq("user_id", userId)
                    }
                    limit(1)
                }

            response.decodeSingle<UserSettingsRow>().let {
                UserSettingsData(
                    aiPreferences = it.ai_preferences,
                    notificationSettings = it.notification_settings,
                )
            }
        } catch (e: Exception) {
            println("Failed to fetch user settings: ${e.message}")
            null
        }
    }
}

data class ComprehensiveUserData(
    val userId: String,
    val firstName: String?,
    val lastName: String?,
    val userProfile: UserProfileData?,
    val learningProgress: LearningProgressData?,
    val skillProgress: List<SkillProgressData>,
    val vocabularyStats: VocabularyStatsData?,
    val lessonProgress: LessonProgressData?,
    val chatHistory: ChatHistoryData?,
    val achievements: List<String>,
    val userSettings: UserSettingsData?,
)

data class UserProfileData(
    val personalInfo: JsonObject?,
    val learningProfile: JsonObject?,
    val accountInfo: JsonObject?,
    val profileStats: JsonObject?,
)

data class LearningProgressData(
    val overallLevel: Int,
    val xpPoints: Int,
    val weeklyXp: Int,
    val monthlyXp: Int,
    val streakDays: Int,
    val longestStreak: Int,
    val totalStudyTime: Int,
    val weeklyStudyTime: Int,
)

data class SkillProgressData(
    val skillArea: String,
    val level: Int,
    val xpPoints: Int,
    val accuracyPercentage: Double,
    val timeSpent: Int,
    val exercisesCompleted: Int,
)

data class VocabularyStatsData(
    val totalWords: Int,
    val newWords: Int,
    val learningWords: Int,
    val reviewingWords: Int,
    val masteredWords: Int,
    val averageReviewCount: Double,
    val averageCorrectRate: Double,
    val words: List<UserVocabularyWord> = emptyList(),
)

data class UserVocabularyWord(
    val word: String,
    val definition: String,
    val pronunciation: String?,
    val exampleSentence: String?,
    val difficultyLevel: String,
    val category: String,
    val status: String,
    val reviewCount: Int,
    val correctCount: Int,
    val lastReviewed: String?,
    val nextReview: String?,
)

data class LessonProgressData(
    val totalLessons: Int,
    val completedLessons: Int,
    val inProgressLessons: Int,
    val averageScore: Double,
    val totalTimeSpent: Int,
)

data class ChatHistoryData(
    val totalSessions: Int,
    val totalMessages: Int,
    val totalDuration: Int,
)

data class UserSettingsData(
    val aiPreferences: JsonObject?,
    val notificationSettings: JsonObject?,
)

// Serialization models for Supabase responses
@kotlinx.serialization.Serializable
data class UserProfileRow(
    val id: String,
    val personal_info: JsonObject? = null,
    val learning_profile: JsonObject? = null,
    val account_info: JsonObject? = null,
    val profile_stats: JsonObject? = null,
)

@kotlinx.serialization.Serializable
data class LearningProgressRow(
    val user_id: String,
    val overall_level: Int = 1,
    val xp_points: Int = 0,
    val weekly_xp: Int = 0,
    val monthly_xp: Int = 0,
    val streak_days: Int = 0,
    val longest_streak: Int = 0,
    val total_study_time: Int = 0,
    val weekly_study_time: Int = 0,
)

@kotlinx.serialization.Serializable
data class SkillProgressRow(
    val skill_area: String,
    val level: Int = 1,
    val xp_points: Int = 0,
    val accuracy_percentage: Double = 0.0,
    val time_spent: Int = 0,
    val exercises_completed: Int = 0,
)

@kotlinx.serialization.Serializable
data class UserVocabularyRow(
    val word_id: String,
    val status: String,
    val review_count: Int = 0,
    val correct_count: Int = 0,
    val last_reviewed: String? = null,
    val next_review: String? = null,
)

@kotlinx.serialization.Serializable
data class VocabularyWordRow(
    val id: String,
    val word: String,
    val definition: String,
    val pronunciation: String? = null,
    val example_sentence: String? = null,
    val difficulty_level: String,
    val category: String,
)

@kotlinx.serialization.Serializable
data class UserLessonProgressRow(
    val user_id: String,
    val lesson_id: String? = null,
    val status: String? = null,
    val score: Int? = null,
    val time_spent: Int = 0,
    val started_at: String? = null,
    val completed_at: String? = null,
)

@kotlinx.serialization.Serializable
data class ChatSessionRow(
    val message_count: Int = 0,
    val total_duration: Int = 0,
)

@kotlinx.serialization.Serializable
data class UserAchievementRow(
    val achievement_id: String,
)

@kotlinx.serialization.Serializable
data class UserSettingsRow(
    val ai_preferences: JsonObject? = null,
    val notification_settings: JsonObject? = null,
)
