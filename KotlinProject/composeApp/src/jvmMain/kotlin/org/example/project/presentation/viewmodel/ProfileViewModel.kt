package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.*

/**
 * ViewModel for the Profile screen
 * 
 * Manages the state and business logic for user profile management,
 * including personal info, learning profile, and account settings
 */
class ProfileViewModel : ViewModel() {
    
    // Private mutable state
    private val _userProfile = mutableStateOf(UserProfile.getSampleProfile())
    private val _profileCompletion = mutableStateOf(ProfileCompletion.calculate(UserProfile.getSampleProfile()))
    private val _isEditing = mutableStateOf(false)
    private val _editingField = mutableStateOf<ProfileField?>(null)
    private val _isLoading = mutableStateOf(false)
    private val _isSaving = mutableStateOf(false)
    private val _showDeleteConfirmation = mutableStateOf(false)
    
    // Section editing state
    private val _editingSection = mutableStateOf<ProfileSection?>(null)
    private val _editingPersonalInfo = mutableStateOf<PersonalInfo?>(null)
    private val _editingLearningProfile = mutableStateOf<LearningProfile?>(null)
    
    // Public read-only state
    val userProfile: State<UserProfile> = _userProfile
    val profileCompletion: State<ProfileCompletion> = _profileCompletion
    val isEditing: State<Boolean> = _isEditing
    val editingField: State<ProfileField?> = _editingField
    val isLoading: State<Boolean> = _isLoading
    val isSaving: State<Boolean> = _isSaving
    val showDeleteConfirmation: State<Boolean> = _showDeleteConfirmation
    
    // Section editing state
    val editingSection: State<ProfileSection?> = _editingSection
    val editingPersonalInfo: State<PersonalInfo?> = _editingPersonalInfo
    val editingLearningProfile: State<LearningProfile?> = _editingLearningProfile
    
    /**
     * Handles starting edit mode for a specific field
     */
    fun onStartEditing(field: ProfileField) {
        _editingField.value = field
        _isEditing.value = true
    }
    
    /**
     * Handles canceling edit mode
     */
    fun onCancelEditing() {
        _editingField.value = null
        _isEditing.value = false
    }
    
    /**
     * Handles starting section edit mode
     */
    fun onStartSectionEdit(section: ProfileSection) {
        _editingSection.value = section
        when (section) {
            ProfileSection.PERSONAL_INFO -> {
                _editingPersonalInfo.value = _userProfile.value.personalInfo.copy()
            }
            ProfileSection.LEARNING_PROFILE -> {
                _editingLearningProfile.value = _userProfile.value.learningProfile.copy()
            }
            else -> {
                // Other sections don't support group editing yet
            }
        }
    }
    
    /**
     * Handles canceling section edit mode
     */
    fun onCancelSectionEdit() {
        _editingSection.value = null
        _editingPersonalInfo.value = null
        _editingLearningProfile.value = null
    }
    
    /**
     * Updates personal info field during editing
     */
    fun onUpdatePersonalInfoField(field: ProfileField, value: String) {
        val currentInfo = _editingPersonalInfo.value ?: return
        val updatedInfo = when (field) {
            ProfileField.FIRST_NAME -> currentInfo.copy(firstName = value)
            ProfileField.LAST_NAME -> currentInfo.copy(lastName = value)
            ProfileField.EMAIL -> currentInfo.copy(email = value)
            ProfileField.DATE_OF_BIRTH -> currentInfo.copy(dateOfBirth = value)
            ProfileField.LOCATION -> currentInfo.copy(location = value)
            ProfileField.NATIVE_LANGUAGE -> currentInfo.copy(nativeLanguage = value)
            ProfileField.BIO -> currentInfo.copy(bio = value)
            else -> currentInfo
        }
        _editingPersonalInfo.value = updatedInfo
    }
    
    /**
     * Updates target languages during editing
     */
    fun onUpdateTargetLanguages(languages: List<String>) {
        val currentInfo = _editingPersonalInfo.value ?: return
        _editingPersonalInfo.value = currentInfo.copy(targetLanguages = languages)
    }
    
    /**
     * Updates learning profile field during editing
     */
    fun onUpdateLearningProfileField(field: ProfileField, value: Any) {
        val currentProfile = _editingLearningProfile.value ?: return
        val updatedProfile = when (field) {
            ProfileField.CURRENT_LEVEL -> currentProfile.copy(currentLevel = value as String)
            ProfileField.PRIMARY_GOAL -> currentProfile.copy(primaryGoal = value as String)
            ProfileField.WEEKLY_GOAL_HOURS -> currentProfile.copy(weeklyGoalHours = value as Int)
            ProfileField.LEARNING_STYLE -> currentProfile.copy(preferredLearningStyle = value as String)
            else -> currentProfile
        }
        _editingLearningProfile.value = updatedProfile
    }
    
