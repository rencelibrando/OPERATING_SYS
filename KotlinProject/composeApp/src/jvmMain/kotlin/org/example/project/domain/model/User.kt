package org.example.project.domain.model

/**
 * Represents a user in the WordBridge application
 * 
 * @property id Unique identifier for the user
 * @property name Display name of the user
 * @property level Current learning level (e.g., "Intermediate Level")
 * @property streak Current learning streak in days
 * @property xpPoints Total XP points earned
 * @property wordsLearned Total number of words learned
 * @property accuracy Learning accuracy percentage
 * @property avatarInitials User's initials for avatar display
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
        /**
         * Creates a sample user for demonstration purposes
         */
        fun sampleUser() = User(
            id = "user_001",
            name = "Sarah Chen",
            level = "Intermediate Level",
            streak = 7,
            xpPoints = 1247,
            wordsLearned = 342,
            accuracy = 89,
            avatarInitials = "SC"
        )
    }
}
