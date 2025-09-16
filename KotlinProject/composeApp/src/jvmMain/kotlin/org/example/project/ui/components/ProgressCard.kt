package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.ui.theme.WordBridgeColors

/**
 * A card component that displays user progress statistics
 * 
 * @param title The title of the progress metric
 * @param value The current value to display
 * @param valueColor The color of the value text
 * @param icon Optional icon to display (for future implementation)
 * @param modifier Optional modifier for styling
 */
@Composable
fun ProgressCard(
    title: String,
    value: String,
    valueColor: Color = WordBridgeColors.TextPrimary,
    icon: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Add fire icon for streak
                if (title == "Streak" && value.contains("days")) {
                    Text(
                        text = "🔥",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = valueColor
                )
            }
        }
    }
}

/**
 * A specialized progress card for displaying today's progress summary
 */
@Composable
fun TodaysProgressCard(
    streak: Int,
    xpPoints: Int,
    wordsLearned: Int,
    accuracy: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Today's Progress",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress metrics in a grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    ProgressMetric(
                        label = "Streak",
                        value = "$streak days",
                        color = WordBridgeColors.AccentOrange
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ProgressMetric(
                        label = "Words Learned",
                        value = wordsLearned.toString(),
                        color = WordBridgeColors.AccentBlue
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    ProgressMetric(
                        label = "XP Points",
                        value = xpPoints.toString(),
                        color = WordBridgeColors.AccentGreen
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ProgressMetric(
                        label = "Accuracy",
                        value = "$accuracy%",
                        color = WordBridgeColors.PrimaryPurple
                    )
                }
            }
        }
    }
}

/**
 * A small progress metric display component
 */
@Composable
private fun ProgressMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (label == "Streak" || label == "Words Learned") 
            Alignment.Start else Alignment.End
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = WordBridgeColors.TextSecondary
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label == "Streak") {
                Text(
                    text = "🔥",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
        }
    }
}
