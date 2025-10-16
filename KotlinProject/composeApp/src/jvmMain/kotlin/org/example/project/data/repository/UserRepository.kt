package org.example.project.data.repository

import org.example.project.domain.model.User
import org.example.project.domain.model.UserProfile
import org.example.project.domain.model.UserSettings

interface UserRepository {
    suspend fun getUserProfile(userId: String): Result<UserProfile?>

    suspend fun createUserProfile(profile: UserProfile): Result<UserProfile>

    suspend fun updateUserProfile(profile: UserProfile): Result<UserProfile>

    suspend fun deleteUserProfile(userId: String): Result<Unit>

    suspend fun getUser(userId: String): Result<User?>

    suspend fun updateUser(user: User): Result<User>

    suspend fun signUp(email: String, password: String): Result<String> // Returns user ID

    suspend fun signIn(email: String, password: String): Result<String> // Returns user ID

    suspend fun signOut(): Result<Unit>

    suspend fun getCurrentUser(): Result<String?> // Returns current user ID

    suspend fun getUserSettings(userId: String): Result<UserSettings?>

    suspend fun updateUserSettings(settings: UserSettings): Result<UserSettings>
}
