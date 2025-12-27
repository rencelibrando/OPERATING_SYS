package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import org.example.project.models.PracticeLanguage
import org.example.project.ui.theme.WordBridgeColors

data class VoiceTutorLevel(
    val id: String,
    val name: String,
    val description: String,
    val icon: String = ""
)

data class VoiceTutorScenario(
    val id: String,
    val name: String,
    val description: String,
    val icon: String = ""
)

@Composable
fun VoiceTutorSelectionFlow(
    onStartPractice: (language: PracticeLanguage, level: String, scenario: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(SelectionStep.LANGUAGE) }
    var selectedLanguage by remember { mutableStateOf<PracticeLanguage?>(null) }
    var selectedLevel by remember { mutableStateOf<VoiceTutorLevel?>(null) }
    var selectedScenario by remember { mutableStateOf<VoiceTutorScenario?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WordBridgeColors.BackgroundLight)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                when (currentStep) {
                    SelectionStep.LANGUAGE -> onBack()
                    SelectionStep.LEVEL -> currentStep = SelectionStep.LANGUAGE
                    SelectionStep.SCENARIO -> currentStep = SelectionStep.LEVEL
                }
            }) {
                Text("← Back", color = WordBridgeColors.TextSecondary)
            }

            Text(
                text = "Voice Tutor",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress indicator
        ProgressIndicator(
            currentStep = currentStep,
            selectedLanguage = selectedLanguage,
            selectedLevel = selectedLevel
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Content based on current step
        when (currentStep) {
            SelectionStep.LANGUAGE -> {
                LanguageSelection(
                    onLanguageSelected = { language ->
                        selectedLanguage = language
                        currentStep = SelectionStep.LEVEL
                    }
                )
            }
            SelectionStep.LEVEL -> {
                LevelSelection(
                    language = selectedLanguage!!,
                    onLevelSelected = { level ->
                        selectedLevel = level
                        currentStep = SelectionStep.SCENARIO
                    }
                )
            }
            SelectionStep.SCENARIO -> {
                ScenarioSelection(
                    language = selectedLanguage!!,
                    level = selectedLevel!!,
                    onScenarioSelected = { scenario ->
                        selectedScenario = scenario
                        onStartPractice(selectedLanguage!!, selectedLevel!!.id, scenario.id)
                    }
                )
            }
        }
    }
}

enum class SelectionStep {
    LANGUAGE, LEVEL, SCENARIO
}

@Composable
private fun ProgressIndicator(
    currentStep: SelectionStep,
    selectedLanguage: PracticeLanguage?,
    selectedLevel: VoiceTutorLevel?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProgressStep(
            number = 1,
            label = "Language",
            isActive = currentStep == SelectionStep.LANGUAGE,
            isCompleted = selectedLanguage != null,
            modifier = Modifier.weight(1f)
        )

        ProgressDivider(isCompleted = selectedLanguage != null)

        ProgressStep(
            number = 2,
            label = "Level",
            isActive = currentStep == SelectionStep.LEVEL,
            isCompleted = selectedLevel != null,
            modifier = Modifier.weight(1f)
        )

        ProgressDivider(isCompleted = selectedLevel != null)

        ProgressStep(
            number = 3,
            label = "Scenario",
            isActive = currentStep == SelectionStep.SCENARIO,
            isCompleted = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProgressStep(
    number: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = when {
                        isCompleted -> Color(0xFF10B981)
                        isActive -> WordBridgeColors.PrimaryPurple
                        else -> Color(0xFFE5E7EB)
                    },
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCompleted) "✓" else number.toString(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isActive || isCompleted) Color.White else Color(0xFF9CA3AF)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) WordBridgeColors.TextPrimary else WordBridgeColors.TextSecondary,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ProgressDivider(
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(40.dp)
            .height(2.dp)
            .background(
                color = if (isCompleted) Color(0xFF10B981) else Color(0xFFE5E7EB)
            )
    )
}

