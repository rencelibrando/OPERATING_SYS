package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.example.project.core.onboarding.OnboardingMessage
import org.example.project.core.onboarding.OnboardingMessageSender
import org.example.project.core.onboarding.OnboardingQuestion
import org.example.project.core.onboarding.OnboardingQuestionBank
import org.example.project.core.onboarding.OnboardingResponse
import org.example.project.core.onboarding.summaryText
import org.example.project.core.onboarding.OnboardingService
import org.example.project.core.onboarding.summaryText

class OnboardingViewModel(
    private val onboardingService: OnboardingService = OnboardingService()
) : ViewModel() {

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _isComplete = mutableStateOf(false)
    val isComplete: State<Boolean> = _isComplete

    private val _currentQuestion = mutableStateOf<OnboardingQuestion?>(null)
    val currentQuestion: State<OnboardingQuestion?> = _currentQuestion

    private val _messages = mutableStateOf<List<OnboardingMessage>>(emptyList())
    val messages: State<List<OnboardingMessage>> = _messages

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error
    
    private val _isSaving = mutableStateOf(false)
    val isSaving: State<Boolean> = _isSaving
    
    private val _successMessage = mutableStateOf<String?>(null)
    val successMessage: State<String?> = _successMessage

    private var typingJob: Job? = null

    init {
        viewModelScope.launch {
            println("=== OnboardingViewModel.init: STARTING ===")
            try {
                onboardingService.initialize().onSuccess { user ->
                    println("=== OnboardingViewModel: Init SUCCESS for user: ${user?.email} ===")
                    println("=== Now checking if onboarding should be shown ===")
                    
                    val shouldShow = onboardingService.shouldShowOnboarding()
                    
                    // Check if user has already completed onboarding
                    if (!shouldShow) {
                        println("=== DECISION: SKIP ONBOARDING (already completed) ===")
                        _isComplete.value = true
                    } else {
                        println("=== DECISION: SHOW ONBOARDING (not completed) ===")
                        loadNextQuestion()
                    }
                }.onFailure { error ->
                    println("=== OnboardingViewModel: Init FAILED: ${error.message} ===")
                    error.printStackTrace()
                    _error.value = getUserFriendlyErrorMessage(error)
                }
            } catch (e: Exception) {
                println("=== OnboardingViewModel: Unexpected ERROR: ${e.message} ===")
                e.printStackTrace()
                _error.value = "An unexpected error occurred. Please try again."
            } finally {
                _isLoading.value = false
                println("=== OnboardingViewModel.init: COMPLETE (loading = false) ===")
            }
        }
    }

    private fun subscribeToState() {
        viewModelScope.launch {
            onboardingService.answers.collectLatest { answers ->
                _isComplete.value = answers.size >= OnboardingQuestionBank.questions.size
                if (!_isComplete.value) {
                    loadNextQuestion()
                }
            }
        }
    }

    private fun loadNextQuestion() {
        val question = onboardingService.getNextQuestion()
        val currentStep = onboardingService.getCurrentStep()
        val totalSteps = OnboardingQuestionBank.questions.size
        
        println("??? loadNextQuestion: ${question?.id ?: "NONE"}")
        println("??? Current step: $currentStep / $totalSteps")
        
        // Check if onboarding is complete
        if (currentStep >= totalSteps && question == null) {
            println("??? All questions answered! Starting completion process...")
            
            // Start saving immediately
            viewModelScope.launch {
                completeOnboarding { result ->
                    if (result.isSuccess) {
                        println("??? Onboarding saved successfully!")
                    } else {
                        println("??? ERROR saving onboarding: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        } else {
            _currentQuestion.value = question
            question?.let { showAssistantMessage(it.prompt) }
        }
    }

    private fun showAssistantMessage(text: String) {
        println("??? showAssistantMessage: $text")
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            println("??? Adding typing indicator")
            appendMessage(OnboardingMessage(
                id = generateId(),
                sender = OnboardingMessageSender.ASSISTANT,
                text = "",
                isTyping = true
            ))

            delay(600L)

            println("??? Adding assistant message: $text")
            appendMessage(OnboardingMessage(
                id = generateId(),
                sender = OnboardingMessageSender.ASSISTANT,
                text = text,
                isTyping = false
            ))
            println("??? Total messages now: ${_messages.value.size}")
        }
    }

    private fun appendMessage(message: OnboardingMessage) {
        _messages.value = _messages.value.filter { !it.isTyping } + message
        println("??? appendMessage: sender=${message.sender}, isTyping=${message.isTyping}, text=${message.text.take(50)}")
    }

    fun submitResponse(question: OnboardingQuestion, response: OnboardingResponse) {
        println("??? ViewModel.submitResponse called for question: ${question.id}")
        println("??? Response: ${response.summaryText()}")
        
        // Clear any previous errors
        _error.value = null
        
        // Record the answer (in-memory only, no database save yet)
        onboardingService.recordAnswer(question, response)
        
        appendMessage(
            OnboardingMessage(
                id = generateId(),
                sender = OnboardingMessageSender.USER,
                text = response.summaryText()
            )
        )

        // Just load the next question - save everything at the end
        println("??? Loading next question...")
        loadNextQuestion()
    }

    fun retry() {
        _error.value = null
        viewModelScope.launch {
            onboardingService.initialize()
            loadNextQuestion()
        }
    }

    fun completeOnboarding(onComplete: (Result<Unit>) -> Unit) {
        println("??? completeOnboarding called!")
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                println("??? Calling onboardingService.completeOnboarding()...")
                onboardingService.completeOnboarding()
                    .onSuccess {
                        println("??? Onboarding completed successfully!")
                        _successMessage.value = "Welcome to WordBridge! I'm Ceddie, your AI language tutor!"
                        _isComplete.value = true
                        delay(500) // Brief delay to show success message
                        onComplete(Result.success(Unit))
                    }
                    .onFailure { error ->
                        println("??? ERROR completing onboarding: ${error.message}")
                        val friendlyError = getUserFriendlyErrorMessage(
                            error, 
                            "Failed to complete onboarding. "
                        )
                        _error.value = friendlyError
                        onComplete(Result.failure(Exception(friendlyError)))
                    }
            } catch (e: Exception) {
                println("??? Unexpected error completing onboarding: ${e.message}")
                val errorMsg = "An unexpected error occurred. Please check your connection and try again."
                _error.value = errorMsg
                onComplete(Result.failure(Exception(errorMsg)))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun resetProgress() {
        onboardingService.resetOnboarding()
        _messages.value = emptyList()
        _isComplete.value = false
        _error.value = null
        _successMessage.value = null
        loadNextQuestion()
    }
    
    /**
     * Converts technical error messages to user-friendly messages
     */
    private fun getUserFriendlyErrorMessage(error: Throwable, prefix: String = ""): String {
        val message = error.message ?: "Unknown error"
        return when {
            message.contains("network", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ->
                "${prefix}Please check your internet connection and try again."
            
            message.contains("not configured", ignoreCase = true) ->
                "${prefix}The app is not properly configured. Please contact support."
            
            message.contains("not all questions", ignoreCase = true) ||
            message.contains("incomplete", ignoreCase = true) ->
                "${prefix}Please answer all questions to continue."
            
            message.contains("verified", ignoreCase = true) ||
            message.contains("email", ignoreCase = true) ->
                "${prefix}Please verify your email address first."
            
            message.contains("Failed to verify", ignoreCase = true) ->
                "${prefix}Unable to verify data was saved. Please try again."
            
            message.contains("unauthorized", ignoreCase = true) ||
            message.contains("authentication", ignoreCase = true) ->
                "${prefix}Your session has expired. Please sign in again."
            
            else -> "$prefix${message.take(100)}"
        }
    }

    private fun generateId(): String = System.currentTimeMillis().toString()
}

