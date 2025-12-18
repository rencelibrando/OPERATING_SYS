package org.example.project.core.config

object AIBackendConfig {
    const val BASE_URL = "http://localhost:8000"
    const val AUTO_START_ON_LAUNCH = false

    // API endpoints
    const val HEALTH_ENDPOINT = "/health"
    const val CHAT_ENDPOINT = "/chat"
    const val PROVIDERS_ENDPOINT = "/providers"
    const val SAVE_HISTORY_ENDPOINT = "/chat/history/save"
    const val LOAD_HISTORY_ENDPOINT = "/chat/history/load"
    const val DELETE_HISTORY_ENDPOINT = "/chat/history"
    const val DEFAULT_PROVIDER = "gemini"
    const val CONNECTION_TIMEOUT_MS = 30000L
    const val REQUEST_TIMEOUT_MS = 60000L

    fun isConfigured(): Boolean = BASE_URL.isNotBlank()

    fun getEndpointUrl(endpoint: String): String = "$BASE_URL$endpoint"
}

