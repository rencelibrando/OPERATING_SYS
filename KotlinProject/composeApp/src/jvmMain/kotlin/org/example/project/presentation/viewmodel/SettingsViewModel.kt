package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.*

class SettingsViewModel : ViewModel() {
    
    // Private mutable state
    private val _userSettings = mutableStateOf(UserSettings.getSampleSettings())
    private val _isLoading = mutableStateOf(false)
    private val _isSaving = mutableStateOf(false)
    
    // Public read-only state
    val userSettings: State<UserSettings> = _userSettings
    val isLoading: State<Boolean> = _isLoading
    val isSaving: State<Boolean> = _isSaving
    
    /**
     * Handles notification setting toggles
     */
    fun onNotificationToggle(settingType: NotificationSettingType, enabled: Boolean) {
        val currentSettings = _userSettings.value
        val updatedNotificationSettings = when (settingType) {
            NotificationSettingType.DAILY_REMINDERS -> 
                currentSettings.notificationSettings.copy(dailyReminders = enabled)
            NotificationSettingType.WEEKLY_PROGRESS -> 
                currentSettings.notificationSettings.copy(weeklyProgress = enabled)
            NotificationSettingType.ACHIEVEMENT_NOTIFICATIONS -> 
                currentSettings.notificationSettings.copy(achievementNotifications = enabled)
            NotificationSettingType.STUDY_STREAK_REMINDERS -> 
                currentSettings.notificationSettings.copy(studyStreakReminders = enabled)
            NotificationSettingType.LESSON_RECOMMENDATIONS -> 
                currentSettings.notificationSettings.copy(lessonRecommendations = enabled)
            NotificationSettingType.SOCIAL_UPDATES -> 
                currentSettings.notificationSettings.copy(socialUpdates = enabled)
            NotificationSettingType.EMAIL_NOTIFICATIONS -> 
                currentSettings.notificationSettings.copy(emailNotifications = enabled)
            NotificationSettingType.PUSH_NOTIFICATIONS -> 
                currentSettings.notificationSettings.copy(pushNotifications = enabled)
            NotificationSettingType.SOUND_ENABLED -> 
                currentSettings.notificationSettings.copy(soundEnabled = enabled)
            NotificationSettingType.VIBRATION_ENABLED -> 
                currentSettings.notificationSettings.copy(vibrationEnabled = enabled)
        }
        
        _userSettings.value = currentSettings.copy(
            notificationSettings = updatedNotificationSettings,
            lastUpdated = System.currentTimeMillis()
        )
        
        saveSettings()
    }

    fun onLearningSettingChanged(settingType: LearningSettingType, value: Any) {
        val currentSettings = _userSettings.value
        val updatedLearningSettings = when (settingType) {
            LearningSettingType.DAILY_GOAL_MINUTES -> 
                currentSettings.learningSettings.copy(dailyGoalMinutes = value as Int)
            LearningSettingType.PREFERRED_DIFFICULTY -> 
                currentSettings.learningSettings.copy(preferredDifficulty = value as String)
            LearningSettingType.AUTOPLAY_AUDIO -> 
                currentSettings.learningSettings.copy(autoplayAudio = value as Boolean)
            LearningSettingType.SHOW_TRANSLATIONS -> 
                currentSettings.learningSettings.copy(showTranslations = value as Boolean)
            LearningSettingType.PRACTICE_REMINDERS -> 
                currentSettings.learningSettings.copy(practiceReminders = value as Boolean)
            LearningSettingType.ADAPTIVE_LEARNING -> 
                currentSettings.learningSettings.copy(adaptiveLearning = value as Boolean)
            LearningSettingType.OFFLINE_MODE -> 
                currentSettings.learningSettings.copy(offlineMode = value as Boolean)
            LearningSettingType.DARK_MODE -> 
                currentSettings.learningSettings.copy(darkMode = value as Boolean)
            LearningSettingType.LANGUAGE_INTERFACE -> 
                currentSettings.learningSettings.copy(languageInterface = value as String)
            LearningSettingType.FONT_SIZE -> 
                currentSettings.learningSettings.copy(fontSize = value as String)
        }
        
        _userSettings.value = currentSettings.copy(
            learningSettings = updatedLearningSettings,
            lastUpdated = System.currentTimeMillis()
        )
        
        saveSettings()
    }

    fun onAppSettingChanged(settingType: AppSettingType, value: Any) {
        val currentSettings = _userSettings.value
        val updatedAppSettings = when (settingType) {
            AppSettingType.AUTO_SAVE -> 
                currentSettings.appSettings.copy(autoSave = value as Boolean)
            AppSettingType.DATA_SYNC -> 
                currentSettings.appSettings.copy(dataSync = value as Boolean)
            AppSettingType.WIFI_ONLY_DOWNLOADS -> 
                currentSettings.appSettings.copy(wifiOnlyDownloads = value as Boolean)
            AppSettingType.CACHE_SIZE -> 
                currentSettings.appSettings.copy(cacheSize = value as String)
            AppSettingType.ANALYTICS_ENABLED -> 
                currentSettings.appSettings.copy(analyticsEnabled = value as Boolean)
            AppSettingType.CRASH_REPORTING -> 
                currentSettings.appSettings.copy(crashReporting = value as Boolean)
            AppSettingType.BETA_FEATURES -> 
                currentSettings.appSettings.copy(betaFeatures = value as Boolean)
            AppSettingType.HAPTIC_FEEDBACK -> 
                currentSettings.appSettings.copy(hapticFeedback = value as Boolean)
        }
        
        _userSettings.value = currentSettings.copy(
            appSettings = updatedAppSettings,
            lastUpdated = System.currentTimeMillis()
        )
        
        saveSettings()
    }
    
