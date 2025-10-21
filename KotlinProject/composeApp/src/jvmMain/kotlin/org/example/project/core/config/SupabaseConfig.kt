package org.example.project.core.config

import io.github.cdimascio.dotenv.dotenv
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
    private val dotenv = dotenv {
        ignoreIfMissing = true
        directory = "composeApp" // Look for .env in composeApp directory
    }
    
    private val SUPABASE_URL = dotenv["SUPABASE_URL"] ?: ""
    private val SUPABASE_ANON_KEY = dotenv["SUPABASE_ANON_KEY"] ?: ""
    
    init {
        println("Debug: SUPABASE_URL = ${if (SUPABASE_URL.isNotEmpty()) "LOADED" else "EMPTY"}")
        println("Debug: SUPABASE_ANON_KEY = ${if (SUPABASE_ANON_KEY.isNotEmpty()) "LOADED" else "EMPTY"}")
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
        return mapOf(
            "url_configured" to SUPABASE_URL.isNotBlank(),
            "key_configured" to (SUPABASE_ANON_KEY.isNotBlank() && (SUPABASE_ANON_KEY.startsWith("eyJ") || SUPABASE_ANON_KEY.startsWith("sb_"))),
            "is_configured" to isConfigured(),
            "supabase_url" to SUPABASE_URL,
            "supabase_key_preview" to
                if (SUPABASE_ANON_KEY.length > 20) "${SUPABASE_ANON_KEY.take(20)}..." else SUPABASE_ANON_KEY,
            "credentials_source" to "environment_variables",
        )
    }
}
