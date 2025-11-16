package org.example.project.domain.model

data class UserProfile(
    val userId: String,
    val personalInfo: PersonalInfo,
    val learningProfile: LearningProfile,
    val accountInfo: AccountInfo,
    val profileStats: ProfileStats,
    val createdAt: Long,
    val lastUpdated: Long,
) {
    companion object {
        fun getDefaultProfile(): UserProfile =
            UserProfile(
                userId = "user_001",
                personalInfo = PersonalInfo.getDefault(),
                learningProfile = LearningProfile.getDefault(),
                accountInfo = AccountInfo.getDefault(),
                profileStats = ProfileStats.getDefault(),
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis(),
            )

        fun getSampleProfile(): UserProfile =
            UserProfile(
                userId = "user_001",
                personalInfo =
                    PersonalInfo(
                        firstName = "Sarah",
                        lastName = "Chen",
                        email = "sarah.chen@email.com",
                        avatar = "SC",
                        dateOfBirth = "1995-03-15",
                        location = "San Francisco, CA",
                        nativeLanguage = "Chinese",
                        targetLanguages = listOf("English", "Spanish"),
                        bio = "Language enthusiast passionate about connecting cultures through communication.",
                    ),
                learningProfile =
                    LearningProfile(
                        currentLevel = "Intermediate",
                        primaryGoal = "Business Communication",
                        weeklyGoalHours = 5,
                        preferredLearningStyle = "Visual + Audio",
                        focusAreas = listOf("Speaking", "Business Vocabulary", "Pronunciation"),
                        availableTimeSlots = listOf("Morning", "Evening"),
                        motivations = listOf("Career Growth", "Travel", "Personal Interest"),
                    ),
                accountInfo =
                    AccountInfo(
                        subscriptionType = "Premium",
                        subscriptionStatus = "Active",
                        subscriptionExpiry = System.currentTimeMillis() + 2592000000, 
                        joinDate = System.currentTimeMillis(), 
                        isEmailVerified = true,
                        isPhoneVerified = false,
                        twoFactorEnabled = true,
                        lastLoginDate = System.currentTimeMillis() - 86400000, 
                    ),
                profileStats =
                    ProfileStats(
                        totalStudyTime = 0,
                        lessonsCompleted = 0,
                        wordsLearned = 0,
                        achievementsUnlocked = 0,
                        currentStreak = 0,
                        longestStreak = 0,
                        profileCompleteness = 0,
                    ),
                createdAt = System.currentTimeMillis(), 
                lastUpdated = System.currentTimeMillis(),
            )
    }
}

data class PersonalInfo(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val avatar: String = "",
    val profileImageUrl: String? = null,
    val dateOfBirth: String? = null,
    val location: String? = null,
    val nativeLanguage: String = "",
    val targetLanguages: List<String> = emptyList(),
    val bio: String? = null,
) {
    val fullName: String
        get() = "$firstName $lastName".trim()

    val initials: String
        get() = "${firstName.firstOrNull() ?: ""}${lastName.firstOrNull() ?: ""}".uppercase()

    companion object {
        fun getDefault(): PersonalInfo = PersonalInfo()

        fun getAvailableLanguages(): List<String> =
            listOf(
                "English", "Spanish", "French", "German", "Italian", "Portuguese",
                "Chinese", "Japanese", "Korean", "Arabic", "Russian", "Hindi",
            )
    }
}

data class LearningProfile(
    val currentLevel: String = "Beginner",
    val primaryGoal: String = "",
    val weeklyGoalHours: Int = 3,
    val preferredLearningStyle: String = "Mixed",
    val focusAreas: List<String> = emptyList(),
    val availableTimeSlots: List<String> = emptyList(),
    val motivations: List<String> = emptyList(),
) {
    companion object {
        fun getDefault(): LearningProfile = LearningProfile()

        fun getLevelOptions(): List<String> = listOf("Beginner", "Intermediate", "Advanced", "Native")

        fun getGoalOptions(): List<String> =
            listOf(
                "General Conversation",
                "Business Communication",
                "Academic Study",
                "Travel",
                "Immigration",
                "Personal Interest",
                "Professional Development",
            )

        fun getLearningStyleOptions(): List<String> =
            listOf(
                "Visual",
                "Audio",
                "Reading/Writing",
                "Kinesthetic",
                "Mixed",
            )

        fun getFocusAreaOptions(): List<String> =
            listOf(
                "Speaking", "Listening", "Reading", "Writing", "Grammar",
                "Vocabulary", "Pronunciation", "Business English", "Academic English",
            )

        fun getTimeSlotOptions(): List<String> =
            listOf(
                "Early Morning",
                "Morning",
                "Afternoon",
                "Evening",
                "Night",
                "Weekend",
            )

        fun getMotivationOptions(): List<String> =
            listOf(
                "Career Growth",
                "Travel",
                "Education",
                "Personal Interest",
                "Family/Relationships",
                "Immigration",
                "Business",
                "Cultural Interest",
            )
    }
}

