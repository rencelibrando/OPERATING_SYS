package org.example.project.domain.model

data class Lesson(
    val id: String,
    val title: String,
    val category: LessonCategory,
    val difficulty: String,
    // in minutes
    val duration: Int,
    val lessonsCount: Int,
    val completedCount: Int,
    val progressPercentage: Int,
    val icon: String,
    val isAvailable: Boolean = true,
) {
    companion object {
        fun getSampleLessons(): List<Lesson> = emptyList()

        fun getDemoLessons(): List<Lesson> =
            listOf(
                Lesson(
                    id = "grammar_mastery",
                    title = "Grammar Mastery",
                    category = LessonCategory.GRAMMAR,
                    difficulty = "Intermediate",
                    duration = 15,
                    lessonsCount = 12,
                    completedCount = 8,
                    progressPercentage = 67,
                    icon = "📚",
                ),
                Lesson(
                    id = "vocabulary_builder",
                    title = "Vocabulary Builder",
                    category = LessonCategory.VOCABULARY,
                    difficulty = "Intermediate",
                    duration = 20,
                    lessonsCount = 15,
                    completedCount = 10,
                    progressPercentage = 67,
                    icon = "📖",
                ),
                Lesson(
                    id = "conversation_skills",
                    title = "Conversation Skills",
                    category = LessonCategory.CONVERSATION,
                    difficulty = "Intermediate",
                    duration = 18,
                    lessonsCount = 10,
                    completedCount = 5,
                    progressPercentage = 50,
                    icon = "💬",
                ),
                Lesson(
                    id = "pronunciation_guide",
                    title = "Pronunciation Guide",
                    category = LessonCategory.PRONUNCIATION,
                    difficulty = "Intermediate",
                    duration = 12,
                    lessonsCount = 8,
                    completedCount = 3,
                    progressPercentage = 38,
                    icon = "🎙️",
                ),
            )
    }
}

enum class LessonCategory(val displayName: String) {
    GRAMMAR("Grammar"),
    VOCABULARY("Vocabulary"),
    CONVERSATION("Conversation"),
    PRONUNCIATION("Pronunciation"),
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
        fun getSampleRecentLessons(): List<RecentLesson> = emptyList()

        fun getDemoRecentLessons(): List<RecentLesson> =
            listOf(
                RecentLesson(
                    id = "present_perfect",
                    title = "Present Perfect Tense",
                    category = "Grammar",
                    difficulty = "Intermediate",
                    duration = 15,
                    progressPercentage = 75,
                    icon = "📚",
                ),
                RecentLesson(
                    id = "business_vocab",
                    title = "Business Vocabulary",
                    category = "Vocabulary",
                    difficulty = "Intermediate",
                    duration = 20,
                    progressPercentage = 100,
                    icon = "📖",
                ),
                RecentLesson(
                    id = "restaurant_conv",
                    title = "Restaurant Conversations",
                    category = "Conversation",
                    difficulty = "Intermediate",
                    duration = 18,
                    progressPercentage = 0,
                    icon = "💬",
                ),
                RecentLesson(
                    id = "difficult_consonants",
                    title = "Difficult Consonants",
                    category = "Pronunciation",
                    difficulty = "Intermediate",
                    duration = 12,
                    progressPercentage = 40,
                    icon = "🎙️",
                ),
            )
    }
}
