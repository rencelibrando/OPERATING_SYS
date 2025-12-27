package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.domain.model.SkillArea
import org.example.project.presentation.viewmodel.ProgressViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.core.auth.User as AuthUser

@Composable
fun ProgressScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    viewModel: ProgressViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val learningProgress by viewModel.learningProgress
    val achievements by viewModel.achievements
    val learningGoals by viewModel.learningGoals
    val weeklyProgressData by viewModel.weeklyProgressData
    val activeGoals by viewModel.activeGoals
    val unlockedAchievements by viewModel.unlockedAchievements
    val isLoading by viewModel.isLoading

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Progress Tracker",
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            UserAvatar(
                initials = authenticatedUser?.initials ?: "U",
                profileImageUrl = authenticatedUser?.profileImageUrl,
                size = 48.dp,
                onClick = onUserAvatarClick,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (learningProgress.xpPoints == 0 && achievements.isEmpty() && learningGoals.isEmpty()) {
            ProgressEmptyState(
                onSetFirstGoalClick = viewModel::onSetFirstGoalClicked,
                onExploreAchievementsClick = viewModel::onExploreAchievementsClicked,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProgressStatsCard(
                    title = "Overall Level",
                    value = learningProgress.overallLevel.toString(),
                    icon = "🎯",
                    backgroundColor = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f),
                )

                ProgressStatsCard(
                    title = "Total XP",
                    value = learningProgress.xpPoints.toString(),
                    icon = "⭐",
                    backgroundColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                )

                ProgressStatsCard(
                    title = "Current Streak",
                    value = learningProgress.streakDays.toString(),
                    unit = "days",
                    icon = "🔥",
                    backgroundColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f),
                )

                ProgressStatsCard(
                    title = "Study Time",
                    value = "${learningProgress.totalStudyTime / 60}",
                    unit = "hrs",
                    icon = "⏱️",
                    backgroundColor = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "This Week's Progress",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            WeeklyProgressChart(
                dailyXP = weeklyProgressData.dailyXP,
                dailyMinutes = weeklyProgressData.dailyMinutes,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Skills Progress",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(200.dp),
            ) {
                items(learningProgress.skillLevels.toList()) { (skill, progress) ->
                    SkillProgressCard(
                        skill = skill,
                        progress = progress,
                        onClick = { viewModel.onSkillSelected(skill) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (activeGoals.isNotEmpty()) {
                Text(
                    text = "Current Goals",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    activeGoals.take(3).forEach { goal ->
                        GoalProgressCard(
                            goal = goal,
                            onClick = { viewModel.onGoalClicked(goal.id) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            if (unlockedAchievements.isNotEmpty()) {
                Text(
                    text = "Recent Achievements",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(unlockedAchievements.take(5)) { achievement ->
                        AchievementCard(
                            achievement = achievement,
                            onClick = { viewModel.onAchievementClicked(achievement.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressEmptyState(
    onSetFirstGoalClick: () -> Unit,
    onExploreAchievementsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "📊",
            style = MaterialTheme.typography.displayMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Track Your Learning Journey",
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Start your learning adventure and watch your progress grow! Set goals, earn achievements, and see your improvement over time with detailed analytics and insights.",
            style = MaterialTheme.typography.bodyLarge,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSetFirstGoalClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = WordBridgeColors.PrimaryPurple,
                ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = "🎯 Set Your First Goal",
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = Color.White,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "or ",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )

            TextButton(
                onClick = onExploreAchievementsClick,
            ) {
                Text(
                    text = "explore achievements to earn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.PrimaryPurple,
                )
            }
        }
    }
}

@Composable
private fun ProgressStatsCard(
    title: String,
    value: String,
    unit: String? = null,
    icon: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.BackgroundWhite,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )

                if (unit != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary,
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun WeeklyProgressChart(
    dailyXP: List<Int>,
    dailyMinutes: List<Int>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WordBridgeColors.BackgroundWhite),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Text(
                text = "📈 Weekly XP Chart Placeholder",
                style = MaterialTheme.typography.titleMedium,
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Chart visualization would go here showing daily XP and study time",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun SkillProgressCard(
    skill: SkillArea,
    progress: org.example.project.domain.model.SkillProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = skill.icon, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Lv.${progress.level}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = skill.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun GoalProgressCard(
    goal: org.example.project.domain.model.LearningGoal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${goal.current}/${goal.target} ${goal.unit}",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: org.example.project.domain.model.Achievement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(120.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = achievement.icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
        }
    }
}
