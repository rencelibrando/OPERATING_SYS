package org.example.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.*
import org.example.project.presentation.viewmodel.SettingsViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val userSettings by viewModel.userSettings
    val isLoading by viewModel.isLoading
    val isSaving by viewModel.isSaving

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            if (isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = WordBridgeColors.PrimaryPurple,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saving...",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = WordBridgeColors.PrimaryPurple)
            }
        } else {
            SettingsSection(
                title = "Notifications",
                icon = "🔔",
                description = "Manage how you receive notifications",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggleItem(
                        title = "Daily Reminders",
                        description = "Get reminded to practice daily",
                        isChecked = userSettings.notificationSettings.dailyReminders,
                        onToggle = { viewModel.onNotificationToggle(NotificationSettingType.DAILY_REMINDERS, it) },
                    )

                    SettingsToggleItem(
                        title = "Weekly Progress",
                        description = "Receive weekly progress summaries",
                        isChecked = userSettings.notificationSettings.weeklyProgress,
                        onToggle = { viewModel.onNotificationToggle(NotificationSettingType.WEEKLY_PROGRESS, it) },
                    )

                    SettingsToggleItem(
                        title = "Achievement Notifications",
                        description = "Get notified when you unlock achievements",
                        isChecked = userSettings.notificationSettings.achievementNotifications,
                        onToggle = { viewModel.onNotificationToggle(NotificationSettingType.ACHIEVEMENT_NOTIFICATIONS, it) },
                    )

                    SettingsToggleItem(
                        title = "Study Streak Reminders",
                        description = "Don't break your learning streak",
                        isChecked = userSettings.notificationSettings.studyStreakReminders,
                        onToggle = { viewModel.onNotificationToggle(NotificationSettingType.STUDY_STREAK_REMINDERS, it) },
                    )

                    SettingsToggleItem(
                        title = "Push Notifications",
                        description = "Receive notifications on your device",
                        isChecked = userSettings.notificationSettings.pushNotifications,
                        onToggle = { viewModel.onNotificationToggle(NotificationSettingType.PUSH_NOTIFICATIONS, it) },
                    )

                    SettingsToggleItem(
                        title = "Sound",
                        description = "Play sound with notifications",
                        isChecked = userSettings.notificationSettings.soundEnabled,
                        onToggle = { viewModel.onNotificationToggle(NotificationSettingType.SOUND_ENABLED, it) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(
                title = "Learning",
                icon = "📚",
                description = "Customize your learning experience",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSelectionItem(
                        title = "Daily Goal",
                        description = "Target minutes per day",
                        currentValue = "${userSettings.learningSettings.dailyGoalMinutes} minutes",
                        onClick = { },
                    )

                    SettingsSelectionItem(
                        title = "Difficulty Level",
                        description = "Your current learning level",
                        currentValue = userSettings.learningSettings.preferredDifficulty,
                        onClick = { },
                    )

                    SettingsToggleItem(
                        title = "Autoplay Audio",
                        description = "Automatically play pronunciation audio",
                        isChecked = userSettings.learningSettings.autoplayAudio,
                        onToggle = { viewModel.onLearningSettingChanged(LearningSettingType.AUTOPLAY_AUDIO, it) },
                    )

                    SettingsToggleItem(
                        title = "Show Translations",
                        description = "Display word translations by default",
                        isChecked = userSettings.learningSettings.showTranslations,
                        onToggle = { viewModel.onLearningSettingChanged(LearningSettingType.SHOW_TRANSLATIONS, it) },
                    )

                    SettingsToggleItem(
                        title = "Adaptive Learning",
                        description = "Adjust difficulty based on your performance",
                        isChecked = userSettings.learningSettings.adaptiveLearning,
                        onToggle = { viewModel.onLearningSettingChanged(LearningSettingType.ADAPTIVE_LEARNING, it) },
                    )

                    SettingsToggleItem(
                        title = "Dark Mode",
                        description = "Use dark theme for the app",
                        isChecked = userSettings.learningSettings.darkMode,
                        onToggle = { viewModel.onLearningSettingChanged(LearningSettingType.DARK_MODE, it) },
                    )

                    SettingsSelectionItem(
                        title = "Font Size",
                        description = "Text size throughout the app",
                        currentValue = userSettings.learningSettings.fontSize,
                        onClick = { },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(
                title = "App",
                icon = "⚙️",
                description = "Application preferences",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggleItem(
                        title = "Auto Save",
                        description = "Automatically save your progress",
                        isChecked = userSettings.appSettings.autoSave,
                        onToggle = { viewModel.onAppSettingChanged(AppSettingType.AUTO_SAVE, it) },
                    )

                    SettingsToggleItem(
                        title = "Data Sync",
                        description = "Sync data across devices",
                        isChecked = userSettings.appSettings.dataSync,
                        onToggle = { viewModel.onAppSettingChanged(AppSettingType.DATA_SYNC, it) },
                    )

                    SettingsToggleItem(
                        title = "WiFi Only Downloads",
                        description = "Download content only on WiFi",
                        isChecked = userSettings.appSettings.wifiOnlyDownloads,
                        onToggle = { viewModel.onAppSettingChanged(AppSettingType.WIFI_ONLY_DOWNLOADS, it) },
                    )

                    SettingsSelectionItem(
                        title = "Cache Size",
                        description = "Storage used for offline content",
                        currentValue = userSettings.appSettings.cacheSize,
                        onClick = { },
                    )

                    SettingsToggleItem(
                        title = "Analytics",
                        description = "Help improve the app with usage data",
                        isChecked = userSettings.appSettings.analyticsEnabled,
                        onToggle = { viewModel.onAppSettingChanged(AppSettingType.ANALYTICS_ENABLED, it) },
                    )

                    SettingsToggleItem(
                        title = "Haptic Feedback",
                        description = "Vibrate on interactions",
                        isChecked = userSettings.appSettings.hapticFeedback,
                        onToggle = { viewModel.onAppSettingChanged(AppSettingType.HAPTIC_FEEDBACK, it) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(
                title = "Privacy",
                icon = "🔒",
                description = "Control your privacy settings",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggleItem(
                        title = "Share Progress",
                        description = "Allow others to see your learning progress",
                        isChecked = userSettings.privacySettings.shareProgress,
                        onToggle = { viewModel.onPrivacySettingChanged(PrivacySettingType.SHARE_PROGRESS, it) },
                    )

                    SettingsToggleItem(
                        title = "Public Profile",
                        description = "Make your profile visible to other learners",
                        isChecked = userSettings.privacySettings.publicProfile,
                        onToggle = { viewModel.onPrivacySettingChanged(PrivacySettingType.PUBLIC_PROFILE, it) },
                    )

                    SettingsToggleItem(
                        title = "Data Collection",
                        description = "Allow collection of anonymous usage data",
                        isChecked = userSettings.privacySettings.dataCollection,
                        onToggle = { viewModel.onPrivacySettingChanged(PrivacySettingType.DATA_COLLECTION, it) },
                    )

                    SettingsToggleItem(
                        title = "Personalization",
                        description = "Use data to personalize your experience",
                        isChecked = userSettings.privacySettings.personalization,
                        onToggle = { viewModel.onPrivacySettingChanged(PrivacySettingType.PERSONALIZATION, it) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(
                title = "Account",
                icon = "👤",
                description = "Account management and support",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsActionItem(
                        title = "Export Data",
                        description = "Download your learning data",
                        onClick = { viewModel.onAccountAction(AccountAction.EXPORT_DATA) },
                    )

                    SettingsActionItem(
                        title = "Clear Cache",
                        description = "Free up storage space",
                        onClick = { viewModel.onAccountAction(AccountAction.CLEAR_CACHE) },
                    )

                    SettingsActionItem(
                        title = "Reset Settings",
                        description = "Restore default settings",
                        onClick = { viewModel.onAccountAction(AccountAction.RESET_SETTINGS) },
                    )

                    SettingsActionItem(
                        title = "Contact Support",
                        description = "Get help with your account",
                        onClick = { viewModel.onAccountAction(AccountAction.CONTACT_SUPPORT) },
                    )

                    SettingsActionItem(
                        title = "Rate App",
                        description = "Leave a review in the app store",
                        onClick = { viewModel.onAccountAction(AccountAction.RATE_APP) },
                    )

                    SettingsActionItem(
                        title = "Privacy Policy",
                        description = "View our privacy policy",
                        onClick = { viewModel.onAccountAction(AccountAction.VIEW_PRIVACY_POLICY) },
                    )

                    SettingsActionItem(
                        title = "Terms of Service",
                        description = "View terms and conditions",
                        onClick = { viewModel.onAccountAction(AccountAction.VIEW_TERMS) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: String,
    description: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WordBridgeColors.BackgroundWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )

                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = WordBridgeColors.PrimaryPurple,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = WordBridgeColors.TextMuted.copy(alpha = 0.3f),
                ),
        )
    }
}

@Composable
private fun SettingsSelectionItem(
    title: String,
    description: String,
    currentValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondary,
                )
            }

            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.PrimaryPurple,
            )
        }
    }
}

@Composable
private fun SettingsActionItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun SettingsInfoItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
            color = WordBridgeColors.TextPrimary,
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondary,
        )
    }
}
