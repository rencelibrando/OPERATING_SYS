package org.example.project.data.repository

import org.example.project.domain.model.*

interface UserRepository {
    
    // User Profile Operations
    suspend fun getUserProfile(userId: String): Result<UserProfile?>
    suspend fun createUserProfile(profile: UserProfile): Result<UserProfile>
    suspend fun updateUserProfile(profile: UserProfile): Result<UserProfile>
    suspend fun deleteUserProfile(userId: String): Result<Unit>
    
    // User Basic Info
    suspend fun getUser(userId: String): Result<User?>
    suspend fun updateUser(user: User): Result<User>
    
    // Authentication
    suspend fun signUp(email: String, password: String): Result<String> // Returns user ID
    suspend fun signIn(email: String, password: String): Result<String> // Returns user ID
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentUser(): Result<String?> // Returns current user ID
    
    // Settings
    suspend fun getUserSettings(userId: String): Result<UserSettings?>
    suspend fun updateUserSettings(settings: UserSettings): Result<UserSettings>
}