    /**
     * Updates learning profile list fields during editing
     */
    fun onUpdateLearningProfileList(field: ProfileField, values: List<String>) {
        val currentProfile = _editingLearningProfile.value ?: return
        val updatedProfile = when (field) {
            ProfileField.FOCUS_AREAS -> currentProfile.copy(focusAreas = values)
            ProfileField.TIME_SLOTS -> currentProfile.copy(availableTimeSlots = values)
            ProfileField.MOTIVATIONS -> currentProfile.copy(motivations = values)
            else -> currentProfile
        }
        _editingLearningProfile.value = updatedProfile
    }
    
    /**
     * Saves section changes
     */
    fun onSaveSectionChanges() {
        val section = _editingSection.value ?: return
        val currentProfile = _userProfile.value
        
        val updatedProfile = when (section) {
            ProfileSection.PERSONAL_INFO -> {
                _editingPersonalInfo.value?.let { editedInfo ->
                    currentProfile.copy(
                        personalInfo = editedInfo,
                        lastUpdated = System.currentTimeMillis()
                    )
                } ?: currentProfile
            }
            ProfileSection.LEARNING_PROFILE -> {
                _editingLearningProfile.value?.let { editedProfile ->
                    currentProfile.copy(
                        learningProfile = editedProfile,
                        lastUpdated = System.currentTimeMillis()
                    )
                } ?: currentProfile
            }
            else -> currentProfile
        }
        
        _userProfile.value = updatedProfile
        _profileCompletion.value = ProfileCompletion.calculate(updatedProfile)
        onCancelSectionEdit()
        saveProfile()
    }
    
