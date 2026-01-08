package org.example.project.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.AIFeedbackViewModel
import org.example.project.ui.components.AIFeedbackContent
import org.example.project.ui.components.UserAvatar
import org.example.project.ui.theme.WordBridgeColors
import kotlin.math.PI
import kotlin.math.sin
import org.example.project.core.auth.User as AuthUser

@Composable
fun AIFeedbackScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    viewModel: AIFeedbackViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val conversationSessions by viewModel.conversationSessions
    val selectedSession by viewModel.selectedSession
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val isPlayingAudio by viewModel.isPlayingAudio
    val sortOrder by viewModel.sortOrder

    // Dynamic UI State
    var isExpanded by remember { mutableStateOf(false) }
    var cardScale by remember { mutableStateOf(1.0f) }
    var contentOpacity by remember { mutableStateOf(1.0f) }
    var selectedViewMode by remember { mutableStateOf("list") } // grid, list, compact
    var animationSpeed by remember { mutableStateOf(1.0f) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(authenticatedUser) {
        authenticatedUser?.id?.let { userId ->
            viewModel.loadConversationSessions(userId)
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val screenWidthDp = maxWidth.value.toInt()
        val isCompactScreen = screenWidthDp < 600
        val isTabletScreen = screenWidthDp >= 840

        AnimatedVisibility(
            visible = true,
            enter =
                slideInVertically(
                    initialOffsetY = { with(density) { -40.dp.roundToPx() } },
                    animationSpec = tween(durationMillis = 500, easing = EaseOutQuart),
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit =
                slideOutVertically(
                    targetOffsetY = { with(density) { -40.dp.roundToPx() } },
                    animationSpec = tween(durationMillis = 300),
                ) + fadeOut(animationSpec = tween(durationMillis = 300)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(WordBridgeColors.BackgroundMain)
                        .padding(
                            start = if (isTabletScreen) 16.dp else 12.dp,
                            end = if (isTabletScreen) 16.dp else 12.dp,
                            top = if (isCompactScreen) 8.dp else 12.dp,
                            bottom = if (isCompactScreen) 8.dp else 12.dp,
                        )
                        .imePadding()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                // Dynamic Interactive Header
                AnimatedVisibility(
                    visible = !isExpanded,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 6.dp else 8.dp),
                        ) {
                            if (onBackClick != null) {
                                TextButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Back",
                                        tint = WordBridgeColors.PrimaryPurple,
                                        modifier = Modifier.size(if (isCompactScreen) 18.dp else 20.dp),
                                    )
                                    if (!isCompactScreen) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Back",
                                            color = WordBridgeColors.TextPrimaryDark,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "AI Feedback",
                                    tint = WordBridgeColors.PrimaryPurple,
                                    modifier = Modifier.size(if (isCompactScreen) 24.dp else 28.dp),
                                )
                                if (!isCompactScreen) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI Insights",
                                        style =
                                            MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        color = WordBridgeColors.TextPrimaryDark,
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Expand/Collapse Button

                            UserAvatar(
                                initials = authenticatedUser?.initials ?: "U",
                                profileImageUrl = authenticatedUser?.profileImageUrl,
                                size = if (isCompactScreen) 36.dp else 40.dp,
                                onClick = onUserAvatarClick,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(
                                color = WordBridgeColors.PrimaryPurple,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = "Analyzing your conversations...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WordBridgeColors.TextSecondaryDark,
                            )
                        }
                    }
                } else if (error != null) {
                    ErrorMessage(
                        error = error!!,
                        onRetry = {
                            authenticatedUser?.id?.let { userId ->
                                viewModel.loadConversationSessions(userId)
                            }
                        },
                    )
                } else if (selectedSession != null) {
                    ConversationDetailView(
                        session = selectedSession!!,
                        onBack = { viewModel.clearSelectedSession() },
                        onPlayRecording = { viewModel.playRecording(selectedSession!!.sessionId) },
                        isPlayingAudio = isPlayingAudio,
                        viewModel = viewModel,
                    )
                } else if (conversationSessions.isEmpty()) {
                    EmptyFeedbackState()
                } else {
                    DynamicConversationHistory(
                        sessions = conversationSessions,
                        onSessionClick = { viewModel.selectSession(it) },
                        onDeleteClick = { sessionId ->
                            sessionToDelete = sessionId
                            showDeleteDialog = true
                        },
                        listState = listState,
                        viewMode = selectedViewMode,
                        animationSpeed = animationSpeed,
                        contentOpacity = contentOpacity,
                        cardScale = cardScale,
                        isCompactScreen = isCompactScreen,
                        isExpanded = isExpanded,
                        sortOrder = sortOrder,
                        onSortClick = { showSortMenu = !showSortMenu },
                        onSortOrderChange = { viewModel.setSortOrder(it) },
                        showSortMenu = showSortMenu,
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            DeleteConfirmationDialog(
                onConfirm = {
                    sessionToDelete?.let { viewModel.deleteSession(it) }
                    showDeleteDialog = false
                    sessionToDelete = null
                },
                onDismiss = {
                    showDeleteDialog = false
                    sessionToDelete = null
                },
            )
        }
    }
}

@Composable
internal fun DynamicConversationHistory(
    sessions: List<org.example.project.domain.model.ConversationSession>,
    onSessionClick: (org.example.project.domain.model.ConversationSession) -> Unit,
    onDeleteClick: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewMode: String,
    animationSpeed: Float,
    contentOpacity: Float,
    cardScale: Float,
    isCompactScreen: Boolean,
    isExpanded: Boolean,
    sortOrder: AIFeedbackViewModel.SortOrder,
    onSortClick: () -> Unit,
    onSortOrderChange: (AIFeedbackViewModel.SortOrder) -> Unit,
    showSortMenu: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Dynamic Header with softer design
        AnimatedVisibility(
            visible = !isExpanded,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WordBridgeColors.CardBackgroundDark,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Your Learning Journey",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = WordBridgeColors.TextPrimaryDark,
                        )
                        Text(
                            text = "Track your progress and see how you're improving",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "${sessions.size} ${if (sessions.size == 1) "session" else "sessions"}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = WordBridgeColors.PrimaryPurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Text(
                                text = "Keep up the great work! 🌟",
                                style = MaterialTheme.typography.labelSmall,
                                color = WordBridgeColors.TextSecondaryDark,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Sort Button
                        Box {
                            Surface(
                                onClick = onSortClick,
                                shape = CircleShape,
                                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
                                modifier = Modifier.size(if (isCompactScreen) 36.dp else 40.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = WordBridgeColors.PrimaryPurple,
                                        modifier = Modifier.size(if (isCompactScreen) 16.dp else 18.dp),
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = onSortClick,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date (Newest)") },
                                    onClick = {
                                        onSortOrderChange(org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.DATE_DESC)
                                        onSortClick()
                                    },
                                    leadingIcon =
                                        if (sortOrder == org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.DATE_DESC) {
                                            { Text("✓", color = WordBridgeColors.PrimaryPurple) }
                                        } else {
                                            null
                                        },
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Oldest)") },
                                    onClick = {
                                        onSortOrderChange(org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.DATE_ASC)
                                        onSortClick()
                                    },
                                    leadingIcon =
                                        if (sortOrder == AIFeedbackViewModel.SortOrder.DATE_ASC) {
                                            { Text("✓", color = WordBridgeColors.PrimaryPurple) }
                                        } else {
                                            null
                                        },
                                )
                                DropdownMenuItem(
                                    text = { Text("Duration (Longest)") },
                                    onClick = {
                                        onSortOrderChange(
                                            org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.DURATION_DESC,
                                        )
                                        onSortClick()
                                    },
                                    leadingIcon =
                                        if (sortOrder == org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.DURATION_DESC) {
                                            { Text("✓", color = WordBridgeColors.PrimaryPurple) }
                                        } else {
                                            null
                                        },
                                )
                                DropdownMenuItem(
                                    text = { Text("Duration (Shortest)") },
                                    onClick = {
                                        onSortOrderChange(org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.DURATION_ASC)
                                        onSortClick()
                                    },
                                    leadingIcon =
                                        if (sortOrder == org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.DURATION_ASC) {
                                            { Text("✓", color = WordBridgeColors.PrimaryPurple) }
                                        } else {
                                            null
                                        },
                                )
                                DropdownMenuItem(
                                    text = { Text("Language (A-Z)") },
                                    onClick = {
                                        onSortOrderChange(org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.LANGUAGE_AZ)
                                        onSortClick()
                                    },
                                    leadingIcon =
                                        if (sortOrder == org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.LANGUAGE_AZ) {
                                            { Text("✓", color = WordBridgeColors.PrimaryPurple) }
                                        } else {
                                            null
                                        },
                                )
                                DropdownMenuItem(
                                    text = { Text("Language (Z-A)") },
                                    onClick = {
                                        onSortOrderChange(org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.LANGUAGE_ZA)
                                        onSortClick()
                                    },
                                    leadingIcon =
                                        if (sortOrder == org.example.project.presentation.viewmodel.AIFeedbackViewModel.SortOrder.LANGUAGE_ZA) {
                                            { Text("✓", color = WordBridgeColors.PrimaryPurple) }
                                        } else {
                                            null
                                        },
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
                            modifier = Modifier.size(if (isCompactScreen) 36.dp else 40.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = "📈",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dynamic Content based on view mode
        when (viewMode) {
            "grid" ->
                DynamicGridView(
                    sessions = sessions,
                    onSessionClick = onSessionClick,
                    onDeleteClick = onDeleteClick,
                    animationSpeed = animationSpeed,
                    contentOpacity = contentOpacity,
                    cardScale = cardScale,
                    isCompactScreen = isCompactScreen,
                )
            "compact" ->
                DynamicCompactView(
                    sessions = sessions,
                    onSessionClick = onSessionClick,
                    onDeleteClick = onDeleteClick,
                    animationSpeed = animationSpeed,
                    contentOpacity = contentOpacity,
                    cardScale = cardScale,
                    isCompactScreen = isCompactScreen,
                )
            else ->
                DynamicListView(
                    sessions = sessions,
                    onSessionClick = onSessionClick,
                    onDeleteClick = onDeleteClick,
                    listState = listState,
                    animationSpeed = animationSpeed,
                    contentOpacity = contentOpacity,
                    cardScale = cardScale,
                    isCompactScreen = isCompactScreen,
                )
        }
    }
}

@Composable
internal fun DynamicGridView(
    sessions: List<org.example.project.domain.model.ConversationSession>,
    onSessionClick: (org.example.project.domain.model.ConversationSession) -> Unit,
    onDeleteClick: (String) -> Unit,
    animationSpeed: Float,
    contentOpacity: Float,
    cardScale: Float,
    isCompactScreen: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        val columns = if (isCompactScreen) 1 else 2
        items(sessions.chunked(columns).size) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 12.dp else 16.dp),
            ) {
                val chunk = sessions.chunked(columns)[rowIndex]
                chunk.forEach { session ->
                    DynamicSessionCard(
                        session = session,
                        onClick = { onSessionClick(session) },
                        onDeleteClick = { onDeleteClick(session.sessionId) },
                        animationSpeed = animationSpeed,
                        contentOpacity = contentOpacity,
                        cardScale = cardScale,
                        isCompactScreen = isCompactScreen,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill empty slots if needed
                repeat(columns - chunk.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun DynamicListView(
    sessions: List<org.example.project.domain.model.ConversationSession>,
    onSessionClick: (org.example.project.domain.model.ConversationSession) -> Unit,
    onDeleteClick: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    animationSpeed: Float,
    contentOpacity: Float,
    cardScale: Float,
    isCompactScreen: Boolean,
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(
            items = sessions,
            key = { it.sessionId },
        ) { session ->
            DynamicSessionCard(
                session = session,
                onClick = { onSessionClick(session) },
                onDeleteClick = { onDeleteClick(session.sessionId) },
                animationSpeed = animationSpeed,
                contentOpacity = contentOpacity,
                cardScale = cardScale,
                isCompactScreen = isCompactScreen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun DynamicCompactView(
    sessions: List<org.example.project.domain.model.ConversationSession>,
    onSessionClick: (org.example.project.domain.model.ConversationSession) -> Unit,
    onDeleteClick: (String) -> Unit,
    animationSpeed: Float,
    contentOpacity: Float,
    cardScale: Float,
    isCompactScreen: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(
            items = sessions,
            key = { it.sessionId },
        ) { session ->
            DynamicCompactSessionCard(
                session = session,
                onClick = { onSessionClick(session) },
                onDeleteClick = { onDeleteClick(session.sessionId) },
                animationSpeed = animationSpeed,
                contentOpacity = contentOpacity,
                cardScale = cardScale,
                isCompactScreen = isCompactScreen,
            )
        }
    }
}

@Composable
internal fun DynamicSessionCard(
    session: org.example.project.domain.model.ConversationSession,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    animationSpeed: Float,
    contentOpacity: Float,
    cardScale: Float,
    isCompactScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }

    val animationDuration = (200 / animationSpeed).toInt()
    
    // Smooth elevation animation
    val animatedElevation by animateFloatAsState(
        targetValue = if (isPressed) 2f else 6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Card(
        onClick = onClick,
        modifier =
            modifier
                .shadow(
                    elevation = animatedElevation.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = WordBridgeColors.PrimaryPurple.copy(alpha = 0.2f),
                )
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            awaitRelease()
                            isPressed = false
                        },
                    )
                }
                .animateContentSize(animationSpec = tween(animationDuration)),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header with language and personal touch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Text(
                                text = getLanguageEmoji(session.language),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${session.language.replaceFirstChar { it.uppercase() }} Practice",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = WordBridgeColors.TextPrimaryDark,
                        )
                        Text(
                            text = "Your ${session.level.lowercase()} session",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                    }
                }

                // Level badge with more visual appeal
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = getLevelColor(session.level),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = session.level.first().uppercase() + session.level.drop(1),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Personalized progress message
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "📈",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Great progress! You completed ${session.turnCount} turns",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = WordBridgeColors.TextPrimaryDark,
                        )
                        Text(
                            text = "Practice time: ${formatDuration(session.duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                    }
                }
            }

            // Footer with date and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Completed on ${formatTimestamp(session.createdAt).split(" ").first()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WordBridgeColors.TextSecondaryDark,
                    )
                    Text(
                        text = "Tap to review your feedback 🎯",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = WordBridgeColors.PrimaryPurple,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Audio indicator
                    if (session.audioUrl != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Has Audio",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    // Delete button
                    Surface(
                        onClick = { onDeleteClick() },
                        shape = CircleShape,
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DynamicCompactSessionCard(
    session: org.example.project.domain.model.ConversationSession,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    animationSpeed: Float,
    contentOpacity: Float,
    cardScale: Float,
    isCompactScreen: Boolean,
) {
    val animationDuration = (200 / animationSpeed).toInt()

    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(animationDuration))
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(10.dp),
                    spotColor = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f),
                ),
        shape = RoundedCornerShape(10.dp),
        color = WordBridgeColors.CardBackgroundDark,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Language emoji + name + stats
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = getLanguageEmoji(session.language),
                    style = MaterialTheme.typography.titleSmall,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = session.language.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = WordBridgeColors.TextPrimaryDark,
                    )
                    Text(
                        text = "${session.turnCount} turns • ${formatDuration(session.duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WordBridgeColors.TextSecondaryDark,
                    )
                }
            }

            // Right: Level + audio + delete
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = getLevelColor(session.level).copy(alpha = 0.15f),
                ) {
                    Text(
                        text = session.level.first().uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = getLevelColor(session.level),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (session.audioUrl != null) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Has Audio",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Surface(
                    onClick = { onDeleteClick() },
                    shape = CircleShape,
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationSessionCard(
    session: org.example.project.domain.model.ConversationSession,
    onClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isPressed) 2.dp else 6.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = WordBridgeColors.PrimaryPurple.copy(alpha = 0.2f),
                )
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    isPressed = true
                    onClick()
                    isPressed = false
                },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = CircleShape,
                    color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = getLanguageEmoji(session.language),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Column {
                    Text(
                        text = session.language.capitalize(),
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = WordBridgeColors.TextPrimaryDark,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${session.turnCount} turns",
                            style = MaterialTheme.typography.labelSmall,
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                        Text(
                            text = formatDuration(session.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formatTimestamp(session.createdAt).split(" ").first(),
                    style = MaterialTheme.typography.labelSmall,
                    color = WordBridgeColors.TextSecondaryDark,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (session.audioUrl != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            modifier = Modifier.size(20.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Has Audio",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = getLevelColor(session.level),
                    ) {
                        Text(
                            text = session.level.first().uppercase() + session.level.drop(1),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EnhancedPlaybackButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    session: org.example.project.domain.model.ConversationSession,
) {
    var isPressed by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isPlaying) Color(0xFF10B981) else WordBridgeColors.PrimaryPurple,
            ),
        shape = RoundedCornerShape(16.dp),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = if (isPressed) 2.dp else 6.dp,
                pressedElevation = 2.dp,
            ),
        modifier =
            Modifier
                .shadow(
                    elevation = if (isPressed) 4.dp else 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = if (isPlaying) Color(0xFF10B981).copy(alpha = 0.3f) else WordBridgeColors.PrimaryPurple.copy(alpha = 0.3f),
                )
                .clickable {
                    isPressed = true
                    onClick()
                    isPressed = false
                },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = isPlaying,
                enter = scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                exit = scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Stop")
                }
            }

            AnimatedVisibility(
                visible = !isPlaying,
                enter = scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                exit = scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Play Recording")
                }
            }
        }
    }
}

