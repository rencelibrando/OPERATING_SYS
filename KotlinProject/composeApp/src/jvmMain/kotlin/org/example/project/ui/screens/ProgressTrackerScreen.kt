package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.domain.model.LessonLanguage
import org.example.project.domain.model.ProgressTrackerState
import org.example.project.presentation.viewmodel.ProgressViewModel
import org.example.project.ui.components.LanguageProgressCard
import org.example.project.ui.components.ProgressShareDialog
import org.example.project.ui.components.ProgressTrendsChart
import org.example.project.ui.components.UserAvatar
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.core.auth.User as AuthUser

/**
 * New Progress Tracker Screen with comprehensive language-based analytics.
 *
 * Features:
 * - Language selector tabs
 * - Per-language metrics (lessons, conversations, vocabulary, time, scores)
 * - Pull-to-refresh support
 * - Loading/Error/Empty states
 * - Responsive layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressTrackerScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    viewModel: ProgressViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    isRealtimeConnected: Boolean = false,
) {
    val selectedLanguage by viewModel.selectedLanguage
    val progressState by viewModel.progressState
    val isRefreshing by viewModel.isRefreshing
    val progressHistory by viewModel.progressHistory
    val selectedTimeRange by viewModel.selectedTimeRange
    val isLoadingHistory by viewModel.isLoadingHistory
    val showShareDialog by viewModel.showShareDialog
    val exportMessage by viewModel.exportMessage
    val isRealtimeConnected by viewModel.isRealtimeConnected

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(WordBridgeColors.BackgroundMain),
    ) {
        // Header
        ProgressTrackerHeader(
            authenticatedUser = authenticatedUser,
            onUserAvatarClick = onUserAvatarClick,
            onRefresh = { viewModel.refreshProgress() },
            isRefreshing = isRefreshing,
            isRealtimeConnected = isRealtimeConnected,
        )

        // Language Selector
        LanguageSelector(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { viewModel.selectLanguage(it) },
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Content based on state
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
        ) {
            when (val state = progressState) {
                is ProgressTrackerState.Loading -> {
                    LoadingState()
                }
                is ProgressTrackerState.Success -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        // Current Progress Card
                        LanguageProgressCard(progress = state.progress)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        ActionButtons(
                            onShare = { viewModel.showShareDialog() },
                            onCaptureSnapshot = { viewModel.captureCurrentSnapshot() },
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Historical Trends Chart
                        if (progressHistory.isNotEmpty() || isLoadingHistory) {
                            ProgressTrendsChart(
                                history = progressHistory,
                                selectedTimeRange = selectedTimeRange,
                                onTimeRangeSelected = { viewModel.selectTimeRange(it) },
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
                is ProgressTrackerState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadLanguageProgress(forceRefresh = true) },
                    )
                }
                is ProgressTrackerState.Empty -> {
                    EmptyState(language = selectedLanguage.displayName)
                }
            }
        }

        // Share Dialog
        if (showShareDialog) {
            val state = progressState
            if (state is ProgressTrackerState.Success) {
                ProgressShareDialog(
                    progress = state.progress,
                    onDismiss = { viewModel.hideShareDialog() },
                    onExportPNG = { /* TODO: Implement screenshot capture */ },
                    onExportHTML = { viewModel.exportToHTML() },
                    onCopyText = { viewModel.copyProgressText() },
                    onShareLink = { viewModel.shareLink() },
                )
            }
        }

        // Export Feedback Snackbar
        exportMessage?.let { message ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearExportMessage() }) {
                            Text("OK")
                        }
                    },
                    containerColor = WordBridgeColors.PrimaryPurple,
                    contentColor = Color.White,
                ) {
                    Text(message)
                }
            }
        }
    }
}

@Composable
private fun ProgressTrackerHeader(
    authenticatedUser: AuthUser?,
    onUserAvatarClick: (() -> Unit)?,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    isRealtimeConnected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WordBridgeColors.CardBackgroundDark,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Progress Tracker",
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.TextPrimaryDark,
                )
                Text(
                    text = "Track your language learning journey",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondaryDark,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Real-time status indicator
                if (isRealtimeConnected) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                ) {
                    Text(
                        text = if (isRefreshing) "" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                UserAvatar(
                    initials = authenticatedUser?.initials ?: "U",
                    profileImageUrl = authenticatedUser?.profileImageUrl,
                    size = 48.dp,
                    onClick = onUserAvatarClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(
    selectedLanguage: LessonLanguage,
    onLanguageSelected: (LessonLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = LessonLanguage.entries.indexOf(selectedLanguage),
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        edgePadding = 0.dp,
        divider = {},
    ) {
        LessonLanguage.entries.forEach { language ->
            val isSelected = language == selectedLanguage

            Tab(
                selected = isSelected,
                onClick = { onLanguageSelected(language) },
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isSelected) {
                                    WordBridgeColors.PrimaryPurple
                                } else {
                                    WordBridgeColors.CardBackgroundDark
                                },
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = if (isSelected) 4.dp else 2.dp,
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = getLanguageFlag(language.code),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = language.displayName,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                            color = if (isSelected) Color.White else WordBridgeColors.TextPrimaryDark,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = WordBridgeColors.PrimaryPurple)
            Text(
                text = "Loading your progress...",
                style = MaterialTheme.typography.bodyLarge,
                color = WordBridgeColors.TextSecondaryDark,
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = WordBridgeColors.CardBackgroundDark,
                ),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.displayMedium,
                )

                Text(
                    text = "Oops! Something went wrong",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.TextPrimaryDark,
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondaryDark,
                    textAlign = TextAlign.Center,
                )

                Button(
                    onClick = onRetry,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple,
                        ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "🔄 Try Again",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    language: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = WordBridgeColors.CardBackgroundDark,
                ),
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "📊",
                    style = MaterialTheme.typography.displayLarge,
                )

                Text(
                    text = "Start Your $language Journey",
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.TextPrimaryDark,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Begin learning $language to see your progress here! Complete lessons, practice conversations, and build your vocabulary.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WordBridgeColors.TextSecondaryDark,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = "📚", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Take Lessons",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WordBridgeColors.PrimaryPurple,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF3B82F6).copy(alpha = 0.1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = "💬", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Practice Speaking",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF3B82F6),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onShare: () -> Unit,
    onCaptureSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = WordBridgeColors.PrimaryPurple,
                ),
        ) {
            Text("📤 Share")
        }

        OutlinedButton(
            onClick = onCaptureSnapshot,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = WordBridgeColors.PrimaryPurple,
                ),
        ) {
            Text("📸 Save Snapshot")
        }
    }
}

private fun getLanguageFlag(code: String): String {
    return when (code) {
        "ko" -> "🇰🇷"
        "zh" -> "🇨🇳"
        "fr" -> "🇫🇷"
        "de" -> "🇩🇪"
        "es" -> "🇪🇸"
        else -> "🌍"
    }
}
