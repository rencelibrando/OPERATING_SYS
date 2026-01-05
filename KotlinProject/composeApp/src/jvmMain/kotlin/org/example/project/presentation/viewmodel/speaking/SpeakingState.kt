package org.example.project.presentation.viewmodel.speaking

import org.example.project.domain.model.PracticeLanguage
import org.example.project.domain.model.PracticeFeedback
import org.example.project.domain.model.VocabularyWord
import org.example.project.domain.model.SpeakingScenario
import org.example.project.domain.model.ConversationRecording
import org.example.project.models.ConversationTurnUI

/**
 * Voice tutor configuration state.
 */
data class VoiceTutorConfig(
    val language: PracticeLanguage? = null,
    val level: String? = null,
    val scenario: String? = null,
    val prompt: String? = null
) {
    fun isValid(): Boolean = language != null && level != null && scenario != null
    
    fun getLanguageCode(): String = when (language) {
        PracticeLanguage.ENGLISH -> "en"
        PracticeLanguage.FRENCH -> "fr"
        PracticeLanguage.GERMAN -> "de"
        PracticeLanguage.HANGEUL -> "ko"
        PracticeLanguage.MANDARIN -> "zh"
        PracticeLanguage.SPANISH -> "es"
        else -> "en"
    }
    
    fun shouldUseCustomPipeline(): Boolean = when (language) {
        PracticeLanguage.HANGEUL, PracticeLanguage.MANDARIN -> true
        else -> false
    }
}

/**
 * Conversation mode state.
 */
data class ConversationState(
    val isActive: Boolean = false,
    val isMode: Boolean = false,
    val turns: List<ConversationTurnUI> = emptyList(),
    val isAgentSpeaking: Boolean = false,
    val isAgentThinking: Boolean = false,
    val error: String? = null,
    val isRecording: Boolean = false
) {
    fun hasError(): Boolean = error != null
    fun hasTurns(): Boolean = turns.isNotEmpty()
}

/**
 * Practice session state (word practice mode).
 */
data class PracticeSessionState(
    val currentWord: VocabularyWord? = null,
    val selectedLanguage: PracticeLanguage? = null,
    val isRecording: Boolean = false,
    val hasRecording: Boolean = false,
    val isPlayingRecording: Boolean = false,
    val feedback: PracticeFeedback? = null,
    val isAnalyzing: Boolean = false,
    val recordingDuration: Float = 0f
) {
    fun canRecord(): Boolean = !isRecording && !isAnalyzing
    fun canPlay(): Boolean = hasRecording && !isPlayingRecording && !isRecording
    fun canAnalyze(): Boolean = hasRecording && !isAnalyzing
}

/**
 * UI dialog/selection state.
 */
data class DialogState(
    val showLanguageDialog: Boolean = false,
    val showVoiceTutorSelection: Boolean = false
)

/**
 * Session tracking data for conversation recordings.
 */
data class SessionTracking(
    val sessionId: String? = null,
    val startTime: Long = 0L
) {
    fun isActive(): Boolean = sessionId != null
    fun durationSeconds(): Float = if (startTime > 0) {
        (System.currentTimeMillis() - startTime) / 1000f
    } else 0f
}

/**
 * Lesson-related state.
 */
data class LessonState(
    val scenarios: List<SpeakingScenario> = emptyList(),
    val selectedScenario: SpeakingScenario? = null,
    val conversationRecording: ConversationRecording? = null,
    val isLoadingRecording: Boolean = false
)