@Composable
internal fun AudioWaveformVisualization(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isCompactScreen: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isCompactScreen) 40.dp else 48.dp)
            .background(
                color = WordBridgeColors.CardBackgroundDark.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = if (isCompactScreen) 12.dp else 16.dp, vertical = if (isCompactScreen) 8.dp else 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val barCount = if (isCompactScreen) 80 else 110
            repeat(barCount) { index ->
                val height =
                    if (isPlaying) {
                        val progress = (animatedProgress + index * 0.01f) % 1f
                        val minHeight = if (isCompactScreen) 6.dp else 8.dp
                        val maxHeight = if (isCompactScreen) 28.dp else 36.dp
                        val baseHeight = if (isCompactScreen) 12.dp else 16.dp
                        (baseHeight + ((maxHeight - baseHeight) * sin(progress * 2 * PI.toFloat()))).coerceIn(minHeight, maxHeight)
                    } else {
                        if (isCompactScreen) 12.dp else 16.dp
                    }

                Box(
                    modifier =
                        Modifier
                            .width(if (isCompactScreen) 3.dp else 4.dp)
                            .height(height)
                            .background(
                                color =
                                    if (isPlaying) {
                                        Color(0xFF10B981)
                                    } else {
                                        WordBridgeColors.PrimaryPurple.copy(alpha = 0.7f)
                                    },
                                shape = RoundedCornerShape(if (isCompactScreen) 1.dp else 1.5.dp),
                            ),
                )
            }
        }
    }
}

