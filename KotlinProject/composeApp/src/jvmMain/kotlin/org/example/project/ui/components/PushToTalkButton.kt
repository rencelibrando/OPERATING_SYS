package org.example.project.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Push-to-talk microphone button with visual feedback.
 * Press and hold to record, release to send.
 */
@Composable
fun PushToTalkButton(
    isRecording: Boolean,
    isAgentSpeaking: Boolean,
    isAgentReady: Boolean = true,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    // Track if button is being pressed to prevent duplicate calls
    var isPressing by remember { mutableStateOf(false) }

    // Pulsing animation when recording
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    // Flowing gradient animation
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    // Glow animation when idle and ready
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    // Strong pulse effect when recording
    val recordingPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    // Determine button state
    val isDisabled = isAgentSpeaking || !isAgentReady
    val buttonScale =
        when {
            isRecording -> recordingPulse
            isDisabled -> 1f
            else -> 1f
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Instruction text with animation
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(),
        ) {
            Text(
                text =
                    when {
                        isRecording -> "Recording... Release to send"
                        isAgentSpeaking -> "Agent is speaking..."
                        !isAgentReady -> "Connecting to agent..."
                        else -> "Press and hold to speak"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    when {
                        isRecording -> Color(0xFFEF4444)
                        isDisabled -> Color(0xFF6B7280)
                        else -> Color(0xFFB4B4C4)
                    },
                fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Microphone button with flowing gradient and shadow
        Box(
            contentAlignment = Alignment.Center,
        ) {
            // Outer pulsing glow effect when recording - ENHANCED for dark background
            if (isRecording) {
                Box(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .scale(recordingPulse)
                            .background(
                                color = Color(0xFFEF4444).copy(alpha = 0.4f),
                                shape = CircleShape,
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .size(100.dp)
                            .scale(recordingPulse * 0.9f)
                            .background(
                                color = Color(0xFFEF4444).copy(alpha = 0.6f),
                                shape = CircleShape,
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .size(85.dp)
                            .scale(recordingPulse * 0.85f)
                            .background(
                                color = Color(0xFFEF4444).copy(alpha = 0.8f),
                                shape = CircleShape,
                            ),
                )
            }

            // Outer glow effect when idle and ready - ENHANCED visibility
            if (!isRecording && !isDisabled) {
                Box(
                    modifier =
                        Modifier
                            .size(110.dp)
                            .scale(pulseScale)
                            .background(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                Color(0xFF8B5CF6).copy(alpha = glowAlpha * 0.6f),
                                                Color(0xFF8B5CF6).copy(alpha = glowAlpha * 0.3f),
                                                Color.Transparent,
                                            ),
                                    ),
                                shape = CircleShape,
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .size(95.dp)
                            .scale(pulseScale * 0.9f)
                            .background(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                Color(0xFF8B5CF6).copy(alpha = glowAlpha * 0.8f),
                                                Color(0xFF8B5CF6).copy(alpha = glowAlpha * 0.4f),
                                                Color.Transparent,
                                            ),
                                    ),
                                shape = CircleShape,
                            ),
                )
            }

            // Main button with flowing gradient
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(80.dp)
                        .scale(buttonScale)
                        .shadow(
                            elevation =
                                when {
                                    isRecording -> 20.dp
                                    isDisabled -> 2.dp
                                    else -> 12.dp
                                },
                            shape = CircleShape,
                            ambientColor =
                                when {
                                    isRecording -> Color(0xFFEF4444)
                                    isDisabled -> Color.Gray
                                    else -> Color(0xFF8B5CF6)
                                },
                            spotColor =
                                when {
                                    isRecording -> Color(0xFFEF4444)
                                    isDisabled -> Color.Gray
                                    else -> Color(0xFF8B5CF6)
                                },
                        )
                        .clip(CircleShape)
                        .background(
                            brush =
                                when {
                                    isRecording ->
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFFFF6B6B),
                                                    Color(0xFFEF4444),
                                                    Color(0xFFDC2626),
                                                ),
                                        )
                                    isDisabled ->
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFFBDBDBD),
                                                    Color(0xFF9E9E9E),
                                                ),
                                        )
                                    else ->
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFFA78BFA),
                                                    Color(0xFF8B5CF6),
                                                    Color(0xFF7C3AED),
                                                    Color(0xFF6D28D9),
                                                ),
                                        )
                                },
                        )
                        .pointerInput(isDisabled) {
                            detectTapGestures(
                                onPress = {
                                    if (!isDisabled && !isPressing) {
                                        isPressing = true
                                        println("[PTT] Button pressed")
                                        coroutineScope.launch {
                                            onPress()
                                        }

                                        val released = tryAwaitRelease()

                                        if (released && isPressing) {
                                            println("[PTT] Button released")
                                            coroutineScope.launch {
                                                onRelease()
                                            }
                                        }
                                        isPressing = false
                                    }
                                },
                            )
                        },
            ) {
                // Microphone icon with gradient overlay when recording
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(if (isRecording) 1.15f else 1f),
                ) {
                    Text(
                        text = if (isDisabled && !isAgentSpeaking) "⏳" else "🎙️",
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status indicator with animations
        androidx.compose.animation.AnimatedVisibility(
            visible = isRecording || isAgentSpeaking,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
        ) {
            if (isRecording) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Pulsing recording dot with strong effect
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .scale(recordingPulse)
                                .background(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFFFF6B6B),
                                                    Color(0xFFEF4444),
                                                ),
                                        ),
                                    shape = CircleShape,
                                ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recording your voice...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (isAgentSpeaking) {
                // AI speaking indicator with animation
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Animated speaking dots
                    repeat(3) { index ->
                        val dotScale by infiniteTransition.animateFloat(
                            initialValue = 0.6f,
                            targetValue = 1f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(400, delayMillis = index * 150),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(6.dp)
                                    .scale(dotScale)
                                    .background(Color(0xFF10B981), shape = CircleShape),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI is responding...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
