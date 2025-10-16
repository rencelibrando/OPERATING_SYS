package org.example.project.domain.model

data class LearningProgress(
    val userId: String,
    val overallLevel: Int,
    val xpPoints: Int,
    val weeklyXP: Int,
    val monthlyXP: Int,
    val streakDays: Int,
    val longestStreak: Int,
    val totalStudyTime: Int, // in minutes
    val weeklyStudyTime: Int, // in minutes
    val skillLevels: Map<SkillArea, SkillProgress>,
    val lastUpdated: Long,
) {
    companion object {
        fun getSampleProgress(): LearningProgress =
            LearningProgress(
                userId = "user_001",
                overallLevel = 1,
                xpPoints = 0,
                weeklyXP = 0,
                monthlyXP = 0,
                streakDays = 0,
                longestStreak = 0,
                totalStudyTime = 0,
                weeklyStudyTime = 0,
                skillLevels =
                    SkillArea.values().associateWith {
                        SkillProgress(level = 1, xp = 0, maxXP = 100)
                    },
                lastUpdated = System.currentTimeMillis(),
            )

        fun getDemoProgress(): LearningProgress =
            LearningProgress(
                userId = "user_001",
                overallLevel = 15,
                xpPoints = 2847,
                weeklyXP = 420,
                monthlyXP = 1650,
                streakDays = 12,
                longestStreak = 28,
                totalStudyTime = 1680, // 28 hours
                weeklyStudyTime = 280, // 4.67 hours
                skillLevels =
                    mapOf(
                        SkillArea.VOCABULARY to SkillProgress(level = 18, xp = 750, maxXP = 1000),
                        SkillArea.GRAMMAR to SkillProgress(level = 12, xp = 300, maxXP = 800),
                        SkillArea.SPEAKING to SkillProgress(level = 8, xp = 200, maxXP = 600),
                        SkillArea.LISTENING to SkillProgress(level = 14, xp = 520, maxXP = 900),
                        SkillArea.READING to SkillProgress(level = 16, xp = 680, maxXP = 950),
                        SkillArea.WRITING to SkillProgress(level = 10, xp = 150, maxXP = 700),
                    ),
                lastUpdated = System.currentTimeMillis(),
            )
    }
}

data class SkillProgress(
    val level: Int,
    val xp: Int,
    val maxXP: Int,
) {
    val progressPercentage: Int
        get() = if (maxXP > 0) (xp * 100 / maxXP) else 0
}

enum class SkillArea(val displayName: String, val icon: String, val color: String) {
    VOCABULARY("Vocabulary", "📚", "#8B5CF6"),
    GRAMMAR("Grammar", "📝", "#10B981"),
    SPEAKING("Speaking", "🗣️", "#F59E0B"),
    LISTENING("Listening", "👂", "#3B82F6"),
    READING("Reading", "📖", "#EF4444"),
    WRITING("Writing", "✍️", "#6366F1"),
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val category: AchievementCategory,
    val xpReward: Int,
    val unlockedAt: Long?,
    val isRare: Boolean = false,
    val requirements: String,
) {
    val isUnlocked: Boolean
        get() = unlockedAt != null

    companion object {
        fun getSampleAchievements(): List<Achievement> = emptyList()

        fun getDemoAchievements(): List<Achievement> =
            listOf(
                Achievement(
                    id = "first_lesson",
                    title = "First Steps",
                    description = "Completed your first lesson",
                    icon = "🎯",
                    category = AchievementCategory.MILESTONE,
                    xpReward = 50,
                    unlockedAt = System.currentTimeMillis() - 2592000000, // 30 days ago
                    requirements = "Complete 1 lesson",
                ),
                Achievement(
                    id = "week_streak",
                    title = "Week Warrior",
                    description = "Practiced for 7 days in a row",
                    icon = "🔥",
                    category = AchievementCategory.STREAK,
                    xpReward = 200,
                    unlockedAt = System.currentTimeMillis() - 604800000, // 7 days ago
                    requirements = "Practice 7 days in a row",
                ),
                Achievement(
                    id = "vocabulary_master",
                    title = "Word Collector",
                    description = "Learned 100 new words",
                    icon = "📚",
                    category = AchievementCategory.SKILL,
                    xpReward = 300,
                    unlockedAt = System.currentTimeMillis() - 86400000, // 1 day ago
                    requirements = "Learn 100 vocabulary words",
                ),
                Achievement(
                    id = "speaking_confidence",
                    title = "Confident Speaker",
                    description = "Complete 50 speaking exercises",
                    icon = "🎤",
                    category = AchievementCategory.SKILL,
                    xpReward = 400,
                    unlockedAt = null, // Not unlocked yet
                    requirements = "Complete 50 speaking exercises",
                ),
                Achievement(
                    id = "night_owl",
                    title = "Night Owl",
                    description = "Studied after 10 PM",
                    icon = "🦉",
                    category = AchievementCategory.SPECIAL,
                    xpReward = 75,
                    unlockedAt = System.currentTimeMillis() - 172800000, // 2 days ago
                    isRare = true,
                    requirements = "Study after 10 PM",
                ),
            )
    }
}

