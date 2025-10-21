package org.example.project.core.config

import java.io.File
import java.util.Properties

object EnvironmentLoader {
    private var loaded = false
    
    fun loadEnvironmentVariables() {
        if (loaded) return
        
        try {
            // Try to load from .env file in the project root
            val envFile = File(".env")
            if (envFile.exists()) {
                val properties = Properties()
                envFile.inputStream().use { properties.load(it) }
                
                // Set system properties for environment variables
                properties.forEach { key, value ->
                    System.setProperty(key.toString(), value.toString())
                }
            }
            
            // Also try to load from composeApp/.env
            val composeAppEnvFile = File("composeApp/.env")
            if (composeAppEnvFile.exists()) {
                val properties = Properties()
                composeAppEnvFile.inputStream().use { properties.load(it) }
                
                properties.forEach { key, value ->
                    System.setProperty(key.toString(), value.toString())
                }
            }
            
            loaded = true
        } catch (e: Exception) {
            println("Warning: Could not load .env file: ${e.message}")
        }
    }
    
    fun getEnvironmentVariable(key: String): String? {
        loadEnvironmentVariables()
        
        // First try system properties (from .env file)
        val systemProperty = System.getProperty(key)
        if (systemProperty != null) {
            return systemProperty
        }
        
        // Fall back to actual environment variables
        return System.getenv(key)
    }
}
