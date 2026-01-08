package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular waveform indicator that shows conversation status.
 * This is a STATUS INDICATOR, not a button - continuous conversation mode.
 *
 * States:
 * - User speaking (green): User's voice is being captured
 * - Agent speaking (blue): AI is responding with audio
 * - Agent thinking (purple): AI is processing
 * - Idle/Ready (gray): Waiting for conversation
 * - Not ready (dim gray): Connecting
 */
@Composable
fun CircularWaveformMicrophone(
    isRecording: Boolean,
    isAgentSpeaking: Boolean,
    isAgentThinking: Boolean = false,
    isAgentReady: Boolean,
    audioLevel: Float = 0f,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    // Different rotation speeds for different states
    val userRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "userRotation",
    )

    val agentRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "agentRotation",
    )

    val thinkingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "thinkingRotation",
    )

    val idleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "idleRotation",
    )

    // Pulse animations
    val userPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "userPulse",
    )

    val agentPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "agentPulse",
    )

    val thinkingPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "thinkingPulse",
    )

    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "idlePulse",
    )

    // Glow animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "glowAlpha",
    )

    // State-based values - IMPORTANT: Check agent states BEFORE recording state
    // because in continuous mode, isRecording is always true but agent states take priority
    val currentRotation =
        when {
            isAgentSpeaking -> agentRotation
            isAgentThinking -> thinkingRotation
            isRecording -> userRotation
            else -> idleRotation
        }

    val currentPulse =
        when {
            isAgentSpeaking -> agentPulse
            isAgentThinking -> thinkingPulse
            isRecording -> userPulse
            else -> idlePulse
        }

    val primaryColor by animateColorAsState(
        targetValue =
            when {
                !isAgentReady -> Color(0xFF4B5563)
                isAgentSpeaking -> Color(0xFF3B82F6) // Blue for agent - check FIRST
                isAgentThinking -> Color(0xFF8B5CF6) // Purple for thinking
                isRecording -> Color(0xFF10B981) // Green for user
                else -> Color(0xFF6B7280) // Gray for idle
            },
        animationSpec = tween(300),
        label = "primaryColor",
    )

    val secondaryColor by animateColorAsState(
        targetValue =
            when {
                !isAgentReady -> Color(0xFF374151)
                isAgentSpeaking -> Color(0xFF2563EB) // Check agent states FIRST
                isAgentThinking -> Color(0xFF7C3AED)
                isRecording -> Color(0xFF059669)
                else -> Color(0xFF4B5563)
            },
        animationSpec = tween(300),
        label = "secondaryColor",
    )

    // Status text - prioritize agent states over recording state
    val statusText =
        when {
            !isAgentReady -> "Connecting..."
            isAgentSpeaking -> "Speaking"
            isAgentThinking -> "Thinking"
            isRecording -> "Listening"
            else -> "Ready"
        }

    val statusEmoji =
        when {
            !isAgentReady -> "⏳"
            isAgentSpeaking -> "🔊"
            isAgentThinking -> "💭"
            isRecording -> "🎤"
            else -> "✨"
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer glow ring
            if (isRecording || isAgentSpeaking) {
                Box(
                    modifier =
                        Modifier
                            .size(80.dp)
                            .scale(currentPulse * 1.1f)
                            .clip(CircleShape)
                            .background(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                primaryColor.copy(alpha = glowAlpha * 0.4f),
                                                primaryColor.copy(alpha = glowAlpha * 0.2f),
                                                Color.Transparent,
                                            ),
                                    ),
                            ),
                )
            }

            // Waveform canvas
            Canvas(
                modifier =
                    Modifier
                        .size(64.dp)
                        .scale(currentPulse),
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f
                val maxRadius = canvasWidth * 0.45f

                val waveformSegments = 36
                val angleStep = 360f / waveformSegments
                val concentricRings = if (isRecording || isAgentSpeaking) 6 else 4

                for (ringIndex in 0 until concentricRings) {
                    val ringProgress = (ringIndex + 1).toFloat() / concentricRings
                    val ringRadius = maxRadius * ringProgress
                    val ringAlpha = 0.4f + (ringProgress * 0.6f)

                    for (i in 0 until waveformSegments) {
                        val angle = (i * angleStep + currentRotation * (1f + ringIndex * 0.15f)) % 360f
                        val angleRad = Math.toRadians(angle.toDouble()).toFloat()

                        val amplitude =
                            when {
                                isRecording -> {
                                    val normalizedLevel = audioLevel.coerceIn(0f, 1f)
                                    val segmentPhase = (i.toFloat() / waveformSegments) * 2f * PI.toFloat()
                                    val ringPhase = ringIndex * 0.6f
                                    val baseWave = sin(segmentPhase + ringPhase + (currentRotation / 40f))
                                    0.1f + (normalizedLevel * 0.2f) + (baseWave * 0.15f)
                                }
                                isAgentSpeaking -> {
                                    val segmentPhase = (i.toFloat() / waveformSegments) * 2f * PI.toFloat()
                                    val ringPhase = ringIndex * 0.5f
                                    val wave = sin(segmentPhase + ringPhase + (currentRotation / 60f))
                                    0.08f + (wave * 0.1f)
                                }
                                isAgentThinking -> {
                                    val segmentPhase = (i.toFloat() / waveformSegments) * 2f * PI.toFloat()
                                    val wave = sin(segmentPhase + (currentRotation / 30f))
                                    0.05f + (wave * 0.05f)
                                }
                                else -> {
                                    val segmentPhase = (i.toFloat() / waveformSegments) * 2f * PI.toFloat()
                                    val wave = sin(segmentPhase + (currentRotation / 100f))
                                    0.03f + (wave * 0.02f)
                                }
                            }

                        val innerRadius = ringRadius * (1f - amplitude)
                        val outerRadius = ringRadius * (1f + amplitude)

                        val innerX = centerX + cos(angleRad) * innerRadius
                        val innerY = centerY + sin(angleRad) * innerRadius
                        val outerX = centerX + cos(angleRad) * outerRadius
                        val outerY = centerY + sin(angleRad) * outerRadius

                        drawLine(
                            color = primaryColor.copy(alpha = ringAlpha),
                            start = Offset(innerX, innerY),
                            end = Offset(outerX, outerY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }

                // Center circle with gradient
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(primaryColor, secondaryColor),
                            center = Offset(centerX, centerY),
                            radius = 12.dp.toPx(),
                        ),
                    radius = 10.dp.toPx(),
                    center = Offset(centerX, centerY),
                )

                // Outer ring indicator
                if (isRecording || isAgentSpeaking) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.6f),
                        radius = maxRadius + 2.dp.toPx(),
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            // Center emoji indicator
            Text(
                text = statusEmoji,
                fontSize = 16.sp,
            )
        }

        // Status text
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = primaryColor,
            fontWeight = if (isRecording || isAgentSpeaking) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
