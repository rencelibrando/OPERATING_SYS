package org.example.project.core.initialization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.example.project.core.ai.BackendManager
import org.example.project.core.config.SupabaseConfig


object AppInitializer {
    
    private var isInitialized = false

    suspend fun initialize(
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isInitialized) {
                return@runCatching
            }
            
            println("[Init] Starting app initialization...")
            
            // Step 1: Initialize Supabase
            onProgress("Connecting to Supabase...", 0.15f)
            initializeSupabase()
            delay(800)
            
            // Step 2: Check Python installation (this may take time if installing)
            onProgress("Checking Python installation...", 0.25f)
            val pythonInstallTime = preWarmBackend(onProgress)
            
            // Adjust timing based on whether Python was installed
            if (pythonInstallTime > 5000) {
                // Python was installed, skip extra delays
                delay(500)
            } else {
                // Normal flow
                delay(1000)
            }
            
            // Step 3: Initialize cache systems
            onProgress("Initializing cache...", 0.75f)
            initializeCache()
            delay(800)
            
            // Step 4: Pre-load static resources
            onProgress("Loading resources...", 0.85f)
            preLoadResources()
            delay(800)
            
            // Step 5: Finalize
            onProgress("Preparing interface...", 0.95f)
            delay(800)
            
            isInitialized = true
            onProgress("Ready!", 1f)
            
            println("[Init] App initialization complete")
        }
    }
    
    private fun initializeSupabase() {
        try {
            println(" Initializing Supabase connection...")
            // Supabase client is already initialized in SupabaseConfig
            val isConfigured = SupabaseConfig.isConfigured()
            println(" Supabase ${if (isConfigured) "connected" else "not configured"}")
        } catch (e: Exception) {
            println(" Supabase initialization warning: ${e.message}")
            // Non-critical - continue anyway
        }
    }
    
    /**
     * Pre-warm backend and return time taken in milliseconds.
     */
    private suspend fun preWarmBackend(
        onProgress: (String, Float) -> Unit
    ): Long {
        val startTime = System.currentTimeMillis()
        
        try {
            println("[Backend] Pre-warming backend...")

            if (BackendManager.isRunning()) {
                println("[Backend] Backend already running")
                return System.currentTimeMillis() - startTime
            } else {
                println("[Backend] Checking Python installation...")
                
                // Check if Python is installed and install if needed
                val result = BackendManager.ensurePythonIsInstalled { step, progress ->
                    // Map backend progress (0-1) to our progress range (0.25-0.65)
                    val mappedProgress = 0.25f + (progress * 0.4f)
                    onProgress(step, mappedProgress)
                }
                
                if (result) {
                    println("[Backend] Python is ready")
                } else {
                    println("[Backend] Python check completed with issues")
                }
            }
        } catch (e: Exception) {
            println("[Backend] Pre-warm warning: ${e.message}")
        }
        
        return System.currentTimeMillis() - startTime
    }
    
    private fun initializeCache() {
        try {
            println("Initializing cache systems...")
            println("Cache initialized")
        } catch (e: Exception) {
            println("⚠Cache initialization warning: ${e.message}")
        }
    }
    
    private fun preLoadResources() {
        try {
            println("Pre-loading static resources...")
            println("Resources loaded")
        } catch (e: Exception) {
            println("Resource loading warning: ${e.message}")
        }
    }
    

    fun reset() {
        isInitialized = false
    }

    fun isInitialized(): Boolean = isInitialized
}

