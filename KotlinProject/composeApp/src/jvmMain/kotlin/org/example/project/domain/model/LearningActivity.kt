package org.example.project.domain.model

data class LearningActivity(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val route: String,
    val isEnabled: Boolean = true,
) {
    companion object {
        fun getDefaultActivities(): List<LearningActivity> = emptyList()

        fun getSampleActivities(): List<LearningActivity> =
            listOf(
                LearningActivity(
                    id = "start_lesson",
                    title = "Start Lesson",
                    description = "Begin your personalized AI-powered lesson",
                    icon = "book",
                    route = "/lessons/start",
                ),
                LearningActivity(
                    id = "review_vocabulary",
                    title = "Review Vocabulary",
                    description = "Practice words you've learned recently",
                    icon = "vocabulary",
                    route = "/vocabulary/review",
                ),
                LearningActivity(
                    id = "practice_speaking",
                    title = "Practice Speaking",
                    description = "Improve your pronunciation with AI feedback",
                    icon = "microphone",
                    route = "/speaking/practice",
                ),
                LearningActivity(
                    id = "ai_chat",
                    title = "AI Chat",
                    description = "Have a conversation with your AI tutor",
                    icon = "chat",
                    route = "/ai-chat",
                ),
            )
    }
}
