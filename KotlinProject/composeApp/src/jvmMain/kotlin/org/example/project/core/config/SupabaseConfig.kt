package org.example.project.core.config

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
    
    private const val SUPABASE_URL = "https://tgsivldflzyydwjgoqhd.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRnc2l2bGRmbHp5eWR3amdvcWhkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgxNzAzODIsImV4cCI6MjA3Mzc0NjM4Mn0.aPD7Qgv_u45tgxe1-w5CXnRjFEAqxH9F9W_YxPlTP6Y"
    
    const val EMAIL_REDIRECT_URL: String = "https://rencelibrando.github.io/OPERATING_SYS/auth/callback.html"
    
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
    fun isConfigured(): Boolean {
        return SUPABASE_URL.isNotBlank() && 
               SUPABASE_ANON_KEY.isNotBlank() &&
               SUPABASE_ANON_KEY.startsWith("eyJ") 
    }
    fun getConfigStatus(): Map<String, Any> {
        return mapOf(
            "url_configured" to SUPABASE_URL.isNotBlank(),
            "key_configured" to (SUPABASE_ANON_KEY.isNotBlank() && SUPABASE_ANON_KEY.startsWith("eyJ")),
            "is_configured" to isConfigured(),
            "supabase_url" to SUPABASE_URL,
            "supabase_key_preview" to if (SUPABASE_ANON_KEY.length > 20) "${SUPABASE_ANON_KEY.take(20)}..." else SUPABASE_ANON_KEY,
            "credentials_source" to "embedded_in_code"
        )
    }
}
