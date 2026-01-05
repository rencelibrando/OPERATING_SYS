package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.LearningActivity
import org.example.project.domain.model.NavigationItem
import org.example.project.domain.model.User

class HomeViewModel : ViewModel() {
    private val _user = mutableStateOf(User.sampleUser())
    private val _navigationItems = mutableStateOf(NavigationItem.getDefaultNavigationItems())
    private val _learningActivities = mutableStateOf(LearningActivity.getDefaultActivities())
    private val _selectedNavigationItem = mutableStateOf("home")
    private val _isLoading = mutableStateOf(false)
    private val _showProfile = mutableStateOf(false)
    private val _selectedLessonId = mutableStateOf<String?>(null)
    private val _refreshTrigger = mutableStateOf(0L)

    val user: State<User> = _user
    val navigationItems: State<List<NavigationItem>> = _navigationItems
    val learningActivities: State<List<LearningActivity>> = _learningActivities
    val selectedNavigationItem: State<String> = _selectedNavigationItem
    val isLoading: State<Boolean> = _isLoading
    val showProfile: State<Boolean> = _showProfile
    val selectedLessonId: State<String?> = _selectedLessonId
    val refreshTrigger: State<Long> = _refreshTrigger

    fun onNavigationItemSelected(itemId: String) {
        _showProfile.value = false
        _selectedLessonId.value = null

        _selectedNavigationItem.value = itemId

        _navigationItems.value =
            _navigationItems.value.map { item ->
                item.copy(isSelected = item.id == itemId)
            }
    }

    fun onUserAvatarClicked() {
        _showProfile.value = true

        _navigationItems.value =
            _navigationItems.value.map { item ->
                item.copy(isSelected = false)
            }
        _selectedNavigationItem.value = ""

        println("Opening profile")
    }

    fun onCloseProfile() {
        _showProfile.value = false

        _selectedNavigationItem.value = "home"
        _navigationItems.value =
            _navigationItems.value.map { item ->
                item.copy(isSelected = item.id == "home")
            }

        println("Closing profile")
    }

    fun onLearningActivityClicked(activityId: String) {
        println("Navigating to activity: $activityId")
    }

    fun onContinueLearningClicked() {
        _isLoading.value = true

        println("Continuing learning journey...")

        _isLoading.value = false
    }

    fun refreshUserData() {
        _isLoading.value = true

        _isLoading.value = false
    }

    fun updateStreak(newStreak: Int) {
        _user.value = _user.value.copy(streak = newStreak)
    }

    fun onLessonSelected(lessonId: String) {
        println("[HomeViewModel] Lesson selected: $lessonId")
        _selectedLessonId.value = lessonId
        _showProfile.value = false
        _navigationItems.value = _navigationItems.value.map { it.copy(isSelected = false) }
        _selectedNavigationItem.value = ""
    }

    fun onCloseLessonPlayer() {
        println("[HomeViewModel] Closing lesson player")
        _selectedLessonId.value = null
        // Return to lessons screen
        onNavigationItemSelected("lessons")
    }

    fun onLessonCompleted(userId: String, lessonId: String) {
        println("[HomeViewModel] Lesson completed: $lessonId for user: $userId")
        // Trigger refresh by updating the timestamp
        _refreshTrigger.value = System.currentTimeMillis()
    }
}
