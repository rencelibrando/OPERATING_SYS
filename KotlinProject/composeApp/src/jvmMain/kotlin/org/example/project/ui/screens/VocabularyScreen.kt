package org.example.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.VocabularyViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.core.auth.User as AuthUser

@Composable
fun VocabularyScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    viewModel: VocabularyViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val vocabularyWords by viewModel.vocabularyWords
    val vocabularyStats by viewModel.vocabularyStats
    val vocabularyFeatures by viewModel.vocabularyFeatures
    val searchQuery by viewModel.searchQuery
    val selectedFilter by viewModel.selectedFilter
    val filteredWords by viewModel.filteredWords
    val isLoading by viewModel.isLoading
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vocabulary Bank",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            UserAvatar(
                initials = authenticatedUser?.initials ?: "U",
                profileImageUrl = authenticatedUser?.profileImageUrl,
                size = 48.dp,
                onClick = onUserAvatarClick
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VocabularyStatsCard(
                title = "Total Words",
                count = vocabularyStats.totalWords,
                icon = "📚",
                backgroundColor = Color(0xFF8B5CF6), // Purple
                modifier = Modifier.weight(1f)
            )
            
            VocabularyStatsCard(
                title = "Mastered",
                count = vocabularyStats.masteredWords,
                icon = "✅",
                backgroundColor = Color(0xFF10B981), // Green
                modifier = Modifier.weight(1f)
            )
            
            VocabularyStatsCard(
                title = "Learning",
                count = vocabularyStats.learningWords,
                icon = "🎯",
                backgroundColor = Color(0xFFF59E0B), // Orange
                modifier = Modifier.weight(1f)
            )
            
            VocabularyStatsCard(
                title = "Need Review",
                count = vocabularyStats.needReviewWords,
                icon = "🔄",
                backgroundColor = Color(0xFFEF4444), // Red
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (vocabularyStats.totalWords == 0) {
            VocabularyEmptyState(
                features = vocabularyFeatures,
                onAddFirstWordClick = viewModel::onAddFirstWordClicked,
                onExploreLessonsClick = viewModel::onExploreLessonsClicked
            )
        } else {
            VocabularySearchBar(
                searchQuery = searchQuery,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                selectedFilter = selectedFilter,
                onFilterSelected = viewModel::onFilterSelected,
                onAddWordClick = viewModel::onAddWordClicked
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (filteredWords.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    filteredWords.forEach { word ->
                        Text(
                            text = "${word.word} - ${word.definition}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextPrimary
                        )
                    }
                }
            } else if (searchQuery.isNotEmpty() || selectedFilter.name != "ALL") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔍",
                        style = MaterialTheme.typography.displaySmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "No vocabulary words found",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                    
                    Text(
                        text = "Try adjusting your search or filter criteria",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary
                    )
                }
            }
        }
    }
}
