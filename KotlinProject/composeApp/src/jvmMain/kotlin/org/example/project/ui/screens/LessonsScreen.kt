package org.example.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.LessonsViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors


@Composable
fun LessonsScreen(
    viewModel: LessonsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val lessons by viewModel.lessons
    val levelProgress by viewModel.levelProgress
    val recentLessons by viewModel.recentLessons
    val isLoading by viewModel.isLoading
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header with title and user info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lessons",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            // User info placeholder - can be expanded later
            UserAvatar(
                initials = "SC", // This should come from user data
                size = 40.dp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Content based on whether user has lessons
        if (lessons.isEmpty()) {
            // Empty state
            LessonsEmptyState(
                onCreateFirstLessonClick = {
                    // TODO: Navigate to lesson creation or show lesson setup
                    viewModel.onLessonClicked("create_first")
                },
                onExploreCurriculumClick = {
                    // TODO: Navigate to curriculum templates
                    viewModel.onLessonClicked("explore_curriculum")
                }
            )
        } else {
            // Level Progress Banner
            LevelProgressBanner(
                levelProgress = levelProgress
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Lessons Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(400.dp) // Fixed height to prevent scroll conflicts
            ) {
                items(lessons) { lesson ->
                    LessonCard(
                        lesson = lesson,
                        onContinueClick = viewModel::onContinueLessonClicked,
                        onStartClick = viewModel::onStartLessonClicked
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Continue Recent Lessons Section (only if there are recent lessons)
            if (recentLessons.isNotEmpty()) {
                Text(
                    text = "Continue Recent Lessons",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Recent lessons list
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentLessons.forEach { recentLesson ->
                        RecentLessonCard(
                            recentLesson = recentLesson,
                            onClick = viewModel::onRecentLessonClicked
                        )
                    }
                }
            }
        }
    }
}
