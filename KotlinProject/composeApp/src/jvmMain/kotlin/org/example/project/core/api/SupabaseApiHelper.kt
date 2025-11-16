package org.example.project.core.api

import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.delay
import org.example.project.core.config.SupabaseConfig

object SupabaseApiHelper {
    private var lastSessionCheck: Long = 0
    private const val SESSION_CHECK_INTERVAL = 5000L 

    
    suspend fun ensureValidSession(): Boolean {
        return try {
            val now = System.currentTimeMillis()
            if (now - lastSessionCheck < SESSION_CHECK_INTERVAL) {
                return true 
            }

            val supabase = SupabaseConfig.client
            val session = supabase.auth.currentSessionOrNull()

            if (session == null) {
                println("[API] No active session found")
                return false
            }

            
            val expiresIn = session.expiresIn
            val isExpiringSoon = expiresIn < 60 

            if (isExpiringSoon) {
                println("[API] Session expiring soon (${expiresIn}s remaining), refreshing...")
                try {
                    supabase.auth.refreshCurrentSession()
                    println("[API] Session refreshed successfully")
                } catch (e: Exception) {
                    println("[API] Failed to refresh session: ${e.message}")
                    return false
                }
            }

            lastSessionCheck = now
            true
        } catch (e: Exception) {
            println("[API] Error checking session: ${e.message}")
            false
        }
    }

    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 500,
        block: suspend () -> T,
    ): Result<T> {
        var lastException: Exception? = null
        var delayMs = initialDelayMs

        if (!ensureValidSession()) {
            return Result.failure(Exception("No valid session. Please sign in again."))
        }

        repeat(maxRetries) { attempt ->
            try {
                val result = block()
                if (attempt > 0) {
                    println("[API] Request succeeded after ${attempt + 1} attempts")
                }
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                val errorMessage = e.message ?: "Unknown error"

                when {
                    
                    errorMessage.contains("401") ||
                        errorMessage.contains("unauthorized", ignoreCase = true) -> {
                        println("[API] Authentication error, not retrying: $errorMessage")
                        return Result.failure(
                            Exception("Session expired. Please sign in again."),
                        )
                    }

                    
                    errorMessage.contains("403") ||
                        errorMessage.contains("forbidden", ignoreCase = true) -> {
                        println("[API] Access denied error, not retrying: $errorMessage")
                        return Result.failure(
                            Exception("Access denied. Please check your permissions."),
                        )
                    }

                    
                    errorMessage.contains("network", ignoreCase = true) ||
                        errorMessage.contains("timeout", ignoreCase = true) ||
                        errorMessage.contains("connection", ignoreCase = true) -> {
                        if (attempt < maxRetries - 1) {
                            println("[API] Network error on attempt ${attempt + 1}, retrying in ${delayMs}ms...")
                            delay(delayMs)
                            delayMs *= 2 
                        }
                    }

                    
                    else -> {
                        if (attempt < maxRetries - 1) {
                            println("[API] Request failed on attempt ${attempt + 1}: $errorMessage, retrying...")
                            delay(delayMs)
                            delayMs *= 2
                        }
                    }
                }
            }
        }

        println("[API] Request failed after $maxRetries attempts")
        return Result.failure(
            lastException ?: Exception("Request failed after $maxRetries attempts"),
        )
    }

    fun isReady(): Boolean {
        return SupabaseConfig.isConfigured()
    }

    suspend fun getCurrentUserId(): String? {
        return try {
            val session = SupabaseConfig.client.auth.currentSessionOrNull()
            session?.user?.id
        } catch (e: Exception) {
            println("[API] Error getting current user ID: ${e.message}")
            null
        }
    }
}