@Composable
private fun LanguageSelection(
    onLanguageSelected: (PracticeLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Choose Your Language",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = WordBridgeColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Select the language you want to practice speaking",
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        val languages = listOf(
            PracticeLanguage.ENGLISH,
            PracticeLanguage.FRENCH,
            PracticeLanguage.GERMAN,
            PracticeLanguage.HANGEUL,
            PracticeLanguage.MANDARIN,
            PracticeLanguage.SPANISH
        )

        languages.forEach { language ->
            LanguageCard(
                language = language,
                onClick = { onLanguageSelected(language) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LanguageCard(
    language: PracticeLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language indicator badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f)
            ) {
                Text(
                    text = language.displayName.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = WordBridgeColors.PrimaryPurple,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = WordBridgeColors.TextPrimary
                )
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = WordBridgeColors.TextSecondary
            )
        }
    }
}

@Composable
private fun LevelSelection(
    language: PracticeLanguage,
    onLevelSelected: (VoiceTutorLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Choose Your Level",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = WordBridgeColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Select your ${language.displayName} proficiency level",
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        val levels = listOf(
            VoiceTutorLevel(
                id = "beginner",
                name = "Beginner",
                description = "Just starting out. Basic phrases and simple conversations."
            ),
            VoiceTutorLevel(
                id = "intermediate",
                name = "Intermediate",
                description = "Some experience. Can handle common situations."
            ),
            VoiceTutorLevel(
                id = "advanced",
                name = "Advanced",
                description = "Fluent speaker. Complex topics and nuanced discussions."
            )
        )

        levels.forEach { level ->
            LevelCard(
                level = level,
                onClick = { onLevelSelected(level) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LevelCard(
    level: VoiceTutorLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val levelColor = when (level.id) {
        "beginner" -> WordBridgeColors.AccentGreen
        "intermediate" -> WordBridgeColors.AccentOrange
        "advanced" -> WordBridgeColors.PrimaryPurple
        else -> WordBridgeColors.TextMuted
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level indicator badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = levelColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = level.name.take(1),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = levelColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = level.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = WordBridgeColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = level.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondary,
                    lineHeight = 18.sp
                )
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = WordBridgeColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ScenarioSelection(
    language: PracticeLanguage,
    level: VoiceTutorLevel,
    onScenarioSelected: (VoiceTutorScenario) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Choose a Scenario",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = WordBridgeColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Pick a topic to practice in ${language.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        val scenarios = listOf(
            VoiceTutorScenario(
                id = "travel",
                name = "Travel",
                description = "Hotels, directions, transportation, sightseeing"
            ),
            VoiceTutorScenario(
                id = "food",
                name = "Food & Dining",
                description = "Ordering food, restaurants, recipes, cooking"
            ),
            VoiceTutorScenario(
                id = "daily_conversation",
                name = "Daily Conversation",
                description = "Greetings, small talk, weather, hobbies"
            ),
            VoiceTutorScenario(
                id = "work",
                name = "Work & Business",
                description = "Meetings, emails, presentations, negotiations"
            ),
            VoiceTutorScenario(
                id = "culture",
                name = "Culture & Traditions",
                description = "History, customs, festivals, local traditions"
            )
        )

        scenarios.forEach { scenario ->
            ScenarioCard(
                scenario = scenario,
                onClick = { onScenarioSelected(scenario) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ScenarioCard(
    scenario: VoiceTutorScenario,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scenarioColor = when (scenario.id) {
        "travel" -> WordBridgeColors.AccentBlue
        "food" -> WordBridgeColors.AccentOrange
        "daily_conversation" -> WordBridgeColors.AccentGreen
        "work" -> WordBridgeColors.PrimaryPurple
        "culture" -> WordBridgeColors.PrimaryPurpleLight
        else -> WordBridgeColors.TextMuted
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scenario indicator badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = scenarioColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = scenario.name.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = scenarioColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scenario.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = WordBridgeColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = scenario.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondary,
                    lineHeight = 18.sp
                )
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = WordBridgeColors.TextSecondary
            )
        }
    }
}