@Composable
internal fun AudioPlayerCard(
    session: org.example.project.domain.model.ConversationSession,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    isCompactScreen: Boolean = false,
    hasAudio: Boolean = true,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 6.dp,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(if (isCompactScreen) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompactScreen) 8.dp else 12.dp),
        ) {
            // Header section with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color =
                            if (!hasAudio) {
                                Color.Gray.copy(alpha = 0.15f)
                            } else if (isPlaying) {
                                Color(0xFF10B981).copy(alpha = 0.15f)
                            } else {
                                WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f)
                            },
                        modifier = Modifier.size(if (isCompactScreen) 32.dp else 40.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector =
                                    if (!hasAudio) {
                                        Icons.Default.VolumeUp
                                    } else if (isPlaying) {
                                        Icons.Default.VolumeUp
                                    } else {
                                        Icons.Default.Mic
                                    },
                                contentDescription =
                                    if (!hasAudio) {
                                        "No Audio"
                                    } else if (isPlaying) {
                                        "Playing"
                                    } else {
                                        "Audio"
                                    },
                                tint =
                                    if (!hasAudio) {
                                        Color.Gray
                                    } else if (isPlaying) {
                                        Color(0xFF10B981)
                                    } else {
                                        WordBridgeColors.PrimaryPurple
                                    },
                                modifier = Modifier.size(if (isCompactScreen) 16.dp else 20.dp),
                            )
                        }
                    }

                    Column {
                        Text(
                            text =
                                if (!hasAudio) {
                                    "No Audio Available"
                                } else if (isPlaying) {
                                    "Now Playing"
                                } else {
                                    "Audio Recording"
                                },
                            style =
                                if (isCompactScreen) {
                                    MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                } else {
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                },
                            color = if (!hasAudio) Color.Gray else WordBridgeColors.TextPrimaryDark,
                        )
                        Text(
                            text = "${session.language.capitalize()} Practice Session",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.TextSecondaryDark,
                        )
                        if (!hasAudio) {
                            Text(
                                text = "Audio recording not available",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                            )
                        }
                    }
                }

                if (hasAudio) {
                    EnhancedPlaybackButton(
                        isPlaying = isPlaying,
                        onClick = onPlayClick,
                        session = session,
                    )
                }
            }

            // Extended waveform section in the middle
            if (hasAudio) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    AudioWaveformVisualization(
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxWidth(),
                        isCompactScreen = isCompactScreen,
                    )
                }
            } else {
                // Show placeholder for no audio
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Color.Gray.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "🔇 No Audio Waveform",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                    )
                }
            }

            // Bottom info section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(session.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = WordBridgeColors.TextSecondaryDark,
                )

                Text(
                    text = "${session.turnCount} turns",
                    style = MaterialTheme.typography.labelSmall,
                    color = WordBridgeColors.TextSecondaryDark,
                )
            }
        }
    }
}

