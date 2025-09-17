package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.LearningProgress
import org.example.project.domain.model.Achievement
import org.example.project.domain.model.LearningGoal
import org.example.project.domain.model.WeeklyProgressData
import org.example.project.domain.model.SkillArea
import org.example.project.domain.model.GoalType

/**
 * ViewModel for the Progress Tracker screen
 * 
 * Manages the state and business logic for the progress tracking screen,
 * including learning progress, achievements, goals, and analytics
 */
class ProgressViewModel : ViewModel() {
    
    // Private mutable state
    private val _learningProgress = mutableStateOf(LearningProgress.getSampleProgress())
    private val _achievements = mutableStateOf(Achievement.getSampleAchievements())
    private val _learningGoals = mutableStateOf(LearningGoal.getSampleGoals())
    private val _weeklyProgressData = mutableStateOf(WeeklyProgressData.getSampleWeeklyData())
    private val _selectedTimeframe = mutableStateOf(ProgressTimeframe.WEEK)
    private val _selectedSkill = mutableStateOf<SkillArea?>(null)
    private val _isLoading = mutableStateOf(false)
    
    // Public read-only state
    val learningProgress: State<LearningProgress> = _learningProgress
    val achievements: State<List<Achievement>> = _achievements
    val learningGoals: State<List<LearningGoal>> = _learningGoals
    val weeklyProgressData: State<WeeklyProgressData> = _weeklyProgressData
    val selectedTimeframe: State<ProgressTimeframe> = _selectedTimeframe
    val selectedSkill: State<SkillArea?> = _selectedSkill
    val isLoading: State<Boolean> = _isLoading
    
    // Computed properties
    private val _unlockedAchievements = mutableStateOf(emptyList<Achievement>())
    val unlockedAchievements: State<List<Achievement>> = _unlockedAchievements
    
    private val _lockedAchievements = mutableStateOf(emptyList<Achievement>())
    val lockedAchievements: State<List<Achievement>> = _lockedAchievements
    
    private val _activeGoals = mutableStateOf(emptyList<LearningGoal>())
    val activeGoals: State<List<LearningGoal>> = _activeGoals
    
    private val _completedGoals = mutableStateOf(emptyList<LearningGoal>())
    val completedGoals: State<List<LearningGoal>> = _completedGoals
    
    init {
        updateComputedProperties()
    }
    
    /**
     * Handles timeframe selection change
     * @param timeframe The selected timeframe
     */
    fun onTimeframeSelected(timeframe: ProgressTimeframe) {
        _selectedTimeframe.value = timeframe
        loadProgressData(timeframe)
    }
    
    /**
     * Handles skill area selection for detailed view
     * @param skill The selected skill area
     */
    fun onSkillSelected(skill: SkillArea?) {
        _selectedSkill.value = skill
    }
    
    /**
     * Handles achievement click
     * @param achievementId The ID of the clicked achievement
     */
    fun onAchievementClicked(achievementId: String) {
        // TODO: Show achievement details or celebration
        println("Achievement clicked: $achievementId")
    }
    
    /**
     * Handles goal click
     * @param goalId The ID of the clicked goal
     */
    fun onGoalClicked(goalId: String) {
        // TODO: Show goal details or edit goal
        println("Goal clicked: $goalId")
    }
    
    /**
     * Handles creating a new goal
     */
    fun onCreateGoalClicked() {
        // TODO: Navigate to goal creation screen
        println("Create goal clicked")
    }
    
    /**
     * Handles setting first goal (from empty state)
     */
    fun onSetFirstGoalClicked() {
        // TODO: Show goal creation wizard
        println("Set first goal clicked")
    }
    
    /**
     * Handles exploring achievements (from empty state)
     */
    fun onExploreAchievementsClicked() {
        // TODO: Navigate to achievements gallery
        println("Explore achievements clicked")
    }
    
    /**
     * Creates a new learning goal
     * @param title Goal title
     * @param description Goal description
     * @param type Goal type
     * @param target Target value
     * @param unit Unit of measurement
     * @param deadline Optional deadline
     */
    fun createGoal(
        title: String,
        description: String,
        type: GoalType,
        target: Int,
        unit: String,
        deadline: Long?
    ) {
        val newGoal = LearningGoal(
            id = "goal_${System.currentTimeMillis()}",
            title = title,
            description = description,
            type = type,
            target = target,
            current = 0,
            unit = unit,
            deadline = deadline,
            createdAt = System.currentTimeMillis(),
            completedAt = null,
            isActive = true
        )
        
        _learningGoals.value = _learningGoals.value + newGoal
        updateComputedProperties()
    }
    
