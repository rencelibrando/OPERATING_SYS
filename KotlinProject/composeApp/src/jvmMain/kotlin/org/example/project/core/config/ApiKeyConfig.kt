package org.example.project.core.config

import java.io.File
import java.util.Properties

/**
 * Configuration loader for API keys and secrets.
 * Checks multiple sources in order:
 * 1. System environment variables
 * 2. .env file in project root
 * 3. config.properties file
 */
object ApiKeyConfig {
    private val properties = Properties()
    
    init {
        loadEnvFile()
        loadPropertiesFile()
    }
    
    private fun loadEnvFile() {
        try {
            // Print current working directory for debugging
            val workingDir = System.getProperty("user.dir")
            println("🔍 Working directory: $workingDir")
            
            // Try multiple possible locations for .env file
            val possiblePaths = listOf(
                ".env",
                "composeApp/.env",
                "KotlinProject/composeApp/.env",
                "../composeApp/.env"
            )
            
            for (path in possiblePaths) {
                val envFile = File(path)
                val absolutePath = envFile.absolutePath
                println("🔍 Checking: $absolutePath")
                
                if (envFile.exists()) {
                    println("✅ Found .env file at: $absolutePath")
                    envFile.readLines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val parts = trimmed.split("=", limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                val value = parts[1].trim()
                                properties[key] = value
                                println("   📝 Loaded: $key = ${if (key.contains("KEY")) "***" else value}")
                            }
                        }
                    }
                    println("✅ Successfully loaded configuration from .env file")
                    return
                }
            }
            
            println("⚠️ No .env file found in any checked location")
        } catch (e: Exception) {
            println("❌ Error loading .env file: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun loadPropertiesFile() {
        try {
            // Try multiple possible locations for config.properties
            val possiblePaths = listOf(
                "config.properties",
                "composeApp/config.properties",
                "KotlinProject/composeApp/config.properties"
            )
            
            for (path in possiblePaths) {
                val configFile = File(path)
                if (configFile.exists()) {
                    configFile.inputStream().use { properties.load(it) }
                    println("✅ Loaded configuration from config.properties at: ${configFile.absolutePath}")
                    return
                }
            }
        } catch (e: Exception) {
            println("⚠️ Could not load config.properties: ${e.message}")
        }
    }
    
    /**
     * Get API key with fallback order:
     * 1. System environment variable
     * 2. .env file
     * 3. config.properties file
     */
    fun getDeepgramApiKey(): String? {
        // First try system environment
        System.getenv("DEEPGRAM_API_KEY")?.let { 
            println("✅ Using DEEPGRAM_API_KEY from system environment")
            return it 
        }
        
        // Then try loaded properties (from .env or config.properties)
        properties.getProperty("DEEPGRAM_API_KEY")?.let { 
            println("✅ Using DEEPGRAM_API_KEY from config file")
            return it 
        }
        
        println("❌ DEEPGRAM_API_KEY not found in any configuration source")
        println("   Please set it in one of these locations:")
        println("   1. System environment variable: DEEPGRAM_API_KEY")
        println("   2. File: composeApp/.env")
        println("   3. File: composeApp/config.properties")
        return null
    }
    
    fun getElevenLabsApiKey(): String? {
        // First try system environment
        System.getenv("ELEVEN_LABS_API_KEY")?.let { 
            println("✅ Using ELEVEN_LABS_API_KEY from system environment")
            return it 
        }
        
        // Then try loaded properties (from .env or config.properties)
        properties.getProperty("ELEVEN_LABS_API_KEY")?.let { 
            println("✅ Using ELEVEN_LABS_API_KEY from config file")
            return it 
        }
        
        println("❌ ELEVEN_LABS_API_KEY not found in any configuration source")
        println("   Please set it in one of these locations:")
        println("   1. System environment variable: ELEVEN_LABS_API_KEY")
        println("   2. File: composeApp/.env")
        println("   3. File: composeApp/config.properties")
        return null
    }
    
    fun getSupabaseUrl(): String? {
        return System.getenv("SUPABASE_URL") 
            ?: properties.getProperty("SUPABASE_URL")
    }
    
    fun getSupabaseAnonKey(): String? {
        return System.getenv("SUPABASE_ANON_KEY") 
            ?: properties.getProperty("SUPABASE_ANON_KEY")
    }
}
