package org.example.project.data.repository

import org.example.project.domain.model.*

/**
 * Repository interface for AI Chat operations
 */
interface AIChatRepository {
    
    // Chat Sessions
    suspend fun createChatSession(userId: String, botId: String): Result<ChatSession>
    suspend fun getChatSession(sessionId: String): Result<ChatSession?>
    suspend fun getUserChatSessions(userId: String): Result<List<ChatSession>>
    suspend fun updateChatSession(session: ChatSession): Result<ChatSession>
    suspend fun deleteChatSession(sessionId: String): Result<Unit>
    
    // Chat Messages
    suspend fun sendMessage(sessionId: String, messageText: String): Result<ChatMessage>
    suspend fun getChatMessages(sessionId: String): Result<List<ChatMessage>>
    suspend fun getLatestMessages(sessionId: String, limit: Int = 50): Result<List<ChatMessage>>
    suspend fun deleteMessage(messageId: String): Result<Unit>
    
    // AI Integration
    suspend fun generateAIResponse(
        sessionId: String, 
        userMessage: String, 
        context: Map<String, Any> = emptyMap()
    ): Result<String>
    
    // Chat Bots
    suspend fun getAvailableBots(): Result<List<ChatBot>>
    suspend fun getChatBot(botId: String): Result<ChatBot?>
    
    // Real-time subscriptions
    suspend fun subscribeToChatMessages(sessionId: String, onMessage: (ChatMessage) -> Unit): Result<Unit>
    suspend fun unsubscribeFromChatMessages(sessionId: String): Result<Unit>
}

/**
 * Represents AI response metadata
 */
data class AIResponseMetadata(
    val model: String,
    val tokensUsed: Int,
    val responseTime: Long,
    val timestamp: Long,
    val cost: Double? = null
)
