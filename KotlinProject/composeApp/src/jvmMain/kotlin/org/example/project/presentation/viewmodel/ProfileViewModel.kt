package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.domain.model.*
import org.example.project.core.auth.User as AuthUser
import org.example.project.core.auth.RealSupabaseAuthService
import org.example.project.core.profile.ProfileService
import org.example.project.core.image.ImageUploadService
import org.example.project.core.image.DesktopFilePicker

class ProfileViewModel : ViewModel() {
    
    private val profileService = ProfileService()
    private val imageUploadService = ImageUploadService()
    private val authService = RealSupabaseAuthService()
    private val filePicker = DesktopFilePicker()
    
    // Private mutable state
    private val _userProfile = mutableStateOf(UserProfile.getDefaultProfile())
    private val _profileCompletion = mutableStateOf(ProfileCompletion.calculate(UserProfile.getDefaultProfile()))
    private val _isEditing = mutableStateOf(false)
    private val _editingField = mutableStateOf<ProfileField?>(null)
    private val _isLoading = mutableStateOf(false)
    private val _isSaving = mutableStateOf(false)
    private val _showDeleteConfirmation = mutableStateOf(false)
    
    // Section editing state
    private val _editingSection = mutableStateOf<ProfileSection?>(null)
    private val _editingPersonalInfo = mutableStateOf<PersonalInfo?>(null)
    private val _editingLearningProfile = mutableStateOf<LearningProfile?>(null)
    
    // Profile picture editing state
    private val _isEditingProfilePicture = mutableStateOf(false)
    private val _tempProfileImageBytes = mutableStateOf<ByteArray?>(null)
    private val _tempProfileImageUrl = mutableStateOf<String?>(null)
    
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
    
    // Profile picture editing state
    val isEditingProfilePicture: State<Boolean> = _isEditingProfilePicture
    val tempProfileImageBytes: State<ByteArray?> = _tempProfileImageBytes
    val tempProfileImageUrl: State<String?> = _tempProfileImageUrl

    fun initializeWithAuthenticatedUser(authUser: AuthUser) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                println("Loading profile data for user: ${authUser.email}")
                
                // Load existing profile data from Supabase
                val personalInfoResult = profileService.loadPersonalInfo()
                val personalInfo = personalInfoResult.getOrNull() ?: PersonalInfo.getDefault()
                
                val learningProfileResult = profileService.loadLearningProfile()
                val learningProfile = learningProfileResult.getOrNull() ?: LearningProfile.getDefault()
                
                // Merge with authenticated user data (auth data takes precedence)
                val mergedPersonalInfo = personalInfo.copy(
                    firstName = if (personalInfo.firstName.isNotEmpty()) personalInfo.firstName else authUser.firstName,
                    lastName = if (personalInfo.lastName.isNotEmpty()) personalInfo.lastName else authUser.lastName,
                    email = authUser.email,
                    avatar = if (personalInfo.avatar.isNotEmpty()) personalInfo.avatar else authUser.initials
                )
                
                val updatedProfile = UserProfile.getDefaultProfile().copy(
                    userId = authUser.id,
                    personalInfo = mergedPersonalInfo,
                    learningProfile = learningProfile,
                    lastUpdated = System.currentTimeMillis()
                )
                
                _userProfile.value = updatedProfile
                _profileCompletion.value = ProfileCompletion.calculate(updatedProfile)
                
