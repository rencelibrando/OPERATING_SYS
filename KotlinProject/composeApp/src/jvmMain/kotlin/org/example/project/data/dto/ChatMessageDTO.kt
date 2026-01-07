package org.example.project.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDTO(
    @SerialName("id")
    val id: String, // Database uses UUID but we'll handle as String
    @SerialName("session_id")
    val sessionId: String, // Database uses UUID but we'll handle as String
    @SerialName("sender_type")
    val senderType: String, // Maps to role in domain model
    @SerialName("message_text")
    val messageText: String, // Maps to content in domain model
    @SerialName("metadata")
    val metadata: Map<String, String>? = emptyMap(),
    @SerialName("created_at")
    val createdAt: String? = null, // Database field name
)
