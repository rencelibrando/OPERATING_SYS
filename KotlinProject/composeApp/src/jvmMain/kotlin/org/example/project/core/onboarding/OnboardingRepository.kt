package org.example.project.core.onboarding

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.config.SupabaseConfig
import java.time.Instant

class OnboardingRepository {

    private val supabase = SupabaseConfig.client

    suspend fun fetchOnboardingProfile(userId: String): Result<OnboardingProfile?> = runCatching {
        withContext(Dispatchers.IO) {
            val response = supabase.postgrest["profiles"].select {
                filter {
                    eq("user_id", userId)
                }
                limit(1)
            }

            try {
                response.decodeSingle<OnboardingProfileDTO>().toDomain()
            } catch (e: Exception) {
                // Profile doesn't exist yet (first-time user)
                println("??? No existing profile found for user $userId, will create new one")
                null
            }
        }
    }

    suspend fun upsertOnboardingState(
        userId: String,
        isOnboarded: Boolean,
        aiProfile: JsonObject?,
        onboardingState: JsonObject?,
        currentStep: Int
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
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
        }
    }

    suspend fun markOnboardingComplete(
        userId: String,
        aiProfile: JsonObject,
        stateSnapshot: JsonObject,
        stepCount: Int
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            // Use UPSERT instead of UPDATE to create row if it doesn't exist
            supabase.postgrest["profiles"].upsert(
                buildJsonObject {
                    put("user_id", JsonPrimitive(userId))  // Required for upsert
                    put("is_onboarded", JsonPrimitive(true))
                    put("ai_profile", aiProfile)
                    put("onboarding_state", stateSnapshot)
                    put("current_step", JsonPrimitive(stepCount))
                    put("completed_at", JsonPrimitive(Instant.now().toString()))
                }
            )
        }
    }

    suspend fun resetOnboardingProfile(userId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            supabase.postgrest["profiles"].upsert(
                buildJsonObject {
                    put("user_id", JsonPrimitive(userId))
                    put("is_onboarded", JsonPrimitive(false))
                    put("ai_profile", JsonNull)
                    put("onboarding_state", JsonNull)
                    put("current_step", JsonPrimitive(0))
                    put("completed_at", JsonNull)
                }
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class OnboardingProfileDTO(
    val user_id: String,
    val is_onboarded: Boolean = false,
    val ai_profile: JsonObject? = null,
    val onboarding_state: JsonObject? = null,
    val current_step: Int = 0
) {
    fun toDomain(): OnboardingProfile = OnboardingProfile(
        userId = user_id,
        isOnboarded = is_onboarded,
        aiProfile = ai_profile,
        stateSnapshot = onboarding_state,
        currentStep = current_step
    )
}

data class OnboardingProfile(
    val userId: String,
    val isOnboarded: Boolean,
    val aiProfile: JsonObject?,
    val stateSnapshot: JsonObject?,
    val currentStep: Int
)

