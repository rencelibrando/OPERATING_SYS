package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.core.auth.User
import org.example.project.ui.components.AnimatedNetworkBackground

@Composable
fun EmailVerificationSuccessScreen(
    user: User,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0xFF0D1117), // Dark GitHub-like background
                                Color(0xFF161B22), // Slightly lighter dark
                                Color(0xFF0D1117), // Back to darker at edges
                            ),
                        radius = 1000f,
                    ),
                ),
    ) {
        AnimatedNetworkBackground(
            modifier = Modifier.fillMaxSize(),
            nodeCount = 35,
            connectionDistance = 130f,
            speed = 0.2f,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SuccessCard(
                user = user,
                message = message,
                modifier = Modifier.widthIn(max = 500.dp),
            )
        }
    }
}

@Composable
private fun SuccessCard(
    user: User,
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF21262D).copy(alpha = 0.9f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color(0xFF22C55E).copy(alpha = 0.1f),
                    ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✅",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Email Verified!",
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = Color(0xFF22C55E),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to WordBridge, ${user.firstName}!",
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Taking you to the app...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
