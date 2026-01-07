package org.example.project.models

import java.util.UUID

/**
 * Data class representing a conversation turn for UI.
 * Supports real-time streaming with interim transcripts.
 */
data class ConversationTurnUI(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFinal: Boolean = true, // false = interim/streaming transcript
    val isStreaming: Boolean = false, // true when actively receiving updates
)