                println("Profile initialized with Supabase data for user: ${authUser.email}")
                println("   firstName: ${mergedPersonalInfo.firstName}")
                println("   lastName: ${mergedPersonalInfo.lastName}")
                println("   profileImageUrl: ${mergedPersonalInfo.profileImageUrl}")
            } catch (e: Exception) {
                println("Failed to initialize profile from Supabase: ${e.message}")
                // Fallback to basic auth user data
                val currentProfile = _userProfile.value
                val updatedProfile = currentProfile.copy(
                    userId = authUser.id,
                    personalInfo = currentProfile.personalInfo.copy(
                        firstName = authUser.firstName,
                        lastName = authUser.lastName,
                        email = authUser.email,
                        avatar = authUser.initials
                    )
                )
                _userProfile.value = updatedProfile
                _profileCompletion.value = ProfileCompletion.calculate(updatedProfile)
            } finally {
                _isLoading.value = false
            }
        }
    }
    

    fun onStartEditing(field: ProfileField) {
        _editingField.value = field
        _isEditing.value = true
    }
    

    fun onCancelEditing() {
        _editingField.value = null
        _isEditing.value = false
    }
    

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
    

    fun onCancelSectionEdit() {
        _editingSection.value = null
        _editingPersonalInfo.value = null
        _editingLearningProfile.value = null
    }
    

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

    fun onUpdateTargetLanguages(languages: List<String>) {
        val currentInfo = _editingPersonalInfo.value ?: return
        _editingPersonalInfo.value = currentInfo.copy(targetLanguages = languages)
    }

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

    fun onUploadProfilePicture() {
        viewModelScope.launch {
            try {
                _isSaving.value = true
                
                // Show file picker dialog
                val imageBytes = filePicker.selectImage()
                if (imageBytes == null) {
                    println("No image selected")
                    return@launch
                }
                
                // Check file size (limit to 5MB)
                val maxSize = 5 * 1024 * 1024 // 5MB
                if (imageBytes.size > maxSize) {
                    println("Image too large. Please select an image smaller than 5MB")
                    return@launch
                }
                
                println("📸 Uploading image (${imageBytes.size} bytes)...")
                
                // Upload to Supabase Storage
                val uploadResult = imageUploadService.uploadProfilePicture(imageBytes)
                uploadResult.fold(
                    onSuccess = { imageUrl ->
                        println("Received image URL: $imageUrl")
                        
                        // Update profile with new image URL
                        val currentProfile = _userProfile.value
                        val updatedPersonalInfo = currentProfile.personalInfo.copy(
                            profileImageUrl = imageUrl,
                            avatar = "" // Clear emoji avatar when real image is uploaded
                        )
                        
                        _userProfile.value = currentProfile.copy(
                            personalInfo = updatedPersonalInfo,
                            lastUpdated = System.currentTimeMillis()
                        )
                        
                        println("Updated profile with image URL: ${_userProfile.value.personalInfo.profileImageUrl}")
                        
                        _profileCompletion.value = ProfileCompletion.calculate(_userProfile.value)
                        
                        // Save to Supabase
                        saveProfile()
                        
                        
                        println("Profile picture uploaded successfully!")
                    },
                    onFailure = { error ->
                        println("Failed to upload profile picture: ${error.message}")
                        // TODO: Show error message to user
                    }
                )
            } catch (e: Exception) {
                println("Error uploading profile picture: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onStartProfilePictureEdit() {
        viewModelScope.launch {
            try {
                // Show file picker dialog
                val imageBytes = filePicker.selectImage()
                if (imageBytes == null) {
                    println("No image selected")
                    return@launch
                }
                
                // Check file size (limit to 5MB)
                val maxSize = 5 * 1024 * 1024 // 5MB
                if (imageBytes.size > maxSize) {
                    println("Image too large. Please select an image smaller than 5MB")
                    return@launch
                }
                
                // Store temporarily and enter edit mode
                _tempProfileImageBytes.value = imageBytes
                _isEditingProfilePicture.value = true
                
                println("Profile picture ready for preview (${imageBytes.size} bytes)")
            } catch (e: Exception) {
                println("Error selecting profile picture: ${e.message}")
            }
        }
    }

    fun onSaveProfilePicture() {
        val imageBytes = _tempProfileImageBytes.value
        if (imageBytes == null) {
            onCancelProfilePictureEdit()
            return
        }
        
        viewModelScope.launch {
            try {
                _isSaving.value = true
                
                println("Uploading profile picture (${imageBytes.size} bytes)...")
                
                // Delete old profile picture if it exists
                val currentProfile = _userProfile.value
                val oldImageUrl = currentProfile.personalInfo.profileImageUrl
                if (!oldImageUrl.isNullOrEmpty()) {
                    println("🗑Deleting old profile picture: $oldImageUrl")
                    imageUploadService.deleteProfilePicture(oldImageUrl)
                }
                
                // Upload to Supabase Storage
                val uploadResult = imageUploadService.uploadProfilePicture(imageBytes)
                uploadResult.fold(
                    onSuccess = { imageUrl ->
                        println("🔗 Received image URL: $imageUrl")
                        
                        // Update profile with new image URL
                        val updatedPersonalInfo = currentProfile.personalInfo.copy(
                            profileImageUrl = imageUrl,
                            avatar = "" // Clear emoji avatar when real image is uploaded
                        )
                        
                        _userProfile.value = currentProfile.copy(
                            personalInfo = updatedPersonalInfo,
                            lastUpdated = System.currentTimeMillis()
                        )
                        
                        println("Updated profile with image URL: ${_userProfile.value.personalInfo.profileImageUrl}")
                        
                        _profileCompletion.value = ProfileCompletion.calculate(_userProfile.value)
                        
                        // Save to Supabase
                        saveProfile()
                        
                        // Update user metadata in Supabase Auth
                        updateUserMetadataInAuth(imageUrl)
                        
                        // Clear edit state
                        onCancelProfilePictureEdit()
                        
                        println("Profile picture saved successfully!")
                    },
                    onFailure = { error ->
                        println("Failed to upload profile picture: ${error.message}")
                        // TODO: Show error message to user
                    }
                )
            } catch (e: Exception) {
                println("Error saving profile picture: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onCancelProfilePictureEdit() {
        _isEditingProfilePicture.value = false
        _tempProfileImageBytes.value = null
        _tempProfileImageUrl.value = null
    }
    

    fun onRequestAccountDeletion() {
        _showDeleteConfirmation.value = true
    }



    fun onExportData() {
        println("Exporting profile data...")
    }

    fun onChangePassword() {
        // TODO: Navigate to password change screen
        println("Changing password...")
    }

    fun onToggleTwoFactor(enabled: Boolean) {
        val currentProfile = _userProfile.value
        val updatedAccountInfo = currentProfile.accountInfo.copy(twoFactorEnabled = enabled)
        
        _userProfile.value = currentProfile.copy(
            accountInfo = updatedAccountInfo,
            lastUpdated = System.currentTimeMillis()
        )
        
        saveProfile()
    }

    fun onVerifyEmail() {
        // TODO: Send verification email
        println("Sending verification email...")
    }

    fun onVerifyPhone() {
        // TODO: Send verification SMS
        println("Sending verification SMS...")
    }
    

    fun onManageSubscription() {
        // TODO: Navigate to subscription management
        println("Managing subscription...")
    }

    private fun saveProfile() {
        _isSaving.value = true
        
        viewModelScope.launch {
            try {
                val profile = _userProfile.value
                
                // Save personal info to Supabase
                profileService.updatePersonalInfo(profile.personalInfo)
                
                // Save learning profile to Supabase
                profileService.updateLearningProfile(profile.learningProfile)
                
                println("Profile saved to Supabase successfully")
            } catch (e: Exception) {
                println("Failed to save profile to Supabase: ${e.message}")
                // TODO: Show error message to user
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    private suspend fun updateUserMetadataInAuth(profileImageUrl: String?) {
        try {
            println("Updating user metadata in Supabase Auth...")
            val result = authService.updateUserMetadata(profileImageUrl)
            result.fold(
                onSuccess = {
                    println("User metadata updated in Supabase Auth successfully")
                },
                onFailure = { error ->
                    println("Failed to update user metadata in Supabase Auth: ${error.message}")
                }
            )
        } catch (e: Exception) {
            println("Error updating user metadata in Supabase Auth: ${e.message}")
        }
    }

}
