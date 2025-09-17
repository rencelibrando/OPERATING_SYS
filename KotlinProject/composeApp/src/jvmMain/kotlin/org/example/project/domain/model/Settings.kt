package org.example.project.domain.model

/**
 * Represents user settings and preferences
 * 
 * @Variable userId Unique identifier for the user
 * @Variable notificationSettings Notification preferences
 * @Variable learningSettings Learning preferences
 * @Variable appSettings Application preferences
 * @Variable privacySettings Privacy preferences
 * @Variable lastUpdated When settings were last updated
 */
data class UserSettings(
    val userId: String,
    val notificationSettings: NotificationSettings,
    val learningSettings: LearningSettings,
    val appSettings: AppSettings,
    val privacySettings: PrivacySettings,
    val lastUpdated: Long
) {
    companion object {
        /**
         * Creates default user settings
         */
        fun getDefaultSettings(): UserSettings = UserSettings(
            userId = "user_001",
            notificationSettings = NotificationSettings.getDefault(),
            learningSettings = LearningSettings.getDefault(),
            appSettings = AppSettings.getDefault(),
            privacySettings = PrivacySettings.getDefault(),
            lastUpdated = System.currentTimeMillis()
        )
        
        /**
         * Creates sample user settings for demo
         */
        fun getSampleSettings(): UserSettings = UserSettings(
            userId = "user_001",
            notificationSettings = NotificationSettings(
                dailyReminders = true,
                weeklyProgress = true,
                achievementNotifications = true,
                studyStreakReminders = true,
                lessonRecommendations = false,
                socialUpdates = true,
                emailNotifications = true,
                pushNotifications = true,
                soundEnabled = true,
                vibrationEnabled = false
            ),
            learningSettings = LearningSettings(
                dailyGoalMinutes = 30,
                preferredDifficulty = "Intermediate",
                autoplayAudio = true,
                showTranslations = true,
                practiceReminders = true,
                adaptiveLearning = true,
                offlineMode = false,
                darkMode = false,
                languageInterface = "English",
                fontSize = "Medium"
            ),
            appSettings = AppSettings(
                autoSave = true,
                dataSync = true,
                wifiOnlyDownloads = true,
                cacheSize = "100 MB",
                analyticsEnabled = true,
                crashReporting = true,
                betaFeatures = false,
                hapticFeedback = true
            ),
            privacySettings = PrivacySettings(
                shareProgress = false,
                publicProfile = false,
                dataCollection = true,
                personalization = true,
                targetedAds = false,
                thirdPartySharing = false
            ),
            lastUpdated = System.currentTimeMillis()
        )
    }
}

/**
 * Represents notification settings
 */
data class NotificationSettings(
    val dailyReminders: Boolean = true,
    val weeklyProgress: Boolean = true,
    val achievementNotifications: Boolean = true,
    val studyStreakReminders: Boolean = true,
    val lessonRecommendations: Boolean = true,
    val socialUpdates: Boolean = false,
    val emailNotifications: Boolean = true,
    val pushNotifications: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
) {
    companion object {
        fun getDefault(): NotificationSettings = NotificationSettings()
    }
}

/**
 * Represents learning preferences
 */
data class LearningSettings(
    val dailyGoalMinutes: Int = 15,
    val preferredDifficulty: String = "Beginner",
    val autoplayAudio: Boolean = true,
    val showTranslations: Boolean = true,
    val practiceReminders: Boolean = true,
    val adaptiveLearning: Boolean = true,
    val offlineMode: Boolean = false,
    val darkMode: Boolean = false,
    val languageInterface: String = "English",
    val fontSize: String = "Medium"
) {
    companion object {
        fun getDefault(): LearningSettings = LearningSettings()
        
        fun getDifficultyOptions(): List<String> = listOf("Beginner", "Intermediate", "Advanced")
        fun getFontSizeOptions(): List<String> = listOf("Small", "Medium", "Large", "Extra Large")
        fun getLanguageOptions(): List<String> = listOf("English", "Spanish", "French", "German", "Chinese", "Japanese")
    }
}

/**
 * Represents application settings
 */
data class AppSettings(
    val autoSave: Boolean = true,
    val dataSync: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val cacheSize: String = "50 MB",
    val analyticsEnabled: Boolean = true,
    val crashReporting: Boolean = true,
    val betaFeatures: Boolean = false,
    val hapticFeedback: Boolean = true
) {
    companion object {
        fun getDefault(): AppSettings = AppSettings()
        
        fun getCacheSizeOptions(): List<String> = listOf("25 MB", "50 MB", "100 MB", "200 MB", "500 MB")
    }
}

/**
 * Represents privacy settings
 */
data class PrivacySettings(
    val shareProgress: Boolean = false,
    val publicProfile: Boolean = false,
    val dataCollection: Boolean = true,
    val personalization: Boolean = true,
    val targetedAds: Boolean = false,
    val thirdPartySharing: Boolean = false
) {
    companion object {
        fun getDefault(): PrivacySettings = PrivacySettings()
    }
}

/**
 * Represents a settings section for grouping related settings
 */
data class SettingsSection(
    val id: String,
    val title: String,
    val description: String?,
    val icon: String,
    val items: List<SettingItem>
)

/**
 * Represents individual setting items
 */
sealed class SettingItem {
    abstract val id: String
    abstract val title: String
    abstract val description: String?
    
    data class Toggle(
        override val id: String,
        override val title: String,
        override val description: String? = null,
        val isEnabled: Boolean,
        val onToggle: (Boolean) -> Unit
    ) : SettingItem()
    
    data class Selection(
        override val id: String,
        override val title: String,
        override val description: String? = null,
        val currentValue: String,
        val options: List<String>,
        val onSelection: (String) -> Unit
    ) : SettingItem()
    
    data class Action(
        override val id: String,
        override val title: String,
        override val description: String? = null,
        val actionText: String = "Tap to configure",
        val onClick: () -> Unit
    ) : SettingItem()
    
    data class Info(
        override val id: String,
        override val title: String,
        override val description: String? = null,
        val value: String
    ) : SettingItem()
}

