package org.example.project.core.profile

import io.github.jan.supabase.gotrue.auth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.config.SupabaseConfig
import org.example.project.domain.model.PersonalInfo
import org.example.project.domain.model.LearningProfile


class ProfileService {
    
    private val supabase = SupabaseConfig.client
    
    suspend fun updatePersonalInfo(personalInfo: PersonalInfo): Result<Unit> {
        return try {
            println("💾 Updating personal info in Supabase")
            
            if (!SupabaseConfig.isConfigured()) {
                throw Exception("Supabase is not configured")
            }
            
            
            val user = supabase.auth.currentUserOrNull()
            if (user == null) {
                throw Exception("No authenticated user found")
            }
            
            
            val metadata = buildJsonObject {
                put("first_name", personalInfo.firstName)
                put("last_name", personalInfo.lastName)
                put("full_name", "${personalInfo.firstName} ${personalInfo.lastName}")
                put("date_of_birth", personalInfo.dateOfBirth ?: "")
                put("location", personalInfo.location ?: "")
                put("native_language", personalInfo.nativeLanguage)
                put("target_languages", personalInfo.targetLanguages.joinToString(","))
                put("bio", personalInfo.bio ?: "")
                put("avatar", personalInfo.avatar)
                put("avatar_url", personalInfo.profileImageUrl ?: "")
            }
            
            
            println("🔍 ProfileService - Saving metadata:")
            metadata.forEach { (key, value) ->
                println("   $key: $value")
            }
            
            
            supabase.auth.updateUser {
                data = metadata
            }
            
            println("✅ Personal info updated successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("❌ Failed to update personal info: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun updateLearningProfile(learningProfile: LearningProfile): Result<Unit> {
        return try {
            println("💾 Updating learning profile in Supabase")
            
            if (!SupabaseConfig.isConfigured()) {
                throw Exception("Supabase is not configured")
            }
            
            
            val user = supabase.auth.currentUserOrNull()
            if (user == null) {
                throw Exception("No authenticated user found")
            }
            
            
            val metadata = buildJsonObject {
                put("current_level", learningProfile.currentLevel)
                put("primary_goal", learningProfile.primaryGoal)
                put("weekly_goal_hours", learningProfile.weeklyGoalHours)
                put("preferred_learning_style", learningProfile.preferredLearningStyle)
                put("focus_areas", learningProfile.focusAreas.joinToString(","))
                put("available_time_slots", learningProfile.availableTimeSlots.joinToString(","))
                put("motivations", learningProfile.motivations.joinToString(","))
            }
            
            
            supabase.auth.updateUser {
                data = metadata
            }
            
            println("✅ Learning profile updated successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("❌ Failed to update learning profile: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun loadPersonalInfo(): Result<PersonalInfo?> {
        return try {
            println("📖 Loading personal info from Supabase")
            
            val user = supabase.auth.currentUserOrNull()
            if (user == null) {
                return Result.success(null)
            }
            
            val metadata = user.userMetadata
            
            
            println("🔍 ProfileService - Loading metadata:")
            metadata?.forEach { (key, value) ->
                println("   $key: $value")
            }
            
            val personalInfo = PersonalInfo(
                firstName = metadata?.get("first_name")?.toString()?.removeSurrounding("\"") ?: "",
                lastName = metadata?.get("last_name")?.toString()?.removeSurrounding("\"") ?: "",
                email = user.email ?: "",
                avatar = metadata?.get("avatar")?.toString()?.removeSurrounding("\"") ?: "",
                profileImageUrl = metadata?.get("avatar_url")?.toString()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() },
                dateOfBirth = metadata?.get("date_of_birth")?.toString()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() },
                location = metadata?.get("location")?.toString()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() },
                nativeLanguage = metadata?.get("native_language")?.toString()?.removeSurrounding("\"") ?: "",
                targetLanguages = metadata?.get("target_languages")?.toString()?.removeSurrounding("\"")
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                bio = metadata?.get("bio")?.toString()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
            )
            
            println("✅ Personal info loaded successfully")
            Result.success(personalInfo)
            
        } catch (e: Exception) {
            println("❌ Failed to load personal info: ${e.message}")
            Result.failure(e)
        }
    }
    suspend fun loadLearningProfile(): Result<LearningProfile?> {
        return try {
            println("📖 Loading learning profile from Supabase")
            
            val user = supabase.auth.currentUserOrNull()
            if (user == null) {
                return Result.success(null)
            }
            
            val metadata = user.userMetadata
            val learningProfile = LearningProfile(
                currentLevel = metadata?.get("current_level")?.toString()?.removeSurrounding("\"") ?: "Beginner",
                primaryGoal = metadata?.get("primary_goal")?.toString()?.removeSurrounding("\"") ?: "",
                weeklyGoalHours = metadata?.get("weekly_goal_hours")?.toString()?.toIntOrNull() ?: 3,
                preferredLearningStyle = metadata?.get("preferred_learning_style")?.toString()?.removeSurrounding("\"") ?: "Mixed",
                focusAreas = metadata?.get("focus_areas")?.toString()?.removeSurrounding("\"")
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                availableTimeSlots = metadata?.get("available_time_slots")?.toString()?.removeSurrounding("\"")
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                motivations = metadata?.get("motivations")?.toString()?.removeSurrounding("\"")
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            )
            
            println("✅ Learning profile loaded successfully")
            Result.success(learningProfile)
            
        } catch (e: Exception) {
            println("❌ Failed to load learning profile: ${e.message}")
            Result.failure(e)
        }
    }
}
