package org.example.project.core.utils

/**
 * Standardized error logging utility for the WordBridge application.
 *
 * All errors are logged in the format: [FileName]{ErrorMessage}
 *
 * This ensures:
 * - Clear indication of the source file where the error originated
 * - Consistent logging across all modules and layers
 * - No exposure of sensitive information (sanitized messages)
 *
 * Usage:
 * ```
 * ErrorLogger.log("MyClass.kt", "Failed to load data")
 * ErrorLogger.logException("MyClass.kt", exception)
 * ErrorLogger.logWarning("MyClass.kt", "Connection timeout")
 * ```
 */
object ErrorLogger {
    // Sensitive patterns to sanitize from error messages
    private val sensitivePatterns =
        listOf(
            Regex("""password[=:]\s*\S+""", RegexOption.IGNORE_CASE),
            Regex("""token[=:]\s*\S+""", RegexOption.IGNORE_CASE),
            Regex("""api[_-]?key[=:]\s*\S+""", RegexOption.IGNORE_CASE),
            Regex("""secret[=:]\s*\S+""", RegexOption.IGNORE_CASE),
            Regex("""authorization[=:]\s*\S+""", RegexOption.IGNORE_CASE),
            Regex("""bearer\s+\S+""", RegexOption.IGNORE_CASE),
            Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b"""), // Email pattern
        )

    /**
     * Log an error message with standardized format.
     *
     * @param fileName The source file name where the error occurred (e.g., "AuthViewModel.kt")
     * @param message The error message to log
     */
    fun log(
        fileName: String,
        message: String,
    ) {
        val sanitizedMessage = sanitizeMessage(message)
        println("[$fileName]{$sanitizedMessage}")
    }

    /**
     * Log an exception with standardized format.
     *
     * @param fileName The source file name where the error occurred
     * @param exception The exception that was caught
     * @param additionalContext Optional additional context about the error
     */
    fun logException(
        fileName: String,
        exception: Throwable,
        additionalContext: String? = null,
    ) {
        val baseMessage = exception.message ?: "Unknown error"
        val sanitizedMessage = sanitizeMessage(baseMessage)

        val fullMessage =
            if (additionalContext != null) {
                "$additionalContext: $sanitizedMessage"
            } else {
                sanitizedMessage
            }

        println("[$fileName]{$fullMessage}")
    }

    /**
     * Log a warning message with standardized format.
     *
     * @param fileName The source file name where the warning occurred
     * @param message The warning message to log
     */
    fun logWarning(
        fileName: String,
        message: String,
    ) {
        val sanitizedMessage = sanitizeMessage(message)
        println("[$fileName][WARNING]{$sanitizedMessage}")
    }

    /**
     * Log an info message with standardized format.
     *
     * @param fileName The source file name
     * @param message The info message to log
     */
    fun logInfo(
        fileName: String,
        message: String,
    ) {
        val sanitizedMessage = sanitizeMessage(message)
        println("[$fileName][INFO]{$sanitizedMessage}")
    }

    /**
     * Log a debug message with standardized format.
     * Only logs in debug mode.
     *
     * @param fileName The source file name
     * @param message The debug message to log
     */
    fun logDebug(
        fileName: String,
        message: String,
    ) {
        // Only log debug messages if debug mode is enabled
        if (isDebugMode()) {
            val sanitizedMessage = sanitizeMessage(message)
            println("[$fileName][DEBUG]{$sanitizedMessage}")
        }
    }

    /**
     * Sanitize message to remove any potentially sensitive information.
     *
     * @param message The original message
     * @return Sanitized message with sensitive data replaced
     */
    private fun sanitizeMessage(message: String): String {
        var sanitized = message

        for (pattern in sensitivePatterns) {
            sanitized = pattern.replace(sanitized, "[REDACTED]")
        }

        return sanitized
    }

    /**
     * Check if the application is running in debug mode.
     */
    private fun isDebugMode(): Boolean {
        // Check system property or environment variable
        return System.getProperty("wordbridge.debug")?.toBoolean()
            ?: System.getenv("WORDBRIDGE_DEBUG")?.toBoolean()
            ?: true // Default to true for development
    }
}

/**
 * Extension function for easy error logging from any class.
 * Usage: "MyClass.kt".logError("Something went wrong")
 */
fun String.logError(message: String) {
    ErrorLogger.log(this, message)
}

/**
 * Extension function for easy exception logging from any class.
 * Usage: "MyClass.kt".logException(exception)
 */
fun String.logException(
    exception: Throwable,
    context: String? = null,
) {
    ErrorLogger.logException(this, exception, context)
}

/**
 * Extension function for easy warning logging from any class.
 * Usage: "MyClass.kt".logWarning("Connection slow")
 */
fun String.logWarning(message: String) {
    ErrorLogger.logWarning(this, message)
}
