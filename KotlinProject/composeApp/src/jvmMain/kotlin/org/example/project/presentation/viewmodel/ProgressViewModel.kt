package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.core.analytics.ProgressAnalyticsService
import org.example.project.core.api.SupabaseApiHelper
import org.example.project.core.export.ProgressExportService
import org.example.project.core.realtime.ProgressRealtimeService
import org.example.project.core.realtime.ProgressUpdateEvent
import org.example.project.data.repository.ProgressHistoryRepository
import org.example.project.data.repository.ProgressTrackerRepositoryImpl
import org.example.project.domain.model.*

/**
 * ViewModel for comprehensive language progress tracking.
 *
 * Features:
 * - Per-language analytics (lessons, conversations, vocabulary, voice analysis, time)
 * - Automatic caching with 5-minute TTL
 * - Pull-to-refresh support
 * - Graceful error handling with partial data display
 * - Legacy progress tracking compatibility
 */
class ProgressViewModel : ViewModel() {
    private val analyticsService = ProgressAnalyticsService
    private val repository = ProgressTrackerRepositoryImpl()
    private val historyRepository = ProgressHistoryRepository()
    private val realtimeService = ProgressRealtimeService()
    private val exportService = ProgressExportService()

    // Language-specific progress state
    private val _selectedLanguage = mutableStateOf(LessonLanguage.KOREAN)
    val selectedLanguage: State<LessonLanguage> = _selectedLanguage

    private val _progressState = mutableStateOf<ProgressTrackerState>(ProgressTrackerState.Loading)
    val progressState: State<ProgressTrackerState> = _progressState

    private val _isRefreshing = mutableStateOf(false)
    val isRefreshing: State<Boolean> = _isRefreshing

    // Historical trends state
    private val _selectedTimeRange = mutableStateOf(HistoryTimeRange.MONTH)
    val selectedTimeRange: State<HistoryTimeRange> = _selectedTimeRange

    private val _progressHistory = mutableStateOf<List<ProgressHistorySnapshot>>(emptyList())
    val progressHistory: State<List<ProgressHistorySnapshot>> = _progressHistory

    private val _isLoadingHistory = mutableStateOf(false)
    val isLoadingHistory: State<Boolean> = _isLoadingHistory

    // Share/Export state
    private val _showShareDialog = mutableStateOf(false)
    val showShareDialog: State<Boolean> = _showShareDialog

    private val _exportMessage = mutableStateOf<String?>(null)
    val exportMessage: State<String?> = _exportMessage

    // Real-time updates
    private val _isRealtimeConnected = mutableStateOf(false)
    val isRealtimeConnected: State<Boolean> = _isRealtimeConnected