@Composable
internal fun ConversationDetailView(
    session: org.example.project.domain.model.ConversationSession,
    onBack: () -> Unit,
    onPlayRecording: () -> Unit,
    isPlayingAudio: Boolean,
    viewModel: AIFeedbackViewModel,
) {
    val listState = rememberLazyListState()
    val currentFeedback by viewModel.currentFeedback
    val isAnalyzing by viewModel.isAnalyzing
    val analysisError by viewModel.analysisError
    val currentRecording by viewModel.currentRecording
    val audioPlaybackError by viewModel.audioPlaybackError

    // Debug logging
    LaunchedEffect(session) {
        println("[ConversationDetailView] Session audioUrl: ${session.audioUrl}")
        println("[ConversationDetailView] Recording audioUrl: ${currentRecording?.audioUrl}")
        println("[ConversationDetailView] Session ID: ${session.sessionId}")
        println("[ConversationDetailView] Language: ${session.language}")
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val screenWidthDp = maxWidth.value.toInt()
        val isCompactScreen = screenWidthDp < 600
        val isTabletScreen = screenWidthDp >= 840

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isTabletScreen) 24.dp else 0.dp,
                        vertical = 16.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                // Header with Back Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Back",
                            tint = WordBridgeColors.PrimaryPurple,
                            modifier = Modifier.size(if (isCompactScreen) 16.dp else 18.dp),
                        )
                        if (!isCompactScreen) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Back to Progress",
                                color = WordBridgeColors.TextPrimaryDark,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Check for audio from both session and recording
                    val hasAudio = !session.audioUrl.isNullOrEmpty() || !currentRecording?.audioUrl.isNullOrEmpty()

                    // Audio playback is handled in AudioPlayerCard below
                }
            }

            item {
                // Audio Player Card - Always show for debugging, but conditionally enable
                val hasAudio = !session.audioUrl.isNullOrEmpty() || !currentRecording?.audioUrl.isNullOrEmpty()
                AudioPlayerCard(
                    session = session,
                    isPlaying = isPlayingAudio,
                    onPlayClick = onPlayRecording,
                    isCompactScreen = isCompactScreen,
                    hasAudio = hasAudio,
                )
            }

            // Show audio playback error if any
            if (audioPlaybackError != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = Color(0xFF431216),
                            ),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Audio Error",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = audioPlaybackError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                // Session Overview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WordBridgeColors.CardBackgroundDark,
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 4.dp,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Session Overview",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = WordBridgeColors.TextPrimaryDark,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            MetricCard(
                                label = "Duration",
                                value = formatDuration(session.duration),
                                icon = "⏱️",
                            )
                            MetricCard(
                                label = "Turns",
                                value = session.turnCount.toString(),
                                icon = "💬",
                            )
                            MetricCard(
                                label = "Level",
                                value = session.level.first().uppercase() + session.level.drop(1),
                                icon = "📊",
                            )
                        }
                    }
                }
            }

            item {
                // Enhanced Conversation Flow Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WordBridgeColors.CardBackgroundDark,
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 4.dp,
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Conversation Flow",
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = WordBridgeColors.TextPrimaryDark,
                            )
                            Text(
                                text = "${session.turnCount} turns • ${session.language} • ${session.level}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WordBridgeColors.TextSecondaryDark,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = getLevelColor(session.level),
                            ) {
                                Text(
                                    text = session.level.first().uppercase() + session.level.drop(1),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Enhanced Conversation Flow Card - Much Bigger
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(if (isCompactScreen) 400.dp else 600.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(24.dp),
                                spotColor = WordBridgeColors.PrimaryPurple.copy(alpha = 0.2f),
                            ),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WordBridgeColors.CardBackgroundDark,
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 8.dp,
                        ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Conversation Header Inside Card
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp, 16.dp, 20.dp, 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Full Conversation",
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = WordBridgeColors.TextPrimaryDark,
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Text(
                                            text = "🎙️",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                                Text(
                                    text = formatDuration(session.duration),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    color = WordBridgeColors.TextSecondaryDark,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = WordBridgeColors.TextSecondaryDark.copy(alpha = 0.1f),
                        )

                        // Enhanced Conversation List
                        LazyColumn(
                            state = listState,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val turns = parseTranscript(session.transcript)
                            items(
                                items = turns,
                                key = { turn: Pair<String, String> -> "${turn.first}_${turn.second.hashCode()}" },
                            ) { turn: Pair<String, String> ->
                                EnhancedTranscriptBubble(
                                    role = turn.first,
                                    text = turn.second,
                                    isCompactScreen = isCompactScreen,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WordBridgeColors.CardBackgroundDark,
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 8.dp,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "AI Feedback",
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = WordBridgeColors.TextPrimaryDark,
                            )

                            Surface(
                                shape = CircleShape,
                                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Text(
                                        text = "🤖",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }

                        when {
                            isAnalyzing -> {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            color = WordBridgeColors.PrimaryPurple,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(40.dp),
                                        )
                                        Text(
                                            text = "Analyzing your conversation...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = WordBridgeColors.TextSecondaryDark,
                                        )
                                    }
                                }
                            }

                            analysisError != null -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = "⚠️ Analysis Failed",
                                        style =
                                            MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        color = Color(0xFFEF4444),
                                    )
                                    Text(
                                        text = analysisError!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = WordBridgeColors.TextSecondaryDark,
                                        textAlign = TextAlign.Center,
                                    )
                                    Button(
                                        onClick = { viewModel.retryAnalysis() },
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = WordBridgeColors.PrimaryPurple,
                                            ),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text("Retry Analysis")
                                    }
                                }
                            }

                            currentFeedback != null -> {
                                AIFeedbackContent(
                                    feedback = currentFeedback!!,
                                    isCompactScreen = isCompactScreen,
                                )
                            }

                            else -> {
                                Text(
                                    text = "AI feedback will appear here after analysis",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WordBridgeColors.TextSecondaryDark,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EnhancedTranscriptBubble(
    role: String,
    text: String,
    isCompactScreen: Boolean = false,
) {
    val isUser = role.lowercase() == "user"

    AnimatedVisibility(
        visible = true,
        enter =
            slideInHorizontally(
                initialOffsetX = { if (isUser) it else -it },
                animationSpec = tween(durationMillis = 400, delayMillis = 50),
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
        exit =
            slideOutHorizontally(
                targetOffsetX = { if (isUser) -it else it },
                animationSpec = tween(durationMillis = 200),
            ) + fadeOut(animationSpec = tween(durationMillis = 200)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            if (!isUser) {
                // AI Avatar
                Surface(
                    shape = CircleShape,
                    color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = "🤖",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = if (isCompactScreen) 280.dp else 320.dp),
            ) {
                // Role Label
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (isUser) {
                            Color(0xFF10B981).copy(alpha = 0.1f)
                        } else {
                            WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f)
                        },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                ) {
                    Text(
                        text = if (isUser) "You" else "AI Tutor",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = if (isUser) Color(0xFF10B981) else WordBridgeColors.PrimaryPurple,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Message Bubble
                Card(
                    shape =
                        RoundedCornerShape(
                            topStart = if (isUser) 20.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 20.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp,
                        ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isUser) {
                                    Color(0xFF10B981)
                                } else {
                                    Color(0xFF1E293B)
                                },
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 0.dp,
                        ),
                    border =
                        if (!isUser) {
                            BorderStroke(1.dp, WordBridgeColors.TextSecondaryDark.copy(alpha = 0.2f))
                        } else {
                            null
                        },
                ) {
                    Text(
                        text = text,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        color = if (isUser) Color.White else WordBridgeColors.TextPrimaryDark,
                        modifier =
                            Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 12.dp,
                            ),
                        lineHeight = 18.sp,
                    )
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                // User Avatar
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = "👤",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TranscriptBubble(
    role: String,
    text: String,
) {
    val isUser = role.lowercase() == "user"
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter =
                slideInHorizontally(
                    initialOffsetX = { if (isUser) it else -it },
                    animationSpec = tween(durationMillis = 400, easing = EaseOutQuart),
                ) +
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = tween(durationMillis = 300, easing = EaseOutQuart),
                    ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { if (isUser) -it else it },
                    animationSpec = tween(durationMillis = 200),
                ) +
                    scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(durationMillis = 200),
                    ) + fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            Surface(
                shape =
                    RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                color = if (isUser) WordBridgeColors.PrimaryPurple else WordBridgeColors.CardBackgroundDark,
                shadowElevation = 4.dp,
                tonalElevation = 2.dp,
                modifier =
                    Modifier
                        .widthIn(max = 320.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape =
                                RoundedCornerShape(
                                    topStart = if (isUser) 16.dp else 4.dp,
                                    topEnd = if (isUser) 4.dp else 16.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp,
                                ),
                            spotColor = if (isUser) WordBridgeColors.PrimaryPurple.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.1f),
                        ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isUser) Color.White.copy(alpha = 0.2f) else WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f),
                            modifier = Modifier.size(20.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = if (isUser) "👤" else "🤖",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                        Text(
                            text = if (isUser) "You" else "AI Tutor",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            color = if (isUser) Color.White.copy(alpha = 0.9f) else WordBridgeColors.TextSecondaryDark,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) Color.White else WordBridgeColors.TextPrimaryDark,
                        lineHeight = 18.sp,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyFeedbackState() {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter =
                scaleIn(
                    initialScale = 0.5f,
                    animationSpec = tween(durationMillis = 600, easing = EaseOutQuart),
                ) + fadeIn(animationSpec = tween(durationMillis = 400)),
            exit =
                scaleOut(
                    targetScale = 0.5f,
                    animationSpec = tween(durationMillis = 300),
                ) + fadeOut(animationSpec = tween(durationMillis = 300)),
        ) {
            Surface(
                shape = CircleShape,
                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f),
                shadowElevation = 4.dp,
                modifier = Modifier.size(120.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = "🎙️",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 56.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = isVisible,
            enter =
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(durationMillis = 500, delayMillis = 200, easing = EaseOutQuart),
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit =
                slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = tween(durationMillis = 200),
                ) + fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = WordBridgeColors.CardBackgroundDark,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(28.dp),
                ) {
                    Text(
                        text = "Ready to Start Your Journey?",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimaryDark,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Begin your language learning adventure with personalized AI feedback. Every conversation brings you closer to fluency! 🌟",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondaryDark,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "💡",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Your first session will appear here with detailed AI feedback",
                                style = MaterialTheme.typography.bodySmall,
                                color = WordBridgeColors.TextSecondaryDark,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Conversation?",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimaryDark,
            )
        },
        text = {
            Text(
                text = "This will permanently delete this conversation session and its feedback. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondaryDark,
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Delete",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = WordBridgeColors.TextSecondaryDark,
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = WordBridgeColors.CardBackgroundDark,
    )
}

@Composable
internal fun ErrorMessage(
    error: String,
    onRetry: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter =
                scaleIn(
                    initialScale = 0.5f,
                    animationSpec = tween(durationMillis = 600, easing = EaseOutQuart),
                ) + fadeIn(animationSpec = tween(durationMillis = 400)),
            exit =
                scaleOut(
                    targetScale = 0.5f,
                    animationSpec = tween(durationMillis = 300),
                ) + fadeOut(animationSpec = tween(durationMillis = 300)),
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFEE2E2),
                modifier = Modifier.size(100.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 50.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedVisibility(
            visible = isVisible,
            enter =
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(durationMillis = 500, delayMillis = 200, easing = EaseOutQuart),
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit =
                slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = tween(durationMillis = 200),
                ) + fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Oops! Something went wrong",
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.TextPrimaryDark,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondaryDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Button(
                    onClick = onRetry,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple,
                        ),
                    shape = RoundedCornerShape(12.dp),
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp,
                        ),
                ) {
                    Text("Try Again")
                }
            }
        }
    }
}

