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

/**
 * Represents a lesson feature for the empty state
 */
data class LessonFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String
)

/**
 * Empty state component for lessons screen
 * 
 * @param onCreateFirstLessonClick Callback when "Create First Lesson" button is clicked
 * @param onExploreCurriculumClick Callback when "explore curriculum" link is clicked
 * @param modifier Optional modifier for styling
 */
@Composable
fun LessonsEmptyState(
    onCreateFirstLessonClick: () -> Unit,
    onExploreCurriculumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lessonFeatures = listOf(
        LessonFeature(
            id = "adaptive_learning",
            title = "Adaptive Learning Path",
            description = "AI-powered lessons that adapt to your learning style and progress at your own pace.",
            icon = "🧠",
            color = "#8B5CF6"
        ),
        LessonFeature(
            id = "interactive_exercises",
            title = "Interactive Exercises",
            description = "Engaging activities, quizzes, and practice sessions to reinforce your learning.",
            icon = "🎯",
            color = "#10B981"
        ),
        LessonFeature(
            id = "progress_tracking",
            title = "Progress Tracking",
            description = "Visual progress indicators and detailed analytics to track your improvement.",
            icon = "📊",
            color = "#F59E0B"
        ),
        LessonFeature(
            id = "personalized_content",
            title = "Personalized Content",
            description = "Lessons tailored to your interests, goals, and current skill level.",
            icon = "⭐",
            color = "#EF4444"
        )
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Graduation cap icon
        Text(
            text = "🎓",
            style = MaterialTheme.typography.displayMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = "Your Learning Journey Begins Here",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Description
        Text(
            text = "Start building your personalized learning path! Our AI will create lessons tailored to your goals and learning style. Choose from grammar, vocabulary, conversation, and pronunciation modules.",
            style = MaterialTheme.typography.bodyLarge,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Create First Lesson Button
        Button(
            onClick = onCreateFirstLessonClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = WordBridgeColors.PrimaryPurple
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Create Your First Lesson",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Explore curriculum link
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "or ",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary
            )
            
            TextButton(
                onClick = onExploreCurriculumClick
            ) {
                Text(
                    text = "explore our curriculum templates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.PrimaryPurple
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Features Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(300.dp) // Fixed height to prevent scroll conflicts
        ) {
            items(lessonFeatures) { feature ->
                LessonFeatureCard(feature = feature)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Feature card component for lesson features
 */
@Composable
private fun LessonFeatureCard(
    feature: LessonFeature,
    modifier: Modifier = Modifier
) {
    val featureColor = try {
        androidx.compose.ui.graphics.Color(feature.color.removePrefix("#").toLong(16) or 0xFF000000)
    } catch (e: Exception) {
        WordBridgeColors.PrimaryPurple
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            hoveredElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        featureColor.copy(alpha = 0.1f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feature.icon,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}
