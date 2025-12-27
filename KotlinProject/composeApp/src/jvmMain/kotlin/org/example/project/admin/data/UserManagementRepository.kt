package org.example.project.admin.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.example.project.core.api.SupabaseApiHelper
import org.example.project.core.config.SupabaseConfig

data class AdminUser(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val isEmailVerified: Boolean,
    val createdAt: String,
    val lastLoginAt: String?,
    val profileImageUrl: String?,
)

@Serializable
data class UserProfileRow(
    @SerialName("id")
    val id: String,
    @SerialName("personal_info")
    val personalInfo: JsonObject? = null,
    @SerialName("learning_profile")
    val learningProfile: JsonObject? = null,
    @SerialName("account_info")
    val accountInfo: JsonObject? = null,
)

class UserManagementRepository {
    private val supabase = SupabaseConfig.client

    suspend fun getAllUsers(): Result<List<AdminUser>> =
        runCatching {
            SupabaseApiHelper.executeWithRetry {
                withContext(Dispatchers.IO) {
                    if (!SupabaseApiHelper.isReady()) {
                        throw Exception("Supabase not configured")
                    }

                    val response =
                        supabase.postgrest["user_profiles"].select {
                        }

                    val profiles = response.decodeList<UserProfileRow>()

                    profiles.map { profile ->
                        val personalInfo = profile.personalInfo
                        val accountInfo = profile.accountInfo

                        val firstName = personalInfo?.get("firstName")?.jsonPrimitive?.contentOrNull
                        val lastName = personalInfo?.get("lastName")?.jsonPrimitive?.contentOrNull
                        val email =
                            personalInfo?.get("email")?.jsonPrimitive?.contentOrNull
                                ?: accountInfo?.get("email")?.jsonPrimitive?.contentOrNull
                                ?: ""
                        val avatarUrl =
                            personalInfo?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                                ?: personalInfo?.get("avatar")?.jsonPrimitive?.contentOrNull

                        AdminUser(
                            id = profile.id,
                            email = email,
                            firstName = firstName,
                            lastName = lastName,
                            isEmailVerified = true, // Would need to check from auth.users
                            createdAt = "", // Could extract from profile if available
                            lastLoginAt = null,
                            profileImageUrl = avatarUrl,
                        )
                    }
                }
            }.getOrElse { e ->
                println("[UserManagement] Error fetching users: ${e.message}")
                throw e
            }
        }

    suspend fun deleteUser(userId: String): Result<Unit> =
        SupabaseApiHelper.executeWithRetry {
            withContext(Dispatchers.IO) {
                try {
                    // Delete user profile
                    supabase.postgrest["user_profiles"].delete {
                        filter {
                            eq("id", userId)
                        }
                    }
                    println("[UserManagement] Deleted user profile: $userId")
                    Result.success(Unit)
                } catch (e: Exception) {
                    println("[UserManagement] Error deleting user: ${e.message}")
                    Result.failure(e)
                }
            }
        }
}
