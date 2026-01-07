package org.example.project.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.ChartDataPoint
import org.example.project.domain.model.HistoryTimeRange
import org.example.project.domain.model.MetricTrend
import org.example.project.domain.model.ProgressHistorySnapshot
import org.example.project.ui.theme.WordBridgeColors
import kotlin.math.max

/**
 * Line chart component for progress trends.
 * Shows historical data with interactive time range selection.
 */
@Composable
fun ProgressTrendsChart(
    history: List<ProgressHistorySnapshot>,
    selectedTimeRange: HistoryTimeRange,
    onTimeRangeSelected: (HistoryTimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            // Header with time range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "📊 Progress Trends",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = WordBridgeColors.TextPrimaryDark,
                )

                TimeRangeSelector(
                    selectedRange = selectedTimeRange,
                    onRangeSelected = onTimeRangeSelected,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (history.isEmpty()) {
                EmptyChartState()
            } else {
                // Tabs for different metrics
                var selectedMetric by remember { mutableStateOf(ChartMetric.LESSONS) }

                MetricTabs(
                    selectedMetric = selectedMetric,
                    onMetricSelected = { selectedMetric = it },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Chart
                LineChart(
                    dataPoints = getDataPoints(history, selectedMetric),
                    metric = selectedMetric,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Trend indicators
                TrendIndicators(history, selectedMetric)
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: HistoryTimeRange,
    onRangeSelected: (HistoryTimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HistoryTimeRange.entries.forEach { range ->
            val isSelected = range == selectedRange

            FilterChip(
                selected = isSelected,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        text =
                            when (range) {
                                HistoryTimeRange.WEEK -> "7D"
                                HistoryTimeRange.MONTH -> "30D"
                                HistoryTimeRange.QUARTER -> "90D"
                                HistoryTimeRange.YEAR -> "1Y"
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = WordBridgeColors.PrimaryPurple,
                        selectedLabelColor = Color.White,
                    ),
            )
        }
    }
}

@Composable
private fun MetricTabs(
    selectedMetric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = ChartMetric.entries.indexOf(selectedMetric),
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        edgePadding = 0.dp,
        divider = {},
    ) {
        ChartMetric.entries.forEach { metric ->
            Tab(
                selected = metric == selectedMetric,
                onClick = { onMetricSelected(metric) },
                text = {
                    Text(
                        text = "${metric.icon} ${metric.displayName}",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight =
                                    if (metric == selectedMetric) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                            ),
                    )
                },
            )
        }
    }
}

@Composable
private fun LineChart(
    dataPoints: List<ChartDataPoint>,
    metric: ChartMetric,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary,
            )
        }
        return
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val padding = 40f

        // Calculate bounds
        val maxValue = dataPoints.maxOfOrNull { it.value }?.toFloat() ?: 1f
        val minValue = dataPoints.minOfOrNull { it.value }?.toFloat() ?: 0f
        val range = max(maxValue - minValue, 1f)

        // Draw grid lines
        val gridColor = Color.Gray.copy(alpha = 0.2f)
        for (i in 0..4) {
            val y = padding + (height - 2 * padding) * i / 4
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f,
            )
        }

        // Calculate points
        val points =
            dataPoints.mapIndexed { index, dataPoint ->
                val x = padding + (width - 2 * padding) * index / (dataPoints.size - 1).coerceAtLeast(1)
                val normalizedValue = ((dataPoint.value.toFloat() - minValue) / range).coerceIn(0f, 1f)
                val y = height - padding - normalizedValue * (height - 2 * padding)
                Offset(x, y)
            }

        // Draw line path
        val path =
            Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
            }

        drawPath(
            path = path,
            color = metric.color,
            style = Stroke(width = 3f),
        )

        // Draw points
        points.forEach { point ->
            drawCircle(
                color = metric.color,
                radius = 5f,
                center = point,
            )
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = point,
            )
        }

        // Labels are rendered separately using Compose Text
        // This avoids platform-specific Canvas APIs
    }
}

@Composable
private fun TrendIndicators(
    history: List<ProgressHistorySnapshot>,
    selectedMetric: ChartMetric,
    modifier: Modifier = Modifier,
) {
    if (history.size < 2) return

    val first = history.first()
    val last = history.last()

    val trend =
        when (selectedMetric) {
            ChartMetric.LESSONS ->
                MetricTrend.calculate(
                    "Lessons",
                    last.lessonsCompleted.toDouble(),
                    first.lessonsCompleted.toDouble(),
                )
            ChartMetric.SESSIONS ->
                MetricTrend.calculate(
                    "Sessions",
                    last.conversationSessions.toDouble(),
                    first.conversationSessions.toDouble(),
                )
            ChartMetric.VOCABULARY ->
                MetricTrend.calculate(
                    "Vocabulary",
                    last.vocabularyWords.toDouble(),
                    first.vocabularyWords.toDouble(),
                )
            ChartMetric.TIME ->
                MetricTrend.calculate(
                    "Time",
                    last.totalTimeSeconds / 3600, // Convert to hours
                    first.totalTimeSeconds / 3600,
                )
            ChartMetric.SCORE ->
                MetricTrend.calculate(
                    "Score",
                    last.voiceAnalysis.averageScore,
                    first.voiceAnalysis.averageScore,
                )
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Change",
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trend.direction.emoji,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${if (trend.changeAmount >= 0) "+" else ""}${String.format("%.1f", trend.changeAmount)}",
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color =
                        when {
                            trend.changePercentage > 0 -> Color(0xFF10B981)
                            trend.changePercentage < 0 -> Color(0xFFEF4444)
                            else -> WordBridgeColors.TextPrimary
                        },
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Growth",
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
            )
            Text(
                text = "${if (trend.changePercentage >= 0) "+" else ""}${String.format("%.1f", trend.changePercentage)}%",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color =
                    when {
                        trend.changePercentage > 0 -> Color(0xFF10B981)
                        trend.changePercentage < 0 -> Color(0xFFEF4444)
                        else -> WordBridgeColors.TextPrimary
                    },
            )
        }
    }
}

@Composable
private fun EmptyChartState(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "📈",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = "Start learning to see trends",
                style = MaterialTheme.typography.bodyLarge,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}

enum class ChartMetric(
    val displayName: String,
    val icon: String,
    val color: Color,
) {
    LESSONS("Lessons", "📚", Color(0xFF8B5CF6)),
    SESSIONS("Sessions", "💬", Color(0xFF3B82F6)),
    VOCABULARY("Vocabulary", "📖", Color(0xFF10B981)),
    TIME("Time", "⏱️", Color(0xFFF59E0B)),
    SCORE("Avg Score", "⭐", Color(0xFFEC4899)),
}

private fun getDataPoints(
    history: List<ProgressHistorySnapshot>,
    metric: ChartMetric,
): List<ChartDataPoint> {
    return history.map { snapshot ->
        val value =
            when (metric) {
                ChartMetric.LESSONS -> snapshot.lessonsCompleted.toDouble()
                ChartMetric.SESSIONS -> snapshot.conversationSessions.toDouble()
                ChartMetric.VOCABULARY -> snapshot.vocabularyWords.toDouble()
                ChartMetric.TIME -> snapshot.totalTimeSeconds / 3600.0 // Convert to hours
                ChartMetric.SCORE -> snapshot.voiceAnalysis.averageScore
            }

        ChartDataPoint(
            date = snapshot.date,
            value = value,
            label = snapshot.formattedDate,
        )
    }
}
