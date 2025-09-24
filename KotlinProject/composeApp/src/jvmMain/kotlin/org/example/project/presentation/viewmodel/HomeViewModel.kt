package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.User
import org.example.project.domain.model.NavigationItem
import org.example.project.domain.model.LearningActivity


class HomeViewModel : ViewModel() {
    
    // Private mutable state
    private val _user = mutableStateOf(User.sampleUser())
    private val _navigationItems = mutableStateOf(NavigationItem.getDefaultNavigationItems())
    private val _learningActivities = mutableStateOf(LearningActivity.getDefaultActivities())
    private val _selectedNavigationItem = mutableStateOf("home")
    private val _isLoading = mutableStateOf(false)
    private val _showProfile = mutableStateOf(false)
    
    // Public read-only state
    val user: State<User> = _user
    val navigationItems: State<List<NavigationItem>> = _navigationItems
    val learningActivities: State<List<LearningActivity>> = _learningActivities
    val selectedNavigationItem: State<String> = _selectedNavigationItem
    val isLoading: State<Boolean> = _isLoading
    val showProfile: State<Boolean> = _showProfile
    

    fun onNavigationItemSelected(itemId: String) {
        // Close profile if it was open
        _showProfile.value = false
        
        _selectedNavigationItem.value = itemId
        
        // Update navigation items to reflect selection
        _navigationItems.value = _navigationItems.value.map { item ->
            item.copy(isSelected = item.id == itemId)
        }
    }
    

    fun onUserAvatarClicked() {
        _showProfile.value = true
        
        // Reset navigation selection when viewing profile
        _navigationItems.value = _navigationItems.value.map { item ->
            item.copy(isSelected = false)
        }
        _selectedNavigationItem.value = ""
        
        println("Opening profile")
    }
    

    fun onCloseProfile() {
        _showProfile.value = false
        
        // Return to home
        _selectedNavigationItem.value = "home"
        _navigationItems.value = _navigationItems.value.map { item ->
            item.copy(isSelected = item.id == "home")
        }
        
        println("Closing profile")
    }

    fun onLearningActivityClicked(activityId: String) {
        // TODO: Implement navigation to specific activity
        // For now, we'll just print the action (in real app, use navigation)
        println("Navigating to activity: $activityId")
    }

    fun onContinueLearningClicked() {
        _isLoading.value = true
        
        // Simulate loading (in real app, this would start a lesson)
        // TODO: Implement actual lesson continuation logic
        println("Continuing learning journey...")
        
        // Reset loading state after simulation
        _isLoading.value = false
    }

    fun refreshUserData() {
        _isLoading.value = true
        
        // TODO: Implement actual data fetching from repository
        // For now, we'll simulate a refresh
        
        _isLoading.value = false
    }

    fun updateStreak(newStreak: Int) {
        _user.value = _user.value.copy(streak = newStreak)
    }
}