    /**
     * Updates progress on a specific goal
     * @param goalId Goal identifier
     * @param progress Progress to add
     */
    fun updateGoalProgress(goalId: String, progress: Int) {
        _learningGoals.value = _learningGoals.value.map { goal ->
            if (goal.id == goalId) {
                val newCurrent = (goal.current + progress).coerceAtMost(goal.target)
                val completedAt = if (newCurrent >= goal.target) System.currentTimeMillis() else null
                goal.copy(current = newCurrent, completedAt = completedAt)
            } else {
                goal
            }
        }
        updateComputedProperties()
    }
    
    /**
     * Refreshes all progress data
     */
    fun refreshProgressData() {
        _isLoading.value = true
        
        // TODO: Implement actual data refresh from repository
        // For now, simulate refresh
        _learningProgress.value = LearningProgress.getSampleProgress()
        _achievements.value = Achievement.getSampleAchievements()
        _learningGoals.value = LearningGoal.getSampleGoals()
        _weeklyProgressData.value = WeeklyProgressData.getSampleWeeklyData()
        
        updateComputedProperties()
        _isLoading.value = false
    }
    
    /**
     * Loads progress data for the specified timeframe
     */
    private fun loadProgressData(timeframe: ProgressTimeframe) {
        _isLoading.value = true
        
        // TODO: Load data based on timeframe from repository
        when (timeframe) {
            ProgressTimeframe.WEEK -> {
                _weeklyProgressData.value = WeeklyProgressData.getSampleWeeklyData()
            }
            ProgressTimeframe.MONTH -> {
                // TODO: Load monthly data
            }
            ProgressTimeframe.YEAR -> {
                // TODO: Load yearly data
            }
            ProgressTimeframe.ALL_TIME -> {
                // TODO: Load all-time data
            }
        }
        
        _isLoading.value = false
    }
    
    /**
     * Updates computed properties based on current data
     */
    private fun updateComputedProperties() {
        val achievements = _achievements.value
        _unlockedAchievements.value = achievements.filter { it.isUnlocked }
        _lockedAchievements.value = achievements.filter { !it.isUnlocked }
        
        val goals = _learningGoals.value
        _activeGoals.value = goals.filter { it.isActive && !it.isCompleted }
        _completedGoals.value = goals.filter { it.isCompleted }
    }
    
    /**
     * Calculates XP needed for next level
     */
    fun getXPNeededForNextLevel(): Int {
        val currentLevel = _learningProgress.value.overallLevel
        val baseXP = 100
        val multiplier = 1.2
        val nextLevelXP = (baseXP * Math.pow(multiplier, currentLevel.toDouble())).toInt()
        val currentXP = _learningProgress.value.xpPoints
        val currentLevelStartXP = if (currentLevel > 1) {
            (baseXP * Math.pow(multiplier, (currentLevel - 1).toDouble())).toInt()
        } else {
            0
        }
        return nextLevelXP - (currentXP - currentLevelStartXP)
    }
    
    /**
     * Gets current level progress percentage
     */
    fun getCurrentLevelProgress(): Int {
        val currentLevel = _learningProgress.value.overallLevel
        val baseXP = 100
        val multiplier = 1.2
        val currentLevelStartXP = if (currentLevel > 1) {
            (baseXP * Math.pow(multiplier, (currentLevel - 1).toDouble())).toInt()
        } else {
            0
        }
        val nextLevelXP = (baseXP * Math.pow(multiplier, currentLevel.toDouble())).toInt()
        val currentXP = _learningProgress.value.xpPoints
        val progressInLevel = currentXP - currentLevelStartXP
        val levelRange = nextLevelXP - currentLevelStartXP
        
        return if (levelRange > 0) (progressInLevel * 100 / levelRange).coerceIn(0, 100) else 0
    }
}

/**
 * Represents different timeframes for progress tracking
 */
enum class ProgressTimeframe(val displayName: String) {
    WEEK("This Week"),
    MONTH("This Month"),
    YEAR("This Year"),
    ALL_TIME("All Time")
}
