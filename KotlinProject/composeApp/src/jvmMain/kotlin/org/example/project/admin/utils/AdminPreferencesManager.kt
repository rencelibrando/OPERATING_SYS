package org.example.project.admin.utils

import java.util.prefs.Preferences

/**
 * Separate preferences manager for admin app.
 * Uses different node to avoid conflicts with main app.
 */
object AdminPreferencesManager {
    // Use a different package/node for admin app preferences
    private val prefs: Preferences =
        Preferences.userNodeForPackage(AdminPreferencesManager::class.java)
            .node("wordbridge_admin")

    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_WINDOW_WIDTH = "window_width"
    private const val KEY_WINDOW_HEIGHT = "window_height"
    private const val KEY_SELECTED_DIFFICULTY = "selected_difficulty"

    fun getLastSyncTime(): Long {
        return try {
            prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        } catch (e: Exception) {
            println("AdminPreferencesManager: Failed to get last sync time: ${e.message}")
            0L
        }
    }

    fun setLastSyncTime(time: Long) {
        try {
            prefs.putLong(KEY_LAST_SYNC_TIME, time)
            prefs.flush()
        } catch (e: Exception) {
            println("AdminPreferencesManager: Failed to set last sync time: ${e.message}")
        }
    }

    fun getWindowWidth(): Double {
        return try {
            prefs.getDouble(KEY_WINDOW_WIDTH, 1400.0)
        } catch (e: Exception) {
            1400.0
        }
    }

    fun setWindowWidth(width: Double) {
        try {
            prefs.putDouble(KEY_WINDOW_WIDTH, width)
            prefs.flush()
        } catch (e: Exception) {
            println("AdminPreferencesManager: Failed to set window width: ${e.message}")
        }
    }

    fun getWindowHeight(): Double {
        return try {
            prefs.getDouble(KEY_WINDOW_HEIGHT, 900.0)
        } catch (e: Exception) {
            900.0
        }
    }

    fun setWindowHeight(height: Double) {
        try {
            prefs.putDouble(KEY_WINDOW_HEIGHT, height)
            prefs.flush()
        } catch (e: Exception) {
            println("AdminPreferencesManager: Failed to set window height: ${e.message}")
        }
    }

    fun getSelectedDifficulty(): String? {
        return try {
            val value = prefs.get(KEY_SELECTED_DIFFICULTY, null)
            if (value.isNullOrEmpty()) null else value
        } catch (e: Exception) {
            null
        }
    }

    fun setSelectedDifficulty(difficulty: String) {
        try {
            prefs.put(KEY_SELECTED_DIFFICULTY, difficulty)
            prefs.flush()
        } catch (e: Exception) {
            println("AdminPreferencesManager: Failed to set selected difficulty: ${e.message}")
        }
    }

    fun clearAll() {
        try {
            prefs.clear()
            prefs.flush()
            println("AdminPreferencesManager: Cleared all admin preferences")
        } catch (e: Exception) {
            println("AdminPreferencesManager: Failed to clear preferences: ${e.message}")
        }
    }
}