    /**
     * Handles privacy setting changes
     */
    fun onPrivacySettingChanged(settingType: PrivacySettingType, enabled: Boolean) {
        val currentSettings = _userSettings.value
        val updatedPrivacySettings = when (settingType) {
            PrivacySettingType.SHARE_PROGRESS -> 
                currentSettings.privacySettings.copy(shareProgress = enabled)
            PrivacySettingType.PUBLIC_PROFILE -> 
                currentSettings.privacySettings.copy(publicProfile = enabled)
            PrivacySettingType.DATA_COLLECTION -> 
                currentSettings.privacySettings.copy(dataCollection = enabled)
            PrivacySettingType.PERSONALIZATION -> 
                currentSettings.privacySettings.copy(personalization = enabled)
            PrivacySettingType.TARGETED_ADS -> 
                currentSettings.privacySettings.copy(targetedAds = enabled)
            PrivacySettingType.THIRD_PARTY_SHARING -> 
                currentSettings.privacySettings.copy(thirdPartySharing = enabled)
        }
        
        _userSettings.value = currentSettings.copy(
            privacySettings = updatedPrivacySettings,
            lastUpdated = System.currentTimeMillis()
        )
        
        saveSettings()
    }

    fun onAccountAction(action: AccountAction) {
        when (action) {
            AccountAction.EXPORT_DATA -> exportUserData()
            AccountAction.DELETE_ACCOUNT -> deleteAccount()
            AccountAction.CLEAR_CACHE -> clearCache()
            AccountAction.RESET_SETTINGS -> resetSettings()
            AccountAction.CONTACT_SUPPORT -> contactSupport()
            AccountAction.RATE_APP -> rateApp()
            AccountAction.SHARE_APP -> shareApp()
            AccountAction.VIEW_PRIVACY_POLICY -> viewPrivacyPolicy()
            AccountAction.VIEW_TERMS -> viewTermsOfService()
        }
    }

    private fun saveSettings() {
        _isSaving.value = true
        
        // TODO: Save settings to repository/storage
        // For now, simulate saving
        println("Saving settings: ${_userSettings.value}")
        
        _isSaving.value = false
    }
    

    private fun exportUserData() {
        // TODO: Implement data export functionality
        println("Exporting user data...")
    }
    

    private fun deleteAccount() {
        println("Deleting account...")
    }

    private fun clearCache() {
        println("Clearing cache...")
    }

    private fun resetSettings() {
        _userSettings.value = UserSettings.getDefaultSettings()
        saveSettings()
    }
    

    private fun contactSupport() {
        println("Contacting support...")
    }
    

    private fun rateApp() {
        println("Rating app...")
    }

    private fun shareApp() {
        println("Sharing app...")
    }

    private fun viewPrivacyPolicy() {
        println("Viewing privacy policy...")
    }

    private fun viewTermsOfService() {
        println("Viewing terms of service...")
    }
}

enum class NotificationSettingType {
    DAILY_REMINDERS,
    WEEKLY_PROGRESS,
    ACHIEVEMENT_NOTIFICATIONS,
    STUDY_STREAK_REMINDERS,
    LESSON_RECOMMENDATIONS,
    SOCIAL_UPDATES,
    EMAIL_NOTIFICATIONS,
    PUSH_NOTIFICATIONS,
    SOUND_ENABLED,
    VIBRATION_ENABLED
}

enum class LearningSettingType {
    DAILY_GOAL_MINUTES,
    PREFERRED_DIFFICULTY,
    AUTOPLAY_AUDIO,
    SHOW_TRANSLATIONS,
    PRACTICE_REMINDERS,
    ADAPTIVE_LEARNING,
    OFFLINE_MODE,
    DARK_MODE,
    LANGUAGE_INTERFACE,
    FONT_SIZE
}

enum class AppSettingType {
    AUTO_SAVE,
    DATA_SYNC,
    WIFI_ONLY_DOWNLOADS,
    CACHE_SIZE,
    ANALYTICS_ENABLED,
    CRASH_REPORTING,
    BETA_FEATURES,
    HAPTIC_FEEDBACK
}

enum class PrivacySettingType {
    SHARE_PROGRESS,
    PUBLIC_PROFILE,
    DATA_COLLECTION,
    PERSONALIZATION,
    TARGETED_ADS,
    THIRD_PARTY_SHARING
}

enum class AccountAction {
    EXPORT_DATA,
    DELETE_ACCOUNT,
    CLEAR_CACHE,
    RESET_SETTINGS,
    CONTACT_SUPPORT,
    RATE_APP,
    SHARE_APP,
    VIEW_PRIVACY_POLICY,
    VIEW_TERMS
}
