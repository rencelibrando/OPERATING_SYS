package org.example.project.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

@Composable
fun AnimatedNetworkBackground(
    modifier: Modifier = Modifier,
    nodeCount: Int = 500,
    connectionDistance: Float = 180f,
    speed: Float = 0.9f,
) {
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val infiniteTransition = rememberInfiniteTransition(label = "network_animation")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(15000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "time_animation",
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse_animation",
    )

    val nodes =
        remember {
            (0 until nodeCount).map { index ->
                NetworkNode(
                    id = index,
                    initialX = Random.nextFloat(),
                    initialY = Random.nextFloat(),
                    speedX = (Random.nextFloat() - 0.2f) * speed,
                    speedY = (Random.nextFloat() - 0.2f) * speed,
                    radius = Random.nextFloat() * 1f + 1f,
                )
            }
        }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val width = size.width
        val height = size.height

        val currentNodes =
            nodes.map { node ->
                val currentX = (node.initialX + node.speedX * time) % 1f
                val currentY = (node.initialY + node.speedY * time) % 1f

                node.copy(
                    currentX = if (currentX < 0) currentX + 1f else currentX,
                    currentY = if (currentY < 0) currentY + 1f else currentY,
                )
            }

        drawConnections(
            nodes = currentNodes,
            width = width,
            height = height,
            connectionDistance = connectionDistance,
            color = primaryColor.copy(alpha = 0.6f),
            pulse = pulse,
        )

        drawNodes(
            nodes = currentNodes,
            width = width,
            height = height,
            nodeColor = primaryColor.copy(alpha = 0.8f),
            glowColor = primaryColor.copy(alpha = 0.3f),
            pulse = pulse,
        )
    }
}

private data class NetworkNode(
    val id: Int,
    val initialX: Float,
    val initialY: Float,
    val speedX: Float,
    val speedY: Float,
    val radius: Float,
    val currentX: Float = initialX,
    val currentY: Float = initialY,
)

private fun DrawScope.drawConnections(
    nodes: List<NetworkNode>,
    width: Float,
    height: Float,
    connectionDistance: Float,
    color: Color,
    pulse: Float,
) {
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val node1 = nodes[i]
            val node2 = nodes[j]

            val x1 = node1.currentX * width
            val y1 = node1.currentY * height
            val x2 = node2.currentX * width
            val y2 = node2.currentY * height

            val distance = sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))

            if (distance < connectionDistance) {
                val distanceRatio = 1f - distance / connectionDistance
                val baseAlpha = distanceRatio * 0.6f
                val pulsedAlpha = baseAlpha * pulse

                drawLine(
                    color = color.copy(alpha = pulsedAlpha),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = (1.5f + distanceRatio * 1f).dp.toPx(),
                )

                if (distanceRatio > 0.7f) {
                    drawLine(
                        color = color.copy(alpha = pulsedAlpha * 0.3f),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 4.dp.toPx(),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawNodes(
    nodes: List<NetworkNode>,
    width: Float,
    height: Float,
    nodeColor: Color,
    glowColor: Color,
    pulse: Float,
) {
    nodes.forEach { node ->
        val x = node.currentX * width
        val y = node.currentY * height
        val radius = node.radius.dp.toPx()
        val pulsedRadius = radius * (1f + pulse * 0.3f)

        drawCircle(
            color = glowColor.copy(alpha = glowColor.alpha * pulse),
            radius = pulsedRadius * 4f,
            center = Offset(x, y),
        )

        drawCircle(
            color = glowColor.copy(alpha = glowColor.alpha * 0.7f),
            radius = pulsedRadius * 2f,
            center = Offset(x, y),
        )

        drawCircle(
            color = nodeColor,
            radius = pulsedRadius,
            center = Offset(x, y),
        )

        drawCircle(
            color = nodeColor.copy(alpha = 1f),
            radius = pulsedRadius * 0.6f,
            center = Offset(x, y),
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.8f * pulse),
            radius = pulsedRadius * 0.3f,
            center = Offset(x, y),
        )
    }
}
