package org.example.project.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.domain.model.SkillArea
import org.example.project.presentation.viewmodel.ProgressViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons
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
                .background(WordBridgeColors.BackgroundLight)
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
                    icon = WordBridgeIcons.Trophy,
                    modifier = Modifier.weight(1f),
                )

                ProgressStatsCard(
                    title = "Total XP",
                    value = learningProgress.xpPoints.toString(),
                    icon = WordBridgeIcons.Star,
                    modifier = Modifier.weight(1f),
                )

                ProgressStatsCard(
                    title = "Current Streak",
                    value = learningProgress.streakDays.toString(),
                    unit = "days",
                    icon = WordBridgeIcons.Streak,
                    modifier = Modifier.weight(1f),
                )

                ProgressStatsCard(
                    title = "Study Time",
                    value = "${learningProgress.totalStudyTime / 60}",
                    unit = "hrs",
                    icon = WordBridgeIcons.Time,
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

        Icon(
            imageVector = WordBridgeIcons.Progress,
            contentDescription = null,
            tint = WordBridgeColors.TextSecondary,
            modifier = Modifier.size(48.dp),
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
            text = "Start your learning adventure and watch your progress grow. Set goals, earn achievements, and see your improvement over time with detailed analytics.",
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
                    containerColor = WordBridgeColors.Primary,
                    contentColor = WordBridgeColors.PrimaryForeground,
                ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Icon(
                    imageVector = WordBridgeIcons.Trophy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Set Your First Goal",
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = WordBridgeColors.TextPrimary,
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
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WordBridgeColors.BorderLight),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.Surface,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WordBridgeColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )

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
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WordBridgeColors.BorderLight),
        colors = CardDefaults.cardColors(containerColor = WordBridgeColors.Surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = WordBridgeIcons.Progress,
                    contentDescription = null,
                    tint = WordBridgeColors.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Weekly Activity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = WordBridgeColors.TextPrimary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Chart visualization showing daily XP and study time breakdown",
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
    val icon = when (skill) {
        SkillArea.VOCABULARY -> WordBridgeIcons.Vocabulary
        SkillArea.GRAMMAR -> WordBridgeIcons.Edit
        SkillArea.SPEAKING -> WordBridgeIcons.Speaking
        SkillArea.LISTENING -> WordBridgeIcons.Headphones
        SkillArea.READING -> WordBridgeIcons.Lessons
        SkillArea.WRITING -> WordBridgeIcons.Edit
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WordBridgeColors.BorderLight),
        colors = CardDefaults.cardColors(containerColor = WordBridgeColors.Surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WordBridgeColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Lv.${progress.level}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = WordBridgeColors.TextPrimary,
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WordBridgeColors.BorderSubtle),
        colors = CardDefaults.cardColors(containerColor = WordBridgeColors.CardBackground),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = WordBridgeColors.TextPrimary,
                )
                Text(
                    text = "${goal.current}/${goal.target} ${goal.unit}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = WordBridgeColors.Primary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val goalProgress = if (goal.target > 0) (goal.current.toFloat() / goal.target.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = goalProgress,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                color = WordBridgeColors.Primary,
                trackColor = WordBridgeColors.ProgressBackground,
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
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WordBridgeColors.BorderLight),
        colors = CardDefaults.cardColors(containerColor = WordBridgeColors.Surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = WordBridgeIcons.Trophy,
                contentDescription = null,
                tint = WordBridgeColors.TextPrimary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = WordBridgeColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
