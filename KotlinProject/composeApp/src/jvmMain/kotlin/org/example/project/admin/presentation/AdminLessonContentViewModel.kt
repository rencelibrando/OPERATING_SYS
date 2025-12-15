package org.example.project.admin.presentation

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.core.utils.ErrorLogger
import org.example.project.data.repository.LessonContentRepository
import org.example.project.data.repository.LessonContentRepositoryImpl
import org.example.project.domain.model.*
import java.io.File

private const val LOG_TAG = "AdminLessonContentViewModel.kt"

/**
 * ViewModel for managing lesson content in the admin panel.
 * Handles lesson creation, editing, and question management with dynamic fields.
 */
class AdminLessonContentViewModel : ViewModel() {
    
    private val repository: LessonContentRepository = LessonContentRepositoryImpl()
    
    // State
    private val _lessons = mutableStateOf<List<LessonSummary>>(emptyList())
    val lessons: State<List<LessonSummary>> = _lessons
    
    private val _currentLesson = mutableStateOf<LessonContent?>(null)
    val currentLesson: State<LessonContent?> = _currentLesson
    
    private val _selectedTopicId = mutableStateOf<String?>(null)
    val selectedTopicId: State<String?> = _selectedTopicId
    
    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading
    
    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage
    
    private val _successMessage = mutableStateOf<String?>(null)
    val successMessage: State<String?> = _successMessage
    
    // Lesson creation state
    private val _lessonTitle = mutableStateOf("")
    val lessonTitle: State<String> = _lessonTitle
    
    private val _lessonDescription = mutableStateOf("")
    val lessonDescription: State<String> = _lessonDescription
    
    private val _questions = mutableStateOf<List<QuestionBuilder>>(emptyList())
    val questions: State<List<QuestionBuilder>> = _questions
    
    private val _isPublished = mutableStateOf(false)
    val isPublished: State<Boolean> = _isPublished
    
    // ============================================
    // LESSON OPERATIONS
    // ============================================
    
    fun loadLessonsForTopic(topicId: String) {
        println("[AdminLesson] Loading lessons for topic: $topicId")
        _selectedTopicId.value = topicId
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                println("[AdminLesson] Repository call: getLessonsByTopic(topicId=$topicId, publishedOnly=false)")
                repository.getLessonsByTopic(topicId, publishedOnly = false)
                    .onSuccess { lessonList ->
                        println("[AdminLesson] ✓ Successfully loaded ${lessonList.size} lessons for topic $topicId")
                        _lessons.value = lessonList
                        _errorMessage.value = null
                    }
                    .onFailure { error ->
                        val errorMsg = "Failed to load lessons for topic: ${error.message}"
                        ErrorLogger.logException(LOG_TAG, error, "Failed to load lessons for topic")
                        _errorMessage.value = errorMsg
                    }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error loading lessons: ${e.message}"
                println("[AdminLesson] ✗ EXCEPTION: $errorMsg")
                println("[AdminLesson] Exception details: ${e.stackTraceToString()}")
                _errorMessage.value = errorMsg
            }
            
