package org.example.project.data.config

/**
 * Supabase configuration and client setup for WordBridge
 * Simplified version without dependencies for now
 */
object SupabaseConfig {
    
    // Environment configuration
    enum class Environment { DEVELOPMENT, STAGING, PRODUCTION }
    
    private val currentEnvironment = Environment.DEVELOPMENT
    
    // Supabase configuration - using hardcoded values for now
    val supabaseUrl: String = "https://tgsivldflzyydwjgoqhd.supabase.co"
    val supabaseAnonKey: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRnc2l2bGRmbHp5eWR3amdvcWhkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgxNzAzODIsImV4cCI6MjA3Mzc0NjM4Mn0.aPD7Qgv_u45tgxe1-w5CXnRjFEAqxH9F9W_YxPlTP6Y"
    
    // OpenAI configuration - will be added later
    val openaiApiKey: String? = null
    val openaiOrgId: String? = null
    
    val isDebugMode = currentEnvironment == Environment.DEVELOPMENT
    

    
    /**
     * Validates that all required configuration is present
     */
    fun validateConfiguration(): Result<Unit> {
        return try {
            require(supabaseUrl.isNotBlank()) { "SUPABASE_URL cannot be blank" }
            require(supabaseAnonKey.isNotBlank()) { "SUPABASE_ANON_KEY cannot be blank" }
            
            println("✅ Supabase configuration is valid")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Gets configuration summary for debugging
     */
    fun getConfigSummary(): Map<String, String> {
        return mapOf(
            "environment" to currentEnvironment.name,
            "supabase_url" to supabaseUrl,
            "has_anon_key" to supabaseAnonKey.isNotBlank().toString(),
            "has_openai_key" to (openaiApiKey != null).toString(),
            "debug_mode" to isDebugMode.toString()
        )
    }
}
