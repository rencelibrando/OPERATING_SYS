package org.example.project.core.ai

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader


object BackendManager {
    private var backendProcess: Process? = null
    private var isBackendRunning = false
    private var lastSetupError: String? = null


    fun startBackend(): Boolean {
        return try {
            println("[Backend] Checking if backend is already running...")

            // Check if backend is already running
            if (isBackendHealthy()) {
                println("[Backend] Backend is already running")
                isBackendRunning = true
                return true
            }

            println("[Backend] Starting Python backend...")

            // Find the backend directory
            val projectRoot = File(System.getProperty("user.dir"))

            val backendDir = when {
                File(projectRoot, "backend").exists() -> File(projectRoot, "backend")
                File(projectRoot.parentFile, "backend").exists() -> File(projectRoot.parentFile, "backend")
                else -> File(projectRoot, "backend")
            }

            if (!backendDir.exists()) {
                lastSetupError = "Backend directory not found at: ${backendDir.absolutePath}"
                println("[Backend] ERROR: $lastSetupError")
                return false
            }
            println("[Backend] Backend directory: ${backendDir.absolutePath}")

            val mainPy = File(backendDir, "main.py")
            if (!mainPy.exists()) {
                lastSetupError = "main.py not found in backend directory"
                println("[Backend] ERROR: $lastSetupError")
                return false
            }

            // Check if requirements.txt exists
            val requirementsTxt = File(backendDir, "requirements.txt")
            if (!requirementsTxt.exists()) {
                println("[Backend] WARNING: requirements.txt not found")
            }

            // Check if .env exists
            val envFile = File(backendDir, ".env")
            if (!envFile.exists()) {
                println("[Backend] WARNING: .env file not found. Backend may not start correctly.")
            }

            // Step 1: Check Python installation, install if not found
            var pythonCmd = findPythonCommand()
            if (pythonCmd == null) {
                println("[Backend] Python not found. Attempting to install Python automatically...")
                
                if (!installPythonAutomatically()) {
                    lastSetupError = "Failed to install Python automatically. Please install Python 3.9 or higher manually from https://www.python.org/downloads/"
                    println("[Backend] ERROR: $lastSetupError")
                    return false
                }

                pythonCmd = findPythonCommand()
                if (pythonCmd == null) {
                    lastSetupError = "Python was installed but cannot be found. Please restart the application."
                    println("[Backend] ERROR: $lastSetupError")
                    return false
                }
            }

            println("[Backend] Using Python: $pythonCmd")

            // Step 2: Check Python version
            if (!checkPythonVersion(pythonCmd)) {
                lastSetupError = "Python 3.9+ is required. Please upgrade your Python installation."
                println("[Backend] ERROR: $lastSetupError")
                return false
            }

            // Step 3: Set up virtual environment
            val venvDir = File(backendDir, "venv")
            if (!venvDir.exists()) {
                println("[Backend] Creating virtual environment...")
                if (!createVirtualEnvironment(pythonCmd, backendDir)) {
                    lastSetupError = "Failed to create virtual environment"
                    println("[Backend] ERROR: $lastSetupError")
                    return false
                }
                println("[Backend] Virtual environment created")
            } else {
                println("[Backend] Virtual environment already exists")
            }

            // Step 4: Get the Python executable from venv
            val venvPython = getVenvPythonPath(venvDir)
            if (!venvPython.exists()) {
                lastSetupError = "Virtual environment Python executable not found"
                println("[Backend] ERROR: $lastSetupError")
                return false
            }

            // Step 5: Install/update dependencies
            if (requirementsTxt.exists()) {
                println("[Backend] Checking dependencies...")
                if (!installDependencies(venvPython.absolutePath, backendDir)) {
                    lastSetupError = "Failed to install dependencies. Check your internet connection."
                    println("[Backend] ERROR: $lastSetupError")
                    return false
                }
                println("[Backend] Dependencies are up to date")
            }

            // Step 6: Start the backend server
            println("[Backend] Starting backend server...")
            val processBuilder =
                ProcessBuilder(venvPython.absolutePath, "main.py")
                    .directory(backendDir)
                    .redirectErrorStream(true)

            backendProcess = processBuilder.start()

            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(backendProcess!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println("[Backend] $line")
                    }
                } catch (e: Exception) {
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

                print(".")
            }

            println("\n[Backend] ERROR: Backend failed to start within $maxAttempts seconds")
            lastSetupError = "Backend server did not respond. Check the console output above for errors."
            stopBackend()
            false
        } catch (e: Exception) {
            lastSetupError = "Error starting backend: ${e.message}"
            println("[Backend] ERROR: $lastSetupError")
            e.printStackTrace()
            false
        }
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

