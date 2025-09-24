package org.example.project.domain.model

data class User(
    val id: String,
    val name: String,
    val level: String,
    val streak: Int,
    val xpPoints: Int,
    val wordsLearned: Int,
    val accuracy: Int, // Percentage (0-100)
    val avatarInitials: String
) {
    companion object {

        fun sampleUser() = User(
            id = "user_001",
            name = "Sarah Chen",
            level = "Beginner Level",
            streak = 0,
            xpPoints = 0,
            wordsLearned = 0,
            accuracy = 0,
            avatarInitials = "SC"
        )
    }
}