enum class AchievementCategory(val displayName: String) {
    MILESTONE("Milestone"),
    STREAK("Streak"),
    SKILL("Skill"),
    SPECIAL("Special"),
    SOCIAL("Social"),
}

data class LearningGoal(
    val id: String,
    val title: String,
    val description: String,
    val type: GoalType,
    val target: Int,
    val current: Int,
    val unit: String,
    val deadline: Long?,
    val createdAt: Long,
    val completedAt: Long?,
    val isActive: Boolean = true,
) {
    val progressPercentage: Int
        get() = if (target > 0) (current * 100 / target).coerceAtMost(100) else 0

    val isCompleted: Boolean
        get() = completedAt != null || current >= target

    companion object {
        fun getSampleGoals(): List<LearningGoal> = emptyList()

        fun getDemoGoals(): List<LearningGoal> =
            listOf(
                LearningGoal(
                    id = "daily_practice",
                    title = "Daily Practice",
                    description = "Practice for 30 minutes today",
                    type = GoalType.DAILY,
                    target = 30,
                    current = 22,
                    unit = "minutes",
                    deadline = System.currentTimeMillis() + 86400000, // Tomorrow
                    createdAt = System.currentTimeMillis() - 86400000, // Yesterday
                    completedAt = null,
                ),
                LearningGoal(
                    id = "weekly_vocabulary",
                    title = "Weekly Vocabulary",
                    description = "Learn 25 new words this week",
                    type = GoalType.WEEKLY,
                    target = 25,
                    current = 18,
                    unit = "words",
                    deadline = System.currentTimeMillis() + 432000000, // 5 days from now
                    createdAt = System.currentTimeMillis() - 172800000, // 2 days ago
                    completedAt = null,
                ),
                LearningGoal(
                    id = "speaking_sessions",
                    title = "Speaking Practice",
                    description = "Complete 10 speaking sessions this month",
                    type = GoalType.MONTHLY,
                    target = 10,
                    current = 7,
                    unit = "sessions",
                    deadline = System.currentTimeMillis() + 1728000000, // 20 days from now
                    createdAt = System.currentTimeMillis() - 864000000, // 10 days ago
                    completedAt = null,
                ),
            )
    }
}

enum class GoalType(val displayName: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom"),
}

data class WeeklyProgressData(
    val weekStartDate: Long,
    val dailyXP: List<Int>, // 7 values for each day
    val dailyMinutes: List<Int>, // 7 values for each day
    val totalXP: Int,
    val totalMinutes: Int,
) {
    companion object {
        fun getSampleWeeklyData(): WeeklyProgressData =
            WeeklyProgressData(
                weekStartDate = System.currentTimeMillis() - 518400000, // 6 days ago (start of week)
                dailyXP = listOf(0, 0, 0, 0, 0, 0, 0),
                dailyMinutes = listOf(0, 0, 0, 0, 0, 0, 0),
                totalXP = 0,
                totalMinutes = 0,
            )

        fun getDemoWeeklyData(): WeeklyProgressData =
            WeeklyProgressData(
                weekStartDate = System.currentTimeMillis() - 518400000, // 6 days ago
                dailyXP = listOf(45, 60, 0, 80, 95, 70, 85), // Monday to Sunday
                dailyMinutes = listOf(25, 35, 0, 45, 50, 30, 40),
                totalXP = 435,
                totalMinutes = 225,
            )
    }
}
