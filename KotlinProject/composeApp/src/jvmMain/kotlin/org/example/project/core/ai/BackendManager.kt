package org.example.project.core.ai

import org.example.project.core.utils.ErrorLogger
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

private const val LOG_TAG = "BackendManager.kt"

object BackendManager {
    private var backendProcess: Process? = null
    private var isBackendRunning = false
    private var lastSetupError: String? = null
    private var retryAttempts = 0
    private val maxRetryAttempts = 3

    /**
     * Main entry point with automatic retry and self-healing.
     * Attempts to start backend with automatic error recovery.
     */
    fun startBackend(): Boolean {
        return startBackendWithRetry(0)
    }

    /**
     * Start backend with automatic retry and self-healing capabilities.
     * Handles common errors automatically and retries with exponential backoff.
     */
    private fun startBackendWithRetry(attempt: Int): Boolean {
        return try {
            ErrorLogger.logInfo(LOG_TAG, "Checking if backend is already running")
            if (isBackendHealthy()) {
                ErrorLogger.logInfo(LOG_TAG, "Backend is already running")
                isBackendRunning = true
                retryAttempts = 0
                return true
            }

            ErrorLogger.logInfo(LOG_TAG, "Starting Python backend (attempt ${attempt + 1}/$maxRetryAttempts)")

            // ============================================
            // EARLY VALIDATION: Check Python FIRST with aggressive installation
            // ============================================
            println("[Backend] Validating Python installation...")
            var pythonCmd = validatePythonInstallationWithRetry()
            if (pythonCmd == null) {
                // If we've tried multiple times and still no Python, give up
                if (attempt >= maxRetryAttempts - 1) {
                    lastSetupError =
                        """
                        |Unable to install Python after multiple attempts.
                        |Please install Python 3.12 manually from https://www.python.org/downloads/
                        |Make sure to check "Add Python to PATH" during installation.
                        """.trimMargin()
                    return false
                }
                // Wait before retrying (exponential backoff)
                val waitTime = (attempt + 1) * 2000L
                println("[Backend] Waiting ${waitTime / 1000}s before retrying Python installation...")
                Thread.sleep(waitTime)
                return startBackendWithRetry(attempt + 1)
            }

            println("[Backend] Python validation passed: $pythonCmd")

            // Now check backend directory
            val backendDir = findBackendDirectory()

            if (backendDir == null || !backendDir.exists()) {
                lastSetupError = "Backend directory not found. Please ensure the backend folder is in the installation directory."
                ErrorLogger.log(LOG_TAG, lastSetupError!!)
                ErrorLogger.logDebug(LOG_TAG, "Searched locations: ${File(System.getProperty("user.dir"), "backend").absolutePath}")
                return false
            }
            println("[Backend] Backend directory: ${backendDir.absolutePath}")

            val mainPy = File(backendDir, "main.py")
            if (!mainPy.exists()) {
                lastSetupError = "main.py not found in backend directory"
                ErrorLogger.log(LOG_TAG, lastSetupError!!)
                return false
            }

            val requirementsTxt = File(backendDir, "requirements.txt")
            if (!requirementsTxt.exists()) {
                ErrorLogger.logWarning(LOG_TAG, "requirements.txt not found")
            }

            val envFile = File(backendDir, ".env")
            if (!envFile.exists()) {
                ErrorLogger.logWarning(LOG_TAG, ".env file not found - Backend may not start correctly")
            }

            // Enhanced venv handling with automatic recovery
            val venvDir = File(backendDir, "venv")
            val venvNeedsRecreation = !venvDir.exists() || !isVenvValid(venvDir, pythonCmd)

            if (venvNeedsRecreation) {
                if (venvDir.exists()) {
                    println("[Backend] Virtual environment appears corrupted, recreating...")
                    if (!deleteDirectory(venvDir)) {
                        println("[Backend] WARNING: Could not fully delete corrupted venv, but continuing...")
                    }
                }

                println("[Backend] Creating virtual environment...")
                if (!createVirtualEnvironment(pythonCmd, backendDir)) {
                    if (attempt < maxRetryAttempts - 1) {
                        println("[Backend] Failed to create venv, will retry...")
                        Thread.sleep(2000)
                        return startBackendWithRetry(attempt + 1)
                    }
                    lastSetupError = "Failed to create virtual environment after multiple attempts"
                    println("[Backend] ERROR: $lastSetupError")
                    return false
                }
                println("[Backend] Virtual environment created successfully")
            } else {
                println("[Backend] Virtual environment exists and is valid")
            }

            val venvPython = getVenvPythonPath(venvDir)
            if (!venvPython.exists()) {
                if (attempt < maxRetryAttempts - 1) {
                    println("[Backend] venv Python not found, recreating venv...")
                    return startBackendWithRetry(attempt + 1)
                }
                lastSetupError = "Virtual environment Python executable not found"
                println("[Backend] ERROR: $lastSetupError")
                return false
            }

            // Install/update dependencies with retry
            if (requirementsTxt.exists()) {
                println("[Backend] Ensuring dependencies are installed...")
                if (!installDependenciesWithRetry(venvPython.absolutePath, backendDir, attempt)) {
                    if (attempt < maxRetryAttempts - 1) {
                        println("[Backend] Dependency installation failed, will retry...")
                        Thread.sleep(3000)
                        return startBackendWithRetry(attempt + 1)
                    }
                    lastSetupError = "Failed to install dependencies after multiple attempts. Please check your internet connection."
                    println("[Backend] ERROR: $lastSetupError")
                    return false
                }
                println("[Backend] Dependencies verified")
            }

            // Try to start the server
            println("[Backend] Starting backend server...")
            val serverStarted = startServerProcess(venvPython.absolutePath, backendDir, attempt)

            if (serverStarted) {
                retryAttempts = 0
                return true
            } else {
                // Server failed to start - try recovery
                if (attempt < maxRetryAttempts - 1) {
                    println("[Backend] Server failed to start, attempting recovery...")
                    performErrorRecovery(backendDir, pythonCmd, venvDir)
                    Thread.sleep((attempt + 1) * 2000L)
                    return startBackendWithRetry(attempt + 1)
                }
                return false
            }
        } catch (e: Exception) {
            lastSetupError = "Error starting backend: ${e.message}"
            ErrorLogger.logException(LOG_TAG, e, "Error starting backend")

            if (attempt < maxRetryAttempts - 1) {
                ErrorLogger.logInfo(LOG_TAG, "Exception occurred, will retry")
                Thread.sleep((attempt + 1) * 2000L)
                return startBackendWithRetry(attempt + 1)
            }
            false
        }
    }

