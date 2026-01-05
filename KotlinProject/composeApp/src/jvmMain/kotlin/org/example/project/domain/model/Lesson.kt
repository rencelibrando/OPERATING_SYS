package org.example.project.domain.model

data class Lesson(
    val id: String,
    val title: String,
    val category: LessonCategory,
    val difficulty: LessonDifficulty,
    val duration: Int,
    val lessonsCount: Int,
    val completedCount: Int,
    val progressPercentage: Int,
    val icon: String,
    val isAvailable: Boolean = true,
) {
    companion object {
        /** Returns an empty list for initial state. */
        fun getSampleLessons(): List<Lesson> = emptyList()
    }
}

enum class LessonCategory(val displayName: String) {
    GRAMMAR("Grammar"),
    VOCABULARY("Vocabulary"),
    CONVERSATION("Conversation"),
    PRONUNCIATION("Pronunciation"),
}

enum class LessonDifficulty(val displayName: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

enum class LessonLanguage(val displayName: String, val code: String) {
    KOREAN("Korean", "ko"),
    CHINESE("Chinese", "zh"),
    FRENCH("French", "fr"),
    GERMAN("German", "de"),
    SPANISH("Spanish", "es"),
}

data class LevelProgress(
    val level: Int,
    val title: String,
    val completedLessons: Int,
    val remainingLessons: Int,
    val progressPercentage: Int,
) {
    companion object {
        fun getSampleProgress(): LevelProgress =
            LevelProgress(
                level = 1,
                title = "Beginner Level",
                completedLessons = 0,
                remainingLessons = 0,
                progressPercentage = 0,
            )
    }
}

data class RecentLesson(
    val id: String,
    val title: String,
    val category: String,
    val difficulty: String,
    val duration: Int,
    val progressPercentage: Int,
    val icon: String,
) {
    companion object {
        /** Returns an empty list for initial state. */
        fun getSampleRecentLessons(): List<RecentLesson> = emptyList()
    }
}

data class LessonCategoryInfo(
    val difficulty: LessonDifficulty,
    val title: String,
    val description: String,
    val totalLessons: Int,
    val completedLessons: Int,
    val isLocked: Boolean,
    val progressPercentage: Int,
) {
    companion object {
        /**
         * Get lesson category information.
         * Note: Lesson counts are now loaded dynamically from Supabase based on selected language.
         * Use LessonsViewModel to get accurate counts per language.
         */
        fun getSampleCategories(userLevel: LessonDifficulty): List<LessonCategoryInfo> {
            val beginnerCompleted = 0
            val intermediateCompleted = 0

            // Lesson counts are now dynamic from Supabase, set to 0 as placeholder
            // The actual counts will be shown when lessons are loaded from the database
            val beginnerLessonCount = 0
            val intermediateLessonCount = 0
            val advancedLessonCount = 0

            return listOf(
                LessonCategoryInfo(
                    difficulty = LessonDifficulty.BEGINNER,
                    title = "Beginner",
                    description = "Start your language learning journey with foundational lessons",
                    totalLessons = beginnerLessonCount,
                    completedLessons = beginnerCompleted,
                    isLocked = false,
                    progressPercentage = 0,
                ),
                LessonCategoryInfo(
                    difficulty = LessonDifficulty.INTERMEDIATE,
                    title = "Intermediate",
                    description = "Build on your basics with more complex concepts and conversations",
                    totalLessons = intermediateLessonCount,
                    completedLessons = intermediateCompleted,
                    isLocked = false, // Don't lock based on hardcoded counts anymore
                    progressPercentage = 0,
                ),
                LessonCategoryInfo(
                    difficulty = LessonDifficulty.ADVANCED,
                    title = "Advanced",
                    description = "Master advanced topics and achieve fluency in complex situations",
                    totalLessons = advancedLessonCount,
                    completedLessons = 0,
                    isLocked = false, // Don't lock based on hardcoded counts anymore
                    progressPercentage = 0,
                ),
            )
        }
    }
}

data class LessonTopic(
    val id: String,
    val title: String,
    val description: String,
    val lessonNumber: Int? = null,
    val isCompleted: Boolean = false,
    val isLocked: Boolean = false,
    val durationMinutes: Int? = null,
    val language: LessonLanguage? = null,
    val completedLessonsCount: Int = 0,
    val totalLessonsCount: Int = 0,
) {
    val progressPercentage: Int
        get() = if (totalLessonsCount > 0) {
            ((completedLessonsCount.toFloat() / totalLessonsCount.toFloat()) * 100).toInt()
        } else {
            0
        }

    val isFullyCompleted: Boolean
        get() = totalLessonsCount > 0 && completedLessonsCount >= totalLessonsCount

    companion object {
    }
}
