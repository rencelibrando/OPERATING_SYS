package org.example.project.core.config

import io.github.cdimascio.dotenv.dotenv
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import java.io.File

object SupabaseConfig {
    /**
     * Find the .env file in various locations (development and installed app).
     */
    private fun findEnvFile(): File? {
        // Write debug info to a log file for troubleshooting
        val logFile = File(System.getProperty("user.home"), "AppData\\Local\\WordBridge\\env-debug.log")
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText("=== .env File Search Debug ===\n")
            logFile.appendText("Time: ${java.time.LocalDateTime.now()}\n")
            logFile.appendText("Working Directory: ${System.getProperty("user.dir")}\n")
            logFile.appendText("User Home: ${System.getProperty("user.home")}\n\n")
        } catch (e: Exception) {
            // Ignore log file errors
        }
        val currentDir = File(System.getProperty("user.dir"))

        // List of possible .env locations
        val possibleLocations = mutableListOf<File>()

        // 1. Development locations
        possibleLocations.add(File(currentDir, ".env"))
        possibleLocations.add(File(currentDir, "env.config")) // Backup non-hidden name
        possibleLocations.add(File(currentDir, "KotlinProject/composeApp/.env"))
        possibleLocations.add(File(currentDir, "composeApp/.env"))

        // 2. Check JAR resources (where .env is bundled)
        // Extract JAR resources to temp file if found
        try {
            // Prefer .env over env.config
            val resourceUrl = SupabaseConfig::class.java.classLoader.getResource(".env")
            val resourceUrl2 = SupabaseConfig::class.java.classLoader.getResource("env.config")

            // Extract .env with standard filename for better dotenv compatibility
            val tempEnvFile = File(System.getProperty("java.io.tmpdir"), "wordbridge.env.tmp")
            var extracted = false

            if (resourceUrl != null) {
                try {
                    resourceUrl.openStream().use { input ->
                        tempEnvFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // Rename to .env so dotenv library can find it easily
                    val finalEnvFile = File(System.getProperty("java.io.tmpdir"), ".env")
                    if (finalEnvFile.exists()) finalEnvFile.delete()
                    tempEnvFile.renameTo(finalEnvFile)
                    if (finalEnvFile.exists()) {
                        possibleLocations.add(0, finalEnvFile) // Add at beginning with highest priority
                        println("Debug: Extracted .env from JAR resources to: ${finalEnvFile.absolutePath}")
                        extracted = true
                    }
                } catch (e: Exception) {
                    println("Debug: Error extracting .env from JAR: ${e.message}")
                }
            }

            // Fall back to env.config if .env not found
            if (!extracted && resourceUrl2 != null) {
                try {
                    resourceUrl2.openStream().use { input ->
                        tempEnvFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val finalEnvFile = File(System.getProperty("java.io.tmpdir"), ".env")
                    if (finalEnvFile.exists()) finalEnvFile.delete()
                    tempEnvFile.renameTo(finalEnvFile)
                    if (finalEnvFile.exists()) {
                        possibleLocations.add(0, finalEnvFile)
                        println("Debug: Extracted env.config from JAR resources to: ${finalEnvFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    println("Debug: Error extracting env.config from JAR: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Debug: Could not access JAR resources: ${e.message}")
            e.printStackTrace()
        }

        // 3. Installed application locations (Windows)
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        possibleLocations.add(File(programFiles, "WordBridge/.env"))

        val programFiles86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        possibleLocations.add(File(programFiles86, "WordBridge/.env"))

        // 4. User installation location (if per-user install)
        val userHome = System.getProperty("user.home")
        possibleLocations.add(File(userHome, "AppData\\Local\\WordBridge\\.env"))

        // 5. Check location relative to executable (for installed apps)
        try {
            val codeSource = SupabaseConfig::class.java.protectionDomain.codeSource?.location
            if (codeSource != null) {
                val jarFile = File(codeSource.toURI())
                val executableDir = if (jarFile.isDirectory) jarFile else jarFile.parentFile

                val debugMsg1 = "Debug: Code source location: $codeSource"
                val debugMsg2 = "Debug: Executable directory: ${executableDir.absolutePath}"
                println(debugMsg1)
                println(debugMsg2)
                try {
                    logFile.appendText("$debugMsg1\n")
                    logFile.appendText("$debugMsg2\n")
                } catch (e: Exception) {
                }

                // Check in executable directory
                possibleLocations.add(executableDir.resolve(".env"))

                // For Compose Desktop MSI installations:
                // Structure is typically: INSTALL_DIR/app/bin/WordBridge.exe
                // Resources from appResourcesRootDir are in: INSTALL_DIR/app/resources/

                // Check in bin directory (where exe typically is)
                if (executableDir.name == "bin") {
                    executableDir.parentFile?.let { appDir ->
                        val debugMsg3 = "Debug: Found 'app' directory: ${appDir.absolutePath}"
                        println(debugMsg3)
                        try {
                            logFile.appendText("$debugMsg3\n")
                        } catch (e: Exception) {
                        }
                        // Resources are in app/resources/
                        possibleLocations.add(appDir.resolve("resources/.env"))
                        possibleLocations.add(appDir.resolve("resources/env.config")) // Backup non-hidden name
                        possibleLocations.add(appDir.resolve(".env"))
                        // Check installation root (one level up from app)
                        appDir.parentFile?.let { installRoot ->
                            val debugMsg4 = "Debug: Installation root: ${installRoot.absolutePath}"
                            println(debugMsg4)
                            try {
                                logFile.appendText("$debugMsg4\n")
                            } catch (e: Exception) {
                            }
                            possibleLocations.add(installRoot.resolve("app/resources/.env"))
                            possibleLocations.add(installRoot.resolve("app/resources/env.config")) // Backup
                            possibleLocations.add(installRoot.resolve("resources/.env"))
                            possibleLocations.add(installRoot.resolve("resources/env.config")) // Backup
                            possibleLocations.add(installRoot.resolve(".env"))
                        }
                    }
                }

                // Also check parent directories in general
                executableDir.parentFile?.let { parent ->
                    possibleLocations.add(parent.resolve("resources/.env"))
                    possibleLocations.add(parent.resolve("app/resources/.env"))
                    possibleLocations.add(parent.resolve(".env"))

                    parent.parentFile?.let { installRoot ->
                        possibleLocations.add(installRoot.resolve("app/resources/.env"))
                        possibleLocations.add(installRoot.resolve("resources/.env"))
                        possibleLocations.add(installRoot.resolve(".env"))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors in finding code source
            println("Debug: Error finding code source: ${e.message}")
            e.printStackTrace()
        }

        // Debug: Print all checked locations
        val debugMsg5 = "Debug: Checking .env file in ${possibleLocations.size} locations:"
        println(debugMsg5)
        try {
            logFile.appendText("$debugMsg5\n")
        } catch (e: Exception) {
        }

        possibleLocations.forEach { location ->
            val exists = location.exists() && location.isFile
            val statusMsg = "Debug:   ${if (exists) "✓" else "✗"} ${location.absolutePath} ${if (exists) "[FOUND]" else ""}"
            println(statusMsg)
            try {
                logFile.appendText("$statusMsg\n")
            } catch (e: Exception) {
            }
        }

        // Return the first location that exists
        val found = possibleLocations.firstOrNull { it.exists() && it.isFile }
        if (found != null) {
            val successMsg = "Debug: Using .env file from: ${found.absolutePath}"
            println(successMsg)
            try {
                logFile.appendText("$successMsg\n")
            } catch (e: Exception) {
            }
        } else {
            val errorMsg = "Debug: No .env file found in any checked location!"
            println(errorMsg)
            try {
                logFile.appendText("$errorMsg\n")
            } catch (e: Exception) {
            }
        }

        try {
            logFile.appendText("=== End Debug ===\n\n")
        } catch (e: Exception) {
        }

        return found
    }

    private val envFile = findEnvFile()

    private val dotenv =
        try {
            if (envFile != null && envFile.exists()) {
                val envFileName = envFile.name
                println("Debug: Loading .env from: ${envFile.absolutePath}")
                println("Debug: Using file name: $envFileName")
                println("Debug: File exists: ${envFile.exists()}, size: ${envFile.length()} bytes")

                // Read the file content to verify it's correct
                try {
                    val content = envFile.readText()
                    println("Debug: File content preview (first 200 chars): ${content.take(200)}")
                    // Check if it contains SUPABASE variables
                    val hasSupabaseUrl = content.contains("SUPABASE_URL")
                    val hasSupabaseKey = content.contains("SUPABASE_KEY") || content.contains("SUPABASE_ANON_KEY")
                    println("Debug: File contains SUPABASE_URL: $hasSupabaseUrl, SUPABASE_KEY: $hasSupabaseKey")
                } catch (e: Exception) {
                    println("Debug: Error reading file: ${e.message}")
                    e.printStackTrace()
                }

                // Load dotenv directly from the file's directory with the exact filename
                dotenv {
                    ignoreIfMissing = false
                    directory = envFile.parentFile.absolutePath
                    filename = envFileName // Use the actual filename (e.g., "wordbridge.env.config" or ".env")
                }
            } else {
                println("Debug: .env file not found or doesn't exist, trying default locations")
                dotenv {
                    ignoreIfMissing = true
                    directory = "KotlinProject/composeApp"
                }
            }
        } catch (e: Exception) {
            println("Debug: Failed to load .env from ${envFile?.absolutePath}: ${e.message}")
            e.printStackTrace()
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

    // Public property for accessing the Supabase URL
    val supabaseUrl: String = SUPABASE_URL

    init {
        val urlSource =
            if (dotenv["SUPABASE_URL"] != null) {
                ".env file"
            } else if (System.getenv("SUPABASE_URL") != null) {
                "system environment"
            } else {
                "not found"
            }
        val keySource =
            if (dotenv["SUPABASE_ANON_KEY"] != null || dotenv["SUPABASE_KEY"] != null) {
                ".env file"
            } else if (System.getenv("SUPABASE_ANON_KEY") != null || System.getenv("SUPABASE_KEY") != null) {
                "system environment"
            } else {
                "not found"
            }

        println(
            "Debug: SUPABASE_URL = ${if (SUPABASE_URL.isNotEmpty()) "LOADED from $urlSource: ${SUPABASE_URL.take(20)}..." else "EMPTY"}",
        )
        println(
            "Debug: SUPABASE_ANON_KEY = ${if (SUPABASE_ANON_KEY.isNotEmpty()) {
                "LOADED from $keySource: ${SUPABASE_ANON_KEY.take(
                    20,
                )}..."
            } else {
                "EMPTY"
            }}",
        )
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
        val urlSource =
            if (dotenv["SUPABASE_URL"] != null) {
                ".env file"
            } else if (System.getenv("SUPABASE_URL") != null) {
                "system environment"
            } else {
                "not found"
            }
        val keySource =
            if (dotenv["SUPABASE_ANON_KEY"] != null || dotenv["SUPABASE_KEY"] != null) {
                ".env file"
            } else if (System.getenv("SUPABASE_ANON_KEY") != null || System.getenv("SUPABASE_KEY") != null) {
                "system environment"
            } else {
                "not found"
            }

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
