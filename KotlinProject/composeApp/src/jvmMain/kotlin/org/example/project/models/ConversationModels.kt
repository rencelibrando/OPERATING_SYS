package org.example.project.models

/**
 * Data class representing a conversation turn for UI.
 */
data class ConversationTurnUI(
    val role: String, // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