            _isLoading.value = false
        }
    }
    
    fun loadLesson(lessonId: String) {
        println("[AdminLesson] Loading lesson for editing: $lessonId")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                println("[AdminLesson] Repository call: getLessonById(lessonId=$lessonId, includeQuestions=true)")
                repository.getLessonById(lessonId, includeQuestions = true)
                    .onSuccess { lesson ->
                        println("[AdminLesson] ✓ Successfully loaded lesson: ${lesson.title}")
                        println("[AdminLesson] Lesson details: id=${lesson.id}, questions=${lesson.questions.size}, published=${lesson.isPublished}")
                        _currentLesson.value = lesson
                        loadLessonForEditing(lesson)
                        _errorMessage.value = null
                    }
                    .onFailure { error ->
                        val errorMsg = "Failed to load lesson $lessonId: ${error.message}"
                        println("[AdminLesson] ✗ ERROR: $errorMsg")
                        println("[AdminLesson] Error details: ${error.stackTraceToString()}")
                        _errorMessage.value = errorMsg
                    }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error loading lesson: ${e.message}"
                println("[AdminLesson] ✗ EXCEPTION: $errorMsg")
                println("[AdminLesson] Exception details: ${e.stackTraceToString()}")
                _errorMessage.value = errorMsg
            }
            
            _isLoading.value = false
        }
    }
    
    private fun loadLessonForEditing(lesson: LessonContent) {
        _lessonTitle.value = lesson.title
        _lessonDescription.value = lesson.description ?: ""
        _isPublished.value = lesson.isPublished
        
        println("[AdminLesson] Loading ${lesson.questions.size} questions for editing...")
        
        // Convert questions to builders
        _questions.value = lesson.questions.mapIndexed { index, question ->
            println("[AdminLesson]   Question ${index + 1} (${question.questionType}):")
            println("[AdminLesson]     - explanation: ${if (question.explanation.isNullOrBlank()) "NOT SET" else "\"${question.explanation}\""}")
            println("[AdminLesson]     - wrongAnswerFeedback: ${if (question.wrongAnswerFeedback.isNullOrBlank()) "NOT SET" else "\"${question.wrongAnswerFeedback}\""}")
            
            QuestionBuilder(
                id = question.id,
                type = question.questionType,
                text = question.questionText,
                answerText = question.answerText ?: "",
                questionAudioUrl = question.questionAudioUrl,
                answerAudioUrl = question.answerAudioUrl,
                errorText = question.errorText ?: "",
                explanation = question.explanation ?: "",
                wrongAnswerFeedback = question.wrongAnswerFeedback ?: "",
                choices = question.choices.map { choice ->
                    ChoiceBuilder(
                        id = choice.id,
                        text = choice.choiceText,
                        isCorrect = choice.isCorrect,
                        imageUrl = choice.imageUrl,
                        audioUrl = choice.audioUrl,
                        matchPairId = choice.matchPairId
                    )
                }.toMutableList()
            )
        }
    }
    
    fun createLesson(topicId: String) {
        println("[AdminLesson] ========== CREATING LESSON ==========")
        println("[AdminLesson] Topic ID: $topicId")
        println("[AdminLesson] Lesson Title: ${_lessonTitle.value}")
        println("[AdminLesson] Description: ${_lessonDescription.value.takeIf { it.isNotBlank() } ?: "(empty)"}")
        println("[AdminLesson] Published: ${_isPublished.value}")
        println("[AdminLesson] Number of questions: ${_questions.value.size}")
        
        viewModelScope.launch {
            println("[AdminLesson] Starting validation...")
            if (!validateLesson()) {
                println("[AdminLesson] ✗ Validation failed - aborting lesson creation")
                return@launch
            }
            println("[AdminLesson] ✓ Validation passed")
            
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                // Log question details
                _questions.value.forEachIndexed { index, question ->
                    println("[AdminLesson] Question ${index + 1}: type=${question.type}, text=${question.text.take(50)}...")
                    if (question.choices.isNotEmpty()) {
                        println("[AdminLesson]   Choices: ${question.choices.size} options")
                    }
                }
                
                val lessonCreate = LessonCreate(
                    topicId = topicId,
                    title = _lessonTitle.value,
                    description = _lessonDescription.value.takeIf { it.isNotBlank() },
                    lessonOrder = _lessons.value.size,
                    isPublished = _isPublished.value,
                    questions = _questions.value.mapIndexed { index, question -> 
                        question.toQuestionCreate(index) 
                    }
                )
                
                println("[AdminLesson] Repository call: createLesson(...)")
                println("[AdminLesson] Lesson data: title='${lessonCreate.title}', questions=${lessonCreate.questions.size}, order=${lessonCreate.lessonOrder}")
                
                repository.createLesson(lessonCreate)
                    .onSuccess { createdLesson ->
                        println("[AdminLesson] ✓ Lesson created successfully!")
                        println("[AdminLesson] Created lesson ID: ${createdLesson.id}")
                        _successMessage.value = "Lesson '${_lessonTitle.value}' created successfully with ${_questions.value.size} questions"
                        clearForm()
                        println("[AdminLesson] Reloading lessons list...")
                        loadLessonsForTopic(topicId)
                    }
                    .onFailure { error ->
                        val errorMsg = "Failed to create lesson '${_lessonTitle.value}': ${error.message}"
                        println("[AdminLesson] ✗ ERROR: $errorMsg")
                        println("[AdminLesson] Error type: ${error.javaClass.simpleName}")
                        println("[AdminLesson] Error details: ${error.stackTraceToString()}")
                        _errorMessage.value = errorMsg
                    }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error creating lesson: ${e.message}"
                println("[AdminLesson] ✗ EXCEPTION: $errorMsg")
                println("[AdminLesson] Exception type: ${e.javaClass.simpleName}")
                println("[AdminLesson] Exception details: ${e.stackTraceToString()}")
                _errorMessage.value = errorMsg
            }
            
            _isLoading.value = false
            println("[AdminLesson] =========================================")
        }
    }
    
    fun updateLesson(lessonId: String) {
        println("[AdminLesson] ========== UPDATING LESSON ==========")
        println("[AdminLesson] Lesson ID: $lessonId")
        println("[AdminLesson] New Title: ${_lessonTitle.value}")
        println("[AdminLesson] Number of questions: ${_questions.value.size}")
        
        viewModelScope.launch {
            println("[AdminLesson] Starting validation...")
            if (!validateLesson()) {
                println("[AdminLesson] ✗ Validation failed - aborting lesson update")
                return@launch
            }
            println("[AdminLesson] ✓ Validation passed")
            
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                println("[AdminLesson] Step 1: Updating basic lesson info...")
                repository.updateLesson(
                    lessonId = lessonId,
                    title = _lessonTitle.value,
                    description = _lessonDescription.value.takeIf { it.isNotBlank() },
                    isPublished = _isPublished.value
                )
                    .onSuccess { updatedLesson ->
                        println("[AdminLesson] ✓ Lesson info updated")
                        println("[AdminLesson] Step 2: Deleting ${updatedLesson.questions.size} existing questions...")
                        
                        // Delete all existing questions and recreate them
                        val existingQuestionIds = updatedLesson.questions.map { it.id }
                        
                        var deletedCount = 0
                        var deleteErrors = 0
                        existingQuestionIds.forEach { questionId ->
                            try {
                                repository.deleteQuestion(questionId)
                                deletedCount++
                            } catch (e: Exception) {
                                deleteErrors++
                                println("[AdminLesson] ⚠ Failed to delete question $questionId: ${e.message}")
                            }
                        }
                        
                        println("[AdminLesson] Deleted $deletedCount questions${if (deleteErrors > 0) " ($deleteErrors errors)" else ""}")
                        println("[AdminLesson] Step 3: Creating ${_questions.value.size} new questions...")
                        
                        // Create new questions
                        var createdCount = 0
                        var createErrors = 0
                        _questions.value.forEachIndexed { index, questionBuilder ->
                            try {
                                val questionCreate = questionBuilder.toQuestionCreate(index)
                                println("[AdminLesson]   Creating question ${index + 1}: type=${questionCreate.questionType}")
                                println("[AdminLesson]     - wrongAnswerFeedback: ${if (questionCreate.wrongAnswerFeedback.isNullOrBlank()) "NOT SET" else "\"${questionCreate.wrongAnswerFeedback}\""}")
                                println("[AdminLesson]     - explanation: ${if (questionCreate.explanation.isNullOrBlank()) "NOT SET" else "\"${questionCreate.explanation}\""}")
                                repository.createQuestion(lessonId, questionCreate)
                                createdCount++
                            } catch (e: Exception) {
                                createErrors++
                                println("[AdminLesson] ✗ Failed to create question ${index + 1}: ${e.message}")
                            }
                        }
                        
                        if (createErrors > 0) {
                            val errorMsg = "Lesson updated but failed to create $createErrors out of ${_questions.value.size} questions"
                            println("[AdminLesson] ⚠ WARNING: $errorMsg")
                            _errorMessage.value = errorMsg
                        } else {
                            println("[AdminLesson] ✓ All questions created successfully ($createdCount questions)")
                        }
                        
                        _successMessage.value = "Lesson '${_lessonTitle.value}' updated successfully with $createdCount questions"
                        _selectedTopicId.value?.let { 
                            println("[AdminLesson] Reloading lessons list...")
                            loadLessonsForTopic(it) 
                        }
                        clearForm()
                    }
                    .onFailure { error ->
                        val errorMsg = "Failed to update lesson: ${error.message}"
                        println("[AdminLesson] ✗ ERROR: $errorMsg")
                        println("[AdminLesson] Error details: ${error.stackTraceToString()}")
                        _errorMessage.value = errorMsg
                    }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error updating lesson: ${e.message}"
                println("[AdminLesson] ✗ EXCEPTION: $errorMsg")
                println("[AdminLesson] Exception details: ${e.stackTraceToString()}")
                _errorMessage.value = errorMsg
            }
            
            _isLoading.value = false
            println("[AdminLesson] =========================================")
        }
    }
    
    fun deleteLesson(lessonId: String) {
        println("[AdminLesson] ========== DELETING LESSON ==========")
        println("[AdminLesson] Lesson ID: $lessonId")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                println("[AdminLesson] Repository call: deleteLesson(lessonId=$lessonId)")
                repository.deleteLesson(lessonId)
                    .onSuccess {
                        println("[AdminLesson] ✓ Lesson deleted successfully!")
                        _successMessage.value = "Lesson deleted successfully"
                        _selectedTopicId.value?.let { 
                            println("[AdminLesson] Reloading lessons list...")
                            loadLessonsForTopic(it) 
                        }
                    }
                    .onFailure { error ->
                        val errorMsg = "Failed to delete lesson: ${error.message}"
                        println("[AdminLesson] ✗ ERROR: $errorMsg")
                        println("[AdminLesson] Error details: ${error.stackTraceToString()}")
                        _errorMessage.value = errorMsg
                    }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error deleting lesson: ${e.message}"
                println("[AdminLesson] ✗ EXCEPTION: $errorMsg")
                println("[AdminLesson] Exception details: ${e.stackTraceToString()}")
                _errorMessage.value = errorMsg
            }
            
            _isLoading.value = false
            println("[AdminLesson] =========================================")
        }
    }
    
    private fun validateLesson(): Boolean {
        println("[AdminLesson] Validating lesson...")
        
        if (_lessonTitle.value.isBlank()) {
            val errorMsg = "Lesson title is required"
            println("[AdminLesson] ✗ Validation error: $errorMsg")
            _errorMessage.value = errorMsg
            return false
        }
        println("[AdminLesson] ✓ Title validated: '${_lessonTitle.value}'")
        
        if (_questions.value.isEmpty()) {
            val errorMsg = "At least one question is required"
            println("[AdminLesson] ✗ Validation error: $errorMsg")
            _errorMessage.value = errorMsg
            return false
        }
        println("[AdminLesson] ✓ Question count validated: ${_questions.value.size} questions")
        
        _questions.value.forEachIndexed { index, question ->
            println("[AdminLesson] Validating question ${index + 1} (type: ${question.type})...")
            
            if (question.text.isBlank()) {
                val errorMsg = "Question ${index + 1} text is required"
                println("[AdminLesson] ✗ Validation error: $errorMsg")
                _errorMessage.value = errorMsg
                return false
            }
            println("[AdminLesson]   ✓ Question text validated")
            
            when (question.type) {
                QuestionType.MULTIPLE_CHOICE -> {
                    println("[AdminLesson]   Validating multiple choice question...")
                    if (question.choices.size < 2) {
                        val errorMsg = "Question ${index + 1} (Multiple Choice) needs at least 2 choices (found: ${question.choices.size})"
                        println("[AdminLesson]   ✗ Validation error: $errorMsg")
                        _errorMessage.value = errorMsg
                        return false
                    }
                    println("[AdminLesson]   ✓ Choice count validated: ${question.choices.size} choices")
                    
                    val correctCount = question.choices.count { it.isCorrect }
                    if (correctCount == 0) {
                        val errorMsg = "Question ${index + 1} (Multiple Choice) needs at least one correct answer (found: 0)"
                        println("[AdminLesson]   ✗ Validation error: $errorMsg")
                        _errorMessage.value = errorMsg
                        return false
                    }
                    println("[AdminLesson]   ✓ Correct answer validated: $correctCount correct choice(s)")
                }
                QuestionType.TEXT_ENTRY -> {
                    println("[AdminLesson]   Validating text entry question...")
                    if (question.answerText.isBlank()) {
                        val errorMsg = "Question ${index + 1} (Text Entry) needs an answer"
                        println("[AdminLesson]   ✗ Validation error: $errorMsg")
                        _errorMessage.value = errorMsg
                        return false
                    }
                    println("[AdminLesson]   ✓ Answer text validated")
                }
                QuestionType.MATCHING -> {
                    println("[AdminLesson]   Validating matching question...")
                    if (question.choices.size < 2) {
                        val errorMsg = "Question ${index + 1} (Matching) needs at least 2 items to match (found: ${question.choices.size})"
                        println("[AdminLesson]   ✗ Validation error: $errorMsg")
                        _errorMessage.value = errorMsg
                        return false
                    }
                    println("[AdminLesson]   ✓ Matching items validated: ${question.choices.size} items")
                }
                QuestionType.PARAPHRASING -> {
                    println("[AdminLesson]   Validating paraphrasing question...")
                // Sample answer is helpful but optional; keep non-blocking to avoid friction
                if (question.answerText.isBlank()) {
                    println("[AdminLesson]   ⚠ No sample answer provided for paraphrasing (allowed)")
                } else {
                    println("[AdminLesson]   ✓ Sample answer provided")
                }
                }
                QuestionType.ERROR_CORRECTION -> {
                    println("[AdminLesson]   Validating error correction question...")
                    if (question.errorText.isBlank()) {
                        val errorMsg = "Question ${index + 1} (Error Correction) needs text with errors"
                        println("[AdminLesson]   ✗ Validation error: $errorMsg")
                        _errorMessage.value = errorMsg
                        return false
                    }
                    if (question.answerText.isBlank()) {
                        val errorMsg = "Question ${index + 1} (Error Correction) needs the corrected text"
                        println("[AdminLesson]   ✗ Validation error: $errorMsg")
                        _errorMessage.value = errorMsg
                        return false
                    }
                    println("[AdminLesson]   ✓ Error text and corrected text validated")
                }
            }
            println("[AdminLesson]   ✓ Question ${index + 1} validation passed")
        }
        
        println("[AdminLesson] ✓ All validations passed!")
        return true
    }
    
    // ============================================
    // QUESTION MANAGEMENT
    // ============================================
    
    fun addQuestion(type: QuestionType) {
        val newQuestion = QuestionBuilder(
            type = type,
            text = "",
            answerText = "",
            errorText = "",
            explanation = "",
            wrongAnswerFeedback = "",
            choices = when (type) {
                QuestionType.MULTIPLE_CHOICE -> listOf(
                    ChoiceBuilder(text = "", isCorrect = false),
                    ChoiceBuilder(text = "", isCorrect = false)
                )
                QuestionType.MATCHING -> listOf(
                    ChoiceBuilder(text = "Item 1", isCorrect = false, matchPairId = "pair1"),
                    ChoiceBuilder(text = "Match 1", isCorrect = false, matchPairId = "pair1"),
                    ChoiceBuilder(text = "Item 2", isCorrect = false, matchPairId = "pair2"),
                    ChoiceBuilder(text = "Match 2", isCorrect = false, matchPairId = "pair2")
                )
                // For paraphrasing we prefill a gentle hint to guide admins
                QuestionType.PARAPHRASING -> emptyList()
                else -> emptyList()
            }
        )
        _questions.value = _questions.value + newQuestion
    }
    
    fun removeQuestion(index: Int) {
        _questions.value = _questions.value.filterIndexed { i, _ -> i != index }
    }
    
    fun updateQuestionText(index: Int, text: String) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == index) q.copy(text = text) else q
        }
    }
    
    fun updateQuestionAnswerText(index: Int, answer: String) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == index) q.copy(answerText = answer) else q
        }
    }
    
    fun updateQuestionAudioUrl(index: Int, url: String?) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == index) q.copy(questionAudioUrl = url) else q
        }
    }
    
    fun updateAnswerAudioUrl(index: Int, url: String?) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == index) q.copy(answerAudioUrl = url) else q
        }
    }
    
    fun updateQuestionErrorText(index: Int, text: String) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == index) q.copy(errorText = text) else q
        }
    }
    
    fun updateQuestionExplanation(index: Int, text: String) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == index) q.copy(explanation = text) else q
        }
    }
    
    fun updateQuestionWrongAnswerFeedback(index: Int, text: String) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == index) q.copy(wrongAnswerFeedback = text) else q
        }
    }
    
    fun updateChoiceMatchPairId(questionIndex: Int, choiceIndex: Int, pairId: String?) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == questionIndex) {
                q.copy(
                    choices = q.choices.mapIndexed { ci, c ->
                        if (ci == choiceIndex) c.copy(matchPairId = pairId) else c
                    }
                )
            } else {
                q
            }
        }
    }
    
    // ============================================
    // CHOICE MANAGEMENT
    // ============================================
    
    fun addChoice(questionIndex: Int, matchPairId: String? = null) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == questionIndex) {
                q.copy(choices = q.choices + ChoiceBuilder(
                    text = "", 
                    isCorrect = false,
                    matchPairId = matchPairId
                ))
            } else {
                q
            }
        }
    }
    
    fun removeChoice(questionIndex: Int, choiceIndex: Int) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == questionIndex) {
                q.copy(choices = q.choices.filterIndexed { ci, _ -> ci != choiceIndex })
            } else {
                q
            }
        }
    }
    
    fun updateChoiceText(questionIndex: Int, choiceIndex: Int, text: String) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == questionIndex) {
                q.copy(
                    choices = q.choices.mapIndexed { ci, c ->
                        if (ci == choiceIndex) c.copy(text = text) else c
                    }
                )
            } else {
                q
            }
        }
    }
    
    fun updateChoiceCorrect(questionIndex: Int, choiceIndex: Int, isCorrect: Boolean) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == questionIndex) {
                q.copy(
                    choices = q.choices.mapIndexed { ci, c ->
                        if (ci == choiceIndex) c.copy(isCorrect = isCorrect) else c
                    }
                )
            } else {
                q
            }
        }
    }
    
    fun updateChoiceImageUrl(questionIndex: Int, choiceIndex: Int, url: String?) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == questionIndex) {
                q.copy(
                    choices = q.choices.mapIndexed { ci, c ->
                        if (ci == choiceIndex) c.copy(imageUrl = url) else c
                    }
                )
            } else {
                q
            }
        }
    }
    
    fun updateChoiceAudioUrl(questionIndex: Int, choiceIndex: Int, url: String?) {
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == questionIndex) {
                q.copy(
                    choices = q.choices.mapIndexed { ci, c ->
                        if (ci == choiceIndex) c.copy(audioUrl = url) else c
                    }
                )
            } else {
                q
            }
        }
    }
    
    fun shuffleMatchingRightSide(questionIndex: Int) {
        println("[AdminLesson] Shuffling matching question right side items")
        _questions.value = _questions.value.mapIndexed { i, q ->
            if (i == questionIndex) {
                // Group choices by pair ID
                val pairs = q.choices.groupBy { it.matchPairId ?: "unpaired" }
                val validPairs = pairs.filter { (pairId, choices) -> 
                    pairId != "unpaired" && choices.size >= 2 
                }
                
                if (validPairs.isEmpty()) {
                    println("[AdminLesson] ⚠ No valid pairs to shuffle")
                    return@mapIndexed q
                }
                
                // Extract left items (first in each pair) and right items (second in each pair)
                val leftItems = validPairs.values.map { it[0] }
                val rightItems = validPairs.values.map { it[1] }.shuffled()
                
                println("[AdminLesson] ✓ Shuffled ${rightItems.size} right-side items")
                
                // Recreate choices with shuffled right items
                val newChoices = mutableListOf<ChoiceBuilder>()
                leftItems.forEachIndexed { index, leftItem ->
                    val rightItem = rightItems[index]
                    // Keep the same pair ID for both
                    newChoices.add(leftItem)
                    newChoices.add(rightItem.copy(matchPairId = leftItem.matchPairId))
                }
                
                q.copy(choices = newChoices)
            } else {
                q
            }
        }
    }
    
    // ============================================
    // MEDIA UPLOAD
    // ============================================
    
    fun uploadMedia(file: File, mediaType: String, onSuccess: (String) -> Unit) {
        println("[AdminLesson] ========== UPLOADING MEDIA ==========")
        println("[AdminLesson] File: ${file.name}")
        println("[AdminLesson] File size: ${file.length()} bytes")
        println("[AdminLesson] Media type: $mediaType")
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                if (!file.exists()) {
                    val errorMsg = "File does not exist: ${file.absolutePath}"
                    println("[AdminLesson] ✗ ERROR: $errorMsg")
                    _errorMessage.value = errorMsg
                    _isLoading.value = false
                    return@launch
                }
                
                if (file.length() == 0L) {
                    val errorMsg = "File is empty: ${file.name}"
                    println("[AdminLesson] ✗ ERROR: $errorMsg")
                    _errorMessage.value = errorMsg
                    _isLoading.value = false
                    return@launch
                }
                
                println("[AdminLesson] Repository call: uploadMedia(file=${file.name}, mediaType=$mediaType)")
                repository.uploadMedia(file, mediaType)
                    .onSuccess { response ->
                        println("[AdminLesson] ✓ Media uploaded successfully!")
                        println("[AdminLesson] Uploaded URL: ${response.url}")
                        onSuccess(response.url)
                        // Don't set success message here to avoid closing the dialog
                    }
                    .onFailure { error ->
                        val errorMsg = "Failed to upload ${file.name} ($mediaType): ${error.message}"
                        println("[AdminLesson] ✗ ERROR: $errorMsg")
                        println("[AdminLesson] Error details: ${error.stackTraceToString()}")
                        _errorMessage.value = errorMsg
                    }
            } catch (e: Exception) {
                val errorMsg = "Unexpected error uploading media: ${e.message}"
                println("[AdminLesson] ✗ EXCEPTION: $errorMsg")
                println("[AdminLesson] Exception details: ${e.stackTraceToString()}")
                _errorMessage.value = errorMsg
            }
            
            _isLoading.value = false
            println("[AdminLesson] =========================================")
        }
    }
    
    // ============================================
    // FORM MANAGEMENT
    // ============================================
    
    fun setLessonTitle(title: String) {
        _lessonTitle.value = title
    }
    
    fun setLessonDescription(description: String) {
        _lessonDescription.value = description
    }
    
    fun setIsPublished(published: Boolean) {
        _isPublished.value = published
    }
    
    fun clearForm() {
        _lessonTitle.value = ""
        _lessonDescription.value = ""
        _questions.value = emptyList()
        _isPublished.value = false
        _currentLesson.value = null
    }
    
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}

