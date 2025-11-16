package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.Achievement
import org.example.project.domain.model.GoalType
import org.example.project.domain.model.LearningGoal
import org.example.project.domain.model.LearningProgress
import org.example.project.domain.model.SkillArea
import org.example.project.domain.model.WeeklyProgressData

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
        
        println("Achievement clicked: $achievementId")
    }

    fun onGoalClicked(goalId: String) {
        
        println("Goal clicked: $goalId")
    }

    fun onCreateGoalClicked() {
        
        println("Create goal clicked")
    }

    fun onSetFirstGoalClicked() {
        
        println("Set first goal clicked")
    }

    fun onExploreAchievementsClicked() {
        
        println("Explore achievements clicked")
    }

    fun createGoal(
        title: String,
        description: String,
        type: GoalType,
        target: Int,
        unit: String,
        deadline: Long?,
    ) {
        val newGoal =
            LearningGoal(
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
                isActive = true,
            )

        _learningGoals.value = _learningGoals.value + newGoal
        updateComputedProperties()
    }

    private fun loadProgressData(timeframe: ProgressTimeframe) {
        _isLoading.value = true

        
        when (timeframe) {
            ProgressTimeframe.WEEK -> {
                _weeklyProgressData.value = WeeklyProgressData.getSampleWeeklyData()
            }
            ProgressTimeframe.MONTH -> {
                
            }
            ProgressTimeframe.YEAR -> {
                
            }
            ProgressTimeframe.ALL_TIME -> {
                
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
    ALL_TIME("All Time"),
}
