package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.ui.theme.WordBridgeColors

data class LessonFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String,
)

@Composable
fun LessonsEmptyState(
    modifier: Modifier = Modifier,
) {
    val lessonFeatures =
        listOf(
            LessonFeature(
                id = "adaptive_learning",
                title = "Adaptive Learning Path",
                description = "AI-powered lessons that adapt to your learning style and progress at your own pace.",
                icon = "🧠",
                color = "#8B5CF6",
            ),
            LessonFeature(
                id = "interactive_exercises",
                title = "Interactive Exercises",
                description = "Engaging activities, quizzes, and practice sessions to reinforce your learning.",
                icon = "🎯",
                color = "#10B981",
            ),
            LessonFeature(
                id = "progress_tracking",
                title = "Progress Tracking",
                description = "Visual progress indicators and detailed analytics to track your improvement.",
                icon = "📊",
                color = "#F59E0B",
            ),
            LessonFeature(
                id = "personalized_content",
                title = "Personalized Content",
                description = "Lessons tailored to your interests, goals, and current skill level.",
                icon = "⭐",
                color = "#EF4444",
            ),
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "🎓",
            style = MaterialTheme.typography.displayMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your Learning Journey Begins Here",
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Browse through our structured lesson categories to find the perfect learning path for your level. Start with Beginner lessons and unlock more advanced content as you progress.",
            style = MaterialTheme.typography.bodyLarge,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
        )

        Spacer(modifier = Modifier.height(48.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(300.dp), 
        ) {
            items(lessonFeatures) { feature ->
                LessonFeatureCard(feature = feature)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LessonFeatureCard(
    feature: LessonFeature,
    modifier: Modifier = Modifier,
) {
    val featureColor =
        try {
            androidx.compose.ui.graphics.Color(feature.color.removePrefix("#").toLong(16) or 0xFF000000)
        } catch (e: Exception) {
            WordBridgeColors.PrimaryPurple
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.BackgroundWhite,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                hoveredElevation = 4.dp,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(
                            featureColor.copy(alpha = 0.1f),
                            androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = feature.icon,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = feature.title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )
        }
    }
}