// ============================================
// BUILDER CLASSES
// ============================================

data class QuestionBuilder(
    val id: String? = null,
    val type: QuestionType,
    val text: String,
    val answerText: String = "",
    val questionAudioUrl: String? = null,
    val answerAudioUrl: String? = null,
    val errorText: String = "",
    val explanation: String = "",
    val wrongAnswerFeedback: String = "",
    val choices: List<ChoiceBuilder> = emptyList()
) {
    fun toQuestionCreate(order: Int = 0) = QuestionCreate(
        questionType = type,
        questionText = text,
        questionOrder = order,
        answerText = answerText.takeIf { it.isNotBlank() },
        questionAudioUrl = questionAudioUrl,
        answerAudioUrl = answerAudioUrl,
        errorText = errorText.takeIf { it.isNotBlank() },
        explanation = explanation.takeIf { it.isNotBlank() },
        wrongAnswerFeedback = wrongAnswerFeedback.takeIf { it.isNotBlank() },
        choices = choices.mapIndexed { index, choice -> choice.toChoiceCreate(index) }
    )
}

data class ChoiceBuilder(
    val id: String? = null,
    val text: String,
    val isCorrect: Boolean,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val matchPairId: String? = null
) {
    fun toChoiceCreate(order: Int = 0) = QuestionChoiceCreate(
        choiceText = text,
        choiceOrder = order,
        isCorrect = isCorrect,
        imageUrl = imageUrl,
        audioUrl = audioUrl,
        matchPairId = matchPairId
    )
}
