package org.example.project.ui.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.core.onboarding.OnboardingCategory
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun OnboardingProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    currentCategory: OnboardingCategory,
    modifier: Modifier = Modifier,
) {
    val progress = if (totalSteps == 0) 0f else currentStep.toFloat() / totalSteps

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${currentStep + 1} of $totalSteps",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )

            Text(
                text = friendlyCategoryLabel(currentCategory),
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.PrimaryPurple,
            )
        }

        Box(
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(WordBridgeColors.PrimaryPurple.copy(alpha = 0.15f), RoundedCornerShape(50)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress)
                        .height(10.dp)
                        .background(WordBridgeColors.PrimaryPurple, RoundedCornerShape(50)),
            )
        }
    }
}

private fun friendlyCategoryLabel(category: OnboardingCategory): String {
    return when (category) {
        OnboardingCategory.BASIC_INFO -> "Let's get acquainted"
        OnboardingCategory.GOALS -> "Clarifying your goals"
        OnboardingCategory.LEARNING_PREFERENCES -> "Learning preferences"
        OnboardingCategory.TONE_PERSONALITY -> "Your tutor vibe"
        OnboardingCategory.LIFESTYLE -> "Life rhythm"
        OnboardingCategory.INTERESTS -> "Your interests"
        OnboardingCategory.EMOTIONAL_SUPPORT -> "Motivation style"
        OnboardingCategory.SOCIAL -> "Social preferences"
        OnboardingCategory.VOICE -> "Voice & accent"
        OnboardingCategory.FUTURE -> "Future vision"
    }
}
