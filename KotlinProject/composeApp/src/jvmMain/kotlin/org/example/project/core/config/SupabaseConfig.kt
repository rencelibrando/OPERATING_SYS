package org.example.project.core.config

import io.github.cdimascio.dotenv.dotenv
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
    private val dotenv = try {
        dotenv {
            ignoreIfMissing = true
            directory = "KotlinProject/composeApp" // Look for .env in KotlinProject/composeApp directory
        }
    } catch (e: Exception) {
        println("Debug: Failed to load .env from KotlinProject/composeApp directory, trying composeApp directory")
        try {
            dotenv {
                ignoreIfMissing = true
                directory = "composeApp"
            }
        } catch (e2: Exception) {
            println("Debug: Failed to load .env from composeApp directory, trying current directory")
            dotenv {
                ignoreIfMissing = true
            }
        }
    }
    
    // Try to get values from .env file first, then fall back to system environment variables
    private val SUPABASE_URL = dotenv["SUPABASE_URL"] ?: System.getenv("SUPABASE_URL") ?: ""
    private val SUPABASE_ANON_KEY = dotenv["SUPABASE_ANON_KEY"] ?: dotenv["SUPABASE_KEY"] ?: System.getenv("SUPABASE_ANON_KEY") ?: System.getenv("SUPABASE_KEY") ?: ""
    
    init {
        val urlSource = if (dotenv["SUPABASE_URL"] != null) ".env file" else if (System.getenv("SUPABASE_URL") != null) "system environment" else "not found"
        val keySource = if (dotenv["SUPABASE_ANON_KEY"] != null || dotenv["SUPABASE_KEY"] != null) ".env file" else if (System.getenv("SUPABASE_ANON_KEY") != null || System.getenv("SUPABASE_KEY") != null) "system environment" else "not found"
        
        println("Debug: SUPABASE_URL = ${if (SUPABASE_URL.isNotEmpty()) "LOADED from $urlSource: ${SUPABASE_URL.take(20)}..." else "EMPTY"}")
        println("Debug: SUPABASE_ANON_KEY = ${if (SUPABASE_ANON_KEY.isNotEmpty()) "LOADED from $keySource: ${SUPABASE_ANON_KEY.take(20)}..." else "EMPTY"}")
        println("Debug: Current working directory: ${System.getProperty("user.dir")}")
    }
    
    const val EMAIL_REDIRECT_URL: String =
        "https://rencelibrando.github.io/OPERATING_SYS/auth/callback.html"

    val client =
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
            }
            install(Postgrest)
            install(Storage)

            // Enable HTTP logging
            defaultLogLevel = io.github.jan.supabase.logging.LogLevel.DEBUG
        }

    fun isConfigured(): Boolean {
        return SUPABASE_URL.isNotBlank() &&
            SUPABASE_ANON_KEY.isNotBlank() &&
            (SUPABASE_ANON_KEY.startsWith("eyJ") || SUPABASE_ANON_KEY.startsWith("sb_"))
    }

    fun getConfigStatus(): Map<String, Any> {
        val urlSource = if (dotenv["SUPABASE_URL"] != null) ".env file" else if (System.getenv("SUPABASE_URL") != null) "system environment" else "not found"
        val keySource = if (dotenv["SUPABASE_ANON_KEY"] != null || dotenv["SUPABASE_KEY"] != null) ".env file" else if (System.getenv("SUPABASE_ANON_KEY") != null || System.getenv("SUPABASE_KEY") != null) "system environment" else "not found"
        
        return mapOf(
            "url_configured" to SUPABASE_URL.isNotBlank(),
            "key_configured" to (SUPABASE_ANON_KEY.isNotBlank() && (SUPABASE_ANON_KEY.startsWith("eyJ") || SUPABASE_ANON_KEY.startsWith("sb_"))),
            "is_configured" to isConfigured(),
            "supabase_url" to SUPABASE_URL,
            "supabase_key_preview" to
                if (SUPABASE_ANON_KEY.length > 20) "${SUPABASE_ANON_KEY.take(20)}..." else SUPABASE_ANON_KEY,
            "credentials_source" to "environment_variables",
            "url_source" to urlSource,
            "key_source" to keySource,
        )
    }
}
