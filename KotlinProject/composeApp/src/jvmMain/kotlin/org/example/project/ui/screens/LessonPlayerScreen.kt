package org.example.project.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.project.core.audio.AudioPlayer
import org.example.project.domain.model.*
import org.example.project.presentation.viewmodel.LessonPlayerViewModel
import org.example.project.presentation.viewmodel.UserAnswer
import org.example.project.ui.theme.WordBridgeColors
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory image cache for faster loading
 */
object ImageCache {
    private val cache = ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
    private const val MAX_CACHE_SIZE = 50 // Maximum number of images to cache
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<androidx.compose.ui.graphics.ImageBitmap>>()
    private val inFlightMutex = Mutex()

    fun get(url: String): androidx.compose.ui.graphics.ImageBitmap? = cache[url]

    fun put(
        url: String,
        bitmap: androidx.compose.ui.graphics.ImageBitmap,
    ) {
        // Simple LRU-like behavior: remove oldest entries if cache is too large
        if (cache.size >= MAX_CACHE_SIZE) {
            val firstKey = cache.keys.firstOrNull()
            if (firstKey != null) {
                cache.remove(firstKey)
            }
        }
        cache[url] = bitmap
    }

    fun clear() {
        cache.clear()
    }

    /**
     * Get from cache or load once. Concurrent callers for the same URL will await the
     * same in-flight download to avoid duplicate network requests.
     */
    suspend fun getOrLoad(
        url: String,
        loader: suspend () -> androidx.compose.ui.graphics.ImageBitmap,
    ): androidx.compose.ui.graphics.ImageBitmap =
        coroutineScope {
            // Fast path: existing cache
            cache[url]?.let { return@coroutineScope it }

            val deferred =
                inFlightMutex.withLock {
                    // Re-check cache inside lock
                    cache[url]?.let { return@withLock null }

                    // Reuse an in-flight job if present, otherwise start one
                    inFlight[url] ?: async(start = CoroutineStart.LAZY) { loader() }
                        .also { inFlight[url] = it }
                }

            val bitmap =
                if (deferred != null) {
                    try {
                        deferred.await()
                    } finally {
                        inFlightMutex.withLock { inFlight.remove(url) }
                    }
                } else {
                    // Cache was populated while waiting for the lock
                    cache[url] ?: loader()
                }

            // Ensure cached
            put(url, bitmap)
            bitmap
        }
}

/**
 * Preload a list of image URLs using the shared cache/in-flight deduper.
 */
private suspend fun preloadImages(urls: List<String>) =
    withContext(Dispatchers.IO) {
        coroutineScope {
            urls.distinct().map { url ->
                async {
                    try {
                        ImageCache.getOrLoad(url) {
                            val connection = URL(url).openConnection()
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            connection.connect()
                            val inputStream = connection.getInputStream()
                            inputStream.use {
                                org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).asImageBitmap()
                            }
                        }
                    } catch (_: Exception) {
                        // Preload best-effort; failures handled during actual load
                    }
                }
            }.forEach { it.await() }
        }
    }

/**
 * Main lesson player screen with dynamic rendering based on question types and media.
 */
