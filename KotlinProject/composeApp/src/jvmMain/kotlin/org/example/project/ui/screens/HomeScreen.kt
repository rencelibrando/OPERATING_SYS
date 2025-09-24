package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.HomeViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.core.auth.User as AuthUser

@Composable
fun HomeScreen(
    authenticatedUser: AuthUser? = null,
    onSignOut: (() -> Unit)? = null,
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val user by viewModel.user
    val navigationItems by viewModel.navigationItems
    val learningActivities by viewModel.learningActivities
    val isLoading by viewModel.isLoading
    val selectedNavigationItem by viewModel.selectedNavigationItem
    val showProfile by viewModel.showProfile
    
    // Use authenticated user data if available, otherwise fall back to sample data
    val displayUser = authenticatedUser?.let { authUser ->
        org.example.project.domain.model.User(
            id = authUser.id,
            name = authUser.fullName,
            level = "Beginner Level", // Default level, could be enhanced later
            streak = 0, // Default streak, could be enhanced later
            xpPoints = 0, // Default XP, could be enhanced later
            wordsLearned = 0, // Default words, could be enhanced later
            accuracy = 0, // Default accuracy, could be enhanced later
            avatarInitials = authUser.initials
        )
    } ?: user
    
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(WordBridgeColors.BackgroundLight)
    ) {
        // Sidebar Navigation
        Sidebar(
            navigationItems = navigationItems,
            onNavigationItemClick = viewModel::onNavigationItemSelected
        )
        
        // Main Content Area - Switch based on selected navigation item or profile view
        when {
            showProfile -> {
                ProfileScreen(
                    authenticatedUser = authenticatedUser,
                    modifier = Modifier.weight(1f)
                )
            }
            selectedNavigationItem == "home" || selectedNavigationItem.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header with user info
                    HomeHeader(
                        userName = displayUser.name,
                        userLevel = displayUser.level,
                        userInitials = displayUser.avatarInitials,
                        onUserAvatarClick = viewModel::onUserAvatarClicked,
                        authenticatedUser = authenticatedUser,
                        onSignOut = onSignOut
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Continue Learning Section
                    ContinueLearningCard(
                        onButtonClick = viewModel::onContinueLearningClicked,
                        isLoading = isLoading
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Content based on whether user has learning activities
                    if (learningActivities.isEmpty()) {
                        // Empty state
                        HomeEmptyState(
                            onGetStartedClick = {
                                // Navigate to lessons or show getting started guide
                                viewModel.onNavigationItemSelected("lessons")
                            }
                        )
                    } else {
                        // Main content in two columns
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Left Column - Learning Activities
                            Column(
                                modifier = Modifier.weight(2f)
                            ) {
                                learningActivities.forEach { activity ->
                                    LearningActivityCard(
                                        activity = activity,
                                        onClick = { viewModel.onLearningActivityClicked(activity.id) }
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                            
                            // Right Column - Progress
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                TodaysProgressCard(
                                    streak = user.streak,
                                    xpPoints = user.xpPoints,
                                    wordsLearned = user.wordsLearned,
                                    accuracy = user.accuracy
                                )
                            }
                        }
                    }
                }
            }
            selectedNavigationItem == "lessons" -> {
                LessonsScreen(
                    modifier = Modifier.weight(1f)
                )
            }
            selectedNavigationItem == "vocabulary" -> {
                VocabularyScreen(
                    modifier = Modifier.weight(1f)
                )
            }
            selectedNavigationItem == "speaking" -> {
                SpeakingScreen(
                    modifier = Modifier.weight(1f)
                )
            }
            selectedNavigationItem == "ai_chat" -> {
                AIChatScreen(
                    modifier = Modifier.weight(1f)
                )
            }
            selectedNavigationItem == "progress" -> {
                ProgressScreen(
                    modifier = Modifier.weight(1f)
                )
            }
            selectedNavigationItem == "settings" -> {
                SettingsScreen(
                    modifier = Modifier.weight(1f)
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Page not found",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Header section with welcome message and user info
 */
@Composable
private fun HomeHeader(
    userName: String,
    userLevel: String,
    userInitials: String,
    onUserAvatarClick: () -> Unit,
    authenticatedUser: AuthUser? = null,
    onSignOut: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Welcome message
        Column {
            Text(
                text = "Welcome Back, $userName!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = userLevel,
                style = MaterialTheme.typography.bodyLarge,
                color = WordBridgeColors.TextSecondary
            )
        }
        
        // User avatar and sign out options
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (authenticatedUser != null && onSignOut != null) {
                // Sign out button
                TextButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = WordBridgeColors.TextSecondary
                    )
                ) {
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            // User avatar (clickable - this is now the only way to access profile)
            UserAvatar(
                initials = userInitials,
                profileImageUrl = authenticatedUser?.profileImageUrl,
                size = 48.dp,
                onClick = onUserAvatarClick
            )
        }
    }
}
