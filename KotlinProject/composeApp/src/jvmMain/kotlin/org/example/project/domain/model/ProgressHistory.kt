package org.example.project.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Daily snapshot of language progress for historical trends.
 */
@Serializable
data class ProgressHistorySnapshot(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val language: LessonLanguage,
    val lessonsCompleted: Int = 0,
    val totalLessons: Int = 0,
    val conversationSessions: Int = 0,
    val vocabularyWords: Int = 0,
    val totalTimeSeconds: Double = 0.0,
    val voiceAnalysis: VoiceAnalysisScores = VoiceAnalysisScores(),
) {
    val formattedDate: String
        get() = date.format(DateTimeFormatter.ofPattern("MMM dd"))
    
    val lessonsProgressPercentage: Int
        get() = if (totalLessons > 0) {
            ((lessonsCompleted.toFloat() / totalLessons.toFloat()) * 100).toInt()
        } else {
            0
        }
}

/**
 * Time range for historical data queries.
 */
enum class HistoryTimeRange(val displayName: String, val days: Int) {
    WEEK("Last 7 Days", 7),
    MONTH("Last 30 Days", 30),
    QUARTER("Last 90 Days", 90),
    YEAR("Last Year", 365);
}

/**
 * Chart data point for visualizations.
 */
data class ChartDataPoint(
    val date: LocalDate,
    val value: Double,
    val label: String = ""
)

/**
 * Trend direction indicator.
 */
enum class TrendDirection {
    UP,
    DOWN,
    STABLE;
    
    val emoji: String
        get() = when (this) {
            UP -> "📈"
            DOWN -> "📉"
            STABLE -> "➡️"
        }
}

/**
 * Metric trend analysis.
 */
data class MetricTrend(
    val metricName: String,
    val currentValue: Double,
    val previousValue: Double,
    val changeAmount: Double,
    val changePercentage: Double,
    val direction: TrendDirection
) {
    companion object {
        fun calculate(
            metricName: String,
            current: Double,
            previous: Double
        ): MetricTrend {
            val change = current - previous
            val changePercent = if (previous > 0) {
                (change / previous) * 100
            } else if (current > 0) {
                100.0
            } else {
                0.0
            }
            
            val direction = when {
                changePercent > 2.0 -> TrendDirection.UP
                changePercent < -2.0 -> TrendDirection.DOWN
                else -> TrendDirection.STABLE
            }
            
            return MetricTrend(
                metricName = metricName,
                currentValue = current,
                previousValue = previous,
                changeAmount = change,
                changePercentage = changePercent,
                direction = direction
            )
        }
    }
}

/**
 * Custom LocalDate serializer for kotlinx.serialization.
 */
object LocalDateSerializer : kotlinx.serialization.KSerializer<LocalDate> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "LocalDate",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }
}