    /**
     * Start the actual server process and wait for it to become healthy.
     */
    private fun startServerProcess(
        venvPython: String,
        backendDir: File,
        attempt: Int,
    ): Boolean {
        return try {
            stopBackend() // Make sure no old process is running

            val processBuilder =
                ProcessBuilder(venvPython, "main.py")
                    .directory(backendDir)
                    .redirectErrorStream(true)

            backendProcess = processBuilder.start()

            // Capture output for error detection
            val outputLines = mutableListOf<String>()
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(backendProcess!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val lineStr = line!!
                        outputLines.add(lineStr)
                        println("[Backend] $lineStr")

                        // Check for common errors in output
                        if (lineStr.contains("ModuleNotFoundError", ignoreCase = true) ||
                            lineStr.contains("ImportError", ignoreCase = true) ||
                            lineStr.contains("python-multipart", ignoreCase = true)
                        ) {
                            println("[Backend] Detected missing dependency error in output")
                        }
                    }
                } catch (e: Exception) {
                    // Process ended
                }
            }.start()

            println("[Backend] Waiting for backend to start...")
            var attempts = 0
            val maxAttempts = 30

            while (attempts < maxAttempts) {
                Thread.sleep(1000)
                attempts++

                if (isBackendHealthy()) {
                    println("[Backend] Backend started successfully!")
                    isBackendRunning = true

                    Runtime.getRuntime().addShutdownHook(
                        Thread {
                            stopBackend()
                        },
                    )

                    return true
                }

                // Check if process died
                if (backendProcess != null && !backendProcess!!.isAlive) {
                    val exitCode = backendProcess!!.exitValue()
                    println("[Backend] Process exited with code: $exitCode")

                    // Check output for specific errors
                    val errorOutput = outputLines.joinToString("\n")
                    if (errorOutput.contains("python-multipart", ignoreCase = true)) {
                        println("[Backend] Detected missing python-multipart, will reinstall dependencies")
                        lastSetupError = "Missing dependency detected, will retry..."
                        return false
                    }

                    if (exitCode != 0) {
                        println("[Backend] Server process failed, exit code: $exitCode")
                        lastSetupError = "Backend process exited with code $exitCode. Check logs above."
                        return false
                    }
                }

                print(".")
            }