    // Legacy progress states for backwards compatibility
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
        loadLanguageProgress()
        startRealtimeUpdates()
        loadProgressHistory()
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
    }

    /**
     * Loads progress for the currently selected language.
     */
    fun loadLanguageProgress(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val userId = SupabaseApiHelper.getCurrentUserId()
            if (userId == null) {
                _progressState.value = ProgressTrackerState.Error("Please sign in to view your progress")
                return@launch
            }
            _progressState.value = ProgressTrackerState.Loading

            analyticsService.getLanguageProgress(userId, _selectedLanguage.value, forceRefresh)
                .onSuccess { progress ->
                    _progressState.value =
                        if (progress.hasData) {
                            ProgressTrackerState.Success(progress)
                        } else {
                            ProgressTrackerState.Empty
                        }
                }
                .onFailure { error ->
                    _progressState.value =
                        ProgressTrackerState.Error(
                            error.message ?: "Failed to load progress",
                        )
                }
        }
    }

    /**
     * Switches to a different language and loads its progress.
     */
    fun selectLanguage(language: LessonLanguage) {
        if (_selectedLanguage.value != language) {
            _selectedLanguage.value = language
            loadLanguageProgress()
        }
    }

    /**
     * Manually refreshes progress data (bypasses cache).
     */
    fun refreshProgress() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadLanguageProgress(forceRefresh = true)
            _isRefreshing.value = false
        }
    }

    /**
     * Invalidates cache after user completes a lesson.
     * Call this from lesson completion flow.
     */
    fun onLessonCompleted(language: LessonLanguage) {
        viewModelScope.launch {
            val userId = SupabaseApiHelper.getCurrentUserId() ?: return@launch
            analyticsService.invalidateCache(userId, language)

            if (language == _selectedLanguage.value) {
                loadLanguageProgress(forceRefresh = true)
            }
        }
    }

    /**
     * Invalidates cache after a conversation session.
     * Call this from speaking/conversation flow.
     */
    fun onConversationCompleted(language: LessonLanguage) {
        viewModelScope.launch {
            val userId = SupabaseApiHelper.getCurrentUserId() ?: return@launch
            analyticsService.invalidateCache(userId, language)

            if (language == _selectedLanguage.value) {
                loadLanguageProgress(forceRefresh = true)
            }
        }
    }

    /**
     * Invalidates cache after adding vocabulary.
     * Call this from vocabulary flow.
     */
    fun onVocabularyAdded(language: LessonLanguage) {
        viewModelScope.launch {
            val userId = SupabaseApiHelper.getCurrentUserId() ?: return@launch
            analyticsService.invalidateCache(userId, language)

            if (language == _selectedLanguage.value) {
                loadLanguageProgress(forceRefresh = true)
            }
        }
    }

    /**
     * Clears all cached progress data.
     * Call this on logout.
     */
    fun clearCache() {
        analyticsService.clearAllCache()
    }

    // ============================================================================
    // Real-time Updates
    // ============================================================================

    private fun startRealtimeUpdates() {
        viewModelScope.launch {
            val userId = SupabaseApiHelper.getCurrentUserId() ?: return@launch

            try {
                realtimeService.start(userId)
                _isRealtimeConnected.value = true

                // Listen to progress update events
                realtimeService.progressUpdates.collect { event ->
                    handleRealtimeUpdate(event)
                }
            } catch (e: Exception) {
                println("[ProgressViewModel] Failed to start real-time updates: ${e.message}")
                _isRealtimeConnected.value = false
            }
        }
    }

    private fun stopRealtimeUpdates() {
        viewModelScope.launch {
            realtimeService.stop()
            _isRealtimeConnected.value = false
        }
    }

    private fun handleRealtimeUpdate(event: ProgressUpdateEvent) {
        println("[ProgressViewModel] 🔔 Real-time update received: $event")

        // Refresh progress if it's for the current language
        if (event.language == null || event.language == _selectedLanguage.value) {
            loadLanguageProgress(forceRefresh = true)
            loadProgressHistory() // Also refresh history
        }
    }

    // ============================================================================
    // Historical Trends
    // ============================================================================

    fun loadProgressHistory() {
        viewModelScope.launch {
            val userId = SupabaseApiHelper.getCurrentUserId() ?: return@launch
            _isLoadingHistory.value = true

            historyRepository.getProgressHistory(
                userId = userId,
                language = _selectedLanguage.value,
                days = _selectedTimeRange.value.days,
            )
                .onSuccess { history ->
                    _progressHistory.value = history
                    println("[ProgressViewModel] ✅ Loaded ${history.size} history snapshots")
                }
                .onFailure { error ->
                    println("[ProgressViewModel] ⚠️ Failed to load history: ${error.message}")
                    _progressHistory.value = emptyList()
                }

            _isLoadingHistory.value = false
        }
    }

    fun selectTimeRange(timeRange: HistoryTimeRange) {
        if (_selectedTimeRange.value != timeRange) {
            _selectedTimeRange.value = timeRange
            loadProgressHistory()
        }
    }

    fun captureCurrentSnapshot() {
        viewModelScope.launch {
            val userId = SupabaseApiHelper.getCurrentUserId() ?: return@launch
            historyRepository.captureSnapshot(
                userId = userId,
                language = _selectedLanguage.value,
            )
                .onSuccess {
                    println("[ProgressViewModel] ✅ Snapshot captured")
                    loadProgressHistory() // Reload to show new snapshot
                }
                .onFailure { error ->
                    println("[ProgressViewModel] ⚠️ Failed to capture snapshot: ${error.message}")
                }
        }
    }

    // ============================================================================
    // Export & Share
    // ============================================================================

    fun showShareDialog() {
        _showShareDialog.value = true
    }

    fun hideShareDialog() {
        _showShareDialog.value = false
    }

    fun exportToHTML() {
        val state = _progressState.value
        if (state !is ProgressTrackerState.Success) return

        viewModelScope.launch {
            exportService.exportToHTML(
                progress = state.progress,
                userName = "Student", // TODO: Get from user profile
            )
                .onSuccess { filePath ->
                    _exportMessage.value = "✅ Report exported to: $filePath"
                    exportService.openFile(filePath)
                }
                .onFailure { error ->
                    _exportMessage.value = "❌ Export failed: ${error.message}"
                }
        }
    }

    fun copyProgressText() {
        val state = _progressState.value
        if (state !is ProgressTrackerState.Success) return

        viewModelScope.launch {
            val text =
                exportService.generateShareableText(
                    progress = state.progress,
                    userName = "I",
                )

            exportService.copyToClipboard(text)
                .onSuccess {
                    _exportMessage.value = "✅ Progress copied to clipboard!"
                }
                .onFailure { error ->
                    _exportMessage.value = "❌ Copy failed: ${error.message}"
                }
        }
    }

    fun shareLink() {
        val state = _progressState.value
        if (state !is ProgressTrackerState.Success) return

        viewModelScope.launch {
            val userId = SupabaseApiHelper.getCurrentUserId() ?: return@launch
            val link = exportService.generateShareableLink(state.progress, userId)
            exportService.copyToClipboard(link)
                .onSuccess {
                    _exportMessage.value = "✅ Link copied: $link"
                }
                .onFailure { error ->
                    _exportMessage.value = "❌ Failed to copy link: ${error.message}"
                }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    // Legacy methods for backwards compatibility
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