@Composable
fun LessonPlayerScreen(
    lessonId: String,
    userId: String,
    onBack: () -> Unit,
    onLessonCompleted: ((userId: String, lessonId: String) -> Unit)? = null,
    viewModel: LessonPlayerViewModel =
        viewModel {
            LessonPlayerViewModel(onLessonCompleted = onLessonCompleted)
        },
) {
    val currentLesson by viewModel.currentLesson
    val currentQuestion = viewModel.currentQuestion
    val currentQuestionIndex by viewModel.currentQuestionIndex
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val isLessonAlreadyCompleted by viewModel.isLessonAlreadyCompleted
    val isSubmitted by viewModel.isSubmitted
    val submissionResult by viewModel.submissionResult

    LaunchedEffect(lessonId) {
        println("[LessonPlayerScreen] Screen initialized with lessonId: $lessonId")
        viewModel.loadLesson(lessonId, userId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF15121F), // WordBridgeColors.Background
    ) {
        if (isLoading && currentLesson == null) {
            println("[LessonPlayerScreen] Showing loading screen...")
            LoadingScreen()
        } else if (isLessonAlreadyCompleted) {
            println("[LessonPlayerScreen] Showing completed lesson screen")
            CompletedLessonScreen(
                onRetake = {
                    println("[LessonPlayerScreen] Retake button clicked")
                    viewModel.retakeLesson(lessonId, userId)
                },
                onBack = onBack,
            )
        } else if (errorMessage != null) {
            println("[LessonPlayerScreen] Showing error screen: $errorMessage")
            ErrorScreen(
                message = errorMessage!!,
                onRetry = {
                    println("[LessonPlayerScreen] Retry button clicked - reloading lesson")
                    viewModel.loadLesson(lessonId, userId)
                },
                onBack = onBack,
            )
        } else if (isSubmitted && submissionResult != null) {
            println("[LessonPlayerScreen] Showing results screen: score=${submissionResult?.score}%")
            ResultsScreen(
                result = submissionResult!!,
                onRestart = { viewModel.resetLesson() },
                onBack = onBack,
            )
        } else if (currentLesson != null && currentQuestion != null) {
            LessonContent(
                lesson = currentLesson!!,
                question = currentQuestion!!,
                questionIndex = currentQuestionIndex,
                viewModel = viewModel,
                userId = userId,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun LessonContent(
    lesson: LessonContent,
    question: LessonQuestion,
    questionIndex: Int,
    viewModel: LessonPlayerViewModel,
    userId: String,
    onBack: () -> Unit,
) {
    val userAnswer = viewModel.getAnswer(question.id)
    val canGoNext = viewModel.canGoNext
    val isLastQuestion = viewModel.isLastQuestion

    // Preload images for all questions in the background
    LaunchedEffect(lesson.id) {
        println("[LessonPlayerScreen] Starting image preload for lesson: ${lesson.id}")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var totalImages = 0
            var loadedImages = 0
            var failedImages = 0

            lesson.questions.forEachIndexed { qIndex, q ->
                q.choices.forEachIndexed { cIndex, choice ->
                    choice.imageUrl?.let { url ->
                        totalImages++
                        if (ImageCache.get(url) == null) {
                            try {
                                println("[LessonPlayerScreen] Preloading image ${loadedImages + failedImages + 1}/$totalImages: $url")
                                ImageCache.getOrLoad(url) {
                                    val connection = URL(url).openConnection()
                                    connection.connectTimeout = 5000
                                    connection.readTimeout = 5000
                                    connection.connect()
                                    val inputStream = connection.getInputStream()
                                    inputStream.use {
                                        org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).asImageBitmap()
                                    }
                                }
                                loadedImages++
                                println("[LessonPlayerScreen] ✓ Preloaded image: $url")
                            } catch (e: Exception) {
                                failedImages++
                                println("[LessonPlayerScreen] ✗ Failed to preload image $url: ${e.javaClass.simpleName} - ${e.message}")
                            }
                        } else {
                            println("[LessonPlayerScreen] Image already cached: $url")
                        }
                    }
                }
            }
            println("[LessonPlayerScreen] Image preload complete: $loadedImages loaded, $failedImages failed, $totalImages total")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Header
        LessonHeader(
            lesson = lesson,
            progress = viewModel.progress,
            currentQuestion = questionIndex + 1,
            totalQuestions = lesson.questions.size,
            onBack = onBack,
        )

        // Question content
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Question type badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color =
                        when (question.questionType) {
                            QuestionType.MULTIPLE_CHOICE -> Color(0xFF8B5CF6).copy(alpha = 0.1f)
                            QuestionType.TEXT_ENTRY -> Color(0xFF10B981).copy(alpha = 0.1f)
                            QuestionType.MATCHING -> Color(0xFFF59E0B).copy(alpha = 0.1f)
                            QuestionType.PARAPHRASING -> Color(0xFF3B82F6).copy(alpha = 0.1f)
                            QuestionType.ERROR_CORRECTION -> Color(0xFFEF4444).copy(alpha = 0.1f)
                        },
                    border =
                        BorderStroke(
                            1.dp,
                            when (question.questionType) {
                                QuestionType.MULTIPLE_CHOICE -> Color(0xFF8B5CF6).copy(alpha = 0.3f)
                                QuestionType.TEXT_ENTRY -> Color(0xFF10B981).copy(alpha = 0.3f)
                                QuestionType.MATCHING -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                                QuestionType.PARAPHRASING -> Color(0xFF3B82F6).copy(alpha = 0.3f)
                                QuestionType.ERROR_CORRECTION -> Color(0xFFEF4444).copy(alpha = 0.3f)
                            },
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (question.questionType) {
                                QuestionType.MULTIPLE_CHOICE -> Icons.Default.CheckCircle
                                QuestionType.TEXT_ENTRY -> Icons.Default.Edit
                                QuestionType.MATCHING -> Icons.Default.SwapHoriz
                                QuestionType.PARAPHRASING -> Icons.Default.AutoAwesome
                                QuestionType.ERROR_CORRECTION -> Icons.Default.BugReport
                            },
                            contentDescription = null,
                            tint =
                                when (question.questionType) {
                                    QuestionType.MULTIPLE_CHOICE -> Color(0xFF8B5CF6)
                                    QuestionType.TEXT_ENTRY -> Color(0xFF10B981)
                                    QuestionType.MATCHING -> Color(0xFFF59E0B)
                                    QuestionType.PARAPHRASING -> Color(0xFF3B82F6)
                                    QuestionType.ERROR_CORRECTION -> Color(0xFFEF4444)
                                },
                            modifier = Modifier.size(16.dp),
                        )

                        Text(
                            question.questionType.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color =
                                when (question.questionType) {
                                    QuestionType.MULTIPLE_CHOICE -> Color(0xFF8B5CF6)
                                    QuestionType.TEXT_ENTRY -> Color(0xFF10B981)
                                    QuestionType.MATCHING -> Color(0xFFF59E0B)
                                    QuestionType.PARAPHRASING -> Color(0xFF3B82F6)
                                    QuestionType.ERROR_CORRECTION -> Color(0xFFEF4444)
                                },
                        )
                    }
                }

                // Question card with gradient background
                QuestionTextSection(
                    questionText = question.questionText,
                    questionNumber = questionIndex + 1,
                    audioUrl = question.questionAudioUrl,
                )

                // Dynamic question content based on type
                val showFeedback = viewModel.shouldShowFeedback(question.id)
                when (question.questionType) {
                    QuestionType.MULTIPLE_CHOICE -> {
                        MultipleChoiceQuestion(
                            question = question,
                            selectedAnswer = userAnswer as? UserAnswer.MultipleChoice,
                            showFeedback = showFeedback,
                            onAnswerSelected = { choiceId, isCorrect ->
                                viewModel.answerMultipleChoice(question.id, choiceId, isCorrect)
                            },
                        )
                    }
                    QuestionType.TEXT_ENTRY -> {
                        TextEntryQuestion(
                            question = question,
                            currentAnswer = (userAnswer as? UserAnswer.Identification)?.answer ?: "",
                            showFeedback = showFeedback,
                            isCorrect = (userAnswer as? UserAnswer.Identification)?.isCorrect,
                            onAnswerChanged = { answer ->
                                viewModel.answerIdentification(
                                    question.id,
                                    answer,
                                    question.answerText ?: "",
                                )
                            },
                        )
                    }
                    QuestionType.MATCHING -> {
                        val matchingAnswer = userAnswer as? UserAnswer.Matching
                        MatchingQuestion(
                            question = question,
                            selectedMatches = matchingAnswer?.matches ?: emptyMap(),
                            isCorrect = matchingAnswer?.isCorrect,
                            showFeedback = showFeedback,
                            onMatchSelected = { matches ->
                                viewModel.answerMatching(question.id, matches)
                            },
                        )
                    }
                    QuestionType.PARAPHRASING -> {
                        ParaphrasingQuestion(
                            question = question,
                            currentAnswer = (userAnswer as? UserAnswer.Identification)?.answer ?: "",
                            showFeedback = showFeedback,
                            isCorrect = (userAnswer as? UserAnswer.Identification)?.isCorrect,
                            onAnswerChanged = { answer ->
                                viewModel.answerParaphrasing(question.id, answer)
                            },
                        )
                    }
                    QuestionType.ERROR_CORRECTION -> {
                        ErrorCorrectionQuestion(
                            question = question,
                            currentAnswer = (userAnswer as? UserAnswer.Identification)?.answer ?: "",
                            showFeedback = showFeedback,
                            isCorrect = (userAnswer as? UserAnswer.Identification)?.isCorrect,
                            onAnswerChanged = { answer ->
                                viewModel.answerIdentification(
                                    question.id,
                                    answer,
                                    question.answerText ?: "",
                                )
                            },
                        )
                    }
                }

                // Check Answer button
                val canCheckAnswer = viewModel.canCheckAnswer
                if (canCheckAnswer) {
                    Button(
                        onClick = { viewModel.checkCurrentAnswer() },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6),
                            ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Check Answer",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Navigation footer
        NavigationFooter(
            canGoPrevious = questionIndex > 0,
            canGoNext = canGoNext,
            isLastQuestion = isLastQuestion,
            onPrevious = { viewModel.goToPreviousQuestion() },
            onNext = { viewModel.goToNextQuestion() },
            onSubmit = { viewModel.submitLesson(userId) },
        )
    }
}

@Composable
private fun LessonHeader(
    lesson: LessonContent,
    progress: Float,
    currentQuestion: Int,
    totalQuestions: Int,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF15121F),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top row with back button and question counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Text(
                    text = "Question $currentQuestion / $totalQuestions",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB4B4C4),
                )
            }

            // Progress bar with percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(6.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFF2D2A3E),
                ) {
                    Box {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress),
                            color = WordBridgeColors.PrimaryPurple,
                            shape = RoundedCornerShape(3.dp),
                        ) {}
                    }
                }

                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB4B4C4),
                )
            }
        }
    }
}