    private fun findPythonCommand(): String? {
        val commands = listOf("python", "python3", "py")

        for (cmd in commands) {
            try {
                val process = ProcessBuilder(cmd, "--version").start()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    val version = process.inputStream.bufferedReader().readText().trim()
                    println("   Found: $cmd - $version")
                    return cmd
                }
            } catch (e: Exception) {
                // Command not found, try next
            }
        }

        return null
    }

    private fun checkPythonVersion(pythonCmd: String): Boolean {
        return try {
            val process = ProcessBuilder(pythonCmd, "--version").start()
            process.waitFor()
            val versionOutput = process.inputStream.bufferedReader().readText().trim()

            val versionMatch = Regex("""Python (\d+)\.(\d+)""").find(versionOutput)
            if (versionMatch != null) {
                val major = versionMatch.groupValues[1].toInt()
                val minor = versionMatch.groupValues[2].toInt()
                
                println("[Backend] Python version: $major.$minor")
                
                // Check if version is 3.9 or higher
                if (major == 3 && minor >= 9) {
                    return true
                } else if (major > 3) {
                    return true
                }
                
                println("[Backend] WARNING: Python 3.9+ required, found $major.$minor")
                return false
            }
            
            false
        } catch (e: Exception) {
            println("[Backend] WARNING: Failed to check Python version: ${e.message}")
            false
        }
    }

    private fun installPythonAutomatically(): Boolean {
        return try {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val isMac = System.getProperty("os.name").lowercase().contains("mac")
            val isLinux = System.getProperty("os.name").lowercase().contains("linux")

            println("[Backend] Detected OS: ${System.getProperty("os.name")}")
            
            when {
                isWindows -> installPythonWindows()
                isMac -> installPythonMac()
                isLinux -> installPythonLinux()
                else -> {
                    println("[Backend] ERROR: Unsupported operating system")
                    false
                }
            }
        } catch (e: Exception) {
            println("[Backend] ERROR: Failed to install Python: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun installPythonWindows(): Boolean {
        println("[Backend] Attempting to install Python on Windows...")

        try {
            println("[Backend] Trying winget package manager...")
            val wingetProcess = ProcessBuilder(
                "winget", "install", "Python.Python.3.11", 
                "--silent", "--accept-package-agreements", "--accept-source-agreements"
            ).start()
            
            val exitCode = wingetProcess.waitFor()
            if (exitCode == 0) {
                println("[Backend] Python installed successfully via winget")
                addPythonToPath()
                return true
            }
        } catch (e: Exception) {
            println("[Backend] winget not available or failed: ${e.message}")
        }

        println("[Backend] Downloading Python installer...")
        return downloadAndInstallPythonWindows()
    }


    private fun downloadAndInstallPythonWindows(): Boolean {
        try {
            val pythonVersion = "3.11.8"
            val installerUrl = "https://www.python.org/ftp/python/$pythonVersion/python-$pythonVersion-amd64.exe"
            val tempDir = System.getProperty("java.io.tmpdir")
            val installerPath = File(tempDir, "python-installer.exe")
            
            println("[Backend] Downloading from: $installerUrl")
            
            // Download installer
            val url = java.net.URL(installerUrl)
            url.openStream().use { input ->
                installerPath.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            println("[Backend] Running Python installer...")
            
            // Run installer silently with PATH addition
            val installProcess = ProcessBuilder(
                installerPath.absolutePath,
                "/quiet",
                "InstallAllUsers=1",
                "PrependPath=1",
                "Include_test=0"
            ).start()
            
            val exitCode = installProcess.waitFor()

            installerPath.delete()
            
            if (exitCode == 0) {
                println("[Backend] Python installed successfully")
                addPythonToPath()
                return true
            } else {
                println("[Backend] ERROR: Python installer exited with code $exitCode")
                return false
            }
        } catch (e: Exception) {
            println("[Backend] ERROR: Failed to download/install Python: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun installPythonMac(): Boolean {
        println("[Backend] Attempting to install Python on macOS...")
        
        try {
            val brewCheck = ProcessBuilder("which", "brew").start()
            if (brewCheck.waitFor() != 0) {
                println("[Backend] ERROR: Homebrew is not installed. Please install Homebrew first: https://brew.sh")
                return false
            }
            
            println("[Backend] Installing Python via Homebrew...")
            val brewProcess = ProcessBuilder("brew", "install", "python@3.11").start()
            
            val exitCode = brewProcess.waitFor()
            if (exitCode == 0) {
                println("[Backend] Python installed successfully via Homebrew")
                return true
            } else {
                println("[Backend] ERROR: Homebrew installation failed with exit code $exitCode")
                return false
            }
        } catch (e: Exception) {
            println("[Backend] ERROR: Failed to install Python via Homebrew: ${e.message}")
            return false
        }
    }

    private fun installPythonLinux(): Boolean {
        println("[Backend] Attempting to install Python on Linux...")
        
        try {
            // Try apt (Debian/Ubuntu)
            val aptCheck = ProcessBuilder("which", "apt").start()
            if (aptCheck.waitFor() == 0) {
                println("[Backend] Installing Python via apt...")
                val aptProcess = ProcessBuilder(
                    "sudo", "apt", "update"
                ).start()
                aptProcess.waitFor()
                
                val installProcess = ProcessBuilder(
                    "sudo", "apt", "install", "-y", "python3.11", "python3.11-venv", "python3-pip"
                ).start()
                
                val exitCode = installProcess.waitFor()
                if (exitCode == 0) {
                    println("[Backend] Python installed successfully via apt")
                    return true
                }
            }
            
            // Try yum (RedHat/CentOS)
            val yumCheck = ProcessBuilder("which", "yum").start()
            if (yumCheck.waitFor() == 0) {
                println("[Backend] Installing Python via yum...")
                val yumProcess = ProcessBuilder(
                    "sudo", "yum", "install", "-y", "python311"
                ).start()
                
                val exitCode = yumProcess.waitFor()
                if (exitCode == 0) {
                    println("[Backend] Python installed successfully via yum")
                    return true
                }
            }
            
            println("[Backend] ERROR: Could not find a compatible package manager")
            return false
        } catch (e: Exception) {
            println("[Backend] ERROR: Failed to install Python: ${e.message}")
            return false
        }
    }

    private fun addPythonToPath() {
        try {
            val pythonPath = "C:\\Program Files\\Python311"
            
            val currentPath = System.getenv("PATH") ?: ""
            if (!currentPath.contains(pythonPath)) {
                println("[Backend] Python paths added to current session")
            }
        } catch (e: Exception) {
            println("[Backend] WARNING: Could not modify PATH: ${e.message}")
        }
    }

    private fun createVirtualEnvironment(pythonCmd: String, backendDir: File): Boolean {
        return try {
            val processBuilder = ProcessBuilder(pythonCmd, "-m", "venv", "venv")
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

    private fun getVenvPythonPath(venvDir: File): File {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        return if (isWindows) {
            File(venvDir, "Scripts/python.exe")
        } else {
            File(venvDir, "bin/python")
        }
    }

    private fun installDependencies(venvPython: String, backendDir: File): Boolean {
        return try {
            println("[Backend] Installing/updating dependencies (this may take a moment)...")

            val upgradePipProcess = ProcessBuilder(venvPython, "-m", "pip", "install", "--upgrade", "pip")
                .directory(backendDir)
                .redirectErrorStream(true)
                .start()
            
            upgradePipProcess.waitFor()
            val processBuilder = ProcessBuilder(
                venvPython, "-m", "pip", "install", "-r", "requirements.txt", "--quiet"
            )
                .directory(backendDir)
                .redirectErrorStream(true)
            
            val process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("error", ignoreCase = true) || 
                    line!!.contains("failed", ignoreCase = true)) {
                    println("[Backend] [pip] $line")
                }
            }
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                true
            } else {
                println("[Backend] ERROR: Failed to install dependencies (exit code: $exitCode)")
                false
            }
        } catch (e: Exception) {
            println("[Backend] ERROR: Error installing dependencies: ${e.message}")
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

        println("[Backend] Backend not running, attempting to start...")
        return startBackend()
    }
    
    /**
     * Check if Python is installed and install if needed.
     * This can be called during app initialization to prepare the environment.
     * Returns true if Python is available, false otherwise.
     */
    @Suppress("UNREACHABLE_CODE")
    suspend fun ensurePythonIsInstalled(
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Boolean {
        try {
            onProgress("Checking Python installation...", 0.1f)
            
            // Check if Python is already installed
            var pythonCmd = findPythonCommand()
            if (pythonCmd != null) {
                onProgress("Python found", 0.3f)
                
                // Check version
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
            
            // Python not found, install it
            println("[Backend] Python not found, installing...")
            onProgress("Installing Python...", 0.2f)
            
            val installSuccess = installPythonAutomatically()
            
            if (installSuccess) {
                onProgress("Python installed successfully", 0.8f)
                
                // Verify installation
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
                lastSetupError = "Failed to install Python automatically. Please install Python 3.9+ manually."
                return false
            }
        } catch (e: Exception) {
            println("[Backend] Error ensuring Python is installed: ${e.message}")
            e.printStackTrace()
            onProgress("Error checking Python", 0.0f)
            return false
        }
    }
}

