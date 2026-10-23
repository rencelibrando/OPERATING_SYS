package org.example.project.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun HomeEmptyState(
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = WordBridgeIcons.Explore,
            contentDescription = null,
            tint = WordBridgeColors.Primary,
            modifier = Modifier.size(44.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Ready to Start Learning?",
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = WordBridgeColors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Welcome to WordBridge. Your personalized AI-powered language journey begins here. Start lessons, build vocabulary, or practice speaking.",
            style = MaterialTheme.typography.bodyMedium,
            color = WordBridgeColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onGetStartedClick,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = WordBridgeColors.Primary,
                    contentColor = WordBridgeColors.OnPrimary,
                ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Get Started",
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Explore the sections in the sidebar to begin your lessons",
            style = MaterialTheme.typography.bodySmall,
            color = WordBridgeColors.TextMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