@Composable
private fun QuestionTextSection(
    questionText: String,
    questionNumber: Int,
    audioUrl: String?,
) {
    val audioPlayer = remember { AudioPlayer() }
    var isPlayingAudio by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Cleanup when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF2D2A3E).copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF3A3147).copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Question number badge
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = WordBridgeColors.PrimaryPurple,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$questionNumber",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            // Question text
            Text(
                questionText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )

            // Audio narration button
            if (audioUrl != null) {
                IconButton(
                    onClick = {
                        if (isPlayingAudio) {
                            audioPlayer.stop()
                            isPlayingAudio = false
                        } else {
                            coroutineScope.launch {
                                isPlayingAudio = true
                                audioPlayer.setPlaybackFinishedCallback {
                                    isPlayingAudio = false
                                }
                                audioPlayer.playAudioFromUrl(audioUrl)
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color =
                            if (isPlayingAudio) {
                                WordBridgeColors.PrimaryPurple.copy(alpha = 0.8f)
                            } else {
                                Color(0xFF3A3147).copy(alpha = 0.5f)
                            },
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isPlayingAudio) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = if (isPlayingAudio) "Stop audio" else "Play question audio",
                                tint = if (isPlayingAudio) Color.White else WordBridgeColors.PrimaryPurple,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultipleChoiceQuestion(
    question: LessonQuestion,
    selectedAnswer: UserAnswer.MultipleChoice?,
    showFeedback: Boolean,
    onAnswerSelected: (String, Boolean) -> Unit,
) {
    // Debug logging when feedback is shown
    LaunchedEffect(showFeedback, selectedAnswer) {
        if (showFeedback && selectedAnswer != null) {
            println("[LessonPlayerUI] ========== RENDERING FEEDBACK ==========")
            println("[LessonPlayerUI] Question ID: ${question.id}")
            println("[LessonPlayerUI] Question Type: MULTIPLE_CHOICE")
            println("[LessonPlayerUI] isCorrect: ${selectedAnswer.isCorrect}")
            println(
                "[LessonPlayerUI] explanation: ${if (question.explanation.isNullOrBlank()) "NOT SET" else "\"${question.explanation}\""}",
            )
            println("[LessonPlayerUI] =========================================")
        }
    }

    // Memoize the selected choice ID to avoid recomposition
    val selectedChoiceId = remember(selectedAnswer) { selectedAnswer?.choiceId }
    val isCorrect = selectedAnswer?.isCorrect == true

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Select your answer:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB4B4C4),
        )

        // Show feedback message only after checking answer
        if (showFeedback && selectedAnswer != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Feedback message (correct/incorrect)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCorrect) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            if (isCorrect) {
                                "Correct! Great job!"
                            } else {
                                question.wrongAnswerFeedback?.takeIf { it.isNotBlank() }
                                    ?: "Incorrect. Please try again!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                        )
                    }
                }

                // Show explanation if available
                if (!question.explanation.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        "Explanation",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8B5CF6),
                                    )
                                }

                                // Explanation audio button
                                if (question.explanationAudioUrl != null) {
                                    val audioPlayer = remember { AudioPlayer() }
                                    var isPlayingExplanation by remember { mutableStateOf(false) }
                                    val coroutineScope = rememberCoroutineScope()

                                    DisposableEffect(Unit) {
                                        onDispose {
                                            audioPlayer.stop()
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isPlayingExplanation) {
                                                audioPlayer.stop()
                                                isPlayingExplanation = false
                                            } else {
                                                coroutineScope.launch {
                                                    isPlayingExplanation = true
                                                    audioPlayer.setPlaybackFinishedCallback {
                                                        isPlayingExplanation = false
                                                    }
                                                    audioPlayer.playAudioFromUrl(question.explanationAudioUrl!!)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color =
                                                if (isPlayingExplanation) {
                                                    Color(0xFF8B5CF6).copy(alpha = 0.8f)
                                                } else {
                                                    Color(0xFF3A3147).copy(alpha = 0.5f)
                                                },
                                        ) {
                                            Box(
                                                modifier = Modifier.size(32.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    if (isPlayingExplanation) Icons.Default.Stop else Icons.Default.VolumeUp,
                                                    contentDescription = if (isPlayingExplanation) "Stop explanation audio" else "Play explanation audio",
                                                    tint = if (isPlayingExplanation) Color.White else Color(0xFF8B5CF6),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                question.explanation!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE0E0E8),
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            }
        }

        // 2x2 Grid layout - memoize chunked list
        val choiceRows =
            remember(question.choices) {
                question.choices.chunked(2)
            }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choiceRows.forEachIndexed { rowIndex, rowChoices ->
                key(rowIndex) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowChoices.forEach { choice ->
                            key(choice.id) {
                                ChoiceCard(
                                    choice = choice,
                                    isSelected = selectedChoiceId == choice.id,
                                    showFeedback = showFeedback,
                                    isCorrectAnswer = choice.isCorrect,
                                    onClick = {
                                        onAnswerSelected(choice.id, choice.isCorrect)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        // Fill empty space if odd number of choices
                        if (rowChoices.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    choice: QuestionChoice,
    isSelected: Boolean,
    showFeedback: Boolean = false,
    isCorrectAnswer: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Determine colors based on feedback
    val containerColor =
        remember(isSelected, showFeedback, isCorrectAnswer) {
            when {
                showFeedback && isSelected && isCorrectAnswer -> Color(0xFF10B981).copy(alpha = 0.2f) // Green for correct
                showFeedback && isSelected && !isCorrectAnswer -> Color(0xFFEF4444).copy(alpha = 0.2f) // Red for incorrect
                isSelected -> Color(0xFF2D2A3E).copy(alpha = 0.5f)
                else -> Color(0xFF2D2A3E).copy(alpha = 0.3f)
            }
        }

    val border =
        remember(isSelected, showFeedback, isCorrectAnswer) {
            when {
                showFeedback && isSelected && isCorrectAnswer -> BorderStroke(2.dp, Color(0xFF10B981)) // Green border
                showFeedback && isSelected && !isCorrectAnswer -> BorderStroke(2.dp, Color(0xFFEF4444)) // Red border
                isSelected -> BorderStroke(2.dp, WordBridgeColors.PrimaryPurple)
                else -> BorderStroke(2.dp, Color(0xFF3A3147).copy(alpha = 0.5f))
            }
        }

    Card(
        modifier =
            modifier
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
            ),
        border = border,
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Image if available - Box matches actual image size/shape
                if (choice.imageUrl != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NetworkImage(
                            url = choice.imageUrl,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .widthIn(max = 160.dp)
                                    .heightIn(max = 160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF3A3147).copy(alpha = 0.3f))
                                    .padding(2.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                // Choice text (centered if no image, or below image)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        choice.choiceText,
                        style =
                            if (choice.imageUrl != null) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                        fontWeight = if (choice.imageUrl != null) FontWeight.Medium else FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier =
                            if (choice.imageUrl == null) {
                                Modifier.padding(vertical = 40.dp)
                            } else {
                                Modifier
                            },
                    )

                    // Audio if available
                    if (choice.audioUrl != null) {
                        ChoiceAudioButton(audioUrl = choice.audioUrl)
                    }
                }
            }

            // Selection indicator at top-right
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp),
                shape = CircleShape,
                color =
                    when {
                        showFeedback && isSelected && isCorrectAnswer -> Color(0xFF10B981)
                        showFeedback && isSelected && !isCorrectAnswer -> Color(0xFFEF4444)
                        isSelected -> WordBridgeColors.PrimaryPurple
                        else -> Color.Transparent
                    },
                border = if (!isSelected) BorderStroke(2.dp, Color(0xFF4A4658)) else null,
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (showFeedback && !isCorrectAnswer) Icons.Default.Close else Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceAudioButton(audioUrl: String) {
    val audioPlayer = remember { AudioPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    IconButton(
        onClick = {
            if (isPlaying) {
                audioPlayer.stop()
                isPlaying = false
            } else {
                coroutineScope.launch {
                    isPlaying = true
                    audioPlayer.setPlaybackFinishedCallback {
                        isPlaying = false
                    }
                    audioPlayer.playAudioFromUrl(audioUrl)
                }
            }
        },
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
            contentDescription = if (isPlaying) "Stop audio" else "Play audio",
            tint = if (isPlaying) Color(0xFFEF4444) else WordBridgeColors.PrimaryPurple,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun IdentificationQuestion(
    question: LessonQuestion,
    currentAnswer: String,
    onAnswerChanged: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Type your answer:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = WordBridgeColors.TextSecondary,
        )

        OutlinedTextField(
            value = currentAnswer,
            onValueChange = onAnswerChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your answer here...") },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WordBridgeColors.PrimaryPurple,
                    unfocusedBorderColor = Color(0xFF3A3147),
                    focusedContainerColor = WordBridgeColors.CardBackground,
                    unfocusedContainerColor = WordBridgeColors.CardBackground,
                    focusedTextColor = WordBridgeColors.TextPrimary,
                    unfocusedTextColor = WordBridgeColors.TextPrimary,
                    cursorColor = WordBridgeColors.PrimaryPurple,
                ),
            shape = RoundedCornerShape(12.dp),
        )

        if (question.answerAudioUrl != null) {
            AudioPlayer(audioUrl = question.answerAudioUrl, label = "Listen to hint")
        }
    }
}

@Composable
private fun TextEntryQuestion(
    question: LessonQuestion,
    currentAnswer: String,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    onAnswerChanged: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Type your answer:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB4B4C4),
        )

        OutlinedTextField(
            value = currentAnswer,
            onValueChange = onAnswerChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your answer here...") },
            minLines = 3,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0xFF3A3147),
                    focusedContainerColor = Color(0xFF2D2A3E),
                    unfocusedContainerColor = Color(0xFF2D2A3E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF8B5CF6),
                ),
            shape = RoundedCornerShape(12.dp),
        )

        // Answer audio hint if available
        if (question.answerAudioUrl != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val audioPlayer = remember { AudioPlayer() }
                var isPlaying by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                DisposableEffect(Unit) {
                    onDispose {
                        audioPlayer.stop()
                    }
                }

                IconButton(
                    onClick = {
                        if (isPlaying) {
                            audioPlayer.stop()
                            isPlaying = false
                        } else {
                            coroutineScope.launch {
                                isPlaying = true
                                audioPlayer.setPlaybackFinishedCallback {
                                    isPlaying = false
                                }
                                audioPlayer.playAudioFromUrl(question.answerAudioUrl!!)
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isPlaying) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF8B5CF6).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (isPlaying) Color(0xFFEF4444) else Color(0xFF8B5CF6)),
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = if (isPlaying) "Stop hint audio" else "Play hint audio",
                                tint = if (isPlaying) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Text(
                    "Listen to pronunciation hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B5CF6),
                )
            }
        }

        // Show feedback after checking
        if (showFeedback && isCorrect != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Feedback message (correct/incorrect)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCorrect) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            if (isCorrect) {
                                "Correct! Great job!"
                            } else {
                                question.wrongAnswerFeedback?.takeIf { it.isNotBlank() }
                                    ?: "Incorrect. Please try again!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                        )
                    }
                }

                // Show explanation if available
                if (!question.explanation.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        "Explanation",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8B5CF6),
                                    )
                                }

                                // Explanation audio button
                                if (question.explanationAudioUrl != null) {
                                    val audioPlayer = remember { AudioPlayer() }
                                    var isPlayingExplanation by remember { mutableStateOf(false) }
                                    val coroutineScope = rememberCoroutineScope()

                                    DisposableEffect(Unit) {
                                        onDispose {
                                            audioPlayer.stop()
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isPlayingExplanation) {
                                                audioPlayer.stop()
                                                isPlayingExplanation = false
                                            } else {
                                                coroutineScope.launch {
                                                    isPlayingExplanation = true
                                                    audioPlayer.setPlaybackFinishedCallback {
                                                        isPlayingExplanation = false
                                                    }
                                                    audioPlayer.playAudioFromUrl(question.explanationAudioUrl!!)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color =
                                                if (isPlayingExplanation) {
                                                    Color(0xFF8B5CF6).copy(alpha = 0.8f)
                                                } else {
                                                    Color(0xFF3A3147).copy(alpha = 0.5f)
                                                },
                                        ) {
                                            Box(
                                                modifier = Modifier.size(32.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    if (isPlayingExplanation) Icons.Default.Stop else Icons.Default.VolumeUp,
                                                    contentDescription = if (isPlayingExplanation) "Stop explanation audio" else "Play explanation audio",
                                                    tint = if (isPlayingExplanation) Color.White else Color(0xFF8B5CF6),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                question.explanation!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE0E0E8),
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchingQuestion(
    question: LessonQuestion,
    selectedMatches: Map<String, String>,
    isCorrect: Boolean? = null,
    showFeedback: Boolean = false,
    onMatchSelected: (Map<String, String>) -> Unit,
) {
    println("[LessonPlayerScreen] Rendering MatchingQuestion component")

    // Preload all images used in this matching question (left + right)
    val matchingImageUrls =
        remember(question.id) {
            question.choices.mapNotNull { it.imageUrl }
        }
    var imagesReady by remember(question.id) { mutableStateOf(false) }
    LaunchedEffect(matchingImageUrls) {
        preloadImages(matchingImageUrls)
        imagesReady = true
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!imagesReady && matchingImageUrls.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = WordBridgeColors.PrimaryPurple,
                )
                Text(
                    text = "Loading images...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB4B4C4),
                )
            }
        }

        Text(
            "Click items on the left, then click the matching answer on the right to connect them:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB4B4C4),
        )

        // Group choices by pair ID, filtering out null/empty pair IDs
        val pairs =
            question.choices
                .filter { !it.matchPairId.isNullOrEmpty() }
                .groupBy { it.matchPairId }

        val leftItems = mutableListOf<QuestionChoice>()
        val rightItems = mutableListOf<QuestionChoice>()

        pairs.forEach { (pairId, items) ->
            if (items.size >= 2) {
                leftItems.add(items[0])
                rightItems.add(items[1])
            }
        }

        // Show message if no valid pairs
        if (leftItems.isEmpty()) {
            println("[LessonPlayerScreen] ⚠ No valid matching pairs found")
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                color = Color(0xFF3A3147),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        "No matching pairs available",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        "Please contact the instructor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB4B4C4),
                    )
                }
            }
            return
        }

        // Shuffle right items to make it challenging
        val shuffledRight = remember(question.id) { rightItems.shuffled() }

        // State for tracking which item is selected to connect
        var selectedLeftItem by remember { mutableStateOf<String?>(null) }

        // Show feedback message if answer is submitted
        if (showFeedback && isCorrect != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Feedback message (correct/incorrect)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCorrect == true) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, if (isCorrect == true) Color(0xFF10B981) else Color(0xFFEF4444)),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isCorrect == true) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isCorrect == true) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            if (isCorrect == true) {
                                "All matches are correct! ✓"
                            } else {
                                // Use explanation as the error message when answer is wrong
                                question.explanation?.takeIf { it.isNotBlank() }
                                    ?: "Some matches are incorrect. Please review your answers."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCorrect == true) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Show explanation if available
                if (!question.explanation.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Explanation",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8B5CF6),
                                    )
                                }

                                // Explanation audio button
                                if (question.explanationAudioUrl != null) {
                                    val audioPlayer = remember { AudioPlayer() }
                                    var isPlayingExplanation by remember { mutableStateOf(false) }
                                    val coroutineScope = rememberCoroutineScope()

                                    DisposableEffect(Unit) {
                                        onDispose {
                                            audioPlayer.stop()
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isPlayingExplanation) {
                                                audioPlayer.stop()
                                                isPlayingExplanation = false
                                            } else {
                                                coroutineScope.launch {
                                                    isPlayingExplanation = true
                                                    audioPlayer.setPlaybackFinishedCallback {
                                                        isPlayingExplanation = false
                                                    }
                                                    audioPlayer.playAudioFromUrl(question.explanationAudioUrl!!)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color =
                                                if (isPlayingExplanation) {
                                                    Color(0xFF8B5CF6).copy(alpha = 0.8f)
                                                } else {
                                                    Color(0xFF3A3147).copy(alpha = 0.5f)
                                                },
                                        ) {
                                            Box(
                                                modifier = Modifier.size(28.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    if (isPlayingExplanation) Icons.Default.Stop else Icons.Default.VolumeUp,
                                                    contentDescription = if (isPlayingExplanation) "Stop explanation audio" else "Play explanation audio",
                                                    tint = if (isPlayingExplanation) Color.White else Color(0xFF8B5CF6),
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                question.explanation!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE0E0E8),
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }

        // Interactive matching interface with line connections
        InteractiveMatchingInterface(
            leftItems = leftItems,
            rightItems = shuffledRight,
            selectedMatches = selectedMatches,
            selectedLeftItem = selectedLeftItem,
            question = question,
            showFeedback = showFeedback && isCorrect != null,
            isOverallCorrect = isCorrect ?: false,
            onLeftItemClick = { itemId ->
                println("[LessonPlayerScreen] Left item clicked: $itemId")
                selectedLeftItem = if (selectedLeftItem == itemId) null else itemId
            },
            onRightItemClick = { rightItemId ->
                println("[LessonPlayerScreen] Right item clicked: $rightItemId")
                if (selectedLeftItem != null) {
                    val newMatches = selectedMatches.toMutableMap()
                    newMatches[selectedLeftItem!!] = rightItemId
                    onMatchSelected(newMatches)
                    selectedLeftItem = null
                    println("[LessonPlayerScreen] Match created: $selectedLeftItem -> $rightItemId")
                }
            },
            onClearMatch = { leftItemId ->
                println("[LessonPlayerScreen] Clearing match for: $leftItemId")
                val newMatches = selectedMatches.toMutableMap()
                newMatches.remove(leftItemId)
                onMatchSelected(newMatches)
            },
        )
    }
}

@Composable
private fun InteractiveMatchingInterface(
    leftItems: List<QuestionChoice>,
    rightItems: List<QuestionChoice>,
    selectedMatches: Map<String, String>,
    selectedLeftItem: String?,
    question: LessonQuestion,
    showFeedback: Boolean = false,
    isOverallCorrect: Boolean = false,
    onLeftItemClick: (String) -> Unit,
    onRightItemClick: (String) -> Unit,
    onClearMatch: (String) -> Unit,
) {
    // Track item centers to draw straight lines with connection dots
    val leftCenters = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }
    val rightCenters = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }
    var hoveredConnection by remember { mutableStateOf<Pair<String, String>?>(null) }
    var boxOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B2E).copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF3A3147).copy(alpha = 0.5f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        boxOffset = androidx.compose.ui.geometry.Offset(pos.x, pos.y)
                    },
        ) {
            // Main content (cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(80.dp),
            ) {
                // Left column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Questions",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    leftItems.forEachIndexed { index, item ->
                        val matchedRightId = selectedMatches[item.id]
                        val isMatchCorrect =
                            if (showFeedback && matchedRightId != null) {
                                val rightChoice = question.choices.find { it.id == matchedRightId }
                                val leftPairId = item.matchPairId
                                val rightPairId = rightChoice?.matchPairId
                                leftPairId != null && rightPairId != null && leftPairId == rightPairId && leftPairId.isNotEmpty()
                            } else {
                                null
                            }

                        MatchingItemCard(
                            item = item,
                            index = index + 1,
                            isSelected = selectedLeftItem == item.id,
                            isMatched = selectedMatches.containsKey(item.id),
                            isMatchCorrect = isMatchCorrect,
                            showFeedback = showFeedback,
                            onClick = { onLeftItemClick(item.id) },
                            onClear = { onClearMatch(item.id) },
                            side = MatchingSide.LEFT,
                            onCenterMeasured = { center -> leftCenters[item.id] = center },
                            onHoverChanged = { hovered ->
                                if (hovered) {
                                    val rightId = selectedMatches[item.id]
                                    if (rightId != null) {
                                        hoveredConnection = item.id to rightId
                                    }
                                } else {
                                    hoveredConnection = null
                                }
                            },
                        )
                    }
                }

                // Right column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Answers",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    rightItems.forEachIndexed { index, item ->
                        val isMatched = selectedMatches.values.contains(item.id)
                        val matchedLeftId = selectedMatches.entries.find { it.value == item.id }?.key
                        val isMatchCorrect =
                            if (showFeedback && matchedLeftId != null) {
                                val leftChoice = question.choices.find { it.id == matchedLeftId }
                                val leftPairId = leftChoice?.matchPairId
                                val rightPairId = item.matchPairId
                                leftPairId != null && rightPairId != null && leftPairId == rightPairId && leftPairId.isNotEmpty()
                            } else {
                                null
                            }

                        MatchingItemCard(
                            item = item,
                            index = null, // No numbers on right side
                            isSelected = false,
                            isMatched = isMatched,
                            isMatchCorrect = isMatchCorrect,
                            showFeedback = showFeedback,
                            onClick = { onRightItemClick(item.id) },
                            onClear = null,
                            side = MatchingSide.RIGHT,
                            isClickable = selectedLeftItem != null,
                            onCenterMeasured = { center -> rightCenters[item.id] = center },
                            onHoverChanged = { hovered ->
                                if (hovered && matchedLeftId != null) {
                                    hoveredConnection = matchedLeftId to item.id
                                } else {
                                    hoveredConnection = null
                                }
                            },
                        )
                    }
                }
            }

            // Connection layer draws straight lines with gradient and glow effect (drawn on top)
            Canvas(modifier = Modifier.matchParentSize()) {
                selectedMatches.forEach { (leftId, rightId) ->
                    val startAbsolute = leftCenters[leftId]
                    val endAbsolute = rightCenters[rightId]
                    if (startAbsolute != null && endAbsolute != null) {
                        // Convert absolute positions to Box-relative coordinates
                        val start =
                            androidx.compose.ui.geometry.Offset(
                                x = startAbsolute.x - boxOffset.x,
                                y = startAbsolute.y - boxOffset.y,
                            )
                        val end =
                            androidx.compose.ui.geometry.Offset(
                                x = endAbsolute.x - boxOffset.x,
                                y = endAbsolute.y - boxOffset.y,
                            )
                        val isHovered = hoveredConnection == (leftId to rightId)

                        // Determine line color based on correctness
                        val (startColor, endColor) =
                            if (showFeedback) {
                                val leftChoice = question.choices.find { it.id == leftId }
                                val rightChoice = question.choices.find { it.id == rightId }
                                val leftPairId = leftChoice?.matchPairId
                                val rightPairId = rightChoice?.matchPairId
                                val isCorrect =
                                    leftPairId != null && rightPairId != null &&
                                        leftPairId == rightPairId && leftPairId.isNotEmpty()
                                if (isCorrect) {
                                    Color(0xFF10B981) to Color(0xFF10B981)
                                } else {
                                    Color(0xFFEF4444) to Color(0xFFEF4444)
                                }
                            } else {
                                Color(0xFFA855F7) to Color(0xFF10B981) // Purple to Green gradient
                            }

                        // Draw glow layer (thicker, semi-transparent)
                        drawLine(
                            brush =
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors =
                                        listOf(
                                            startColor.copy(alpha = if (isHovered) 0.6f else 0.4f),
                                            endColor.copy(alpha = if (isHovered) 0.6f else 0.4f),
                                        ),
                                    start = start,
                                    end = end,
                                ),
                            start = start,
                            end = end,
                            strokeWidth = if (isHovered) 8f else 6f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )

                        // Draw main line with gradient
                        drawLine(
                            brush =
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(startColor, endColor),
                                    start = start,
                                    end = end,
                                ),
                            start = start,
                            end = end,
                            strokeWidth = if (isHovered) 4f else 3f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )

                        // Draw connection dots at both ends
                        val dotRadius = if (isHovered) 6.dp.toPx() else 5.dp.toPx()

                        // Left dot (purple)
                        drawCircle(
                            color = startColor,
                            radius = dotRadius,
                            center = start,
                            style = androidx.compose.ui.graphics.drawscope.Fill,
                        )

                        // Right dot (green or color based on feedback)
                        drawCircle(
                            color = endColor,
                            radius = dotRadius,
                            center = end,
                            style = androidx.compose.ui.graphics.drawscope.Fill,
                        )
                    }
                }
            }
        }
    }

    // Help text
    if (selectedLeftItem != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Now click an answer on the right to create a match",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

enum class MatchingSide {
    LEFT,
    RIGHT,
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MatchingItemCard(
    item: QuestionChoice,
    index: Int?,
    isSelected: Boolean,
    isMatched: Boolean,
    isMatchCorrect: Boolean?,
    showFeedback: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    side: MatchingSide,
    isClickable: Boolean = true,
    onCenterMeasured: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onHoverChanged: ((Boolean) -> Unit)? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val containerColor =
        when {
            showFeedback && isMatchCorrect == false -> Color(0xFFEF4444).copy(alpha = 0.2f)
            showFeedback && isMatchCorrect == true -> Color(0xFF10B981).copy(alpha = 0.2f)
            isMatched && side == MatchingSide.RIGHT -> Color(0xFF10B981).copy(alpha = 0.2f) // Green when matched (right side)
            isMatched -> Color(0xFF8B5CF6).copy(alpha = 0.2f) // Purple when matched (left side)
            isSelected -> Color(0xFFF59E0B).copy(alpha = 0.2f)
            else -> Color(0xFF2D2A3E).copy(alpha = 0.5f)
        }

    val borderColor =
        when {
            showFeedback && isMatchCorrect == false -> Color(0xFFEF4444)
            showFeedback && isMatchCorrect == true -> Color(0xFF10B981)
            isMatched && side == MatchingSide.RIGHT -> Color(0xFF10B981) // Green when matched (right side)
            isMatched -> Color(0xFF8B5CF6) // Purple when matched (left side)
            isSelected -> Color(0xFFF59E0B)
            else -> Color(0xFF3A3147).copy(alpha = 0.5f)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(enabled = isClickable) { onClick() }
                .hoverable(
                    interactionSource = remember { MutableInteractionSource() },
                )
                .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                    isHovered = true
                    onHoverChanged?.invoke(true)
                }
                .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                    isHovered = false
                    onHoverChanged?.invoke(false)
                },
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
            ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, borderColor),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (isSelected || isMatched) 2.dp else 0.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .onGloballyPositioned { coords ->
                        onCenterMeasured?.let {
                            val pos = coords.positionInRoot()
                            val size = coords.size
                            val x = if (side == MatchingSide.LEFT) pos.x + size.width.toFloat() else pos.x
                            val center =
                                androidx.compose.ui.geometry.Offset(
                                    x = x,
                                    y = pos.y + size.height.toFloat() / 2f,
                                )
                            it(center)
                        }
                    },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Number badge (only for left side)
                if (index != null) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color =
                            when {
                                showFeedback && isMatchCorrect == false -> Color(0xFFEF4444)
                                showFeedback && isMatchCorrect == true -> Color(0xFF10B981)
                                isMatched -> Color(0xFF8B5CF6)
                                else -> Color(0xFF3A3147)
                            },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$index",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Item text
                Text(
                    item.choiceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMatched || isSelected) Color.White else Color(0xFFB4B4C4),
                    fontWeight = if (isMatched || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                // Audio button for item
                if (item.audioUrl != null) {
                    val audioPlayer = remember { AudioPlayer() }
                    var isPlaying by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()

                    DisposableEffect(Unit) {
                        onDispose {
                            audioPlayer.stop()
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                audioPlayer.stop()
                                isPlaying = false
                            } else {
                                coroutineScope.launch {
                                    isPlaying = true
                                    audioPlayer.setPlaybackFinishedCallback {
                                        isPlaying = false
                                    }
                                    audioPlayer.playAudioFromUrl(item.audioUrl!!)
                                }
                            }
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = if (isPlaying) "Stop audio" else "Play audio",
                            tint = if (isPlaying) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Status icon
            if (isMatched && side == MatchingSide.LEFT && onClear != null && !showFeedback) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear match",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else if (showFeedback && isMatchCorrect != null) {
                Icon(
                    if (isMatchCorrect == true) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = if (isMatchCorrect == true) "Correct" else "Incorrect",
                    tint = if (isMatchCorrect == true) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp),
                )
            } else if (isMatched) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Matched",
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(18.dp),
                )
            } else if (isSelected) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = "Selected",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ParaphrasingQuestion(
    question: LessonQuestion,
    currentAnswer: String,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    onAnswerChanged: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Rewrite the sentence in your own words:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB4B4C4),
        )

        OutlinedTextField(
            value = currentAnswer,
            onValueChange = onAnswerChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Write your paraphrase here...") },
            minLines = 4,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0xFF3A3147),
                    focusedContainerColor = Color(0xFF2D2A3E),
                    unfocusedContainerColor = Color(0xFF2D2A3E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF8B5CF6),
                ),
            shape = RoundedCornerShape(12.dp),
        )

        // Show feedback after checking
        if (showFeedback && isCorrect != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isCorrect) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (isCorrect) {
                            "Correct! Great job!"
                        } else {
                            question.wrongAnswerFeedback?.takeIf { it.isNotBlank() }
                                ?: "Incorrect. Please try again!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                    )
                }
            }
        }

        if (question.answerText != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color(0xFF2D2A3E),
                    ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Sample Answer:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB4B4C4),
                    )
                    Text(
                        question.answerText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB4B4C4),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
            }
        }

        // Show explanation if available (for paraphrasing, show after submission)
        if (!question.explanation.isNullOrBlank() && currentAnswer.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "Explanation",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5CF6),
                            )
                        }

                        // Explanation audio button
                        if (question.explanationAudioUrl != null) {
                            val audioPlayer = remember { AudioPlayer() }
                            var isPlayingExplanation by remember { mutableStateOf(false) }
                            val coroutineScope = rememberCoroutineScope()

                            DisposableEffect(Unit) {
                                onDispose {
                                    audioPlayer.stop()
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isPlayingExplanation) {
                                        audioPlayer.stop()
                                        isPlayingExplanation = false
                                    } else {
                                        coroutineScope.launch {
                                            isPlayingExplanation = true
                                            audioPlayer.setPlaybackFinishedCallback {
                                                isPlayingExplanation = false
                                            }
                                            audioPlayer.playAudioFromUrl(question.explanationAudioUrl!!)
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (isPlayingExplanation) {
                                            Color(0xFF8B5CF6).copy(alpha = 0.8f)
                                        } else {
                                            Color(0xFF3A3147).copy(alpha = 0.5f)
                                        },
                                ) {
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            if (isPlayingExplanation) Icons.Default.Stop else Icons.Default.VolumeUp,
                                            contentDescription = if (isPlayingExplanation) "Stop explanation audio" else "Play explanation audio",
                                            tint = if (isPlayingExplanation) Color.White else Color(0xFF8B5CF6),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        question.explanation!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE0E0E8),
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCorrectionQuestion(
    question: LessonQuestion,
    currentAnswer: String,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    onAnswerChanged: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Find and correct the errors in the text below:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB4B4C4),
        )

        // Display text with errors
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.1f),
                ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Text with errors:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    question.errorText ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
            }
        }

        Text(
            "Write the corrected text:",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFB4B4C4),
        )

        OutlinedTextField(
            value = currentAnswer,
            onValueChange = onAnswerChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Write the corrected text here...") },
            minLines = 4,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0xFF3A3147),
                    focusedContainerColor = Color(0xFF2D2A3E),
                    unfocusedContainerColor = Color(0xFF2D2A3E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF8B5CF6),
                ),
            shape = RoundedCornerShape(12.dp),
        )

        // Show feedback after checking
        if (showFeedback && isCorrect != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Feedback message (correct/incorrect)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCorrect) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            if (isCorrect) {
                                "Correct! Great job!"
                            } else {
                                question.wrongAnswerFeedback?.takeIf { it.isNotBlank() }
                                    ?: "Incorrect. Please try again!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                        )
                    }
                }

                // Show explanation if available
                if (!question.explanation.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        "Explanation",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8B5CF6),
                                    )
                                }

                                // Explanation audio button
                                if (question.explanationAudioUrl != null) {
                                    val audioPlayer = remember { AudioPlayer() }
                                    var isPlayingExplanation by remember { mutableStateOf(false) }
                                    val coroutineScope = rememberCoroutineScope()

                                    DisposableEffect(Unit) {
                                        onDispose {
                                            audioPlayer.stop()
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isPlayingExplanation) {
                                                audioPlayer.stop()
                                                isPlayingExplanation = false
                                            } else {
                                                coroutineScope.launch {
                                                    isPlayingExplanation = true
                                                    audioPlayer.setPlaybackFinishedCallback {
                                                        isPlayingExplanation = false
                                                    }
                                                    audioPlayer.playAudioFromUrl(question.explanationAudioUrl!!)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color =
                                                if (isPlayingExplanation) {
                                                    Color(0xFF8B5CF6).copy(alpha = 0.8f)
                                                } else {
                                                    Color(0xFF3A3147).copy(alpha = 0.5f)
                                                },
                                        ) {
                                            Box(
                                                modifier = Modifier.size(32.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    if (isPlayingExplanation) Icons.Default.Stop else Icons.Default.VolumeUp,
                                                    contentDescription = if (isPlayingExplanation) "Stop explanation audio" else "Play explanation audio",
                                                    tint = if (isPlayingExplanation) Color.White else Color(0xFF8B5CF6),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                question.explanation!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE0E0E8),
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordQuestion(
    question: LessonQuestion,
    recordingUrl: String?,
    onRecordingComplete: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Record your answer:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = WordBridgeColors.TextSecondary,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = WordBridgeColors.CardBackground,
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = WordBridgeColors.PrimaryPurple,
                )

                if (recordingUrl == null) {
                    Button(
                        onClick = {
                            // TODO: Implement voice recording
                            // For now, simulate with a placeholder
                            onRecordingComplete("placeholder-recording-url")
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = WordBridgeColors.PrimaryPurple,
                            ),
                    ) {
                        Icon(Icons.Default.FiberManualRecord, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Recording")
                    }

                    Text(
                        "Tap to record your voice answer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(48.dp),
                    )

                    Text(
                        "Recording complete!",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                    )

                    TextButton(
                        onClick = { onRecordingComplete("") },
                    ) {
                        Text("Re-record", color = WordBridgeColors.PrimaryPurple)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioPlayer(
    audioUrl: String,
    label: String,
) {
    var isPlaying by remember { mutableStateOf(false) }

    Button(
        onClick = {
            // TODO: Implement actual audio playback
            isPlaying = !isPlaying
        },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2D2A3E),
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun AudioPlayerButton(audioUrl: String) {
    var isPlaying by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF3A3147).copy(alpha = 0.5f),
        modifier =
            Modifier
                .clickable {
                    // TODO: Implement actual audio playback
                    isPlaying = !isPlaying
                },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = WordBridgeColors.PrimaryPurple,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationFooter(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    isLastQuestion: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF15121F),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = onPrevious,
                enabled = canGoPrevious,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFB4B4C4),
                        disabledContentColor = Color(0xFF4A4658),
                    ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Previous",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (isLastQuestion) {
                Button(
                    onClick = onSubmit,
                    enabled = canGoNext,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple,
                            disabledContainerColor = Color(0xFF2D2A3E),
                            disabledContentColor = Color(0xFF4A4658),
                        ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = if (canGoNext) 6.dp else 0.dp,
                        ),
                ) {
                    Text(
                        "Submit Lesson",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Button(
                    onClick = onNext,
                    enabled = canGoNext,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple,
                            disabledContainerColor = Color(0xFF2D2A3E),
                            disabledContentColor = Color(0xFF4A4658),
                        ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = if (canGoNext) 6.dp else 0.dp,
                        ),
                ) {
                    Text(
                        "Next",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = WordBridgeColors.PrimaryPurple,
        )
    }
}

@Composable
private fun CompletedLessonScreen(
    onRetake: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0xFF2D1B69).copy(alpha = 0.2f),
                                    Color(0xFF1A0E2E).copy(alpha = 0.4f),
                                ),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(100.dp)
                        .background(
                            Color(0xFF10B981).copy(alpha = 0.15f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color(0xFF10B981),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Lesson Already Completed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Great job! You've already finished this lesson.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Would you like to retake it to practice again?",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = WordBridgeColors.TextPrimaryDark,
                        ),
                    border = BorderStroke(1.dp, WordBridgeColors.TextSecondary.copy(alpha = 0.3f)),
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go Back")
                }

                Button(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple,
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retake Lesson")
                }
            }
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFFEF4444),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Error",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = WordBridgeColors.TextPrimaryDark,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = WordBridgeColors.TextSecondaryDark,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) {
                Text("Go Back")
            }
            Button(
                onClick = onRetry,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = WordBridgeColors.PrimaryPurple,
                    ),
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ResultsScreen(
    result: SubmitLessonAnswersResponse,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    // Soft gradient background
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Background gradient overlay
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0xFF2D1B69).copy(alpha = 0.3f),
                                    Color(0xFF1A0E2E).copy(alpha = 0.5f),
                                ),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Success/Failure icon with soft glow
            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .background(
                            if (result.isPassed) {
                                Color(0xFF10B981).copy(alpha = 0.1f)
                            } else {
                                Color(0xFFEF4444).copy(alpha = 0.1f)
                            },
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (result.isPassed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint =
                        if (result.isPassed) {
                            Color(0xFF34D399) // Softer green
                        } else {
                            Color(0xFFF87171) // Softer red
                        },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title with soft shadow effect
            Text(
                if (result.isPassed) "Excellent Work!" else "Keep Learning!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF8FAFC), // Soft white
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                if (result.isPassed) {
                    "You've mastered this lesson!"
                } else {
                    "Practice makes perfect. Try again!"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFCBD5E1), // Soft gray
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Score card with soft design
            Card(
                modifier = Modifier.fillMaxWidth(0.7f),
                shape = RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1B4B).copy(alpha = 0.8f), // Soft purple background
                    ),
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Score percentage with gradient text effect
                    Text(
                        "${result.score.toInt()}%",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (result.isPassed) {
                                Color(0xFF34D399) // Soft green
                            } else {
                                Color(0xFFF87171)
                            },
                        // Soft red
                        textAlign = TextAlign.Center,
                    )

                    Divider(
                        modifier = Modifier.fillMaxWidth(0.5f),
                        color = Color(0xFF4C1D95).copy(alpha = 0.3f), // Soft purple divider
                        thickness = 1.dp,
                    )

                    Text(
                        "${result.correctAnswers} of ${result.totalQuestions} correct",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE2E8F0), // Soft light gray
                        textAlign = TextAlign.Center,
                    )

                    // Progress indicator
                    LinearProgressIndicator(
                        progress = result.score / 100f,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        color =
                            if (result.isPassed) {
                                Color(0xFF34D399)
                            } else {
                                Color(0xFFF87171)
                            },
                        trackColor = Color(0xFF4C1D95).copy(alpha = 0.2f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Action buttons with soft design
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(0.8f),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFCBD5E1),
                        ),
                    border = BorderStroke(1.dp, Color(0xFF4C1D95).copy(alpha = 0.3f)),
                ) {
                    Text(
                        "Back to Lessons",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Button(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                if (result.isPassed) {
                                    Color(0xFF34D399)
                                } else {
                                    Color(0xFFF87171)
                                },
                            contentColor = Color.White,
                        ),
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp,
                        ),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Try Again",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Network image loader for Compose Desktop with caching support.
 * Loads images from URLs asynchronously and caches them for instant reuse.
 */
@Composable
private fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var imageBitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(ImageCache.get(url)) }
    var isLoading by remember(url) { mutableStateOf(imageBitmap == null) }
    var error by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        // Check cache first
        val cached = ImageCache.get(url)
        if (cached != null) {
            imageBitmap = cached
            isLoading = false
            return@LaunchedEffect
        }

        // Load from network if not cached
        isLoading = true
        error = false
        try {
            println("[LessonPlayerScreen] Loading image from network: $url")
            val bitmap =
                ImageCache.getOrLoad(url) {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 5000 // 5 second timeout
                    connection.readTimeout = 5000
                    connection.connect()
                    val inputStream = connection.getInputStream()
                    val bytes = inputStream.use { it.readBytes() }
                    println("[LessonPlayerScreen] Downloaded ${bytes.size} bytes for image: $url")
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).asImageBitmap()
                }
            imageBitmap = bitmap
            println("[LessonPlayerScreen] ✓ Image loaded and cached: $url")
            isLoading = false
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "Timeout loading image from $url"
            println("[LessonPlayerScreen] ✗ ERROR: $errorMsg")
            error = true
            isLoading = false
        } catch (e: java.io.FileNotFoundException) {
            val errorMsg = "Image not found at $url"
            println("[LessonPlayerScreen] ✗ ERROR: $errorMsg")
            error = true
            isLoading = false
        } catch (e: Exception) {
            val errorMsg = "Error loading image from $url: ${e.javaClass.simpleName} - ${e.message}"
            println("[LessonPlayerScreen] ✗ ERROR: $errorMsg")
            println("[LessonPlayerScreen] Error details: ${e.stackTraceToString()}")
            error = true
            isLoading = false
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = WordBridgeColors.PrimaryPurple,
                )
            }
            error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = "Failed to load image",
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        "Failed to load image",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
        }
    }
}
