package org.example.project.domain.model

data class VocabularyWord(
    val id: String,
    val word: String,
    val definition: String,
    val pronunciation: String,
    val category: String,
    val difficulty: String,
    val examples: List<String>,
    val status: VocabularyStatus,
    val dateAdded: Long,
    val lastReviewed: Long?,
) {
    companion object {
        fun getSampleWords(): List<VocabularyWord> =
            listOf(
                VocabularyWord(
                    id = "word_1",
                    word = "Serendipity",
                    definition = "The occurrence and development of events by chance in a happy or beneficial way",
                    pronunciation = "/ˌserənˈdipədē/",
                    category = "Noun",
                    difficulty = "Advanced",
                    examples =
                        listOf(
                            "Finding this job was pure serendipity.",
                            "The serendipity of meeting you here is amazing.",
                        ),
                    status = VocabularyStatus.MASTERED,
                    dateAdded = System.currentTimeMillis() - 86400000 * 7, // 7 days ago
                    lastReviewed = System.currentTimeMillis() - 86400000 * 2, // 2 days ago
                ),
                VocabularyWord(
                    id = "word_2",
                    word = "Eloquent",
                    definition = "Fluent or persuasive in speaking or writing",
                    pronunciation = "/ˈeləkwənt/",
                    category = "Adjective",
                    difficulty = "Intermediate",
                    examples =
                        listOf(
                            "She gave an eloquent speech.",
                            "His eloquent writing impressed everyone.",
                        ),
                    status = VocabularyStatus.LEARNING,
                    dateAdded = System.currentTimeMillis() - 86400000 * 3, // 3 days ago
                    lastReviewed = System.currentTimeMillis() - 86400000, // 1 day ago
                ),
            )
    }
}

enum class VocabularyStatus(val displayName: String) {
    NEW("New"),
    LEARNING("Learning"),
    MASTERED("Mastered"),
    NEED_REVIEW("Need Review"),
}

data class VocabularyStats(
    val totalWords: Int,
    val masteredWords: Int,
    val learningWords: Int,
    val needReviewWords: Int,
) {
    companion object {
        fun getSampleStats(): VocabularyStats =
            VocabularyStats(
                totalWords = 0,
                masteredWords = 0,
                learningWords = 0,
                needReviewWords = 0,
            )
    }
}

data class VocabularyFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String,
) {
    companion object {
        fun getVocabularyFeatures(): List<VocabularyFeature> =
            listOf(
                VocabularyFeature(
                    id = "ai_learning",
                    title = "AI-Powered Learning",
                    description = "Smart spaced repetition algorithm adapts to your learning pace and retention patterns.",
                    icon = "🧠",
                    color = "#8B5CF6", // Purple
                ),
                VocabularyFeature(
                    id = "audio_pronunciation",
                    title = "Audio Pronunciation",
                    description = "Native speaker audio recordings help you master correct pronunciation.",
                    icon = "🔊",
                    color = "#10B981", // Green
                ),
                VocabularyFeature(
                    id = "contextual_examples",
                    title = "Contextual Examples",
                    description = "Real-world sentence examples show how words are used in context.",
                    icon = "📝",
                    color = "#F59E0B", // Orange
                ),
                VocabularyFeature(
                    id = "progress_tracking",
                    title = "Progress Tracking",
                    description = "Visual progress indicators help you see your vocabulary growth over time.",
                    icon = "📊",
                    color = "#3B82F6", // Blue
                ),
                VocabularyFeature(
                    id = "smart_categorization",
                    title = "Smart Categorization",
                    description = "Automatic tagging by topic, difficulty level, and part of speech for organized learning.",
                    icon = "🏷️",
                    color = "#8B5CF6", // Purple
                ),
                VocabularyFeature(
                    id = "personalized_practice",
                    title = "Personalized Practice",
                    description = "Tailored exercises and quizzes based on your learning style and performance.",
                    icon = "💡",
                    color = "#F59E0B", // Orange
                ),
            )
    }
}

enum class VocabularyFilter(val displayName: String) {
    ALL("All"),
    MASTERED("Mastered"),
    LEARNING("Learning"),
    REVIEW("Review"),
}
