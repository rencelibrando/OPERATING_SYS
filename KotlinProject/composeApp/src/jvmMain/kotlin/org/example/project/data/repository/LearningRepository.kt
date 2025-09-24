package org.example.project.data.repository

import org.example.project.domain.model.*

interface LearningRepository {
    
    suspend fun getLearningProgress(userId: String): Result<LearningProgress?>
    suspend fun updateLearningProgress(progress: LearningProgress): Result<LearningProgress>
    suspend fun getSkillProgress(userId: String, skillArea: SkillArea): Result<SkillProgress?>
    suspend fun updateSkillProgress(userId: String, skillArea: SkillArea, progress: SkillProgress): Result<Unit>
    

    suspend fun getAllLessons(): Result<List<Lesson>>
    suspend fun getLessonsByCategory(category: LessonCategory): Result<List<Lesson>>
    suspend fun getLessonsByDifficulty(difficulty: String): Result<List<Lesson>>
    suspend fun getLesson(lessonId: String): Result<Lesson?>
    
    // User Lesson Progress
    suspend fun getUserLessonProgress(userId: String, lessonId: String): Result<UserLessonProgress?>
    suspend fun updateUserLessonProgress(progress: UserLessonProgress): Result<UserLessonProgress>
    suspend fun getRecentLessons(userId: String, limit: Int = 5): Result<List<RecentLesson>>
    
    // Achievements
    suspend fun getUserAchievements(userId: String): Result<List<Achievement>>
    suspend fun unlockAchievement(userId: String, achievementId: String): Result<Achievement>
    suspend fun getAvailableAchievements(): Result<List<Achievement>>
}


data class UserLessonProgress(
    val userId: String,
    val lessonId: String,
    val completedCount: Int,
    val totalCount: Int,
    val progressPercentage: Int,
    val lastAccessed: Long,
    val completedAt: Long? = null
)
