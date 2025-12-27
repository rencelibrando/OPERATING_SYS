package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.HomeViewModel
import org.example.project.presentation.viewmodel.LessonsViewModel
import org.example.project.presentation.viewmodel.OnboardingViewModel
import org.example.project.ui.components.ContinueLearningCard
import org.example.project.ui.components.HomeEmptyState
import org.example.project.ui.components.LearningActivityCard
import org.example.project.ui.components.Sidebar
import org.example.project.ui.components.TodaysProgressCard
import org.example.project.ui.components.UserAvatar
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.core.auth.User as AuthUser

@Composable
fun HomeScreen(
    authenticatedUser: AuthUser? = null,
    onSignOut: (() -> Unit)? = null,
    onboardingViewModel: OnboardingViewModel = viewModel(),
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val isOnboardingComplete by onboardingViewModel.isComplete
    val isLoadingOnboarding by onboardingViewModel.isLoading

    println("HomeScreen Check:")
    println("    isOnboardingComplete: $isOnboardingComplete")
    println("    isLoadingOnboarding: $isLoadingOnboarding")
    println("    Should show onboarding: ${!isOnboardingComplete && !isLoadingOnboarding}")

    if (!isOnboardingComplete && !isLoadingOnboarding) {
        println("   SHOWING OnboardingScreen")
        OnboardingScreen(
            viewModel = onboardingViewModel,
            onComplete = {
                viewModel.refreshUserData()
            },
            modifier = modifier,
        )
        return
    } else {
        println("   SKIPPING OnboardingScreen - showing HomeScreen")
    }

    val user by viewModel.user
    val navigationItems by viewModel.navigationItems
    val learningActivities by viewModel.learningActivities
    val isLoading by viewModel.isLoading
    val selectedNavigationItem by viewModel.selectedNavigationItem
    val showProfile by viewModel.showProfile
    val selectedLessonId by viewModel.selectedLessonId

    val (isSidebarExpanded, setSidebarExpanded) = remember { mutableStateOf(true) }

    val displayUser =
        authenticatedUser?.let { authUser ->
            org.example.project.domain.model.User(
                id = authUser.id,
                name = authUser.fullName,
                level = "Beginner Level", // Default level, could be enhanced later
                streak = 0, // Default streak, could be enhanced later
                xpPoints = 0, // Default XP, could be enhanced later
                wordsLearned = 0, // Default words, could be enhanced later
                accuracy = 0, // Default accuracy, could be enhanced later
                avatarInitials = authUser.initials,
                profileImageUrl = authUser.profileImageUrl,
            )
        } ?: user

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(WordBridgeColors.BackgroundLight),
    ) {
        Sidebar(
            navigationItems = navigationItems,
            onNavigationItemClick = viewModel::onNavigationItemSelected,
            isExpanded = isSidebarExpanded,
            onToggleExpand = { setSidebarExpanded(!isSidebarExpanded) },
        )

        when {
            selectedLessonId != null -> {
                Box(modifier = Modifier.weight(1f)) {
                    LessonPlayerScreen(
                        lessonId = selectedLessonId!!,
                        userId = authenticatedUser?.id ?: "",
                        onBack = viewModel::onCloseLessonPlayer,
                    )
                }
            }
            showProfile -> {
                ProfileScreen(
                    authenticatedUser = authenticatedUser,
                    modifier = Modifier.weight(1f),
                )
            }
            selectedNavigationItem == "home" || selectedNavigationItem.isEmpty() -> {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    HomeHeader(
                        userName = displayUser.name,
                        userLevel = displayUser.level,
                        userInitials = displayUser.avatarInitials,
                        userProfileImageUrl = displayUser.profileImageUrl,
                        onUserAvatarClick = viewModel::onUserAvatarClicked,
                        authenticatedUser = authenticatedUser,
                        onSignOut = onSignOut,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    ContinueLearningCard(
                        onButtonClick = viewModel::onContinueLearningClicked,
                        isLoading = isLoading,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (learningActivities.isEmpty()) {
                        HomeEmptyState(
                            onGetStartedClick = {
                                viewModel.onNavigationItemSelected("lessons")
                            },
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(2f),
                            ) {
                                learningActivities.forEach { activity ->
                                    LearningActivityCard(
                                        activity = activity,
                                        onClick = { viewModel.onLearningActivityClicked(activity.id) },
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                TodaysProgressCard(
                                    streak = user.streak,
                                    xpPoints = user.xpPoints,
                                    wordsLearned = user.wordsLearned,
                                    accuracy = user.accuracy,
                                )
                            }
                        }
                    }
                }
            }
            selectedNavigationItem == "lessons" -> {
                LessonsScreen(
                    authenticatedUser = authenticatedUser,
                    onUserAvatarClick = viewModel::onUserAvatarClicked,
                    onLessonSelected = viewModel::onLessonSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            selectedNavigationItem == "vocabulary" -> {
                val lessonsViewModel: LessonsViewModel = viewModel()
                LaunchedEffect(authenticatedUser) {
                    if (authenticatedUser != null) {
                        lessonsViewModel.initializeWithAuthenticatedUser(authenticatedUser)
                    }
                }
                VocabularyScreen(
                    authenticatedUser = authenticatedUser,
                    onUserAvatarClick = viewModel::onUserAvatarClicked,
                    lessonsViewModel = lessonsViewModel,
                    modifier = Modifier.weight(1f),
                )
            }
            selectedNavigationItem == "speaking" -> {
                SpeakingScreen(
                    authenticatedUser = authenticatedUser,
                    onUserAvatarClick = viewModel::onUserAvatarClicked,
                    modifier = Modifier.weight(1f),
                )
            }
            selectedNavigationItem == "ai_chat" -> {
                AIChatScreen(
                    authenticatedUser = authenticatedUser,
                    onUserAvatarClick = viewModel::onUserAvatarClicked,
                    modifier = Modifier.weight(1f),
                )
            }
            selectedNavigationItem == "progress" -> {
                ProgressScreen(
                    authenticatedUser = authenticatedUser,
                    onUserAvatarClick = viewModel::onUserAvatarClicked,
                    modifier = Modifier.weight(1f),
                )
            }
            selectedNavigationItem == "settings" -> {
                SettingsScreen(
                    modifier = Modifier.weight(1f),
                )
            }
            else -> {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Page not found",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    userName: String,
    userLevel: String,
    userInitials: String,
    userProfileImageUrl: String?,
    onUserAvatarClick: () -> Unit,
    authenticatedUser: AuthUser? = null,
    onSignOut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Welcome Back, $userName!",
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = userLevel,
                style = MaterialTheme.typography.bodyLarge,
                color = WordBridgeColors.TextSecondary,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (authenticatedUser != null && onSignOut != null) {
                TextButton(
                    onClick = onSignOut,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = WordBridgeColors.TextSecondary,
                        ),
                ) {
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
            }

            UserAvatar(
                initials = userInitials,
                profileImageUrl = userProfileImageUrl,
                size = 48.dp,
                onClick = onUserAvatarClick,
            )
        }
    }
}
