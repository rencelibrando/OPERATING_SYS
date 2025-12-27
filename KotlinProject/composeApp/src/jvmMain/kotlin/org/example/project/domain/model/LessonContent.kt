package org.example.project.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Comprehensive lesson content models with support for multiple question types
 * and multimedia content.
 */

// ============================================
// ENUMS
// ============================================

@Serializable
enum class QuestionType(val displayName: String) {
    @SerialName("multiple_choice")
    MULTIPLE_CHOICE("Multiple Choice"),

    @SerialName("text_entry")
    TEXT_ENTRY("Text Entry"),

    @SerialName("matching")
    MATCHING("Matching"),

    @SerialName("paraphrasing")
    PARAPHRASING("Paraphrasing"),

    @SerialName("error_correction")
    ERROR_CORRECTION("Error Correction"),
}

// ============================================
// QUESTION CHOICE
// ============================================

@Serializable
data class QuestionChoice(
    @SerialName("id")
    val id: String,
    @SerialName("question_id")
    val questionId: String,
    @SerialName("choice_text")
    val choiceText: String,
    @SerialName("choice_order")
    val choiceOrder: Int = 0,
    @SerialName("is_correct")
    val isCorrect: Boolean = false,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("audio_url")
    val audioUrl: String? = null,
    @SerialName("match_pair_id")
    val matchPairId: String? = null,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class QuestionChoiceCreate(
    @SerialName("choice_text")
    val choiceText: String,
    @SerialName("choice_order")
    val choiceOrder: Int = 0,
    @SerialName("is_correct")
    val isCorrect: Boolean = false,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("audio_url")
    val audioUrl: String? = null,
    @SerialName("match_pair_id")
    val matchPairId: String? = null,
)

// ============================================
// QUESTION
// ============================================

@Serializable
data class LessonQuestion(
    @SerialName("id")
    val id: String,
    @SerialName("lesson_id")
    val lessonId: String,
    @SerialName("question_type")
    val questionType: QuestionType,
    @SerialName("question_text")
    val questionText: String,
    @SerialName("question_order")
    val questionOrder: Int = 0,
    // For text_entry, paraphrasing questions
    @SerialName("answer_text")
    val answerText: String? = null,
    // Audio narration
    @SerialName("question_audio_url")
    val questionAudioUrl: String? = null,
    @SerialName("answer_audio_url")
    val answerAudioUrl: String? = null,
    // For error correction questions
    @SerialName("error_text")
    val errorText: String? = null,
    // Explanation for all question types
    @SerialName("explanation")
    val explanation: String? = null,
    @SerialName("explanation_audio_url")
    val explanationAudioUrl: String? = null,
    // Customizable wrong answer feedback
    @SerialName("wrong_answer_feedback")
    val wrongAnswerFeedback: String? = null,
    // Narration settings
    @SerialName("enable_question_narration")
    val enableQuestionNarration: Boolean = true,
    @SerialName("enable_answer_narration")
    val enableAnswerNarration: Boolean = true,
    @SerialName("narration_language")
    val narrationLanguage: String? = null,
    @SerialName("narration_voice")
    val narrationVoice: String? = null,
    // Choices for multiple choice/matching questions
    @SerialName("choices")
    val choices: List<QuestionChoice> = emptyList(),
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
) {
    val hasQuestionAudio: Boolean get() = !questionAudioUrl.isNullOrEmpty()
    val hasAnswerAudio: Boolean get() = !answerAudioUrl.isNullOrEmpty()
    val hasChoicesWithImages: Boolean get() = choices.any { !it.imageUrl.isNullOrEmpty() }
    val hasChoicesWithAudio: Boolean get() = choices.any { !it.audioUrl.isNullOrEmpty() }
}

@Serializable
data class QuestionCreate(
    @SerialName("question_type")
    val questionType: QuestionType,
    @SerialName("question_text")
    val questionText: String,
    @SerialName("question_order")
    val questionOrder: Int = 0,
    @SerialName("answer_text")
    val answerText: String? = null,
    @SerialName("question_audio_url")
    val questionAudioUrl: String? = null,
    @SerialName("answer_audio_url")
    val answerAudioUrl: String? = null,
    @SerialName("error_text")
    val errorText: String? = null,
    @SerialName("explanation")
    val explanation: String? = null,
    @SerialName("explanation_audio_url")
    val explanationAudioUrl: String? = null,
    @SerialName("wrong_answer_feedback")
    val wrongAnswerFeedback: String? = null,
    @SerialName("enable_question_narration")
    val enableQuestionNarration: Boolean = true,
    @SerialName("enable_answer_narration")
    val enableAnswerNarration: Boolean = true,
    @SerialName("narration_language")
    val narrationLanguage: String? = null,
    @SerialName("narration_voice")
    val narrationVoice: String? = null,
    @SerialName("choices")
    val choices: List<QuestionChoiceCreate> = emptyList(),
)

// ============================================
// LESSON
// ============================================

@Serializable
data class LessonContent(
    @SerialName("id")
    val id: String,
    @SerialName("topic_id")
    val topicId: String,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("lesson_order")
    val lessonOrder: Int = 0,
    @SerialName("is_published")
    val isPublished: Boolean = false,
    @SerialName("enable_lesson_narration")
    val enableLessonNarration: Boolean = true,
    @SerialName("narration_language")
    val narrationLanguage: String? = null,
    @SerialName("narration_voice")
    val narrationVoice: String? = null,
    @SerialName("questions")
    val questions: List<LessonQuestion> = emptyList(),
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
) {
    val questionCount: Int get() = questions.size
    val hasMultipleChoice: Boolean get() = questions.any { it.questionType == QuestionType.MULTIPLE_CHOICE }
    val hasTextEntry: Boolean get() = questions.any { it.questionType == QuestionType.TEXT_ENTRY }
    val hasMatching: Boolean get() = questions.any { it.questionType == QuestionType.MATCHING }
    val hasParaphrasing: Boolean get() = questions.any { it.questionType == QuestionType.PARAPHRASING }
    val hasErrorCorrection: Boolean get() = questions.any { it.questionType == QuestionType.ERROR_CORRECTION }
}

@Serializable
data class LessonSummary(
    @SerialName("id")
    val id: String,
    @SerialName("topic_id")
    val topicId: String,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("lesson_order")
    val lessonOrder: Int = 0,
    @SerialName("is_published")
    val isPublished: Boolean = false,
    @SerialName("question_count")
    val questionCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class LessonCreate(
    @SerialName("topic_id")
    val topicId: String,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("lesson_order")
    val lessonOrder: Int = 0,
    @SerialName("is_published")
    val isPublished: Boolean = false,
    @SerialName("enable_lesson_narration")
    val enableLessonNarration: Boolean = true,
    @SerialName("narration_language")
    val narrationLanguage: String? = null,
    @SerialName("narration_voice")
    val narrationVoice: String? = null,
    @SerialName("questions")
    val questions: List<QuestionCreate> = emptyList(),
)

@Serializable
data class LessonUpdate(
    @SerialName("title")
    val title: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("is_published")
    val isPublished: Boolean? = null,
    @SerialName("enable_lesson_narration")
    val enableLessonNarration: Boolean? = null,
    @SerialName("narration_language")
    val narrationLanguage: String? = null,
    @SerialName("narration_voice")
    val narrationVoice: String? = null,
)

// ============================================
// USER PROGRESS
// ============================================

@Serializable
data class UserLessonProgress(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("lesson_id")
    val lessonId: String,
    @SerialName("is_completed")
    val isCompleted: Boolean = false,
    @SerialName("score")
    val score: Float? = null,
    @SerialName("time_spent_seconds")
    val timeSpentSeconds: Int = 0,
    @SerialName("started_at")
    val startedAt: String,
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("last_accessed_at")
    val lastAccessedAt: String,
) {
    val formattedScore: String get() = score?.let { "${it.toInt()}%" } ?: "N/A"
    val isPassed: Boolean get() = (score ?: 0f) >= 70f
}

@Serializable
data class UserQuestionAnswer(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("question_id")
    val questionId: String,
    @SerialName("lesson_id")
    val lessonId: String,
    // For multiple choice
    @SerialName("selected_choice_id")
    val selectedChoiceId: String? = null,
    // For identification/voice
    @SerialName("answer_text")
    val answerText: String? = null,
    @SerialName("voice_recording_url")
    val voiceRecordingUrl: String? = null,
    @SerialName("is_correct")
    val isCorrect: Boolean? = null,
    @SerialName("attempted_at")
    val attemptedAt: String,
)

@Serializable
data class UserQuestionAnswerCreate(
    @SerialName("user_id")
    val userId: String,
    @SerialName("question_id")
    val questionId: String,
    @SerialName("lesson_id")
    val lessonId: String,
    @SerialName("selected_choice_id")
    val selectedChoiceId: String? = null,
    @SerialName("answer_text")
    val answerText: String? = null,
    @SerialName("voice_recording_url")
    val voiceRecordingUrl: String? = null,
    @SerialName("is_correct")
    val isCorrect: Boolean? = null,
)

// ============================================
// API RESPONSES
// ============================================

@Serializable
data class LessonListResponse(
    @SerialName("lessons")
    val lessons: List<LessonSummary>,
    @SerialName("total")
    val total: Int,
)

@Serializable
data class LessonDetailResponse(
    @SerialName("lesson")
    val lesson: LessonContent,
)

@Serializable
data class MediaUploadResponse(
    @SerialName("url")
    val url: String,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("file_size")
    val fileSize: Int,
    @SerialName("media_type")
    val mediaType: String,
)

@Serializable
data class SubmitLessonAnswersRequest(
    @SerialName("user_id")
    val userId: String,
    @SerialName("lesson_id")
    val lessonId: String,
    @SerialName("answers")
    val answers: List<UserQuestionAnswerCreate>,
)

@Serializable
data class SubmitLessonAnswersResponse(
    @SerialName("score")
    val score: Float,
    @SerialName("total_questions")
    val totalQuestions: Int,
    @SerialName("correct_answers")
    val correctAnswers: Int,
    @SerialName("is_passed")
    val isPassed: Boolean,
    @SerialName("completed_at")
    val completedAt: String,
)
