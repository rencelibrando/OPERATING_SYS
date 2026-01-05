package org.example.project.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationFeedback(
    val overallScore: Int,
    val grammarScore: Int,
    val pronunciationScore: Int,
    val vocabularyScore: Int,
    val fluencyScore: Int,
    val detailedAnalysis: String,
    val strengths: List<String>,
    val areasForImprovement: List<String>,
    val specificExamples: List<FeedbackExample>,
    val suggestions: List<String>
) {
    fun getScoreColor(): String {
        return when {
            overallScore >= 80 -> "#10B981"
            overallScore >= 60 -> "#F59E0B"
            overallScore >= 40 -> "#F97316"
            else -> "#EF4444"
        }
    }
    
    fun getScoreLabel(): String {
        return when {
            overallScore >= 80 -> "Excellent"
            overallScore >= 60 -> "Good"
            overallScore >= 40 -> "Fair"
            else -> "Needs Improvement"
        }
    }
}

@Serializable
data class FeedbackExample(
    val userUtterance: String,
    val issue: String,
    val correction: String,
    val explanation: String
)
