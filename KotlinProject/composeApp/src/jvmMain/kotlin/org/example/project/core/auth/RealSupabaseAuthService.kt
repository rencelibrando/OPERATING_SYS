package org.example.project.core.auth

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.config.SupabaseConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RealSupabaseAuthService {
    
    private val supabase = SupabaseConfig.client
    private val verificationServer = EmailVerificationServer()
    
    @Volatile
    private var verifiedUserFromCallback: User? = null
    

    suspend fun signUp(request: SignUpRequest): Result<AuthResponse> {
        return try {
            println("🔐 Creating account with Supabase: ${request.email}")
            
            if (!SupabaseConfig.isConfigured()) {
                val configStatus = SupabaseConfig.getConfigStatus()
                println("❌ Supabase Configuration Error:")
                configStatus.forEach { (key, value) -> 
                    println("   $key: $value")
                }
                throw Exception("Supabase is not configured. Please check your Supabase credentials in SupabaseConfig.kt.")
            }           
            
         
            supabase.auth.signUpWith(Email) {
                email = request.email
                password = request.password
                
                data = buildJsonObject {
                    put("first_name", request.firstName)
                    put("last_name", request.lastName)
                    put("full_name", "${request.firstName} ${request.lastName}")
                }
            }
            
            println("✅ User created successfully")
            println("📧 Confirmation email sent to: ${request.email}")
            println("⚠️ User must confirm email before signing in")
            println("💡 After email verification, return to app and sign in with your credentials")
            
            println("🔧 Email verification will be handled via GitHub Pages callback")
            
            val session = supabase.auth.currentSessionOrNull()
            val supabaseUser = session?.user
            
            
            val appUser = if (supabaseUser != null) {
                
                val firstName = supabaseUser.userMetadata?.get("first_name")?.toString()?.removeSurrounding("\"") ?: ""
                val lastName = supabaseUser.userMetadata?.get("last_name")?.toString()?.removeSurrounding("\"") ?: ""
                
                User(
                    id = supabaseUser.id,
                    email = supabaseUser.email ?: request.email,
                    firstName = firstName,
                    lastName = lastName,
                    profileImageUrl = supabaseUser.userMetadata?.get("avatar_url")?.toString()?.removeSurrounding("\""),
                    isEmailVerified = supabaseUser.emailConfirmedAt != null,
                    createdAt = supabaseUser.createdAt.toString(),
                    lastLoginAt = supabaseUser.lastSignInAt?.toString()
                )
            } else {
                
                User(
                    id = "pending-${request.email.hashCode()}",
                    email = request.email,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    profileImageUrl = null,
                    isEmailVerified = false,
                    createdAt = System.currentTimeMillis().toString(),
                    lastLoginAt = null
                )
            }
            
            println("📧 User created with email verification required: ${appUser.isEmailVerified}")
            
            
            val response = AuthResponse(
                user = appUser,
                accessToken = "pending-verification",
                refreshToken = "pending-verification", 
                expiresIn = 3600L
            )
            
            Result.success(response)
            
        } catch (e: Exception) {
            println("❌ Sign up failed: ${e.message}")
            val errorMessage = when {
                e.message?.contains("User already registered") == true -> "An account with this email already exists"
                e.message?.contains("Invalid email") == true -> "Please enter a valid email address"
                e.message?.contains("Password should be at least 6 characters") == true -> "Password must be at least 6 characters long"
                e.message?.contains("Signup requires a valid password") == true -> "Please enter a valid password"
                e.message?.contains("network") == true -> "Network error. Please check your connection"
                e.message?.contains("not configured") == true -> e.message
                else -> "Failed to create account: ${e.message}"
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
    suspend fun signIn(request: LoginRequest): Result<AuthResponse> {
        return try {
            println("🔐 Signing in with Supabase: ${request.email}")
            
            if (!SupabaseConfig.isConfigured()) {
                val configStatus = SupabaseConfig.getConfigStatus()
                println("❌ Supabase Configuration Error:")
                configStatus.forEach { (key, value) -> 
                    println("   $key: $value")
                }
                throw Exception("Supabase is not configured. Please check your Supabase credentials in SupabaseConfig.kt.")
            }
            
            
            kotlinx.coroutines.withTimeout(15000) {
                supabase.auth.signInWith(Email) {
                    email = request.email
                    password = request.password
                }
            }
            
            
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user
            
            if (user != null) {
                
                if (user.emailConfirmedAt == null) {
                    
                    supabase.auth.signOut()
                    throw Exception("Please verify your email before signing in. Check your inbox for a confirmation email.")
                }
                
                println("✅ User signed in successfully")
                
                
                val firstName = user.userMetadata?.get("first_name")?.toString()?.removeSurrounding("\"") ?: ""
                val lastName = user.userMetadata?.get("last_name")?.toString()?.removeSurrounding("\"") ?: ""
                
                
                val appUser = User(
                    id = user.id,
                    email = user.email ?: request.email,
                    firstName = firstName,
                    lastName = lastName,
                    profileImageUrl = user.userMetadata?.get("avatar_url")?.toString()?.removeSurrounding("\""),
                    isEmailVerified = user.emailConfirmedAt != null,
                    createdAt = user.createdAt.toString(),
                    lastLoginAt = user.lastSignInAt?.toString()
                )
                
                
                val response = AuthResponse(
                    user = appUser,
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresIn = session.expiresIn
                )
                
                Result.success(response)
            } else {
                throw Exception("Sign in failed - no user session found")
            }
            
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            println("❌ Sign in timed out: ${e.message}")
            Result.failure(Exception("Sign in timed out. Please check your internet connection and try again."))
        } catch (e: Exception) {
            println("❌ Sign in failed: ${e.message}")
            val errorMessage = when {
                e.message?.contains("Invalid login credentials") == true -> "Invalid email or password"
                e.message?.contains("Email not confirmed") == true -> "Please verify your email before signing in"
                e.message?.contains("verify your email") == true -> e.message
                e.message?.contains("Too many requests") == true -> "Too many login attempts. Please try again later"
                e.message?.contains("network") == true -> "Network error. Please check your connection"
                e.message?.contains("not configured") == true -> e.message
                else -> "Sign in failed: ${e.message}"
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
    suspend fun resendVerificationEmail(email: String): Result<Unit> {
        return try {
            println("📧 Resending confirmation email to: $email")
            
            if (!SupabaseConfig.isConfigured()) {
                val configStatus = SupabaseConfig.getConfigStatus()
                println("❌ Supabase Configuration Error:")
                configStatus.forEach { (key, value) -> 
                    println("   $key: $value")
                }
                throw Exception("Supabase is not configured. Please check your Supabase credentials in SupabaseConfig.kt.")
            }
            

            println("⚠️ Resend not directly supported in this API version")
            println("💡 Recommendation: Try signing up again to receive a new verification email")
            
            Result.failure(Exception("Please try signing up again to receive a new verification email. If you already have an account, the system will automatically resend the verification email."))
            
        } catch (e: Exception) {
            println("❌ Failed to resend confirmation email: ${e.message}")
            val errorMessage = when {
                e.message?.contains("network") == true -> "Network error. Please check your connection"
                e.message?.contains("not configured") == true -> e.message
                e.message?.contains("rate") == true -> "Too many emails sent. Please wait a few minutes before trying again"
                else -> "Failed to send confirmation email: ${e.message}"
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
    suspend fun signOut(): Result<Unit> {
        return try {
            println("🔐 Signing out from Supabase")
            
            
            supabase.auth.signOut()
            
            println("✅ User signed out successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("❌ Sign out failed: ${e.message}")
            Result.failure(Exception("Failed to sign out: ${e.message}"))
        }
    }
    suspend fun getCurrentUser(): Result<User?> {
        return try {
            println("🔐 Getting current user from Supabase")
            
            
            delay(100)
            
            
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user
            
            if (user != null) {
                
                val firstName = user.userMetadata?.get("first_name")?.toString()?.removeSurrounding("\"") ?: ""
                val lastName = user.userMetadata?.get("last_name")?.toString()?.removeSurrounding("\"") ?: ""
                
                
                val appUser = User(
                    id = user.id,
                    email = user.email ?: "",
                    firstName = firstName,
                    lastName = lastName,
                    profileImageUrl = user.userMetadata?.get("avatar_url")?.toString()?.removeSurrounding("\""),
                    isEmailVerified = user.emailConfirmedAt != null,
                    createdAt = user.createdAt.toString(),
                    lastLoginAt = user.lastSignInAt?.toString()
                )
                
                println("✅ Current user found: ${appUser.email} (verified: ${appUser.isEmailVerified})")
                Result.success(appUser)
            } else {
                println("ℹ️ No authenticated user")
                Result.success(null)
            }
            
        } catch (e: Exception) {
            println("❌ Get current user failed: ${e.message}")
            Result.failure(Exception("Failed to get current user: ${e.message}"))
        }
    }
    
    fun isAuthenticated(): Boolean {
        return supabase.auth.currentSessionOrNull() != null
    }
    
    fun stopVerificationServer() {
        verificationServer.stopServer()
    }
    
    suspend fun checkEmailVerificationStatus(): Result<User?> {
        return try {
            println("🔍 Checking email verification status...")
            
            verifiedUserFromCallback?.let { cached ->
                println("✅ Using verified user from callback cache: ${cached.email}")
                return Result.success(cached)
            }
            
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user
            
            if (user != null) {
                val isEmailVerified = user.emailConfirmedAt != null
                
                val firstName = user.userMetadata?.get("first_name")?.toString()?.removeSurrounding("\"") ?: ""
                val lastName = user.userMetadata?.get("last_name")?.toString()?.removeSurrounding("\"") ?: ""
                
                val appUser = User(
                    id = user.id,
                    email = user.email ?: "",
                    firstName = firstName,
                    lastName = lastName,
                    profileImageUrl = user.userMetadata?.get("avatar_url")?.toString()?.removeSurrounding("\""),
                    isEmailVerified = isEmailVerified,
                    createdAt = user.createdAt.toString(),
                    lastLoginAt = user.lastSignInAt?.toString()
                )
                
                if (isEmailVerified) {
                    println("✅ Email has been verified and user is signed in!")
                    return Result.success(appUser)
                } else {
                    println("User is signed in but email not yet verified")
                    return Result.success(appUser)
                }
            } else {
                println("ℹNo current user session - user needs to sign in after email verification")
                println("💡 After verifying email, user should sign in with their credentials")
                return Result.success(null)
            }
            
        } catch (e: Exception) {
            println("Error checking email verification: ${e.message}")
            Result.failure(Exception("Failed to check email verification: ${e.message}"))
        }
    }
    
    suspend fun updateUserMetadata(profileImageUrl: String?): Result<Unit> {
        return try {
            println("🔐 Updating user metadata with profile image URL: $profileImageUrl")
            
            val session = supabase.auth.currentSessionOrNull()
            if (session == null) {
                return Result.failure(Exception("No active session"))
            }
            
            val currentUser = session.user
            val currentMetadata = mutableMapOf<String, Any>()
            currentUser?.userMetadata?.forEach { (key, value) ->
                currentMetadata[key] = value ?: ""
            }
            
            // Update the avatar_url in metadata
            if (profileImageUrl != null) {
                currentMetadata["avatar_url"] = profileImageUrl
            } else {
                currentMetadata.remove("avatar_url")
            }
            
            // Update user metadata
            supabase.auth.updateUser {
                data = buildJsonObject {
                    currentMetadata.forEach { (key, value) ->
                        put(key, value.toString())
                    }
                }
            }
            
            println("✅ User metadata updated successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("❌ Failed to update user metadata: ${e.message}")
            Result.failure(e)
        }
    }

    fun getDebugInfo(): Map<String, Any> {
        val session = supabase.auth.currentSessionOrNull()
        val user = session?.user
        
        return mapOf(
            "current_user" to (user?.email ?: "none"),
            "is_authenticated" to isAuthenticated(),
            "email_verified" to (user?.emailConfirmedAt != null),
            "session_exists" to (session != null),
            "supabase_configured" to SupabaseConfig.isConfigured(),
            "supabase_config" to SupabaseConfig.getConfigStatus()
        )
    }
}
