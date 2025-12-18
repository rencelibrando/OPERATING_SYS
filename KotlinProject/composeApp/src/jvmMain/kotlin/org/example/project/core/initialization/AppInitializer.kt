package org.example.project.core.initialization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.example.project.core.ai.BackendManager
import org.example.project.core.config.AIBackendConfig
import org.example.project.core.config.SupabaseConfig
import org.example.project.core.utils.PreferencesManager


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
            
            
            onProgress("Connecting to Supabase...", 0.1f)
            initializeSupabase()
            delay(500)
            
            
            if (AIBackendConfig.AUTO_START_ON_LAUNCH) {
                val backendReady = setupAIBackend(onProgress)
                if (!backendReady) {
                    println("[Init] Warning: AI backend failed to start automatically")
                }
            } else {
                println("[Init] Skipping AI backend auto-start (AUTO_START_ON_LAUNCH=false)")
                onProgress("AI tutor will start when first used", 0.6f)
            }
            
            
            onProgress("Initializing cache...", 0.85f)
            initializeCache()
            delay(500)
            
            
            onProgress("Loading resources...", 0.90f)
            preLoadResources()
            delay(500)
            
            
            onProgress("Preparing interface...", 0.95f)
            delay(500)
            
            isInitialized = true
            onProgress("Ready!", 1f)
            
            println("[Init] App initialization complete")
        }
    }
    
    private fun initializeSupabase() {
        try {
            println(" Initializing Supabase connection...")
            
            val isConfigured = SupabaseConfig.isConfigured()
            println(" Supabase ${if (isConfigured) "connected" else "not configured"}")
        } catch (e: Exception) {
            println(" Supabase initialization warning: ${e.message}")
            
        }
    }
    

    private suspend fun setupAIBackend(
        onProgress: (String, Float) -> Unit
    ): Boolean {
        return try {
            
            val cachedSetup = PreferencesManager.getCachedBackendSetupCompleted()
            val isSetupValid = BackendManager.verifyBackendSetup()
            
            if (cachedSetup && isSetupValid) {
                println("[Init] Backend setup found in cache and verified")
                
                
                if (BackendManager.isRunning()) {
                    println("[Init] Backend is already running")
                    onProgress("Backend ready", 0.7f)
                    return true
                } else {
                    onProgress("Starting backend server...", 0.6f)
                    val started = BackendManager.startBackend()
                    if (started) {
                        onProgress("Backend ready", 0.7f)
                        return true
                    } else {
                        println("[Init] Failed to start backend, will re-setup")
                        PreferencesManager.clearBackendSetupCache()
                    }
                }
            }
            
            
            println("[Init] Performing full backend setup...")
            
            
            var backendSetupSuccess = BackendManager.setupBackendEnvironment { step, progress ->
                val mappedProgress = 0.15f + (progress * 0.6f)
                onProgress(step, mappedProgress)
            }
            
            if (!backendSetupSuccess) {
                println("[Init] Backend environment setup failed")
                return false
            }
            
            
            onProgress("Starting backend server...", 0.7f)
            backendSetupSuccess = BackendManager.startBackend()
            
            if (backendSetupSuccess) {
                
                PreferencesManager.cacheBackendSetupCompleted(true)
                println("[Init] Backend setup completed and cached")
            } else {
                println("[Init] Backend server failed to start")
                PreferencesManager.clearBackendSetupCache()
            }
            
            backendSetupSuccess
        } catch (e: Exception) {
            println("[Init] Backend setup error: ${e.message}")
            e.printStackTrace()
            PreferencesManager.clearBackendSetupCache()
            false
        }
    }
    
    private fun initializeCache() {
        try {
            println("Initializing cache systems...")
            println("Cache initialized")
        } catch (e: Exception) {
            println("Cache initialization warning: ${e.message}")
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

