package org.example.project.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentV1SettingsMessage(
    val type: String = "Settings",
    val audio: AgentV1AudioConfig,
    val agent: AgentV1Agent
)

@Serializable
data class AgentV1AudioConfig(
    val input: AgentV1AudioInput,
    val output: AgentV1AudioOutput
)

@Serializable
data class AgentV1AudioInput(
    val encoding: String,
    @SerialName("sample_rate")
    val sampleRate: Int
)

@Serializable
data class AgentV1AudioOutput(
    val encoding: String,
    @SerialName("sample_rate")
    val sampleRate: Int,
    val container: String = "wav"
)

@Serializable
data class AgentV1Agent(
    val language: String,
    val listen: AgentV1Listen,
    val think: AgentV1Think,
    val speak: AgentV1SpeakProviderConfig? = null,
    val greeting: String? = null
)

@Serializable
data class AgentV1Listen(
    val provider: AgentV1ListenProvider
)

@Serializable
data class AgentV1ListenProvider(
    val type: String = "deepgram",
    val model: String,
    // Agent API supported options for Deepgram provider:
    @SerialName("smart_format")
    val smartFormat: Boolean? = null,  // Improves transcript readability (default: false)
    val keyterms: List<String>? = null, // Keywords to boost recognition (nova-3 'en' only)
    val endpointing: Int? = null,  // Milliseconds of silence to trigger end of speech (default: 10, recommended: 300)
    @SerialName("interim_results")
    val interimResults: Boolean? = null  // Enable partial transcripts for responsive UI
)

@Serializable
data class AgentV1Think(
    val provider: AgentV1ThinkProvider,
    val prompt: String
)

@Serializable
data class AgentV1ThinkProvider(
    val type: String,
    val model: String,
    val temperature: Float? = null
)

@Serializable
data class AgentV1SpeakProviderConfig(
    val provider: AgentV1SpeakProvider
)

@Serializable
data class AgentV1SpeakProvider(
    val type: String = "deepgram",
    val model: String
)

@Serializable
data class AgentV1KeepAliveMessage(
    val type: String = "KeepAlive"
)

@Serializable
data class AgentV1WelcomeMessage(
    val type: String,
    @SerialName("request_id")
    val requestId: String? = null
)

@Serializable
data class AgentV1SettingsAppliedMessage(
    val type: String
)

@Serializable
data class AgentV1ConversationTextMessage(
    val type: String,
    val role: String,
    val content: String
)

@Serializable
data class AgentV1UserStartedSpeakingMessage(
    val type: String
)

@Serializable
data class AgentV1AgentThinkingMessage(
    val type: String,
    val content: String? = null
)

@Serializable
data class AgentV1AgentStartedSpeakingMessage(
    val type: String
)

@Serializable
data class AgentV1AgentAudioDoneMessage(
    val type: String
)

@Serializable
data class AgentV1ErrorMessage(
    val type: String,
    val description: String,
    val code: String? = null
)

@Serializable
data class AgentV1WarningMessage(
    val type: String,
    val description: String,
    val code: String? = null
)

@Serializable
data class AgentV1FunctionCallRequestMessage(
    val type: String,
    val functions: List<AgentV1Function>
)

@Serializable
data class AgentV1Function(
    val id: String,
    val name: String,
    val arguments: String,
    @SerialName("client_side")
    val clientSide: Boolean
)

@Serializable
data class AgentV1FunctionCallResponseMessage(
    val type: String = "FunctionCallResponse",
    val id: String,
    val name: String,
    val content: String
)

@Serializable
data class AgentV1InjectAgentMessage(
    val type: String = "InjectAgentMessage",
    val content: String
)

@Serializable
data class AgentV1InjectUserMessage(
    val type: String = "InjectUserMessage",
    val content: String
)

@Serializable
data class AgentV1UpdatePromptMessage(
    val type: String = "UpdatePrompt",
    val prompt: String
)

@Serializable
data class AgentV1UpdateSpeakMessage(
    val type: String = "UpdateSpeak",
    val speak: AgentV1SpeakProviderConfig
)
