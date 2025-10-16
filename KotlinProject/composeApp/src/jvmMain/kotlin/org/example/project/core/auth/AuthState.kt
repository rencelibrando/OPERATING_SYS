package org.example.project.core.auth

sealed class AuthState {
    object Loading : AuthState()

    object Unauthenticated : AuthState()

    data class Authenticated(val user: User) : AuthState()

    data class AwaitingEmailVerification(val email: String, val message: String) : AuthState()

    data class EmailVerified(val user: User, val message: String) : AuthState()

    data class SignupComplete(val email: String, val message: String) : AuthState()

    data class Error(val message: String) : AuthState()
}

data class User(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val profileImageUrl: String? = null,
    val isEmailVerified: Boolean = false,
    val createdAt: String,
    val lastLoginAt: String? = null,
) {
    val fullName: String get() = "$firstName $lastName"
    val initials: String get() = "${firstName.firstOrNull()}${lastName.firstOrNull()}".uppercase()
}

data class LoginRequest(
    val email: String,
    val password: String,
)

data class SignUpRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
)

data class AuthResponse(
    val user: User,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

data class AuthError(
    val code: String,
    val message: String,
    val details: String? = null,
)
