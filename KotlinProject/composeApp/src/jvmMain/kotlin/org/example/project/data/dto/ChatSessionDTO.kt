package org.example.project.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.project.domain.model.ChatSession
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class ChatSessionDTO(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("bot_id")
    val botId: String,
    @SerialName("title")
    val title: String? = null,
    @SerialName("status")
    val status: String = "active",
    @SerialName("message_count")
    val messageCount: Int = 0,
    @SerialName("total_duration")
    val totalDuration: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
) {
    fun toDomain(): ChatSession {
        val startTimeMillis = createdAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis()
        val endTimeMillis = if (status == "archived") {
            updatedAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis()
        } else {
            null
        }
        
        return ChatSession(
            id = id,
            title = title ?: "Chat Session",
            startTime = startTimeMillis,
            endTime = endTimeMillis,
            messageCount = messageCount,
            topic = "General Conversation",
            difficulty = "Intermediate",
        )
    }

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            val zonedDateTime = ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                Instant.parse(timestamp).toEpochMilli()
            } catch (e2: Exception) {
                println("Failed to parse timestamp: $timestamp, using current time")
                System.currentTimeMillis()
            }
        }
    }

    companion object {
        fun fromDomain(
            session: ChatSession,
            userId: String,
            botId: String,
        ): ChatSessionDTO {
            return ChatSessionDTO(
                id = session.id,
                userId = userId,
                botId = botId,
                title = session.title,
                status = if (session.endTime != null) "archived" else "active",
                messageCount = session.messageCount,
                totalDuration = 0,
            )
        }
    }
}

