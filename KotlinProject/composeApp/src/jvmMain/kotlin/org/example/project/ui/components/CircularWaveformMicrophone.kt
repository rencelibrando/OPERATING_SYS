package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularWaveformMicrophone(
    isRecording: Boolean,
    isAgentSpeaking: Boolean,
    isAgentThinking: Boolean = false,
    isAgentReady: Boolean,
    audioLevel: Float = 0f,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val idlePulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val waveformRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val recordingScale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessLow
        )
    )
    
    val waveformColor by animateColorAsState(
        targetValue = when {
            !isAgentReady -> Color(0xFF6B7280)
            isAgentThinking -> Color(0xFF8B5CF6)
            isAgentSpeaking -> Color(0xFFEAB308)
            isRecording -> Color(0xFF00FF88)
            else -> Color(0xFFB4B4C4)
        },
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "waveformColor"
    )
    
    Box(
        modifier = modifier
            .size(100.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (isAgentReady && !isAgentSpeaking) {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f
            val maxRadius = canvasWidth * 0.42f
            
            val currentScale = if (isRecording) {
                recordingScale
            } else {
                idlePulseScale
            }
            
            val waveformSegments = 48
            val angleStep = 360f / waveformSegments
            val concentricRings = 8
            
            for (ringIndex in 0 until concentricRings) {
                val ringProgress = (ringIndex + 1).toFloat() / concentricRings
                val ringRadius = maxRadius * ringProgress * currentScale
                val ringAlpha = 0.3f + (ringProgress * 0.7f)
                
                for (i in 0 until waveformSegments) {
                    val angle = (i * angleStep + waveformRotation * (1f + ringIndex * 0.1f)) % 360f
                    val angleRad = Math.toRadians(angle.toDouble()).toFloat()
                    
                    val amplitude = if (isRecording) {
                        val normalizedLevel = audioLevel.coerceIn(0f, 1f)
                        val segmentPhase = (i.toFloat() / waveformSegments) * 2f * PI.toFloat()
                        val ringPhase = ringIndex * 0.5f
                        val baseWave = sin(segmentPhase + ringPhase + (waveformRotation / 50f))
                        0.08f + (normalizedLevel * 0.12f) + (baseWave * 0.08f)
                    } else {
                        val segmentPhase = (i.toFloat() / waveformSegments) * 2f * PI.toFloat()
                        val ringPhase = ringIndex * 0.5f
                        val wave = sin(segmentPhase + ringPhase + (waveformRotation / 100f))
                        0.05f + (wave * 0.03f)
                    }
                    
                    val innerRadius = ringRadius * (1f - amplitude)
                    val outerRadius = ringRadius * (1f + amplitude)
                    
                    val innerX = centerX + cos(angleRad) * innerRadius
                    val innerY = centerY + sin(angleRad) * innerRadius
                    val outerX = centerX + cos(angleRad) * outerRadius
                    val outerY = centerY + sin(angleRad) * outerRadius
                    
                    drawLine(
                        color = waveformColor.copy(alpha = ringAlpha),
                        start = Offset(innerX, innerY),
                        end = Offset(outerX, outerY),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
