package org.example.project.core.onboarding

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.api.SupabaseApiHelper
import org.example.project.core.config.SupabaseConfig
import java.time.Instant

class OnboardingRepository {
    private val supabase = SupabaseConfig.client
    private val profileCache = mutableMapOf<String, Pair<OnboardingProfile?, Long>>()
    private val cacheDuration = 30_000L // 30 seconds cache

    suspend fun fetchOnboardingProfile(userId: String): Result<OnboardingProfile?> =
        runCatching {
            // Check cache first (before API call)
            val cached = profileCache[userId]
            if (cached != null && System.currentTimeMillis() - cached.second < cacheDuration) {
                println("[Profile] Returning cached profile for user: $userId")
                return@runCatching cached.first
            }

            // Use API helper with retry for the actual API call
            SupabaseApiHelper.executeWithRetry {
                withContext(Dispatchers.IO) {
                    // Check if Supabase is configured
                    if (!SupabaseApiHelper.isReady()) {
                        println("[Profile] ERROR: Supabase not configured")
                        return@withContext null
                    }

                    // Verify user ID is valid
                    if (userId.isBlank()) {
                        println("[Profile] ERROR: User ID is blank")
                        return@withContext null
                    }

                    // Ensure valid session before making request
                    if (!SupabaseApiHelper.ensureValidSession()) {
                        println("[Profile] ERROR: No valid session")
                        return@withContext null
                    }

                    println("[Profile] Fetching from Supabase for user: $userId")

                    try {
                        val response =
                            supabase.postgrest["profiles"].select {
                                filter {
                                    eq("user_id", userId)
                                }
                                limit(1)
                            }

                        val profile =
                            try {
                                val profileDTO = response.decodeSingle<OnboardingProfileDTO>()
                                profileDTO.toDomain()
                            } catch (e: Exception) {
                                // Profile doesn't exist yet (first-time user) - this is OK
                                println("[Profile] No existing profile found for user $userId (first-time user)")
                                null
                            }

                        // Cache the result
                        profileCache[userId] = Pair(profile, System.currentTimeMillis())
                        println("[Profile] Profile fetched and cached successfully")

                        profile
                    } catch (e: Exception) {
                        val errorMessage = e.message ?: "Unknown error"
                        println("[Profile] ERROR fetching profile: $errorMessage")
                        e.printStackTrace()
                        null
                    }
                }
            }.getOrElse { e ->
                println("[Profile] API call failed: ${e.message}")
                null
            }
        }.onFailure { e ->
            println("[Profile] CRITICAL ERROR: ${e.message}")
            e.printStackTrace()
        }

    suspend fun upsertOnboardingState(
        userId: String,
        isOnboarded: Boolean,
        aiProfile: JsonObject?,
        onboardingState: JsonObject?,
        currentStep: Int,
    ): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                // Invalidate cache
                profileCache.remove(userId)

                val payload =
                    buildJsonObject {
                        put("user_id", JsonPrimitive(userId))
                        put("is_onboarded", JsonPrimitive(isOnboarded))
                        aiProfile?.let { put("ai_profile", it) }
                        onboardingState?.let { put("onboarding_state", it) } ?: put("onboarding_state", JsonNull)
                        put("current_step", JsonPrimitive(currentStep))
                        if (isOnboarded) {
                            put("completed_at", JsonPrimitive(Instant.now().toString()))
                        } else {
                            put("completed_at", JsonNull)
                        }
                    }

                supabase.postgrest["profiles"].upsert(payload)
                println("[Profile] Upserted onboarding state for user: $userId")
            }
        }

    suspend fun markOnboardingComplete(
        userId: String,
        aiProfile: JsonObject,
        stateSnapshot: JsonObject,
        stepCount: Int,
    ): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                // Invalidate cache
                profileCache.remove(userId)

                // Use UPSERT instead of UPDATE to create row if it doesn't exist
                supabase.postgrest["profiles"].upsert(
                    buildJsonObject {
                        put("user_id", JsonPrimitive(userId)) // Required for upsert
                        put("is_onboarded", JsonPrimitive(true))
                        put("ai_profile", aiProfile)
                        put("onboarding_state", stateSnapshot)
                        put("current_step", JsonPrimitive(stepCount))
                        put("completed_at", JsonPrimitive(Instant.now().toString()))
                    },
                )
                println("[Profile] Marked onboarding complete for user: $userId")
            }
        }

    suspend fun resetOnboardingProfile(userId: String): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                // Invalidate cache
                profileCache.remove(userId)

                supabase.postgrest["profiles"].upsert(
                    buildJsonObject {
                        put("user_id", JsonPrimitive(userId))
                        put("is_onboarded", JsonPrimitive(false))
                        put("ai_profile", JsonNull)
                        put("onboarding_state", JsonNull)
                        put("current_step", JsonPrimitive(0))
                        put("completed_at", JsonNull)
                    },
                )
                println("[Profile] Reset onboarding profile for user: $userId")
            }
        }
}

@kotlinx.serialization.Serializable
data class OnboardingProfileDTO(
    val user_id: String,
    val is_onboarded: Boolean = false,
    val ai_profile: JsonObject? = null,
    val onboarding_state: JsonObject? = null,
    val current_step: Int = 0,
) {
    fun toDomain(): OnboardingProfile =
        OnboardingProfile(
            userId = user_id,
            isOnboarded = is_onboarded,
            aiProfile = ai_profile,
            stateSnapshot = onboarding_state,
            currentStep = current_step,
        )
}

data class OnboardingProfile(
    val userId: String,
    val isOnboarded: Boolean,
    val aiProfile: JsonObject?,
    val stateSnapshot: JsonObject?,
    val currentStep: Int,
)
