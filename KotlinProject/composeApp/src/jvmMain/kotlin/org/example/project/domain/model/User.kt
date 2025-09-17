package org.example.project.domain.model

/**
 * @Variable id Unique identifier for the user
 * @Variable name Display name of the user
 * @Variable level Current learning level (e.g., "Intermediate Level")
 * @Variable streak Current learning streak in days
 * @Variable xpPoints Total XP points earned
 * @Variable wordsLearned Total number of words learned
 * @Variable accuracy Learning accuracy percentage
 * @Variable avatarInitials User's initials for avatar display
 */
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
