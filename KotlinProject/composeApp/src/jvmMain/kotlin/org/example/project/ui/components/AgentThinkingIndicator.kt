package org.example.project.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * Animated waveform indicator showing when the agent is thinking.
 * Features smooth wave animations with multiple frequency components.
 */
@Composable
fun AgentThinkingIndicator(
    isThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()

    // Wave animations
    val wave1Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    val wave2Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    val wave3Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    // Pulse animation for the center
    val centerPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    // Opacity animation
    val opacity by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    if (isThinking) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Waveform visualization
            Canvas(
                modifier =
                    Modifier
                        .size(width = 200.dp, height = 60.dp),
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerY = canvasHeight / 2f

                // Draw multiple waveforms
                drawWaveform(
                    canvasWidth = canvasWidth,
                    centerY = centerY,
                    phase = wave1Phase,
                    amplitude = 15f,
                    frequency = 3f,
                    color = Color(0xFF8B5CF6).copy(alpha = opacity),
                    strokeWidth = 3f,
                )

                drawWaveform(
                    canvasWidth = canvasWidth,
                    centerY = centerY,
                    phase = wave2Phase,
                    amplitude = 10f,
                    frequency = 5f,
                    color = Color(0xFF06B6D4).copy(alpha = opacity * 0.8f),
                    strokeWidth = 2f,
                )

                drawWaveform(
                    canvasWidth = canvasWidth,
                    centerY = centerY,
                    phase = wave3Phase,
                    amplitude = 8f,
                    frequency = 7f,
                    color = Color(0xFF10B981).copy(alpha = opacity * 0.6f),
                    strokeWidth = 2f,
                )

                // Draw center pulse dot
                drawCircle(
                    color = Color(0xFF8B5CF6).copy(alpha = opacity),
                    radius = 4f * centerPulse,
                    center = Offset(canvasWidth / 2f, centerY),
                )
            }

            // "Thinking" text
            Text(
                text = "AI is thinking...",
                color = Color(0xFFB4B4C4),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Draws a single waveform with the specified parameters.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveform(
    canvasWidth: Float,
    centerY: Float,
    phase: Float,
    amplitude: Float,
    frequency: Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = androidx.compose.ui.graphics.Path()

    // Create smooth waveform
    for (x in 0..canvasWidth.toInt() step 2) {
        val normalizedX = x.toFloat() / canvasWidth
        val y = centerY + amplitude * sin(frequency * 2f * PI * normalizedX + phase).toFloat()

        if (x == 0) {
            path.moveTo(x.toFloat(), y)
        } else {
            path.lineTo(x.toFloat(), y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style =
            Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
            ),
    )
}

/**
 * Compact thinking indicator for inline use in chat.
 */
@Composable
fun CompactThinkingIndicator(
    isThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    val opacity by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    if (isThinking) {
        Canvas(
            modifier = modifier.size(width = 60.dp, height = 20.dp),
        ) {
            val canvasWidth = size.width
            val centerY = size.height / 2f

            drawWaveform(
                canvasWidth = canvasWidth,
                centerY = centerY,
                phase = wavePhase,
                amplitude = 6f,
                frequency = 4f,
                color = Color(0xFF8B5CF6).copy(alpha = opacity),
                strokeWidth = 2f,
            )
        }
    }
}

/**
 * Circular thinking indicator with rotating waves.
 */
@Composable
fun CircularThinkingIndicator(
    isThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    if (isThinking) {
        Canvas(
            modifier = modifier.size(80.dp),
        ) {
            val canvasWidth = size.width
            val centerX = canvasWidth / 2f
            val centerY = size.height / 2f
            val maxRadius = canvasWidth * 0.35f

            // Draw rotating wave segments
            for (i in 0 until 8) {
                val angle = (i * 45f + rotation) * PI / 180f
                val waveAmplitude = sin((i * PI / 4f) + rotation * PI / 180f).toFloat() * 0.3f + 0.7f

                drawCircle(
                    color = Color(0xFF8B5CF6).copy(alpha = 0.6f),
                    radius = 3f,
                    center =
                        androidx.compose.ui.geometry.Offset(
                            centerX + maxRadius * waveAmplitude * cos(angle).toFloat(),
                            centerY + maxRadius * waveAmplitude * sin(angle).toFloat(),
                        ),
                )
            }

            // Center pulsing circle
            drawCircle(
                color = Color(0xFF8B5CF6),
                radius = 6f * pulseScale,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            )
        }
    }
}
