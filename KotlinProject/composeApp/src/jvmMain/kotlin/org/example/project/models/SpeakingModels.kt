package org.example.project.models

enum class PracticeLanguage(
    val displayName: String,
    val flag: String,
    val description: String,
) {
    ENGLISH(
        displayName = "English",
        flag = "🇬🇧",
        description = "Practice English pronunciation",
    ),
    FRENCH(
        displayName = "French",
        flag = "🇫🇷",
        description = "Practice French pronunciation",
    ),
    GERMAN(
        displayName = "German",
        flag = "🇩🇪",
        description = "Practice German pronunciation",
    ),
    HANGEUL(
        displayName = "Korean (Hangeul)",
        flag = "🇰🇷",
        description = "Practice Korean pronunciation",
    ),
    MANDARIN(
        displayName = "Mandarin Chinese",
        flag = "🇨🇳",
        description = "Practice Mandarin pronunciation with tones",
    ),
    SPANISH(
        displayName = "Spanish",
        flag = "🇪🇸",
        description = "Practice Spanish pronunciation",
    ),
}

data class PracticeFeedback(
    val overallScore: Int,
    val pronunciationScore: Int,
    val clarityScore: Int,
    val fluencyScore: Int,
    val messages: List<String>,
    val suggestions: List<String>,
)

data class SpeakingFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String,
)