    /**
     * Handles saving edited field
     */
    fun onSaveField(field: ProfileField, value: Any) {
        val currentProfile = _userProfile.value
        
        val updatedProfile = when (field) {
            ProfileField.FIRST_NAME -> currentProfile.copy(
                personalInfo = currentProfile.personalInfo.copy(firstName = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.LAST_NAME -> currentProfile.copy(
                personalInfo = currentProfile.personalInfo.copy(lastName = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.EMAIL -> currentProfile.copy(
                personalInfo = currentProfile.personalInfo.copy(email = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.DATE_OF_BIRTH -> currentProfile.copy(
                personalInfo = currentProfile.personalInfo.copy(dateOfBirth = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.LOCATION -> currentProfile.copy(
                personalInfo = currentProfile.personalInfo.copy(location = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.NATIVE_LANGUAGE -> currentProfile.copy(
                personalInfo = currentProfile.personalInfo.copy(nativeLanguage = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.TARGET_LANGUAGES -> currentProfile.copy(
                personalInfo = currentProfile.personalInfo.copy(
                    targetLanguages = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                ),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.BIO -> currentProfile.copy(
                personalInfo = currentProfile.personalInfo.copy(bio = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.CURRENT_LEVEL -> currentProfile.copy(
                learningProfile = currentProfile.learningProfile.copy(currentLevel = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.PRIMARY_GOAL -> currentProfile.copy(
                learningProfile = currentProfile.learningProfile.copy(primaryGoal = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.WEEKLY_GOAL_HOURS -> currentProfile.copy(
                learningProfile = currentProfile.learningProfile.copy(weeklyGoalHours = value as Int),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.LEARNING_STYLE -> currentProfile.copy(
                learningProfile = currentProfile.learningProfile.copy(preferredLearningStyle = value as String),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.FOCUS_AREAS -> currentProfile.copy(
                learningProfile = currentProfile.learningProfile.copy(
                    focusAreas = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                ),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.TIME_SLOTS -> currentProfile.copy(
                learningProfile = currentProfile.learningProfile.copy(
                    availableTimeSlots = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                ),
                lastUpdated = System.currentTimeMillis()
            )
            ProfileField.MOTIVATIONS -> currentProfile.copy(
                learningProfile = currentProfile.learningProfile.copy(
                    motivations = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                ),
                lastUpdated = System.currentTimeMillis()
            )
        }
        
        _userProfile.value = updatedProfile
        _profileCompletion.value = ProfileCompletion.calculate(updatedProfile)
        
        onCancelEditing()
        saveProfile()
    }
    
    /**
     * Handles profile picture upload
     */
    fun onUploadProfilePicture() {
        // TODO: Implement profile picture upload
        println("Uploading profile picture...")
    }
    
    /**
     * Handles account deletion request
     */
    fun onRequestAccountDeletion() {
        _showDeleteConfirmation.value = true
    }
    
    /**
     * Handles confirming account deletion
     */
    fun onConfirmAccountDeletion() {
        _showDeleteConfirmation.value = false
        // TODO: Implement account deletion
        println("Deleting account...")
    }
    
    /**
     * Handles canceling account deletion
     */
    fun onCancelAccountDeletion() {
        _showDeleteConfirmation.value = false
    }
    
    /**
     * Handles exporting profile data
     */
    fun onExportData() {
        // TODO: Implement data export
        println("Exporting profile data...")
    }
    
    /**
     * Handles changing password
     */
    fun onChangePassword() {
        // TODO: Navigate to password change screen
        println("Changing password...")
    }
    
    /**
     * Handles enabling/disabling two-factor authentication
     */
    fun onToggleTwoFactor(enabled: Boolean) {
        val currentProfile = _userProfile.value
        val updatedAccountInfo = currentProfile.accountInfo.copy(twoFactorEnabled = enabled)
        
        _userProfile.value = currentProfile.copy(
            accountInfo = updatedAccountInfo,
            lastUpdated = System.currentTimeMillis()
        )
        
        saveProfile()
    }
    
    /**
     * Handles email verification
     */
    fun onVerifyEmail() {
        // TODO: Send verification email
        println("Sending verification email...")
    }
    
    /**
     * Handles phone verification
     */
    fun onVerifyPhone() {
        // TODO: Send verification SMS
        println("Sending verification SMS...")
    }
    
    /**
     * Handles subscription management
     */
    fun onManageSubscription() {
        // TODO: Navigate to subscription management
        println("Managing subscription...")
    }
    
    /**
     * Refreshes profile data
     */
    fun refreshProfile() {
        _isLoading.value = true
        
        // TODO: Load profile from repository/storage
        // For now, simulate loading
        _userProfile.value = UserProfile.getSampleProfile()
        _profileCompletion.value = ProfileCompletion.calculate(_userProfile.value)
        
        _isLoading.value = false
    }
    
    /**
     * Saves profile data
     */
    private fun saveProfile() {
        _isSaving.value = true
        
        // TODO: Save profile to repository/storage
        // For now, simulate saving
        println("Saving profile: ${_userProfile.value}")
        
        _isSaving.value = false
    }
    
    /**
     * Gets available options for a specific field
     */
    fun getFieldOptions(field: ProfileField): List<String> {
        return when (field) {
            ProfileField.NATIVE_LANGUAGE, ProfileField.TARGET_LANGUAGES -> 
                PersonalInfo.getAvailableLanguages()
            ProfileField.CURRENT_LEVEL -> 
                LearningProfile.getLevelOptions()
            ProfileField.PRIMARY_GOAL -> 
                LearningProfile.getGoalOptions()
            ProfileField.LEARNING_STYLE -> 
                LearningProfile.getLearningStyleOptions()
            ProfileField.FOCUS_AREAS -> 
                LearningProfile.getFocusAreaOptions()
            ProfileField.TIME_SLOTS -> 
                LearningProfile.getTimeSlotOptions()
            ProfileField.MOTIVATIONS -> 
                LearningProfile.getMotivationOptions()
            else -> emptyList()
        }
    }
    
    /**
     * Gets current value for a specific field
     */
    fun getFieldValue(field: ProfileField): Any {
        val profile = _userProfile.value
        return when (field) {
            ProfileField.FIRST_NAME -> profile.personalInfo.firstName
            ProfileField.LAST_NAME -> profile.personalInfo.lastName
            ProfileField.EMAIL -> profile.personalInfo.email
            ProfileField.DATE_OF_BIRTH -> profile.personalInfo.dateOfBirth ?: ""
            ProfileField.LOCATION -> profile.personalInfo.location ?: ""
            ProfileField.NATIVE_LANGUAGE -> profile.personalInfo.nativeLanguage
            ProfileField.TARGET_LANGUAGES -> profile.personalInfo.targetLanguages
            ProfileField.BIO -> profile.personalInfo.bio ?: ""
            ProfileField.CURRENT_LEVEL -> profile.learningProfile.currentLevel
            ProfileField.PRIMARY_GOAL -> profile.learningProfile.primaryGoal
            ProfileField.WEEKLY_GOAL_HOURS -> profile.learningProfile.weeklyGoalHours
            ProfileField.LEARNING_STYLE -> profile.learningProfile.preferredLearningStyle
            ProfileField.FOCUS_AREAS -> profile.learningProfile.focusAreas
            ProfileField.TIME_SLOTS -> profile.learningProfile.availableTimeSlots
            ProfileField.MOTIVATIONS -> profile.learningProfile.motivations
        }
    }
    
    /**
     * Checks if a field can be edited
     */
    fun isFieldEditable(field: ProfileField): Boolean {
        // Email might not be editable depending on verification status
        return when (field) {
            ProfileField.EMAIL -> !_userProfile.value.accountInfo.isEmailVerified
            else -> true
        }
    }
}