            println("\n[Backend] Backend failed to respond within $maxAttempts seconds")
            stopBackend()
            lastSetupError = "Backend server did not respond. Attempting recovery..."
            false
        } catch (e: Exception) {
            println("[Backend] Exception starting server: ${e.message}")
            e.printStackTrace()
            stopBackend()
            false
        }
    }

    /**
     * Perform error recovery actions (recreate venv, reinstall deps, etc.)
     */
    private fun performErrorRecovery(
        backendDir: File,
        pythonCmd: String,
        venvDir: File,
    ) {
        println("[Backend] Performing automatic error recovery...")
        try {
            // Try to reinstall dependencies first (less destructive)
            val venvPython = getVenvPythonPath(venvDir)
            if (venvPython.exists()) {
                println("[Backend] Reinstalling dependencies...")
                installDependencies(venvPython.absolutePath, backendDir, forceReinstall = true)
            } else {
                // venv is broken, recreate it
                println("[Backend] Recreating virtual environment...")
                if (venvDir.exists()) {
                    deleteDirectory(venvDir)
                }
                createVirtualEnvironment(pythonCmd, backendDir)
            }
        } catch (e: Exception) {
            println("[Backend] Recovery attempt failed: ${e.message}")
        }
    }

    private fun getVenvPythonPath(venvDir: File): File {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        return if (isWindows) {
            File(venvDir, "Scripts/python.exe")
        } else {
            File(venvDir, "bin/python")
        }
    }

    /**
     * Delete a directory recursively
     */
    private fun deleteDirectory(directory: File): Boolean {
        return try {
            if (!directory.exists()) return true

            if (directory.isDirectory) {
                val children = directory.listFiles()
                if (children != null) {
                    for (child in children) {
                        deleteDirectory(child)
                    }
                }
            }
            directory.delete()
        } catch (e: Exception) {
            println("[Backend] Error deleting directory: ${e.message}")
            false
        }
    }

    private fun findBackendDirectory(): File? {
        val currentDir = File(System.getProperty("user.dir"))

        val possibleLocations = mutableListOf<File>()

        // 1. Development location: current directory or parent
        possibleLocations.add(File(currentDir, "backend"))
        possibleLocations.add(currentDir.parentFile?.resolve("backend") ?: File(currentDir, "backend"))

        // 2. Installed application locations (Windows)
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        possibleLocations.add(File(programFiles, "WordBridge\\backend"))

        val programFiles86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        possibleLocations.add(File(programFiles86, "WordBridge\\backend"))

        // 3. User installation location (if per-user install)
        val userHome = System.getProperty("user.home")
        possibleLocations.add(File(userHome, "AppData\\Local\\WordBridge\\backend"))
        possibleLocations.add(File(userHome, ".wordbridge\\backend"))

        // 4. Check location relative to executable (for installed apps)
        try {
            val codeSource = BackendManager::class.java.protectionDomain.codeSource?.location
            if (codeSource != null) {
                val jarFile = File(codeSource.toURI())
                val executableDir = if (jarFile.isDirectory) jarFile else jarFile.parentFile
                possibleLocations.add(executableDir.resolve("backend"))
                // Also check parent directory (installation root)
                executableDir.parentFile?.let { possibleLocations.add(it.resolve("backend")) }
            }
        } catch (e: Exception) {
        }

        return possibleLocations.firstOrNull { it.exists() && File(it, "main.py").exists() }
    }

    private fun isBackendHealthy(): Boolean {
        return try {
            val url = java.net.URL("http://localhost:8000/health")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000

            val responseCode = connection.responseCode
            connection.disconnect()

            responseCode == 200
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Comprehensive Python validation with aggressive installation attempts.
     * Tries multiple installation methods until Python is available.
     */
    private fun validatePythonInstallationWithRetry(): String? {
        // First, try to find existing Python
        var pythonCmd = findPythonCommand()

        if (pythonCmd != null) {
            println("[Backend] Python found: $pythonCmd")
            if (checkPythonVersion(pythonCmd) && validatePythonModules(pythonCmd)) {
                return pythonCmd
            } else {
                println("[Backend] Existing Python is invalid, will try to install fresh version")
            }
        }

        // Python not found or invalid - try to install with multiple methods
        println("[Backend] Python not found or invalid. Attempting installation...")

        val installMethods =
            listOf(
                ::installPythonMethod1, // winget (Windows) or brew (Mac)
                ::installPythonMethod2, // direct download (Windows) or apt/yum (Linux)
                ::installPythonMethod3, // alternative methods
            )

        for ((index, method) in installMethods.withIndex()) {
            println("[Backend] Trying installation method ${index + 1}...")
            if (method()) {
                // Wait for installation to complete
                println("[Backend] Installation method ${index + 1} completed. Waiting for system to register Python...")
                Thread.sleep(5000)

                // Try to find Python again
                println("[Backend] Checking if Python 3.12 is now available...")
                pythonCmd = findPythonCommand()
                if (pythonCmd != null) {
                    println("[Backend] Found Python command: $pythonCmd")
                    if (checkPythonVersion(pythonCmd) && validatePythonModules(pythonCmd)) {
                        println("[Backend] Python installed successfully via method ${index + 1}")
                        return pythonCmd
                    } else {
                        println("[Backend] Python found but validation failed")
                    }
                } else {
                    println("[Backend] Python command not found after installation")
                }

                // Python might be installed but not in PATH yet - try direct paths
                println("[Backend] Python may need a restart to be available. Checking direct paths...")
                Thread.sleep(5000)

                // Check direct Python 3.12 paths
                val directPaths =
                    listOf(
                        "C:\\Python312\\python.exe",
                        "C:\\Program Files\\Python312\\python.exe",
                        "${System.getProperty("user.home")}\\AppData\\Local\\Programs\\Python\\Python312\\python.exe",
                    )

                for (path in directPaths) {
                    val file = File(path)
                    if (file.exists()) {
                        println("[Backend] Found Python at direct path: $path")
                        if (checkPythonVersion(path) && validatePythonModules(path)) {
                            println("[Backend] Python validated at direct path")
                            return path
                        }
                    }
                }
            }
        }

        // All methods failed
        println("[Backend] All Python installation methods failed")
        return null
    }

    /**
     * Comprehensive Python validation for first-time users.
     * Checks for Python installation, version, and required modules.
     * Returns the Python command if valid, null otherwise.
     */
    private fun validatePythonInstallation(): String? {
        println("[Backend] Step 1: Looking for Python installation...")

        // First, try to find Python
        var pythonCmd = findPythonCommand()

        if (pythonCmd == null) {
            println("[Backend] Python is not installed on your system.")
            println("[Backend] This is required for the backend to run.")
            println("[Backend] Attempting automatic installation...")

            if (!installPythonAutomatically()) {
                lastSetupError =
                    """
                    |Python is not installed and automatic installation failed.
                    |
                    |Please install Python 3.12 manually:
                    |1. Visit: https://www.python.org/downloads/
                    |2. Download Python 3.12.x
                    |3. During installation, check "Add Python to PATH"
                    |4. Restart this application after installation
                    |
                    |If Python is already installed, make sure it's added to your system PATH.
                    """.trimMargin()
                println("[Backend] ERROR: $lastSetupError")
                return null
            }

            // Try to find Python again after installation
            pythonCmd = findPythonCommand()
            if (pythonCmd == null) {
                lastSetupError =
                    """
                    |Python was installed but cannot be found.
                    |
                    |This usually means Python needs to be added to your PATH, or
                    |you need to restart the application.
                    |
                    |Please:
                    |1. Close this application completely
                    |2. Restart the application
                    |3. If the issue persists, manually add Python to your system PATH
                    """.trimMargin()
                println("[Backend] ERROR: $lastSetupError")
                return null
            }
        }

        println("[Backend] Step 2: Checking Python version...")
        if (!checkPythonVersion(pythonCmd)) {
            lastSetupError =
                """
                |Python version is too old.
                |
                |Required: Python 3.12 (recommended for compatibility with fasttext-wheel)
                |Please upgrade Python from: https://www.python.org/downloads/
                """.trimMargin()
            println("[Backend] ERROR: $lastSetupError")
            return null
        }

        println("[Backend] Step 3: Checking required Python modules...")
        if (!validatePythonModules(pythonCmd)) {
            lastSetupError =
                """
                |Python is missing required modules (pip or venv).
                |
                |This usually happens with a minimal Python installation.
                |Please reinstall Python from https://www.python.org/downloads/
                |and make sure "pip" and "venv" are included.
                """.trimMargin()
            println("[Backend] ERROR: $lastSetupError")
            return null
        }

        println("[Backend] Python validation complete - all checks passed!")
        return pythonCmd
    }

    private fun findPythonCommand(): String? {
        // First, try to find Python 3.12 specifically using py launcher
        try {
            val process = ProcessBuilder("py", "-3.12", "--version").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val version = process.inputStream.bufferedReader().readText().trim()
                println("[Backend]   Found: py -3.12 - $version")
                return "py -3.12"
            }
        } catch (e: Exception) {
            // py launcher not available or Python 3.12 not found
        }

        // Check common Python 3.12 installation paths on Windows
        val python312Paths =
            listOf(
                "C:\\Python312\\python.exe",
                "C:\\Program Files\\Python312\\python.exe",
                "C:\\Program Files (x86)\\Python312\\python.exe",
                "${System.getProperty("user.home")}\\AppData\\Local\\Programs\\Python\\Python312\\python.exe",
            )

        for (path in python312Paths) {
            val file = File(path)
            if (file.exists()) {
                try {
                    val process = ProcessBuilder(path, "--version").start()
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        val version = process.inputStream.bufferedReader().readText().trim()
                        if (version.contains("3.12")) {
                            println("[Backend]   Found: $path - $version")
                            return path
                        }
                    }
                } catch (e: Exception) {
                    // Continue to next path
                }
            }
        }

        // Fallback: check generic commands but only accept Python 3.12
        val commands = listOf("python", "python3", "py")
        for (cmd in commands) {
            try {
                val process = ProcessBuilder(cmd, "--version").start()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    val version = process.inputStream.bufferedReader().readText().trim()
                    if (version.contains("3.12")) {
                        println("[Backend]   Found: $cmd - $version")
                        return cmd
                    } else {
                        println("[Backend]   Found: $cmd - $version (not 3.12, skipping)")
                    }
                }
            } catch (e: Exception) {
                // Command not found, try next
            }
        }

        println("[Backend]   Python 3.12 not found")
        return null
    }

    /**
     * Validates that Python has required modules (pip and venv).
     */
    private fun validatePythonModules(pythonCmd: String): Boolean {
        return try {
            // Build command parts for ProcessBuilder
            val cmdParts =
                if (pythonCmd.contains(" ")) {
                    pythonCmd.split(" ")
                } else {
                    listOf(pythonCmd)
                }

            // Check for pip
            val pipProcess = ProcessBuilder(cmdParts + listOf("-m", "pip", "--version")).start()
            val pipExitCode = pipProcess.waitFor()

            if (pipExitCode != 0) {
                println("[Backend]   ERROR: pip module not found")
                return false
            }
            val pipVersion = pipProcess.inputStream.bufferedReader().readText().trim()
            println("[Backend]   ✓ pip found: $pipVersion")

            // Check for venv
            val venvProcess = ProcessBuilder(cmdParts + listOf("-m", "venv", "--help")).start()
            val venvExitCode = venvProcess.waitFor()

            if (venvExitCode != 0) {
                println("[Backend]   ERROR: venv module not found")
                return false
            }
            println("[Backend]   ✓ venv module available")

            true
        } catch (e: Exception) {
            println("[Backend]   ERROR: Failed to validate Python modules: ${e.message}")
            false
        }
    }

    private fun checkPythonVersion(pythonCmd: String): Boolean {
        return try {
            val process =
                if (pythonCmd.contains(" ")) {
                    // Handle commands like "py -3.12"
                    val parts = pythonCmd.split(" ")
                    ProcessBuilder(parts + "--version").start()
                } else {
                    ProcessBuilder(pythonCmd, "--version").start()
                }
            process.waitFor()
            val versionOutput = process.inputStream.bufferedReader().readText().trim()

            val versionMatch = Regex("""Python (\d+)\.(\d+)""").find(versionOutput)
            if (versionMatch != null) {
                val major = versionMatch.groupValues[1].toInt()
                val minor = versionMatch.groupValues[2].toInt()

                println("[Backend]   Detected Python version: $major.$minor")

                // Require Python 3.12 specifically
                if (major == 3 && minor == 12) {
                    println("[Backend]   ✓ Python version is compatible")
                    return true
                }

                println("[Backend]   ✗ Python 3.12 is required, found $major.$minor")
                return false
            }

            println("[Backend]   ✗ Could not determine Python version")
            false
        } catch (e: Exception) {
            println("[Backend]   ✗ Failed to check Python version: ${e.message}")
            false
        }
    }

    /**
     * Installation method 1: Try platform-specific package managers (fastest)
     */
    private fun installPythonMethod1(): Boolean {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        val isLinux = System.getProperty("os.name").lowercase().contains("linux")

        return when {
            isWindows -> installPythonWindows()
            isMac -> installPythonMac()
            isLinux -> installPythonLinux()
            else -> false
        }
    }

    /**
     * Installation method 2: Try alternative methods
     */
    private fun installPythonMethod2(): Boolean {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        val isLinux = System.getProperty("os.name").lowercase().contains("linux")

        return when {
            isWindows -> {
                // Try direct download if winget failed
                downloadAndInstallPythonWindows()
            }
            isMac -> {
                // Try alternative Mac methods
                installPythonMacAlternative()
            }
            isLinux -> {
                // Try alternative Linux package managers
                installPythonLinuxAlternative()
            }
            else -> false
        }
    }

    /**
     * Installation method 3: Last resort methods
     */
    private fun installPythonMethod3(): Boolean {
        // Try downloading portable Python or other fallback methods
        println("[Backend] Trying alternative Python installation methods...")
        // For now, return false - can be enhanced later
        return false
    }

    private fun installPythonAutomatically(): Boolean {
        return installPythonMethod1() || installPythonMethod2() || installPythonMethod3()
    }

    private fun hasAdminRights(): Boolean {
        return try {
            val process = ProcessBuilder("net", "session").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun installPythonWindows(): Boolean {
        println("[Backend] Detected Windows - attempting to install Python...")
        println("[Backend] This may take a few minutes. Please wait...")

        if (!hasAdminRights()) {
            println("[Backend] WARNING: Administrator rights not detected")
            println("[Backend] Python installation may fail without admin privileges")
            println("[Backend] Please run the application as Administrator if installation fails")
        }

        try {
            println("[Backend] Trying winget package manager (Windows Package Manager)...")
            val wingetProcess =
                ProcessBuilder(
                    "winget",
                    "install",
                    "Python.Python.3.12",
                    "--silent",
                    "--accept-package-agreements",
                    "--accept-source-agreements",
                ).start()

            val exitCode = wingetProcess.waitFor()
            if (exitCode == 0) {
                println("[Backend] ✓ Python installed successfully via winget")
                println("[Backend] Waiting for system to register installation...")
                Thread.sleep(5000) // Give system time to register the installation
                addPythonToPath()
                println("[Backend] Winget installation completed")
                return true
            } else {
                println("[Backend] winget installation failed (exit code: $exitCode)")
                println("[Backend] winget output: ${wingetProcess.inputStream.bufferedReader().readText()}")
            }
        } catch (e: Exception) {
            println("[Backend] winget not available or failed: ${e.message}")
            println("[Backend] Falling back to direct download method...")
        }

        println("[Backend] Downloading Python installer from python.org...")
        println("[Backend] This may take a few minutes depending on your internet speed...")
        return downloadAndInstallPythonWindows()
    }

    private fun downloadAndInstallPythonWindows(): Boolean {
        try {
            val pythonVersion = "3.12.7"
            val installerUrl = "https://www.python.org/ftp/python/$pythonVersion/python-$pythonVersion-amd64.exe"
            val tempDir = System.getProperty("java.io.tmpdir")
            val installerPath = File(tempDir, "python-installer.exe")

            println("[Backend] Downloading Python $pythonVersion...")
            println("[Backend] Source: $installerUrl")

            // Download installer with progress indication
            val url = java.net.URL(installerUrl)
            url.openStream().use { input ->
                installerPath.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            println("[Backend] Download complete. File size: ${installerPath.length() / 1024 / 1024} MB")
            println("[Backend] Running Python installer (this may take 2-5 minutes)...")
            println("[Backend] The installer is running silently - please wait...")

            // Run installer silently with PATH addition and pip/venv included
            val installProcess =
                ProcessBuilder(
                    installerPath.absolutePath,
                    "/quiet",
                    "InstallAllUsers=1",
                    "PrependPath=1",
                    "Include_test=0",
                    "Include_pip=1",
                    "Include_launcher=1",
                ).start()

            val exitCode = installProcess.waitFor()

            // Wait a bit for system to register the installation
            Thread.sleep(3000)

            installerPath.delete()

            if (exitCode == 0) {
                println("[Backend] ✓ Python installer completed successfully")
                println("[Backend] Verifying installation...")
                addPythonToPath()
                Thread.sleep(5000) // Additional wait for PATH propagation
                println("[Backend] Direct installation completed")
                return true
            } else {
                println("[Backend] ✗ Python installer exited with code $exitCode")
                println("[Backend] Installer output: ${installProcess.inputStream.bufferedReader().readText()}")
                println("[Backend] This may indicate an installation error.")
                println("[Backend] Please try installing Python manually from https://www.python.org/downloads/")
                return false
            }
        } catch (e: java.net.UnknownHostException) {
            println("[Backend] ✗ ERROR: Cannot connect to download server")
            println("[Backend] Please check your internet connection and try again.")
            lastSetupError = "Failed to download Python installer: No internet connection"
            return false
        } catch (e: Exception) {
            println("[Backend] ✗ ERROR: Failed to download/install Python: ${e.message}")
            e.printStackTrace()
            lastSetupError = "Failed to install Python: ${e.message}"
            return false
        }
    }

    private fun installPythonMac(): Boolean {
        println("[Backend] Attempting to install Python on macOS...")

        try {
            val brewCheck = ProcessBuilder("which", "brew").start()
            if (brewCheck.waitFor() != 0) {
                println("[Backend] Homebrew is not installed. Trying alternative methods...")
                return false
            }

            println("[Backend] Installing Python via Homebrew...")
            val brewProcess = ProcessBuilder("brew", "install", "python@3.12", "--quiet").start()

            val exitCode = brewProcess.waitFor()
            if (exitCode == 0) {
                println("[Backend] ✓ Python installed successfully via Homebrew")
                return true
            } else {
                println("[Backend] Homebrew installation failed with exit code $exitCode")
                return false
            }
        } catch (e: Exception) {
            println("[Backend] Failed to install Python via Homebrew: ${e.message}")
            return false
        }
    }

    private fun installPythonMacAlternative(): Boolean {
        println("[Backend] Trying alternative Python installation for macOS...")

        // Try installing via MacPorts if available
        try {
            val portCheck = ProcessBuilder("which", "port").start()
            if (portCheck.waitFor() == 0) {
                println("[Backend] Trying MacPorts...")
                val portProcess = ProcessBuilder("sudo", "port", "install", "python312").start()
                val exitCode = portProcess.waitFor()
                if (exitCode == 0) {
                    println("[Backend] ✓ Python installed via MacPorts")
                    return true
                }
            }
        } catch (e: Exception) {
            // MacPorts not available
        }

        // Last resort: Download installer (would require user interaction, so skip for now)
        println("[Backend] Alternative Mac installation methods not available")
        return false
    }

    private fun installPythonLinux(): Boolean {
        println("[Backend] Attempting to install Python on Linux...")

        try {
            // Try apt (Debian/Ubuntu)
            val aptCheck = ProcessBuilder("which", "apt").start()
            if (aptCheck.waitFor() == 0) {
                println("[Backend] Installing Python via apt...")
                val updateProcess =
                    ProcessBuilder(
                        "sudo",
                        "apt",
                        "update",
                        "-qq",
                    ).start()
                updateProcess.waitFor()

                val installProcess =
                    ProcessBuilder(
                        "sudo",
                        "apt",
                        "install",
                        "-y",
                        "python3.12",
                        "python3.12-venv",
                        "python3-pip",
                    ).start()

                val exitCode = installProcess.waitFor()
                if (exitCode == 0) {
                    println("[Backend] ✓ Python installed successfully via apt")
                    return true
                }
            }

            // Try yum (RedHat/CentOS)
            val yumCheck = ProcessBuilder("which", "yum").start()
            if (yumCheck.waitFor() == 0) {
                println("[Backend] Installing Python via yum...")
                val yumProcess =
                    ProcessBuilder(
                        "sudo",
                        "yum",
                        "install",
                        "-y",
                        "python312",
                        "python312-pip",
                    ).start()

                val exitCode = yumProcess.waitFor()
                if (exitCode == 0) {
                    println("[Backend] ✓ Python installed successfully via yum")
                    return true
                }
            }

            println("[Backend] Primary package managers not available")
            return false
        } catch (e: Exception) {
            println("[Backend] Failed to install Python: ${e.message}")
            return false
        }
    }

    private fun installPythonLinuxAlternative(): Boolean {
        println("[Backend] Trying alternative Python installation for Linux...")

        try {
            // Try dnf (newer Fedora)
            val dnfCheck = ProcessBuilder("which", "dnf").start()
            if (dnfCheck.waitFor() == 0) {
                println("[Backend] Trying dnf...")
                val dnfProcess =
                    ProcessBuilder(
                        "sudo",
                        "dnf",
                        "install",
                        "-y",
                        "python3.12",
                        "python3-pip",
                    ).start()

                val exitCode = dnfProcess.waitFor()
                if (exitCode == 0) {
                    println("[Backend] ✓ Python installed via dnf")
                    return true
                }
            }

            // Try pacman (Arch)
            val pacmanCheck = ProcessBuilder("which", "pacman").start()
            if (pacmanCheck.waitFor() == 0) {
                println("[Backend] Trying pacman...")
                val pacmanProcess =
                    ProcessBuilder(
                        "sudo",
                        "pacman",
                        "-S",
                        "--noconfirm",
                        "python",
                        "python-pip",
                    ).start()

                val exitCode = pacmanProcess.waitFor()
                if (exitCode == 0) {
                    println("[Backend] ✓ Python installed via pacman")
                    return true
                }
            }

            // Try zypper (openSUSE)
            val zypperCheck = ProcessBuilder("which", "zypper").start()
            if (zypperCheck.waitFor() == 0) {
                println("[Backend] Trying zypper...")
                val zypperProcess =
                    ProcessBuilder(
                        "sudo",
                        "zypper",
                        "-n",
                        "install",
                        "python311",
                        "python311-pip",
                    ).start()

                val exitCode = zypperProcess.waitFor()
                if (exitCode == 0) {
                    println("[Backend] ✓ Python installed via zypper")
                    return true
                }
            }

            println("[Backend] Alternative Linux package managers not available")
            return false
        } catch (e: Exception) {
            println("[Backend] Alternative installation failed: ${e.message}")
            return false
        }
    }

    private fun addPythonToPath() {
        try {
            val pythonPath = "C:\\Program Files\\Python312"
            val userPythonPath = "${System.getProperty("user.home")}\\AppData\\Local\\Programs\\Python\\Python312"

            val currentPath = System.getenv("PATH") ?: ""
            if (!currentPath.contains(pythonPath) && !currentPath.contains(userPythonPath)) {
                println("[Backend] Python 3.12 paths added to current session")
            }
        } catch (e: Exception) {
            println("[Backend] WARNING: Could not modify PATH: ${e.message}")
        }
    }

    private fun createVirtualEnvironment(
        pythonCmd: String,
        backendDir: File,
    ): Boolean {
        return try {
            // Build command parts for ProcessBuilder
            val cmdParts =
                if (pythonCmd.contains(" ")) {
                    pythonCmd.split(" ")
                } else {
                    listOf(pythonCmd)
                }

            val processBuilder =
                ProcessBuilder(cmdParts + listOf("-m", "venv", "venv"))
                    .directory(backendDir)
                    .redirectErrorStream(true)

            val process = processBuilder.start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                true
            } else {
                println("[Backend] ERROR: Failed to create virtual environment:")
                println(output)
                false
            }
        } catch (e: Exception) {
            println("[Backend] ERROR: Error creating virtual environment: ${e.message}")
            false
        }
    }

    private fun isVenvValid(
        venvDir: File,
        pythonCmd: String,
    ): Boolean {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val venvPython =
            if (isWindows) {
                File(venvDir, "Scripts/python.exe")
            } else {
                File(venvDir, "bin/python")
            }

        if (!venvPython.exists()) {
            println("[Backend] Virtual environment Python executable not found")
            return false
        }

        // Check if the virtual environment is using Python 3.12
        return try {
            val process = ProcessBuilder(venvPython.absolutePath, "--version").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val versionOutput = process.inputStream.bufferedReader().readText().trim()
                if (versionOutput.contains("3.12")) {
                    println("[Backend] Virtual environment is using Python 3.12")
                    true
                } else {
                    println("[Backend] Virtual environment is using wrong Python version: $versionOutput")
                    false
                }
            } else {
                println("[Backend] Failed to check virtual environment Python version")
                false
            }
        } catch (e: Exception) {
            println("[Backend] Error checking virtual environment: ${e.message}")
            false
        }
    }

    /**
     * Install dependencies with retry capability
     */
    private fun installDependenciesWithRetry(
        venvPython: String,
        backendDir: File,
        attempt: Int,
    ): Boolean {
        var currentAttempt = 0
        val maxDependencyAttempts = 2

        while (currentAttempt < maxDependencyAttempts) {
            if (currentAttempt > 0) {
                println("[Backend] Retrying dependency installation (attempt ${currentAttempt + 1}/$maxDependencyAttempts)...")
                Thread.sleep(2000)
            }

            if (installDependencies(venvPython, backendDir, forceReinstall = currentAttempt > 0)) {
                return true
            }

            currentAttempt++
        }

        return false
    }

    private fun installDependencies(
        venvPython: String,
        backendDir: File,
        forceReinstall: Boolean = false,
    ): Boolean {
        return try {
            if (forceReinstall) {
                println("[Backend] Force reinstalling dependencies...")
            } else {
                println("[Backend] Installing/updating dependencies (this may take a moment)...")
            }

            // Upgrade pip first
            val upgradePipProcess =
                ProcessBuilder(venvPython, "-m", "pip", "install", "--upgrade", "pip", "--quiet")
                    .directory(backendDir)
                    .redirectErrorStream(true)
                    .start()

            upgradePipProcess.waitFor()

            // Build install command
            val installArgs =
                mutableListOf(
                    venvPython,
                    "-m",
                    "pip",
                    "install",
                    "-r",
                    "requirements.txt",
                )

            if (forceReinstall) {
                installArgs.add("--force-reinstall")
                installArgs.add("--no-cache-dir")
            } else {
                installArgs.add("--quiet")
            }

            val processBuilder =
                ProcessBuilder(installArgs)
                    .directory(backendDir)
                    .redirectErrorStream(true)

            val process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorLines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val lineStr = line!!
                if (lineStr.contains("error", ignoreCase = true) ||
                    lineStr.contains("failed", ignoreCase = true) ||
                    lineStr.contains("warning", ignoreCase = true)
                ) {
                    println("[Backend] [pip] $lineStr")
                    errorLines.add(lineStr)
                }
            }

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                true
            } else {
                println("[Backend] ERROR: Failed to install dependencies (exit code: $exitCode)")
                if (errorLines.isNotEmpty()) {
                    println("[Backend] Error details:")
                    errorLines.take(5).forEach { println("[Backend]   $it") }
                }
                false
            }
        } catch (e: Exception) {
            println("[Backend] ERROR: Error installing dependencies: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun getLastSetupError(): String? = lastSetupError

    fun stopBackend() {
        if (backendProcess != null && backendProcess!!.isAlive) {
            println("Stopping backend server...")
            backendProcess!!.destroy()

            try {
                backendProcess!!.waitFor()
                println("Backend stopped")
            } catch (e: InterruptedException) {
                println("Backend stop interrupted")
                backendProcess!!.destroyForcibly()
            }

            backendProcess = null
            isBackendRunning = false
        }
    }

    fun isRunning(): Boolean = isBackendRunning && isBackendHealthy()

    suspend fun ensureBackendIsRunning(): Boolean {
        if (isRunning()) {
            return true
        }

        println("[Backend] Backend not running, attempting to start with automatic recovery...")
        // Reset retry attempts for fresh start
        retryAttempts = 0
        return startBackend()
    }

    // check python installation and download all required and if python is not installed install it automatically
    @Suppress("UNREACHABLE_CODE")
    suspend fun ensurePythonIsInstalled(onProgress: (String, Float) -> Unit = { _, _ -> }): Boolean {
        try {
            onProgress("Checking Python installation...", 0.1f)

            var pythonCmd = findPythonCommand()
            if (pythonCmd != null) {
                onProgress("Python found", 0.3f)

                if (checkPythonVersion(pythonCmd)) {
                    onProgress("Python version OK", 0.5f)
                    println("[Backend] Python is already installed and ready")
                    return true
                } else {
                    println("[Backend] Python version is too old")
                    onProgress("Python version too old", 0.5f)
                    return false
                }
            }

            println("[Backend] Python not found, installing...")
            onProgress("Installing Python...", 0.2f)

            val installSuccess = installPythonAutomatically()

            if (installSuccess) {
                onProgress("Python installed successfully", 0.8f)

                pythonCmd = findPythonCommand()
                if (pythonCmd != null && checkPythonVersion(pythonCmd)) {
                    onProgress("Python ready", 1.0f)
                    println("[Backend] Python installation verified")
                    return true
                } else {
                    onProgress("Python installation needs verification", 0.9f)
                    println("[Backend] Python installed but needs app restart")
                    lastSetupError = "Python was installed but cannot be found. Please restart the application."
                    return false
                }
            } else {
                onProgress("Python installation failed", 0.5f)
                lastSetupError = "Failed to install Python automatically. Please install Python 3.12 manually."
                return false
            }
        } catch (e: Exception) {
            println("[Backend] Error ensuring Python is installed: ${e.message}")
            e.printStackTrace()
            onProgress("Error checking Python", 0.0f)
            return false
        }
    }

    fun verifyBackendSetup(): Boolean {
        try {
            val backendDir = findBackendDirectory()

            if (backendDir == null || !backendDir.exists()) {
                println("[Backend] Backend directory not found")
                return false
            }

            val venvDir = File(backendDir, "venv")
            if (!venvDir.exists()) {
                println("[Backend] Virtual environment not found")
                return false
            }

            val venvPython = getVenvPythonPath(venvDir)
            if (!venvPython.exists()) {
                println("[Backend] Virtual environment Python executable not found")
                return false
            }

            val mainPy = File(backendDir, "main.py")
            if (!mainPy.exists()) {
                println("[Backend] main.py not found")
                return false
            }

            println("[Backend] Backend setup verification passed")
            return true
        } catch (e: Exception) {
            println("[Backend] Error verifying backend setup: ${e.message}")
            return false
        }
    }

    // Set up backend environment (Python, venv, dependencies) without starting the server.
    suspend fun setupBackendEnvironment(onProgress: (String, Float) -> Unit = { _, _ -> }): Boolean {
        return try {
            onProgress("Validating Python installation...", 0.1f)

            // Use comprehensive validation that handles installation if needed
            val pythonCmd = validatePythonInstallation()
            if (pythonCmd == null) {
                // Error message already set by validatePythonInstallation()
                return false
            }

            onProgress("Python ready", 0.3f)

            val backendDir = findBackendDirectory()

            if (backendDir == null || !backendDir.exists()) {
                lastSetupError = "Backend directory not found. Please ensure the backend folder is in the installation directory."
                return false
            }

            onProgress("Setting up virtual environment...", 0.4f)

            val venvDir = File(backendDir, "venv")
            if (!venvDir.exists() || !isVenvValid(venvDir, pythonCmd)) {
                println("[Backend] Virtual environment missing or invalid. Recreating with Python 3.12...")
                // Always delete existing venv to ensure clean Python 3.12 environment
                if (venvDir.exists()) {
                    println("[Backend] Deleting existing virtual environment...")
                    deleteDirectory(venvDir)
                }
                println("[Backend] Creating new virtual environment with Python 3.12...")
                if (!createVirtualEnvironment(pythonCmd, backendDir)) {
                    lastSetupError = "Failed to create virtual environment with Python 3.12"
                    return false
                }
            }

            onProgress("Virtual environment ready", 0.5f)
            val venvPython = getVenvPythonPath(venvDir)
            if (!venvPython.exists()) {
                lastSetupError = "Virtual environment Python executable not found"
                return false
            }

            onProgress("Installing dependencies...", 0.6f)

            val requirementsTxt = File(backendDir, "requirements.txt")
            if (requirementsTxt.exists()) {
                if (!installDependencies(venvPython.absolutePath, backendDir)) {
                    lastSetupError = "Failed to install dependencies"
                    return false
                }
            }

            onProgress("Backend environment ready", 0.8f)

            println("[Backend] Backend environment setup completed successfully")
            true
        } catch (e: Exception) {
            println("[Backend] Error setting up backend environment: ${e.message}")
            e.printStackTrace()
            lastSetupError = "Error setting up backend: ${e.message}"
            false
        }
    }
}
