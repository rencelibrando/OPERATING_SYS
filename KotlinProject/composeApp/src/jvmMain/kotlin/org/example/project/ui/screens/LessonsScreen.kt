package org.example.project.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.LessonsViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.core.auth.User as AuthUser
import org.example.project.domain.model.LessonDifficulty
import org.example.project.domain.model.LessonLanguage

@Composable
fun LessonsScreen(
    authenticatedUser: AuthUser? = null,
    onUserAvatarClick: (() -> Unit)? = null,
    onLessonSelected: ((String) -> Unit)? = null,
    viewModel: LessonsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(authenticatedUser) {
        if (authenticatedUser != null) {
            viewModel.initializeWithAuthenticatedUser(authenticatedUser)
        }
    }

    val lessonCategories by viewModel.lessonCategories
    val selectedCategory by viewModel.selectedCategory
    val categoryLessons by viewModel.categoryLessons
    val lessonTopics by viewModel.lessonTopics
    val recentLessons by viewModel.recentLessons
    val selectedLanguage by viewModel.selectedLanguage
    val availableLanguages by viewModel.availableLanguages
    val isLanguageChanging by viewModel.isLanguageChanging
    val selectedTopicForLessons by viewModel.selectedTopicForLessons
    val topicLessons by viewModel.topicLessons
    val isLoadingLessons by viewModel.isLoadingLessons

    // Show lesson list if a topic is selected
    if (selectedTopicForLessons != null) {
        LessonListView(
            topic = selectedTopicForLessons!!,
            lessons = topicLessons,
            isLoading = isLoadingLessons,
            selectedLanguage = selectedLanguage,
            availableLanguages = availableLanguages,
            isLanguageChanging = isLanguageChanging,
            authenticatedUser = authenticatedUser,
            onBack = viewModel::onBackFromLessonList,
            onLessonClick = { lessonId ->
                onLessonSelected?.invoke(lessonId)
            },
            onUserAvatarClick = onUserAvatarClick,
            onLanguageSelected = viewModel::changeLanguage,
            modifier = modifier
        )
    }
    // Use LazyColumn when showing topics to avoid nested scroll issues
    else if (selectedCategory != null && lessonTopics.isNotEmpty()) {
        val listState = rememberLazyListState()
        
        // Smooth scroll progress calculation - slower and more gradual
        val density = LocalDensity.current
        val scrollProgress = remember(density) { derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            
            if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0 || lessonTopics.isEmpty()) {
                return@derivedStateOf 0f
            }
            
            // Find the timeline item (should be at index 4 after header, spacer, intro, spacer)
            val timelineItem = visibleItems.find { it.index == 4 }
            
            if (timelineItem != null) {
                // Calculate how much of the timeline has been scrolled (offset is already in pixels)
                val timelineOffset = -timelineItem.offset.toFloat()
                
                // Each lesson is ~280dp tall (bigger cards) - convert to pixels for accurate calculation
                val lessonHeightPx = with(density) { 280.dp.toPx() }
                
                // Account for centered node positioning (120dp from top)
                val nodeCenterOffsetPx = with(density) { 120.dp.toPx() }
                
                // Calculate the total height needed to reach the last node's center
                // Last node is at position: (lessonTopics.size - 1) * 280dp + 120dp
                val lastNodePositionPx = (lessonTopics.size - 1) * lessonHeightPx + nodeCenterOffsetPx
                // Add buffer to ensure line reaches the last node
                val totalTimelineHeight = lastNodePositionPx + with(density) { 200.dp.toPx() }
                
                // Calculate raw progress (0 to 1) - immediate response, no easing delay
                // Adjust buffer to account for centered node positioning
                val rawProgress = ((timelineOffset + nodeCenterOffsetPx) / totalTimelineHeight).coerceIn(0f, 1f)
                
                // Use linear progress for immediate visual feedback - no easing delay
                rawProgress
            } else {
                // Not yet scrolled to timeline
                0f
            }
        } }
        
        // Track which lesson item is currently visible for pop-up animation
        val visibleItemIndex = remember { derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            
            if (visibleItems.isEmpty()) {
                return@derivedStateOf 0
            }
            
            val timelineItem = visibleItems.find { it.index == 4 }
            
            if (timelineItem != null) {
                val timelineOffset = -timelineItem.offset.toFloat()
                val lessonHeightPx = with(density) { 280.dp.toPx() }
                val currentLessonIndex = (timelineOffset / lessonHeightPx).toInt().coerceIn(0, lessonTopics.size - 1)
                currentLessonIndex
            } else {
                0
            }
        } }
        
        LazyColumn(
            state = listState,
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "←",
                            style =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = WordBridgeColors.TextPrimary,
                            modifier =
                                Modifier
                                    .clickable { viewModel.onBackFromCategory() }
                                    .padding(8.dp),
                        )

                        Text(
                            text = "${selectedLanguage.displayName} ${selectedCategory!!.displayName} Lessons",
                            style =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = WordBridgeColors.TextPrimary,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Language Switcher
                        LessonLanguageSwitcher(
                            selectedLanguage = selectedLanguage,
                            availableLanguages = availableLanguages,
                            onLanguageSelected = { language ->
                                viewModel.changeLanguage(language)
                            },
                            enabled = !isLanguageChanging,
                        )
                        
                        UserAvatar(
                            initials = authenticatedUser?.initials ?: "U",
                            profileImageUrl = authenticatedUser?.profileImageUrl,
                            size = 48.dp,
                            onClick = onUserAvatarClick,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Introduction text - dynamic based on selected language
            item {
                Text(
                    text = "Your ${selectedLanguage.displayName} learning journey. We've also included valuable learning tips. Enjoy!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WordBridgeColors.TextSecondary,
                )
            }
            
            // Show loading indicator when changing language
            if (isLanguageChanging) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = WordBridgeColors.PrimaryPurple,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Switching to ${selectedLanguage.displayName}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WordBridgeColors.TextSecondary,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Lesson topics - Use timeline view for all learning paths
            item {
                LessonTimelineView(
                    lessonTopics = lessonTopics,
                    onLessonClick = { lessonId -> viewModel.onLessonTopicClicked(lessonId) },
                    scrollProgress = scrollProgress.value,
                    visibleItemIndex = visibleItemIndex.value
                )
            }
        }
    } else {
        // Use Column with verticalScroll for category selection view
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selectedCategory != null) {
                        Text(
                            text = "←",
                            style =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = WordBridgeColors.TextPrimary,
                            modifier =
                                Modifier
                                    .clickable { viewModel.onBackFromCategory() }
                                    .padding(8.dp),
                        )
                    }

                    Text(
                        text =
                            if (selectedCategory != null) {
                                "${selectedLanguage.displayName} ${selectedCategory!!.displayName} Lessons"
                            } else {
                                "Lessons - Learning Paths"
                            },
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Language Switcher - Main Feature
                    LessonLanguageSwitcher(
                        selectedLanguage = selectedLanguage,
                        availableLanguages = availableLanguages,
                        onLanguageSelected = { language ->
                            viewModel.changeLanguage(language)
                        },
                        enabled = !isLanguageChanging,
                    )
                    
                    UserAvatar(
                        initials = authenticatedUser?.initials ?: "U",
                        profileImageUrl = authenticatedUser?.profileImageUrl,
                        size = 48.dp,
                        onClick = onUserAvatarClick,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedCategory == null) {
                Text(
                    text = "Select a path to continue your personalized language learning journey.",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = WordBridgeColors.TextPrimary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    lessonCategories.forEach { category ->
                        LessonCategoryCard(
                            categoryInfo = category,
                            onClick = {
                                if (!category.isLocked) {
                                    viewModel.onCategoryClicked(category.difficulty)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                // Show introduction text for the category (empty state) - dynamic based on language
                Text(
                    text = "Your ${selectedLanguage.displayName} learning journey. We've also included valuable learning tips. Enjoy!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WordBridgeColors.TextSecondary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Show empty state when no topics
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "📚",
                        style = MaterialTheme.typography.displayMedium,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No lesson topics available yet",
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = WordBridgeColors.TextPrimary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text =
                            "Lesson topics for this category will be available soon. Check back later!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonListView(
    topic: org.example.project.domain.model.LessonTopic,
    lessons: List<org.example.project.domain.model.LessonSummary>,
    isLoading: Boolean,
    selectedLanguage: LessonLanguage,
    availableLanguages: List<LessonLanguage>,
    isLanguageChanging: Boolean,
    authenticatedUser: AuthUser?,
    onBack: () -> Unit,
    onLessonClick: (String) -> Unit,
    onUserAvatarClick: (() -> Unit)?,
    onLanguageSelected: (LessonLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "←",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimary,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(8.dp)
                )

                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LessonLanguageSwitcher(
                    selectedLanguage = selectedLanguage,
                    availableLanguages = availableLanguages,
                    onLanguageSelected = onLanguageSelected,
                    enabled = !isLanguageChanging
                )

                UserAvatar(
                    initials = authenticatedUser?.initials ?: "U",
                    profileImageUrl = authenticatedUser?.profileImageUrl,
                    size = 48.dp,
                    onClick = onUserAvatarClick
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Topic description
        if (topic.description.isNotBlank()) {
            Text(
                text = topic.description,
                style = MaterialTheme.typography.bodyLarge,
                color = WordBridgeColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Loading state
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = WordBridgeColors.PrimaryPurple,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Loading lessons...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary
                )
            }
        }
        // Empty state
        else if (lessons.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📝",
                    style = MaterialTheme.typography.displayMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No lessons available yet",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Lessons for this topic will be available soon. Check back later!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary
                )
            }
        }
        // Lesson list
        else {
            Text(
                text = "${lessons.size} Lesson${if (lessons.size != 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            lessons.forEach { lesson ->
                LessonCard(
                    lesson = lesson,
                    onClick = { onLessonClick(lesson.id) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LessonCard(
    lesson: org.example.project.domain.model.LessonSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = WordBridgeColors.CardBackground
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )

                if (!lesson.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lesson.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WordBridgeColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "${lesson.questionCount} questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary
                    )

                    if (lesson.isPublished) {
                        Text(
                            text = "• Published",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.AccentGreen
                        )
                    }
                }
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.PrimaryPurple
            )
        }
    }
}