data class AccountInfo(
    val subscriptionType: String = "Free",
    val subscriptionStatus: String = "Active",
    val subscriptionExpiry: Long? = null,
    val joinDate: Long = System.currentTimeMillis(),
    val isEmailVerified: Boolean = false,
    val isPhoneVerified: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    val lastLoginDate: Long = System.currentTimeMillis(),
) {
    companion object {
        fun getDefault(): AccountInfo = AccountInfo()

        fun getSubscriptionTypes(): List<String> = listOf("Free", "Basic", "Premium", "Enterprise")
    }
}

data class ProfileStats(
    val totalStudyTime: Int = 0, 
    val lessonsCompleted: Int = 0,
    val wordsLearned: Int = 0,
    val achievementsUnlocked: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val profileCompleteness: Int = 0, 
) {
    companion object {
        fun getDefault(): ProfileStats = ProfileStats()
    }
}

enum class ProfileField(val displayName: String, val isRequired: Boolean = false) {
    FIRST_NAME("First Name", true),
    LAST_NAME("Last Name", true),
    EMAIL("Email", true),
    DATE_OF_BIRTH("Date of Birth"),
    LOCATION("Location"),
    NATIVE_LANGUAGE("Native Language", true),
    TARGET_LANGUAGES("Target Languages"),
    BIO("Bio"),
    CURRENT_LEVEL("Current Level"),
    PRIMARY_GOAL("Primary Goal"),
    WEEKLY_GOAL_HOURS("Weekly Goal (Hours)"),
    LEARNING_STYLE("Preferred Learning Style"),
    FOCUS_AREAS("Focus Areas"),
    TIME_SLOTS("Available Time Slots"),
    MOTIVATIONS("Motivations"),
}

enum class ProfileSection(val displayName: String, val icon: String) {
    PERSONAL_INFO("Personal Information", "👤"),
    LEARNING_PROFILE("Learning Profile", "📚"),
    ACCOUNT_SECURITY("Account & Security", "🔒"),
    SUBSCRIPTION("Subscription", "💎"),
    STATS("Statistics", "📊"),
}

data class ProfileCompletion(
    val completionPercentage: Int,
    val missingFields: List<ProfileField>,
    val suggestions: List<String>,
) {
    companion object {
        fun calculate(profile: UserProfile): ProfileCompletion {
            val requiredFields = ProfileField.values().filter { it.isRequired }
            val optionalFields = ProfileField.values().filter { !it.isRequired }

            var completedRequired = 0
            var completedOptional = 0
            val missing = mutableListOf<ProfileField>()

            requiredFields.forEach { field ->
                when (field) {
                    ProfileField.FIRST_NAME -> {
                        if (profile.personalInfo.firstName.isNotEmpty()) {
                            completedRequired++
                        } else {
                            missing.add(field)
                        }
                    }
                    ProfileField.LAST_NAME -> {
                        if (profile.personalInfo.lastName.isNotEmpty()) {
                            completedRequired++
                        } else {
                            missing.add(field)
                        }
                    }
                    ProfileField.EMAIL -> {
                        if (profile.personalInfo.email.isNotEmpty()) {
                            completedRequired++
                        } else {
                            missing.add(field)
                        }
                    }
                    ProfileField.NATIVE_LANGUAGE -> {
                        if (profile.personalInfo.nativeLanguage.isNotEmpty()) {
                            completedRequired++
                        } else {
                            missing.add(field)
                        }
                    }
                    else -> completedRequired++
                }
            }

            optionalFields.forEach { field ->
                when (field) {
                    ProfileField.DATE_OF_BIRTH -> {
                        if (!profile.personalInfo.dateOfBirth.isNullOrEmpty()) completedOptional++
                    }
                    ProfileField.LOCATION -> {
                        if (!profile.personalInfo.location.isNullOrEmpty()) completedOptional++
                    }
                    ProfileField.TARGET_LANGUAGES -> {
                        if (profile.personalInfo.targetLanguages.isNotEmpty()) completedOptional++
                    }
                    ProfileField.BIO -> {
                        if (!profile.personalInfo.bio.isNullOrEmpty()) completedOptional++
                    }
                    else -> completedOptional++
                }
            }

            val totalFields = ProfileField.values().size
            val totalCompleted = completedRequired + completedOptional
            val percentage = (totalCompleted * 100) / totalFields

            val suggestions = mutableListOf<String>()
            if (missing.isNotEmpty()) {
                suggestions.add("Complete required fields to unlock all features")
            }
            if (profile.personalInfo.targetLanguages.isEmpty()) {
                suggestions.add("Add target languages for personalized recommendations")
            }
            if (profile.personalInfo.bio.isNullOrEmpty()) {
                suggestions.add("Add a bio to connect with other learners")
            }

            return ProfileCompletion(percentage, missing, suggestions)
        }
    }
}
