package org.example.project.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.core.initialization.AppInitializer
import org.example.project.ui.theme.WordBridgeColors

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SplashScreen(
    onInitializationComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStep by remember { mutableStateOf("Initializing...") }
    var progress by remember { mutableStateOf(0f) }
    var isComplete by remember { mutableStateOf(false) }

    // Initialize app services using AppInitializer
    LaunchedEffect(Unit) {
        try {
            AppInitializer.initialize(
                onProgress = { step, progressValue ->
                    currentStep = step
                    progress = progressValue
                }
            ).onSuccess {
                isComplete = true
                delay(800) // Delay to show completion animation
                onInitializationComplete()
            }.onFailure { e ->
                println("Initialization failed: ${e.message}")
                currentStep = "Starting app..."
                progress = 1f
                delay(500)
                onInitializationComplete()
            }
        } catch (e: Exception) {
            println("Initialization error: ${e.message}")
            currentStep = "Starting app..."
            progress = 1f
            delay(500)
            onInitializationComplete()
        }
    }

    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Pulsing animation for the logo
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF7C3AED),
                        Color(0xFF5B21B6),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        ) {
            // App Logo/Name
            Text(
                text = "WordBridge",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                ),
                color = Color.White.copy(alpha = pulseAlpha),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Language Learning Assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Progress indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(300.dp),
            ) {
                // Progress bar
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status text with fade animation
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                    }
                ) { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.95f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Percentage
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Success checkmark animation
            AnimatedVisibility(
                visible = isComplete,
                enter = scaleIn() + fadeIn(),
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

