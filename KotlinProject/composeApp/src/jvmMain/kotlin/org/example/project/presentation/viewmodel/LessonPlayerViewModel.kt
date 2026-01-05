package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.core.analytics.ProgressAnalyticsService
import org.example.project.data.repository.LessonContentRepository
import org.example.project.data.repository.LessonContentRepositoryImpl
import org.example.project.domain.model.*

/**
 * ViewModel for playing lessons in the main app.
 * Handles lesson display, question answering, and progress tracking.
 */
class LessonPlayerViewModel(
    private val onLessonCompleted: ((userId: String, lessonId: String) -> Unit)? = null
) : ViewModel() {
    private val repository: LessonContentRepository = LessonContentRepositoryImpl()

    // State
    private val _currentLesson = mutableStateOf<LessonContent?>(null)
    val currentLesson: State<LessonContent?> = _currentLesson

    private val _currentQuestionIndex = mutableStateOf(0)
    val currentQuestionIndex: State<Int> = _currentQuestionIndex

    private val _userAnswers = mutableStateOf<Map<String, UserAnswer>>(emptyMap())
    val userAnswers: State<Map<String, UserAnswer>> = _userAnswers

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _isSubmitted = mutableStateOf(false)
    val isSubmitted: State<Boolean> = _isSubmitted

    private val _submissionResult = mutableStateOf<SubmitLessonAnswersResponse?>(null)
    val submissionResult: State<SubmitLessonAnswersResponse?> = _submissionResult

    private val _showFeedback = mutableStateOf<Map<String, Boolean>>(emptyMap()) // questionId -> showFeedback
    val showFeedback: State<Map<String, Boolean>> = _showFeedback

    private val _checkedAnswers = mutableStateOf<Map<String, Boolean>>(emptyMap()) // questionId -> isChecked
    val checkedAnswers: State<Map<String, Boolean>> = _checkedAnswers

    val currentQuestion: LessonQuestion?
        get() = _currentLesson.value?.questions?.getOrNull(_currentQuestionIndex.value)

    val totalQuestions: Int
        get() = _currentLesson.value?.questions?.size ?: 0

    val progress: Float
        get() =
            if (totalQuestions > 0) {
                (_currentQuestionIndex.value + 1).toFloat() / totalQuestions.toFloat()
            } else {
                0f
            }

    val isLastQuestion: Boolean
        get() = _currentQuestionIndex.value >= totalQuestions - 1

    val canGoNext: Boolean
        get() =
            currentQuestion?.let { question ->
                val answer = _userAnswers.value[question.id]
                val isChecked = _checkedAnswers.value[question.id] == true
                // Can proceed only if answer is provided, checked, and correct
                when (question.questionType) {
                    // Paraphrasing: let students proceed with any non-blank response (no “correct” key)
                    QuestionType.PARAPHRASING -> {
                        val text = (answer as? UserAnswer.Identification)?.answer.orEmpty().trim()
                        text.isNotEmpty()
                    }
                    else ->
                        answer != null && isChecked &&
                            when (answer) {
                                is UserAnswer.MultipleChoice -> answer.isCorrect
                                is UserAnswer.Identification -> answer.isCorrect
                                is UserAnswer.Matching -> answer.isCorrect
                                is UserAnswer.VoiceRecording -> true // Voice recordings always pass
                            }
                }
            } ?: false

    val canCheckAnswer: Boolean
        get() =
            currentQuestion?.let { question ->
                val answer = _userAnswers.value[question.id]
                val isChecked = _checkedAnswers.value[question.id] == true
                // Can check if answer is provided but not yet checked (or incorrect and user modified it)
                when (question.questionType) {
                    QuestionType.PARAPHRASING -> false // no correctness check for paraphrasing
                    else -> answer != null && !isChecked
                }
            } ?: false

    // ============================================
    // LESSON LOADING
    // ============================================

    fun loadLesson(lessonId: String) {
        println("[LessonPlayer] ========== LOADING LESSON ==========")
        println("[LessonPlayer] Lesson ID: $lessonId")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                println("[LessonPlayer] Repository call: getLessonById(lessonId=$lessonId, includeQuestions=true)")
                repository.getLessonById(lessonId, includeQuestions = true)
                    .onSuccess { lesson ->
                        println("[LessonPlayer] ✓ Lesson loaded successfully!")
                        println("[LessonPlayer] Lesson title: ${lesson.title}")
                        println("[LessonPlayer] Description: ${lesson.description ?: "(none)"}")
                        println("[LessonPlayer] Number of questions: ${lesson.questions.size}")
                        println("[LessonPlayer] Questions:")
                        lesson.questions.forEachIndexed { index, question ->
                            println(
                                "[LessonPlayer]   ${index + 1}. ${question.questionType.displayName}: ${question.questionText.take(50)}...",
                            )
                            println(
                                "[LessonPlayer]      - wrongAnswerFeedback: ${if (question.wrongAnswerFeedback.isNullOrBlank()) "NOT SET" else "\"${question.wrongAnswerFeedback}\""}",
                            )
                            println(
                                "[LessonPlayer]      - explanation: ${if (question.explanation.isNullOrBlank()) "NOT SET" else "\"${question.explanation}\""}",
                            )
                            if (question.choices.isNotEmpty()) {
                                println("[LessonPlayer]      - Choices: ${question.choices.size}")
                            }
                        }

                        _currentLesson.value = lesson
                        _currentQuestionIndex.value = 0
                        _userAnswers.value = emptyMap()
                        _isSubmitted.value = false
                        _submissionResult.value = null
                        _showFeedback.value = emptyMap()
                        _checkedAnswers.value = emptyMap()

                        println("[LessonPlayer] Lesson state initialized: questionIndex=0, answers=0")
                    }
                    .onFailure { error ->
                        val errorMsg = "Failed to load lesson: ${error.message}"
                        println("[LessonPlayer] ✗ ERROR: $errorMsg")
                        println("[LessonPlayer] Error type: ${error.javaClass.simpleName}")
                        println("[LessonPlayer] Error details: ${error.stackTraceToString()}")
                        _errorMessage.value = errorMsg
                    }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error loading lesson: ${e.message}"
                println("[LessonPlayer] ✗ EXCEPTION: $errorMsg")
                println("[LessonPlayer] Exception type: ${e.javaClass.simpleName}")
                println("[LessonPlayer] Exception details: ${e.stackTraceToString()}")
                _errorMessage.value = errorMsg
            }

            _isLoading.value = false
            println("[LessonPlayer] =========================================")
        }
    }

    // ============================================
    // NAVIGATION
    // ============================================

    fun goToNextQuestion() {
        val currentIndex = _currentQuestionIndex.value
        if (!isLastQuestion) {
            println("[LessonPlayer] Navigating: Question ${currentIndex + 1} -> ${currentIndex + 2}")
            _currentQuestionIndex.value++
        } else {
            println("[LessonPlayer] Cannot navigate forward - already on last question")
        }
    }

    fun goToPreviousQuestion() {
        val currentIndex = _currentQuestionIndex.value
        if (currentIndex > 0) {
            println("[LessonPlayer] Navigating: Question ${currentIndex + 1} -> $currentIndex")
            _currentQuestionIndex.value--
        } else {
            println("[LessonPlayer] Cannot navigate backward - already on first question")
        }
    }

    fun goToQuestion(index: Int) {
        val currentIndex = _currentQuestionIndex.value
        if (index in 0 until totalQuestions) {
            println("[LessonPlayer] Navigating: Question ${currentIndex + 1} -> ${index + 1}")
            _currentQuestionIndex.value = index
        } else {
            println("[LessonPlayer] Invalid question index: $index (valid range: 0-${totalQuestions - 1})")
        }
    }

    // ============================================
    // ANSWER HANDLING
    // ============================================

    fun answerMultipleChoice(
        questionId: String,
        choiceId: String,
        isCorrect: Boolean,
    ) {
        println("[LessonPlayer] Answer selected: Multiple Choice")
        println("[LessonPlayer]   Question ID: $questionId")
        println("[LessonPlayer]   Selected choice: $choiceId")
        println("[LessonPlayer]   Correct: $isCorrect")
        _userAnswers.value = _userAnswers.value + (questionId to UserAnswer.MultipleChoice(choiceId, isCorrect))
        // Reset checked state when answer changes
        _checkedAnswers.value = _checkedAnswers.value - questionId
        _showFeedback.value = _showFeedback.value - questionId
        println("[LessonPlayer]   Total answers: ${_userAnswers.value.size}/$totalQuestions")
    }

    fun answerIdentification(
        questionId: String,
        answer: String,
        correctAnswer: String,
    ) {
        println("[LessonPlayer] Answer changed: Text Entry/Identification")
        println("[LessonPlayer]   Question ID: $questionId")
        println("[LessonPlayer]   User answer: ${answer.take(50)}${if (answer.length > 50) "..." else ""}")
        println("[LessonPlayer]   Correct answer: ${correctAnswer.take(50)}${if (correctAnswer.length > 50) "..." else ""}")

        val isCorrect = answer.trim().equals(correctAnswer.trim(), ignoreCase = true)
        println("[LessonPlayer]   Correct: $isCorrect")

        _userAnswers.value = _userAnswers.value + (questionId to UserAnswer.Identification(answer, isCorrect))
        // Reset checked state when answer changes
        _checkedAnswers.value = _checkedAnswers.value - questionId
        _showFeedback.value = _showFeedback.value - questionId
        println("[LessonPlayer]   Total answers: ${_userAnswers.value.size}/$totalQuestions")
    }

    fun answerParaphrasing(
        questionId: String,
        answer: String,
    ) {
        println("[LessonPlayer] Answer changed: Paraphrasing")
        println("[LessonPlayer]   Question ID: $questionId")
        println("[LessonPlayer]   User answer: ${answer.take(50)}${if (answer.length > 50) "..." else ""}")
        // Paraphrasing is reviewed manually/AI; mark as correct for flow purposes
        _userAnswers.value = _userAnswers.value + (questionId to UserAnswer.Identification(answer, true))
        // No correctness gating; keep unchecked state irrelevant
        _checkedAnswers.value = _checkedAnswers.value - questionId
        _showFeedback.value = _showFeedback.value - questionId
        println("[LessonPlayer]   Total answers: ${_userAnswers.value.size}/$totalQuestions")
    }

    fun answerVoiceRecording(
        questionId: String,
        recordingUrl: String,
    ) {
        println("[LessonPlayer] Answer submitted: Voice Recording")
        println("[LessonPlayer]   Question ID: $questionId")
        println("[LessonPlayer]   Recording URL: $recordingUrl")
        // Voice recordings are always marked as attempted (manual grading would be needed for correctness)
        _userAnswers.value = _userAnswers.value + (questionId to UserAnswer.VoiceRecording(recordingUrl, null))
        println("[LessonPlayer]   Total answers: ${_userAnswers.value.size}/$totalQuestions")
    }

    fun answerMatching(
        questionId: String,
        matches: Map<String, String>,
    ) {
        println("[LessonPlayer] Answer changed: Matching")
        println("[LessonPlayer]   Question ID: $questionId")
        println("[LessonPlayer]   Matches: ${matches.size} pairs")
        matches.forEach { (left, right) ->
            println("[LessonPlayer]     $left -> $right")
        }

        // Check if all matches are correct by comparing matchPairIds
        val question = currentLesson.value?.questions?.find { it.id == questionId }
        val isCorrect =
            question?.let { q ->
                // Get all left items (questions) that should be matched
                val pairs =
                    q.choices
                        .filter { !it.matchPairId.isNullOrEmpty() }
                        .groupBy { it.matchPairId }
                val leftItems =
                    pairs.values.mapNotNull { choices ->
                        choices.firstOrNull() // First item in each pair is the left item
                    }

                println("[LessonPlayer]   Total left items that need matching: ${leftItems.size}")
                println("[LessonPlayer]   User has matched: ${matches.size} pairs")

                // Must match all left items, and all matches must be correct
                if (matches.isEmpty() || matches.size != leftItems.size) {
                    println("[LessonPlayer]   ✗ Not all items matched (need ${leftItems.size}, have ${matches.size})")
                    false
                } else {
                    // Check that all matches are correct
                    val allCorrect =
                        matches.all { (leftId, rightId) ->
                            val leftChoice = q.choices.find { it.id == leftId }
                            val rightChoice = q.choices.find { it.id == rightId }

                            // Both choices must exist and have matching pair IDs (and they must not be null/empty)
                            val pairMatch =
                                leftChoice?.matchPairId?.let { leftPairId ->
                                    rightChoice?.matchPairId?.let { rightPairId ->
                                        leftPairId.isNotEmpty() && rightPairId.isNotEmpty() && leftPairId == rightPairId
                                    } ?: false
                                } ?: false

                            if (!pairMatch) {
                                println("[LessonPlayer]     ✗ Match incorrect: $leftId (${leftChoice?.matchPairId}) != $rightId (${rightChoice?.matchPairId})")
                            } else {
                                println("[LessonPlayer]     ✓ Match correct: $leftId -> $rightId (pairId=${leftChoice?.matchPairId})")
                            }
                            pairMatch
                        }
                    allCorrect
                }
            } ?: false

        println("[LessonPlayer]   All matches correct: $isCorrect")
        _userAnswers.value = _userAnswers.value + (questionId to UserAnswer.Matching(matches, isCorrect))
        // Reset checked state when answer changes
        _checkedAnswers.value = _checkedAnswers.value - questionId
        _showFeedback.value = _showFeedback.value - questionId
        println("[LessonPlayer]   Total answers: ${_userAnswers.value.size}/$totalQuestions")
    }

    fun getAnswer(questionId: String): UserAnswer? {
        return _userAnswers.value[questionId]
    }

    fun shouldShowFeedback(questionId: String): Boolean {
        return _showFeedback.value[questionId] == true
    }

    fun checkCurrentAnswer() {
        currentQuestion?.let { question ->
            val answer = _userAnswers.value[question.id]
            if (answer != null) {
                println("[LessonPlayer] ========== CHECKING ANSWER ==========")
                println("[LessonPlayer] Question ID: ${question.id}")
                println("[LessonPlayer] Question Type: ${question.questionType}")
                // Log explanation instead of deprecated wrongAnswerFeedback
                println("[LessonPlayer] explanation available: ${!question.explanation.isNullOrBlank()}")
                if (!question.explanation.isNullOrBlank()) {
                    println("[LessonPlayer] explanation value: \"${question.explanation}\"")
                }
                println("[LessonPlayer] explanation available: ${!question.explanation.isNullOrBlank()}")
                if (!question.explanation.isNullOrBlank()) {
                    println("[LessonPlayer] explanation value: \"${question.explanation}\"")
                }

                // Paraphrasing skips explicit correctness; no-op for check
                if (question.questionType != QuestionType.PARAPHRASING) {
                    _checkedAnswers.value = _checkedAnswers.value + (question.id to true)
                    _showFeedback.value = _showFeedback.value + (question.id to true)

                    val isCorrect =
                        when (answer) {
                            is UserAnswer.MultipleChoice -> answer.isCorrect
                            is UserAnswer.Identification -> answer.isCorrect
                            is UserAnswer.Matching -> answer.isCorrect
                            is UserAnswer.VoiceRecording -> true
                        }
                    println("[LessonPlayer] Answer is ${if (isCorrect) "correct" else "incorrect"}")
                    println("[LessonPlayer] Feedback will be shown: ${_showFeedback.value[question.id] == true}")
                } else {
                    println("[LessonPlayer] Paraphrasing check skipped (no correctness gate)")
                }
                println("[LessonPlayer] =========================================")
            }
        }
    }

    fun clearQuestionAnswer(questionId: String) {
        _userAnswers.value = _userAnswers.value - questionId
        _showFeedback.value = _showFeedback.value - questionId
        _checkedAnswers.value = _checkedAnswers.value - questionId
    }

    // ============================================
    // SUBMISSION
    // ============================================

    fun submitLesson(userId: String) {
        println("[LessonPlayer] ========== SUBMITTING LESSON ==========")
        val lesson = _currentLesson.value
        if (lesson == null) {
            println("[LessonPlayer] ✗ ERROR: Cannot submit - no lesson loaded")
            return
        }

        println("[LessonPlayer] Lesson ID: ${lesson.id}")
        println("[LessonPlayer] Lesson title: ${lesson.title}")
        println("[LessonPlayer] User ID: $userId")
        println("[LessonPlayer] Total questions: ${lesson.questions.size}")
        println("[LessonPlayer] Answers provided: ${_userAnswers.value.size}")

        // Check for unanswered questions
        val unanswered = lesson.questions.filter { !_userAnswers.value.containsKey(it.id) }
        if (unanswered.isNotEmpty()) {
            println("[LessonPlayer] ⚠ WARNING: ${unanswered.size} unanswered questions")
            unanswered.forEach { q ->
                println("[LessonPlayer]   - Question ${lesson.questions.indexOf(q) + 1}: ${q.questionText.take(30)}...")
            }
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Convert user answers to API format
                println("[LessonPlayer] Converting answers to API format...")
                val answers =
                    lesson.questions.mapNotNull { question ->
                        val answer = _userAnswers.value[question.id]
                        answer?.let {
                            when (it) {
                                is UserAnswer.MultipleChoice -> {
                                    println(
                                        "[LessonPlayer]   Q${lesson.questions.indexOf(question) + 1}: Multiple Choice - choice=$it.choiceId, correct=$it.isCorrect",
                                    )
                                    UserQuestionAnswerCreate(
                                        userId = userId,
                                        questionId = question.id,
                                        lessonId = lesson.id,
                                        selectedChoiceId = it.choiceId,
                                        isCorrect = it.isCorrect,
                                    )
                                }
                                is UserAnswer.Identification -> {
                                    println(
                                        "[LessonPlayer]   Q${lesson.questions.indexOf(question) + 1}: Text Entry - answer=${it.answer.take(
                                            30,
                                        )}..., correct=$it.isCorrect",
                                    )
                                    UserQuestionAnswerCreate(
                                        userId = userId,
                                        questionId = question.id,
                                        lessonId = lesson.id,
                                        answerText = it.answer,
                                        isCorrect = it.isCorrect,
                                    )
                                }
                                is UserAnswer.VoiceRecording -> {
                                    println(
                                        "[LessonPlayer]   Q${lesson.questions.indexOf(question) + 1}: Voice Recording - url=$it.recordingUrl",
                                    )
                                    UserQuestionAnswerCreate(
                                        userId = userId,
                                        questionId = question.id,
                                        lessonId = lesson.id,
                                        voiceRecordingUrl = it.recordingUrl,
                                        isCorrect = it.isCorrect,
                                    )
                                }
                                is UserAnswer.Matching -> {
                                    println(
                                        "[LessonPlayer]   Q${lesson.questions.indexOf(question) + 1}: Matching - matches=${it.matches.size} pairs, correct=$it.isCorrect",
                                    )
                                    UserQuestionAnswerCreate(
                                        userId = userId,
                                        questionId = question.id,
                                        lessonId = lesson.id,
                                        answerText = it.matches.toString(), // Store as text for now
                                        isCorrect = it.isCorrect,
                                    )
                                }
                            }
                        }
                    }

                println("[LessonPlayer] Converted ${answers.size} answers for submission")

                val request =
                    SubmitLessonAnswersRequest(
                        userId = userId,
                        lessonId = lesson.id,
                        answers = answers,
                    )

                println("[LessonPlayer] Repository call: submitLessonAnswers(...)")
                println("[LessonPlayer] Request details: userId=$userId, lessonId=${lesson.id}, answerCount=${answers.size}")

                repository.submitLessonAnswers(request)
                    .onSuccess { result ->
                        println("[LessonPlayer] ✓ Lesson submitted successfully!")
                        println(
                            "[LessonPlayer] Result: score=${result.score}%, correct=${result.correctAnswers}/${result.totalQuestions}, passed=${result.isPassed}",
                        )
                        _submissionResult.value = result
                        _isSubmitted.value = true
                        _errorMessage.value = null
                        
                        // Invalidate lesson content cache
                        (repository as? LessonContentRepositoryImpl)?.clearUserCache(userId)
                        println("[LessonPlayer] ✅ Lesson content cache cleared")
                        
                        // Invalidate lesson topics cache to refresh topic unlocking
                        try {
                            val topicsRepo = org.example.project.data.repository.LessonTopicsRepositoryImpl()
                            topicsRepo.clearTopicsCache()
                            println("[LessonPlayer] ✅ Lesson topics cache cleared")
                        } catch (e: Exception) {
                            println("[LessonPlayer] ⚠️ Failed to clear topics cache: ${e.message}")
                        }
                        
                        // Invalidate progress analytics cache (all languages for this user)
                        ProgressAnalyticsService.invalidateCache(userId)
                        println("[LessonPlayer] ✅ Progress analytics cache invalidated")
                        
                        // Notify parent to invalidate caches
                        onLessonCompleted?.invoke(userId, lesson.id)
                    }
                    .onFailure { error ->
                        val errorMsg = "Failed to submit lesson: ${error.message}"
                        println("[LessonPlayer] ✗ ERROR: $errorMsg")
                        println("[LessonPlayer] Error type: ${error.javaClass.simpleName}")
                        println("[LessonPlayer] Error details: ${error.stackTraceToString()}")
                        _errorMessage.value = errorMsg
                    }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error submitting lesson: ${e.message}"
                println("[LessonPlayer] ✗ EXCEPTION: $errorMsg")
                println("[LessonPlayer] Exception type: ${e.javaClass.simpleName}")
                println("[LessonPlayer] Exception details: ${e.stackTraceToString()}")
                _errorMessage.value = errorMsg
            }

            _isLoading.value = false
            println("[LessonPlayer] =========================================")
        }
    }

    fun resetLesson() {
        _currentQuestionIndex.value = 0
        _userAnswers.value = emptyMap()
        _isSubmitted.value = false
        _submissionResult.value = null
        _showFeedback.value = emptyMap()
        _checkedAnswers.value = emptyMap()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

// ============================================
// USER ANSWER TYPES
// ============================================

sealed class UserAnswer {
    data class MultipleChoice(
        val choiceId: String,
        val isCorrect: Boolean,
    ) : UserAnswer()

    data class Identification(
        val answer: String,
        val isCorrect: Boolean,
    ) : UserAnswer()

    data class VoiceRecording(
        val recordingUrl: String,
        val isCorrect: Boolean?,
    ) : UserAnswer()

    data class Matching(
        val matches: Map<String, String>, // Map of left item ID to right item ID
        val isCorrect: Boolean,
    ) : UserAnswer()
}
