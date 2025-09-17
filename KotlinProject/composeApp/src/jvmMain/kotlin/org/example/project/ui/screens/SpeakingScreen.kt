package org.example.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.SpeakingViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors

/**
 * Speaking Practice screen of the WordBridge application
 * 
 * Displays speaking exercises, statistics, sessions, and empty state
 */
@Composable
fun SpeakingScreen(
    viewModel: SpeakingViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val speakingExercises by viewModel.speakingExercises
    val speakingSessions by viewModel.speakingSessions
    val speakingStats by viewModel.speakingStats
    val speakingFeatures by viewModel.speakingFeatures
    val filteredExercises by viewModel.filteredExercises
    val selectedFilter by viewModel.selectedFilter
    val isRecording by viewModel.isRecording
    val currentSession by viewModel.currentSession
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
                text = "Speaking Practice",
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
        
        // Content based on whether user has speaking exercises
        if (speakingExercises.isEmpty()) {
            // Empty state
            SpeakingEmptyState(
                features = speakingFeatures,
                onStartFirstPracticeClick = viewModel::onStartFirstPracticeClicked,
                onExploreExercisesClick = viewModel::onExploreExercisesClicked
            )
        } else {
            // Statistics Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SpeakingStatsCard(
                    title = "Total Sessions",
                    value = speakingStats.totalSessions.toString(),
                    icon = "🎯",
                    backgroundColor = Color(0xFF8B5CF6), // Purple
                    modifier = Modifier.weight(1f)
                )
                
                SpeakingStatsCard(
                    title = "Practice Time",
                    value = speakingStats.totalMinutes.toString(),
                    unit = "mins",
                    icon = "⏱️",
                    backgroundColor = Color(0xFF10B981), // Green
                    modifier = Modifier.weight(1f)
                )
                
                SpeakingStatsCard(
                    title = "Average Score",
                    value = if (speakingStats.totalSessions > 0) {
                        "${(speakingStats.averageAccuracy + speakingStats.averageFluency + speakingStats.averagePronunciation) / 3}"
                    } else {
                        "0"
                    },
                    unit = "%",
                    icon = "📊",
                    backgroundColor = Color(0xFFF59E0B), // Orange
                    modifier = Modifier.weight(1f)
                )
                
                SpeakingStatsCard(
                    title = "Current Streak",
                    value = speakingStats.currentStreak.toString(),
                    unit = "days",
                    icon = "🔥",
                    backgroundColor = Color(0xFFEF4444), // Red
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Filter buttons for exercises
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                org.example.project.domain.model.SpeakingFilter.values().forEach { filter ->
                    FilterChip(
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (selectedFilter == filter) FontWeight.Medium else FontWeight.Normal
                                )
                            )
                        },
                        selected = selectedFilter == filter
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Speaking Exercises Grid
            if (filteredExercises.isNotEmpty()) {
                Text(
                    text = "Speaking Exercises",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.height(400.dp) // Fixed height to prevent scroll conflicts
                ) {
                    items(filteredExercises) { exercise ->
                        SpeakingExerciseCard(
                            exercise = exercise,
                            onStartClick = viewModel::onStartExerciseClicked
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            } else if (selectedFilter != org.example.project.domain.model.SpeakingFilter.ALL) {
                // No exercises found for filter
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎯",
                        style = MaterialTheme.typography.displaySmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "No exercises found",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                    
                    Text(
                        text = "Try selecting a different category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary
                    )
                }
            }
            
            // Recent Sessions Section (only if there are sessions)
            if (speakingSessions.isNotEmpty()) {
                Text(
                    text = "Recent Sessions",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Sessions list
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    speakingSessions.take(5).forEach { session ->
                        // Find exercise title for session
                        val exerciseTitle = speakingExercises.find { it.id == session.exerciseId }?.title 
                            ?: "Unknown Exercise"
                        
                        SpeakingSessionCard(
                            session = session,
                            exerciseTitle = exerciseTitle,
                            onReviewClick = viewModel::onReviewSessionClicked
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // View all sessions button
                if (speakingSessions.size > 5) {
                    TextButton(
                        onClick = { /* TODO: Navigate to all sessions */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "View All Sessions (${speakingSessions.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.PrimaryPurple
                        )
                    }
                }
            }
        }
        
        // Current session overlay (if recording)
        if (currentSession != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFEF3E2) // Light orange
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎙️ Recording Session",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isRecording) "Recording in progress..." else "Session ready",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = viewModel::onMicrophoneToggle,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFFEF4444) else Color(0xFF10B981)
                            )
                        ) {
                            Text(
                                text = if (isRecording) "Stop Recording" else "Start Recording",
                                color = Color.White
                            )
                        }
                        
                        if (!isRecording && currentSession != null) {
                            Button(
                                onClick = viewModel::onCompleteSession,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WordBridgeColors.PrimaryPurple
                                )
                            ) {
                                Text("Complete Session", color = Color.White)
                            }
                            
                            OutlinedButton(
                                onClick = viewModel::onCancelSession
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = WordBridgeColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