@Composable
internal fun MetricCard(
    label: String,
    value: String,
    icon: String,
) {
    var isHovered by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isHovered) WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f) else Color(0xFF1E293B),
        modifier =
            Modifier
                .size(80.dp)
                .shadow(
                    elevation = if (isHovered) 6.dp else 2.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = WordBridgeColors.PrimaryPurple.copy(alpha = 0.3f),
                )
                .clickable { isHovered = !isHovered },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp),
        ) {
            AnimatedVisibility(
                visible = isHovered,
                enter = scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                exit = scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            AnimatedVisibility(
                visible = !isHovered,
                enter = scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                exit = scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = value,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimaryDark,
                fontSize = 14.sp,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = WordBridgeColors.TextSecondaryDark,
                fontSize = 10.sp,
            )
        }
    }
}

internal fun getLanguageEmoji(language: String): String {
    return when (language.lowercase()) {
        "korean", "hangeul" -> "🇰🇷"
        "mandarin", "chinese" -> "🇨🇳"
        "spanish" -> "🇪🇸"
        "french" -> "🇫🇷"
        "german" -> "🇩🇪"
        else -> "🇬🇧"
    }
}

internal fun getLevelColor(level: String): Color {
    return when (level.lowercase()) {
        "beginner" -> Color(0xFF10B981)
        "intermediate" -> Color(0xFF3B82F6)
        "advanced" -> Color(0xFF8B5CF6)
        else -> Color(0xFF6B7280)
    }
}

internal fun formatDuration(seconds: Float): String {
    val minutes = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return if (minutes > 0) {
        "${minutes}m ${secs}s"
    } else {
        "${secs}s"
    }
}

internal fun formatTimestamp(timestamp: String): String {
    return try {
        val parts = timestamp.split("T")
        val date = parts[0]
        val time = parts.getOrNull(1)?.substring(0, 5) ?: ""
        "$date at $time"
    } catch (e: Exception) {
        timestamp
    }
}

internal fun parseTranscript(transcript: String): List<Pair<String, String>> {
    return transcript.split("\n")
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                Pair(parts[0].trim(), parts[1].trim())
            } else {
                null
            }
        }
}
