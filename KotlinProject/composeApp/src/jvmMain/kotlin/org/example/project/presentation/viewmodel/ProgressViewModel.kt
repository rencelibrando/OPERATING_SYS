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


class ProgressViewModel : ViewModel() {
    
    private val _learningProgress = mutableStateOf(LearningProgress.getSampleProgress())
    private val _achievements = mutableStateOf(Achievement.getSampleAchievements())
    private val _learningGoals = mutableStateOf(LearningGoal.getSampleGoals())
    private val _weeklyProgressData = mutableStateOf(WeeklyProgressData.getSampleWeeklyData())
    private val _selectedTimeframe = mutableStateOf(ProgressTimeframe.WEEK)
    private val _selectedSkill = mutableStateOf<SkillArea?>(null)
    private val _isLoading = mutableStateOf(false)
    
    val learningProgress: State<LearningProgress> = _learningProgress
    val achievements: State<List<Achievement>> = _achievements
    val learningGoals: State<List<LearningGoal>> = _learningGoals
    val weeklyProgressData: State<WeeklyProgressData> = _weeklyProgressData
    val selectedTimeframe: State<ProgressTimeframe> = _selectedTimeframe
    val selectedSkill: State<SkillArea?> = _selectedSkill
    val isLoading: State<Boolean> = _isLoading
    
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

    fun onTimeframeSelected(timeframe: ProgressTimeframe) {
        _selectedTimeframe.value = timeframe
        loadProgressData(timeframe)
    }

    fun onSkillSelected(skill: SkillArea?) {
        _selectedSkill.value = skill
    }

    fun onAchievementClicked(achievementId: String) {
        // TODO: Show achievement details or celebration
        println("Achievement clicked: $achievementId")
    }

    fun onGoalClicked(goalId: String) {
        // TODO: Show goal details or edit goal
        println("Goal clicked: $goalId")
    }

    fun onCreateGoalClicked() {
        // TODO: Navigate to goal creation screen
        println("Create goal clicked")
    }

    fun onSetFirstGoalClicked() {
        // TODO: Show goal creation wizard
        println("Set first goal clicked")
    }

    fun onExploreAchievementsClicked() {
        // TODO: Navigate to achievements gallery
        println("Explore achievements clicked")
    }
    

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
    

    private fun updateComputedProperties() {
        val achievements = _achievements.value
        _unlockedAchievements.value = achievements.filter { it.isUnlocked }
        _lockedAchievements.value = achievements.filter { !it.isUnlocked }
        
        val goals = _learningGoals.value
        _activeGoals.value = goals.filter { it.isActive && !it.isCompleted }
        _completedGoals.value = goals.filter { it.isCompleted }
    }

}


enum class ProgressTimeframe(val displayName: String) {
    WEEK("This Week"),
    MONTH("This Month"),
    YEAR("This Year"),
    ALL_TIME("All Time")
}
