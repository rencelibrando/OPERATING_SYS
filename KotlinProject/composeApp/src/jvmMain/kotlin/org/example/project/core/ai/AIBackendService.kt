package org.example.project.core.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.example.project.core.config.AIBackendConfig
import org.example.project.core.utils.ErrorLogger

private const val LOG_TAG = "AIBackendService.kt"

class AIBackendService {
    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = false
                        encodeDefaults = true
                    },
                )
            }

            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = AIBackendConfig.REQUEST_TIMEOUT_MS
                connectTimeoutMillis = AIBackendConfig.CONNECTION_TIMEOUT_MS
            }
        }

    suspend fun healthCheck(): Result<AIHealthResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl(AIBackendConfig.HEALTH_ENDPOINT)
            println("Checking AI backend health: $url")

            val response = client.get(url)

            if (response.status.value in 200..299) {
                val healthResponse = response.body<AIHealthResponse>()
                println("AI backend is healthy: ${healthResponse.status}")
                healthResponse
            } else {
                throw Exception("Health check failed with status: ${response.status}")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "AI backend health check failed")
        }

    suspend fun sendChatMessage(request: AIChatRequest): Result<AIChatResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl(AIBackendConfig.CHAT_ENDPOINT)
            println("Sending chat message to AI backend: $url")
            println("   User: ${request.userContext.userId}")
            println("   Provider: ${request.provider}")
            println("   Message length: ${request.message.length}")

            val response =
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.value in 200..299) {
                val chatResponse = response.body<AIChatResponse>()
                println("Received AI response: ${chatResponse.message.take(50)}...")
                println("   Tokens used: ${chatResponse.tokensUsed}")
                chatResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Chat request failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "AI chat request failed")
        }

    suspend fun saveChatHistory(request: SaveHistoryRequest): Result<SaveHistoryResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl(AIBackendConfig.SAVE_HISTORY_ENDPOINT)
            println("Saving chat history for session: ${request.sessionId}")
            println("   Message count: ${request.messages.size}")

            val response =
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.value in 200..299) {
                val saveResponse = response.body<SaveHistoryResponse>()
                println("Chat history saved successfully")
                println("   Original size: ${saveResponse.originalSize} bytes")
                println("   Compressed size: ${saveResponse.compressedSize} bytes")
                println("   Compression ratio: ${saveResponse.compressionRatio}%")
                saveResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Save history failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            ErrorLogger.logException(LOG_TAG, error, "Failed to save chat history")
        }

    suspend fun loadChatHistory(request: LoadHistoryRequest): Result<LoadHistoryResponse> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl(AIBackendConfig.LOAD_HISTORY_ENDPOINT)
            println("Loading chat history for session: ${request.sessionId}")

            val response =
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.value in 200..299) {
                val loadResponse = response.body<LoadHistoryResponse>()
                println("   Chat history loaded successfully")
                println("   Message count: ${loadResponse.messageCount}")
                println("   Compression ratio: ${loadResponse.compressionRatio}%")
                loadResponse
            } else {
                val errorBody = response.body<String>()
                throw Exception("Load history failed with status: ${response.status}, body: $errorBody")
            }
        }.onFailure { error ->
            println(" Failed to load chat history: ${error.message}")
            error.printStackTrace()
        }

    suspend fun deleteChatHistory(sessionId: String): Result<Boolean> =
        runCatching {
            val url = AIBackendConfig.getEndpointUrl("${AIBackendConfig.DELETE_HISTORY_ENDPOINT}/$sessionId")
            println("Deleting chat history for session: $sessionId")

            val response =
                client.request(url) {
                    method = io.ktor.http.HttpMethod.Delete
                }

            if (response.status.value in 200..299) {
                println("Chat history deleted successfully")
                true
            } else {
                throw Exception("Delete history failed with status: ${response.status}")
            }
        }.onFailure { error ->
            println("Failed to delete chat history: ${error.message}")
        }

    fun close() {
        client.close()
    }
}
