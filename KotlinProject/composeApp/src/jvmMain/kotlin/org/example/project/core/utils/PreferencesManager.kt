package org.example.project.core.utils

import java.util.prefs.Preferences

object PreferencesManager {
    private val prefs: Preferences = Preferences.userNodeForPackage(PreferencesManager::class.java)

    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed_"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time_"
    private const val KEY_BACKEND_SETUP_COMPLETED = "backend_setup_completed"
    private const val KEY_BACKEND_SETUP_TIME = "backend_setup_time"

    fun cacheOnboardingCompletion(
        userId: String,
        isCompleted: Boolean,
    ) {
        try {
            prefs.putBoolean(KEY_ONBOARDING_COMPLETED + userId, isCompleted)
            prefs.putLong(KEY_LAST_SYNC_TIME + userId, System.currentTimeMillis())
            prefs.flush()
            println("PreferencesManager: Cached onboarding completion for user $userId: $isCompleted")
        } catch (e: Exception) {
            println("PreferencesManager: Failed to cache onboarding completion: ${e.message}")
        }
    }

    fun getCachedOnboardingCompletion(userId: String): Boolean? {
        try {
            val lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME + userId, 0L)
            val cacheAge = System.currentTimeMillis() - lastSyncTime
            val maxCacheAge = 7 * 24 * 60 * 60 * 1000L

            if (lastSyncTime == 0L || cacheAge > maxCacheAge) {
                println("PreferencesManager: Cache miss or stale for user $userId")
                return null
            }

            val isCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED + userId, false)
            println("PreferencesManager: Cache hit for user $userId: $isCompleted")
            return isCompleted
        } catch (e: Exception) {
            println("PreferencesManager: Failed to get cached onboarding completion: ${e.message}")
            return null
        }
    }

    fun clearOnboardingCache(userId: String) {
        try {
            prefs.remove(KEY_ONBOARDING_COMPLETED + userId)
            prefs.remove(KEY_LAST_SYNC_TIME + userId)
            prefs.flush()
            println("PreferencesManager: Cleared onboarding cache for user $userId")
        } catch (e: Exception) {
            println("PreferencesManager: Failed to clear cache: ${e.message}")
        }
    }

    fun cacheBackendSetupCompleted(isCompleted: Boolean) {
        try {
            prefs.putBoolean(KEY_BACKEND_SETUP_COMPLETED, isCompleted)
            prefs.putLong(KEY_BACKEND_SETUP_TIME, System.currentTimeMillis())
            prefs.flush()
            println("PreferencesManager: Cached backend setup completion: $isCompleted")
        } catch (e: Exception) {
            println("PreferencesManager: Failed to cache backend setup completion: ${e.message}")
        }
    }

    fun getCachedBackendSetupCompleted(): Boolean {
        try {
            val lastSetupTime = prefs.getLong(KEY_BACKEND_SETUP_TIME, 0L)

            if (lastSetupTime == 0L) {
                return false
            }

            val isCompleted = prefs.getBoolean(KEY_BACKEND_SETUP_COMPLETED, false)
            println("PreferencesManager: Backend setup cache status: $isCompleted (setup time: $lastSetupTime)")
            return isCompleted
        } catch (e: Exception) {
            println("PreferencesManager: Failed to get cached backend setup status: ${e.message}")
            return false
        }
    }

    fun clearBackendSetupCache() {
        try {
            prefs.remove(KEY_BACKEND_SETUP_COMPLETED)
            prefs.remove(KEY_BACKEND_SETUP_TIME)
            prefs.flush()
            println("PreferencesManager: Cleared backend setup cache")
        } catch (e: Exception) {
            println("PreferencesManager: Failed to clear backend setup cache: ${e.message}")
        }
    }

    fun clearAll() {
        try {
            prefs.clear()
            prefs.flush()
            println("PreferencesManager: Cleared all cached data")
        } catch (e: Exception) {
            println("PreferencesManager: Failed to clear all cache: ${e.message}")
        }
    }
}
