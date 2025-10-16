package org.example.project.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.core.onboarding.OnboardingQuestionBank
import org.example.project.presentation.viewmodel.OnboardingViewModel
import org.example.project.ui.components.onboarding.AvatarAssistant
import org.example.project.ui.components.onboarding.ChatMessageBubble
import org.example.project.ui.components.onboarding.OnboardingProgressIndicator
import org.example.project.ui.components.onboarding.QuestionInputPanel
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(),
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading by viewModel.isLoading
    val isComplete by viewModel.isComplete
    val currentQuestion by viewModel.currentQuestion
    val messages by viewModel.messages
    val error by viewModel.error
    val isSaving by viewModel.isSaving
    val successMessage by viewModel.successMessage

    Surface(color = WordBridgeColors.BackgroundLight, modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> LoadingState()
            error != null -> ErrorState(message = error ?: "Unknown error", onRetry = viewModel::retry)
            isSaving -> {
                println("🎨 UI: Showing SAVING state")
                SavingState()
            }
            isComplete -> {
                println("🎨 UI: Showing COMPLETION state")
                CompletionState(
                    onContinue = onComplete,
                    successMessage = successMessage,
                )
            }
            currentQuestion != null -> {
                val question = currentQuestion!!
                val totalSteps = OnboardingQuestionBank.questions.size
                val currentStep = OnboardingQuestionBank.questions.indexOf(question)

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                ) {
                    OnboardingProgressIndicator(
                        currentStep = currentStep,
                        totalSteps = totalSteps,
                        currentCategory = question.category,
                    )

                    Spacer(modifier = Modifier.size(24.dp))

                    AvatarAssistant(modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.size(24.dp))

                    val listState = rememberLazyListState()

                    // Auto-scroll to show the latest message
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) {
                            println("??? Auto-scrolling to item ${messages.size - 1}")
                            listState.scrollToItem(messages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        reverseLayout = false,
                    ) {
                        println("??? UI: Rendering ${messages.size} messages")
                        items(messages.size) { index ->
                            val message = messages[index]
                            println("??? UI: Rendering message $index: ${message.sender} - ${message.text.take(30)}")
                            ChatMessageBubble(message = message)
                        }
                    }

                    Spacer(modifier = Modifier.size(24.dp))

                    QuestionInputPanel(
                        question = question,
                        onSubmit = { response -> viewModel.submitResponse(question, response) },
                    )
                }
            }
        }
    }

    if (isComplete) {
        println("??? UI: isComplete = true, triggering completion flow")
        LaunchedEffect(Unit) {
            println("??? UI: Calling viewModel.completeOnboarding")
            viewModel.completeOnboarding { result ->
                if (result.isSuccess) {
                    println("??? UI: Completion successful, calling onComplete callback")
                    onComplete()
                } else {
                    println("??? UI: Completion failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = WordBridgeColors.PrimaryPurple)
            Spacer(modifier = Modifier.size(16.dp))
            Text("Getting things ready...", color = WordBridgeColors.TextSecondary)
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Let's try that again",
                style = MaterialTheme.typography.headlineMedium,
                color = WordBridgeColors.TextPrimary,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(message, color = WordBridgeColors.TextSecondary)
            Spacer(modifier = Modifier.size(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = WordBridgeColors.PrimaryPurple),
            ) {
                Text("Retry", color = WordBridgeColors.BackgroundWhite)
            }
        }
    }
}

@Composable
private fun SavingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = WordBridgeColors.PrimaryPurple)
            Spacer(modifier = Modifier.size(16.dp))
            Text("Creating your personalized profile...", color = WordBridgeColors.TextSecondary)
        }
    }
}

@Composable
private fun CompletionState(
    onContinue: () -> Unit,
    successMessage: String?,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "You're all set!",
                style = MaterialTheme.typography.headlineMedium,
                color = WordBridgeColors.PrimaryPurple,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = successMessage ?: "I'm Ceddie, and I'm ready with your personalized learning plan!",
                color = WordBridgeColors.TextSecondary,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
            Spacer(modifier = Modifier.size(24.dp))
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = WordBridgeColors.PrimaryPurple),
            ) {
                Text("Start learning", color = WordBridgeColors.BackgroundWhite)
            }
        }
    }
}
