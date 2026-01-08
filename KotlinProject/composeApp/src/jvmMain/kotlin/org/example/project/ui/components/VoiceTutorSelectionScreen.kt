package org.example.project.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.domain.model.PracticeLanguage
import org.example.project.ui.theme.WordBridgeColors

data class VoiceTutorLevel(
    val id: String,
    val name: String,
    val description: String,
    val icon: String = "",
)

data class VoiceTutorScenario(
    val id: String,
    val name: String,
    val description: String,
    val icon: String = "",
)

@Composable
fun VoiceTutorSelectionFlow(
    onStartPractice: (language: PracticeLanguage, level: String, scenario: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStep by remember { mutableStateOf(SelectionStep.LANGUAGE) }
    var selectedLanguage by remember { mutableStateOf<PracticeLanguage?>(null) }
    var selectedLevel by remember { mutableStateOf<VoiceTutorLevel?>(null) }
    var selectedScenario by remember { mutableStateOf<VoiceTutorScenario?>(null) }

    var isPressed by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color(0xFF0F0F23).copy(alpha = 0.95f) // Softer, warmer background
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        awaitRelease()
                        isPressed = false
                    },
                )
            },
    ) {
        // Soft Header with modern elevated design
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1B2E).copy(alpha = 0.9f),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Modern back button with soft feel
                IconButton(
                    onClick = {
                        when (currentStep) {
                            SelectionStep.LANGUAGE -> onBack()
                            SelectionStep.LEVEL -> currentStep = SelectionStep.LANGUAGE
                            SelectionStep.SCENARIO -> currentStep = SelectionStep.LEVEL
                        }
                    },
                    modifier = Modifier
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            spotColor = Color(0xFF8B5CF6).copy(alpha = 0.4f)
                        )
                        .background(
                            Color(0xFF8B5CF6).copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Modern title with gradient feel
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Voice Tutor",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                    )
                    Text(
                        text = "Your personal speaking coach",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                
                // Spacer for balance
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress indicator
        ProgressIndicator(
            currentStep = currentStep,
            selectedLanguage = selectedLanguage,
            selectedLevel = selectedLevel,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Content based on current step
        when (currentStep) {
            SelectionStep.LANGUAGE -> {
                LanguageSelection(
                    onLanguageSelected = { language ->
                        selectedLanguage = language
                        currentStep = SelectionStep.LEVEL
                    },
                )
            }
            SelectionStep.LEVEL -> {
                LevelSelection(
                    language = selectedLanguage!!,
                    onLevelSelected = { level ->
                        selectedLevel = level
                        currentStep = SelectionStep.SCENARIO
                    },
                )
            }
            SelectionStep.SCENARIO -> {
                ScenarioSelection(
                    language = selectedLanguage!!,
                    level = selectedLevel!!,
                    onScenarioSelected = { scenario ->
                        selectedScenario = scenario
                        onStartPractice(selectedLanguage!!, selectedLevel!!.id, scenario.id)
                    },
                )
            }
        }
    }
}

enum class SelectionStep {
    LANGUAGE,
    LEVEL,
    SCENARIO,
}

@Composable
private fun ProgressIndicator(
    currentStep: SelectionStep,
    selectedLanguage: PracticeLanguage?,
    selectedLevel: VoiceTutorLevel?,
    modifier: Modifier = Modifier,
) {
    // Modern elevated progress container
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1B2E).copy(alpha = 0.8f),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressStep(
                number = 1,
                label = "Language",
                isActive = currentStep == SelectionStep.LANGUAGE,
                isCompleted = selectedLanguage != null,
                modifier = Modifier.weight(1f),
            )

            ProgressDivider(isCompleted = selectedLanguage != null)

            ProgressStep(
                number = 2,
                label = "Level",
                isActive = currentStep == SelectionStep.LEVEL,
                isCompleted = selectedLevel != null,
                modifier = Modifier.weight(1f),
            )

            ProgressDivider(isCompleted = selectedLevel != null)

            ProgressStep(
                number = 3,
                label = "Scenario",
                isActive = currentStep == SelectionStep.SCENARIO,
                isCompleted = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProgressStep(
    number: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Soft, elevated step indicator with modern feel
        Surface(
            modifier = Modifier
                .size(48.dp)
                .scale(pulseScale)
                .shadow(
                    elevation = if (isActive || isCompleted) 8.dp else 2.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = if (isCompleted) 
                        Color(0xFF10B981).copy(alpha = 0.4f) 
                    else if (isActive) 
                        Color(0xFF8B5CF6).copy(alpha = 0.4f)
                    else 
                        Color.Transparent
                ),
            shape = RoundedCornerShape(24.dp),
            color = when {
                isCompleted -> Color(0xFF10B981)
                isActive -> Color(0xFF8B5CF6)
                else -> Color(0xFF374151).copy(alpha = 0.6f)
            }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = when {
                isActive -> Color.White.copy(alpha = 0.95f)
                isCompleted -> Color(0xFF10B981)
                else -> Color.White.copy(alpha = 0.5f)
            },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProgressDivider(
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isCompleted) {
        Box(
            modifier = modifier
                .width(50.dp)
                .height(3.dp)
                .background(
                    color = Color(0xFF10B981).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    } else {
        val infiniteTransition = rememberInfiniteTransition()
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Restart
            )
        )
        
        Box(
            modifier = modifier
                .width(50.dp)
                .height(3.dp)
                .background(
                    color = Color(0xFF374151).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun LanguageSelection(
    onLanguageSelected: (PracticeLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Modern elevated header
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E1B2E).copy(alpha = 0.8f),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Choose Your Language",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Color.White.copy(alpha = 0.95f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select the language you want to practice speaking",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }

        Spacer(modifier = Modifier.height(24.dp))

        val languages =
            listOf(
                PracticeLanguage.ENGLISH,
                PracticeLanguage.FRENCH,
                PracticeLanguage.GERMAN,
                PracticeLanguage.HANGEUL,
                PracticeLanguage.MANDARIN,
                PracticeLanguage.SPANISH,
            )

        languages.forEach { language ->
            LanguageCard(
                language = language,
                onClick = { onLanguageSelected(language) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
}

@Composable
private fun LanguageCard(
    language: PracticeLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            awaitRelease()
                            isPressed = false
                            onClick()
                        },
                    )
                },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B2E).copy(alpha = 0.9f),
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 8.dp else 4.dp
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Subtle shimmer effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startX = shimmerOffset - 200f,
                            endX = shimmerOffset + 200f
                        )
                    )
            )
            
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Modern language indicator with soft gradient
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                    modifier = Modifier
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            spotColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                        )
                ) {
                    Text(
                        text = language.displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = language.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Practice speaking in ${language.displayName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                // Modern chevron icon
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Select",
                    tint = Color(0xFF8B5CF6).copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun LevelSelection(
    language: PracticeLanguage,
    onLevelSelected: (VoiceTutorLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Modern elevated header
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E1B2E).copy(alpha = 0.8f),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Choose Your Level",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Color.White.copy(alpha = 0.95f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select your ${language.displayName} proficiency level",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }

        Spacer(modifier = Modifier.height(24.dp))

        val levels =
            listOf(
                VoiceTutorLevel(
                    id = "beginner",
                    name = "Beginner",
                    description = "Just starting out. Basic phrases and simple conversations.",
                ),
                VoiceTutorLevel(
                    id = "intermediate",
                    name = "Intermediate",
                    description = "Some experience. Can handle common situations.",
                ),
                VoiceTutorLevel(
                    id = "advanced",
                    name = "Advanced",
                    description = "Fluent speaker. Complex topics and nuanced discussions.",
                ),
            )

        levels.forEach { level ->
            LevelCard(
                level = level,
                onClick = { onLevelSelected(level) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
}

@Composable
private fun LevelCard(
    level: VoiceTutorLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val levelColor =
        when (level.id) {
            "beginner" -> Color(0xFF34D399)
            "intermediate" -> Color(0xFFF59E0B)
            "advanced" -> Color(0xFF8B5CF6)
            else -> Color(0xFF6B7280)
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            awaitRelease()
                            isPressed = false
                            onClick()
                        },
                    )
                },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B2E).copy(alpha = 0.9f),
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 8.dp else 4.dp
        ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Modern level indicator with soft gradient
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = levelColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = levelColor.copy(alpha = 0.3f)
                    )
            ) {
                Text(
                    text = level.name.take(1),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = levelColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = level.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    ),
                    color = Color.White.copy(alpha = 0.95f),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = level.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 18.sp,
                )
            }

            // Modern chevron icon
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Select",
                tint = levelColor.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ScenarioSelection(
    language: PracticeLanguage,
    level: VoiceTutorLevel,
    onScenarioSelected: (VoiceTutorScenario) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Modern elevated header
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E1B2E).copy(alpha = 0.8f),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Choose a Scenario",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Color.White.copy(alpha = 0.95f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick a topic to practice in ${language.displayName}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }

        Spacer(modifier = Modifier.height(24.dp))

        val scenarios =
            listOf(
                VoiceTutorScenario(
                    id = "travel",
                    name = "Travel",
                    description = "Hotels, directions, transportation, sightseeing",
                ),
                VoiceTutorScenario(
                    id = "food",
                    name = "Food & Dining",
                    description = "Ordering food, restaurants, recipes, cooking",
                ),
                VoiceTutorScenario(
                    id = "daily_conversation",
                    name = "Daily Conversation",
                    description = "Greetings, small talk, weather, hobbies",
                ),
                VoiceTutorScenario(
                    id = "work",
                    name = "Work & Business",
                    description = "Meetings, emails, presentations, negotiations",
                ),
                VoiceTutorScenario(
                    id = "culture",
                    name = "Culture & Traditions",
                    description = "History, customs, festivals, local traditions",
                ),
            )

        scenarios.forEach { scenario ->
            ScenarioCard(
                scenario = scenario,
                onClick = { onScenarioSelected(scenario) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
}

@Composable
private fun ScenarioCard(
    scenario: VoiceTutorScenario,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scenarioColor =
        when (scenario.id) {
            "travel" -> Color(0xFF3B82F6)
            "food" -> Color(0xFFF59E0B)
            "daily_conversation" -> Color(0xFF34D399)
            "work" -> Color(0xFF8B5CF6)
            "culture" -> Color(0xFFEC4899)
            else -> Color(0xFF6B7280)
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            awaitRelease()
                            isPressed = false
                            onClick()
                        },
                    )
                },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B2E).copy(alpha = 0.9f),
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 8.dp else 4.dp
        ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Modern scenario indicator with soft gradient
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = scenarioColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = scenarioColor.copy(alpha = 0.3f)
                    )
            ) {
                Text(
                    text = scenario.name.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = scenarioColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scenario.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    ),
                    color = Color.White.copy(alpha = 0.95f),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = scenario.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 18.sp,
                )
            }

            // Modern chevron icon
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Select",
                tint = scenarioColor.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
