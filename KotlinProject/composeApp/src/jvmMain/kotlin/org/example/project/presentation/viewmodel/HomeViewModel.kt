package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.User
import org.example.project.domain.model.NavigationItem
import org.example.project.domain.model.LearningActivity

/**
 * ViewModel for the Home screen
 * 
 * Manages the state and business logic for the home screen,
 * including user data, navigation state, and learning activities
 */
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
    
    /**
     * Handles navigation item selection
     * Updates the selected state and triggers navigation
     * 
     * @param itemId The ID of the selected navigation item
     */
    fun onNavigationItemSelected(itemId: String) {
        // Close profile if it was open
        _showProfile.value = false
        
        _selectedNavigationItem.value = itemId
        
        // Update navigation items to reflect selection
        _navigationItems.value = _navigationItems.value.map { item ->
            item.copy(isSelected = item.id == itemId)
        }
    }
    
    /**
     * Handles user avatar click to show profile
     */
    fun onUserAvatarClicked() {
        _showProfile.value = true
        
        // Reset navigation selection when viewing profile
        _navigationItems.value = _navigationItems.value.map { item ->
            item.copy(isSelected = false)
        }
        _selectedNavigationItem.value = ""
        
        println("Opening profile")
    }
    
    /**
     * Handles closing profile view
     */
    fun onCloseProfile() {
        _showProfile.value = false
        
        // Return to home
        _selectedNavigationItem.value = "home"
        _navigationItems.value = _navigationItems.value.map { item ->
            item.copy(isSelected = item.id == "home")
        }
        
        println("Closing profile")
    }
    
    /**
     * Handles learning activity click
     * In a real implementation, this would navigate to the specific activity
     * 
     * @param activityId The ID of the clicked activity
     */
    fun onLearningActivityClicked(activityId: String) {
        // TODO: Implement navigation to specific activity
        // For now, we'll just print the action (in real app, use navigation)
        println("Navigating to activity: $activityId")
    }
    
    /**
     * Handles continue learning button click
     * Resumes the user's learning journey
     */
    fun onContinueLearningClicked() {
        _isLoading.value = true
        
        // Simulate loading (in real app, this would start a lesson)
        // TODO: Implement actual lesson continuation logic
        println("Continuing learning journey...")
        
        // Reset loading state after simulation
        _isLoading.value = false
    }
    
    /**
     * Refreshes user data
     * In a real implementation, this would fetch data from a repository
     */
    fun refreshUserData() {
        _isLoading.value = true
        
        // TODO: Implement actual data fetching from repository
        // For now, we'll simulate a refresh
        
        _isLoading.value = false
    }
    
    /**
     * Updates user streak (for demonstration purposes)
     * In a real app, this would be handled by the backend
     */
    fun updateStreak(newStreak: Int) {
        _user.value = _user.value.copy(streak = newStreak)
    }
}
