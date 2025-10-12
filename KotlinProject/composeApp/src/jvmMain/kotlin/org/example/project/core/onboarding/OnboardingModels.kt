package org.example.project.core.onboarding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

@Serializable
enum class OnboardingCategory {
    @SerialName("basic_info")
    BASIC_INFO,

    @SerialName("goals")
    GOALS,

    @SerialName("learning_preferences")
    LEARNING_PREFERENCES,

    @SerialName("tone_personality")
    TONE_PERSONALITY,

    @SerialName("lifestyle")
    LIFESTYLE,

    @SerialName("interests")
    INTERESTS,

    @SerialName("emotional_support")
    EMOTIONAL_SUPPORT,

    @SerialName("social")
    SOCIAL,

    @SerialName("voice")
    VOICE,

    @SerialName("future")
    FUTURE
}

@Serializable
enum class OnboardingInputType {
    @SerialName("text")
    TEXT,

    @SerialName("single_select")
    SINGLE_SELECT,

    @SerialName("multi_select")
    MULTI_SELECT,

    @SerialName("scale")
    SCALE
}

@Serializable
data class OnboardingOption(
    val id: String,
    val label: String,
    val value: String,
    val emoji: String? = null,
    val description: String? = null
)

@Serializable
data class OnboardingQuestion(
    val id: String,
    val category: OnboardingCategory,
    val prompt: String,
    val helperText: String? = null,
    val inputType: OnboardingInputType,
    val options: List<OnboardingOption> = emptyList(),
    val placeholder: String? = null,
    val isOptional: Boolean = false,
    val allowsVoiceInput: Boolean = false,
    val followUpPrompt: String? = null,
    val minScale: Int = 1,
    val maxScale: Int = 5
)

enum class OnboardingMessageSender {
    ASSISTANT,
    USER
}

data class OnboardingMessage(
    val id: String,
    val sender: OnboardingMessageSender,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isTyping: Boolean = false
)

sealed class OnboardingResponse {

    data class Text(val value: String) : OnboardingResponse()

    data class SingleChoice(
        val optionId: String,
        val label: String,
        val value: String
    ) : OnboardingResponse()

    data class MultiChoice(
        val optionIds: List<String>,
        val labels: List<String>,
        val values: List<String>
    ) : OnboardingResponse()

    data class Scale(
        val score: Int,
        val min: Int = 1,
        val max: Int = 5
    ) : OnboardingResponse()

    data class BooleanChoice(
        val value: Boolean,
        val label: String
    ) : OnboardingResponse()

    object Skipped : OnboardingResponse()
}

data class OnboardingAnswer(
    val questionId: String,
    val response: OnboardingResponse
)

fun OnboardingResponse.toJsonElement(): JsonElement = when (this) {
    is OnboardingResponse.Text -> buildJsonObject {
        put("value", JsonPrimitive(value))
    }
    is OnboardingResponse.SingleChoice -> buildJsonObject {
        put("option_id", JsonPrimitive(optionId))
        put("value", JsonPrimitive(value))
        put("label", JsonPrimitive(label))
    }
    is OnboardingResponse.MultiChoice -> buildJsonObject {
        put("option_ids", buildJsonArray { optionIds.forEach { add(JsonPrimitive(it)) } })
        put("values", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
        put("labels", buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
    }
    is OnboardingResponse.Scale -> buildJsonObject {
        put("score", JsonPrimitive(score))
        put("min", JsonPrimitive(min))
        put("max", JsonPrimitive(max))
    }
    is OnboardingResponse.BooleanChoice -> buildJsonObject {
        put("value", JsonPrimitive(value))
        put("label", JsonPrimitive(label))
    }
    OnboardingResponse.Skipped -> buildJsonObject {
        put("skipped", JsonPrimitive(true))
    }
}

fun OnboardingResponse.summaryText(): String = when (this) {
    is OnboardingResponse.Text -> value
    is OnboardingResponse.SingleChoice -> label
    is OnboardingResponse.MultiChoice -> labels.joinToString(", ")
    is OnboardingResponse.Scale -> "$score / $max"
    is OnboardingResponse.BooleanChoice -> label
    OnboardingResponse.Skipped -> "Skipped for now"
}

fun JsonElement?.asStringList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> listOfNotNull(contentOrNull)
    else -> emptyList()
}

fun JsonElement?.asStringOrNull(): String? = when (this) {
    is JsonObject -> this["value"].asStringOrNull()
    is JsonPrimitive -> contentOrNull
    else -> null
}

fun JsonElement?.asBooleanOrNull(): Boolean? = when (this) {
    is JsonObject -> this["value"].asBooleanOrNull()
    is JsonPrimitive -> booleanOrNull
    else -> null
}

fun JsonElement?.asIntOrNull(): Int? = when (this) {
    is JsonObject -> this["score"].asIntOrNull()
    is JsonPrimitive -> intOrNull
    else -> null
}

fun emptyJsonObject() = buildJsonObject { }

val JsonPrimitive.contentOrNull: String?
    get() = if (isString) content else content

val JsonPrimitive.booleanOrNull: Boolean?
    get() = contentOrNull?.let { value ->
        when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> value.toIntOrNull()?.let { it != 0 }
        }
    }

val JsonPrimitive.intOrNull: Int?
    get() = contentOrNull?.toIntOrNull()

fun JsonElement?.orEmpty(): JsonElement = this ?: JsonNull

