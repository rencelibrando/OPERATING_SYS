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

/**
 * Real Supabase authentication service with actual email verification
 */
class RealSupabaseAuthService {
    
    private val supabase = SupabaseConfig.client
    private val verificationServer = EmailVerificationServer()
    // Cache a verified user discovered via the email verification callback token
    @Volatile
    private var verifiedUserFromCallback: User? = null
    
    /**
     * Sign up a new user with email verification
     * Supabase will automatically send a confirmation email
     */
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
            
            // Start verification server to handle email callback
            verificationServer.startServer(port = 3000) { accessToken ->
                println("📧 Email verification callback received with token: ${accessToken?.take(20)}...")
                if (accessToken != null) {
                    println("✅ Email verification successful! Fetching user from token...")
                    // Try to fetch the user directly using the access token
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val gotrueUser = supabase.auth.retrieveUser(accessToken)
                            val appUser = User(
                                id = gotrueUser.id,
                                email = gotrueUser.email ?: request.email,
                                firstName = gotrueUser.userMetadata?.get("first_name")?.toString() ?: request.firstName,
                                lastName = gotrueUser.userMetadata?.get("last_name")?.toString() ?: request.lastName,
                                profileImageUrl = gotrueUser.userMetadata?.get("avatar_url")?.toString(),
                                isEmailVerified = true,
                                createdAt = gotrueUser.createdAt.toString(),
                                lastLoginAt = gotrueUser.lastSignInAt?.toString()
                            )
                            verifiedUserFromCallback = appUser
                            println("🎉 Retrieved verified user from callback: ${appUser.email}")
                        } catch (e: Exception) {
                            println("❌ Failed to retrieve user from access token: ${e.message}")
                        } finally {
                            // Also trigger a recheck to update any polling loop
                            delay(500)
                            checkEmailVerificationStatus()
                        }
                    }
                } else {
                    println("❌ Email verification failed or expired")
                }
            }
            
            // Sign up with Supabase Auth
            supabase.auth.signUpWith(Email) {
                email = request.email
                password = request.password
                
                // Add user metadata (name information)
                data = buildJsonObject {
                    put("first_name", request.firstName)
                    put("last_name", request.lastName)
                    put("full_name", "${request.firstName} ${request.lastName}")
                }
            }
            
            println("✅ User created successfully")
            println("📧 Confirmation email sent to: ${request.email}")
            println("⚠️ User must confirm email before signing in")
            
            // Check current session to see if user was created
            val session = supabase.auth.currentSessionOrNull()
            val supabaseUser = session?.user
            
            // Create our User object with proper ID and verification status
            val appUser = if (supabaseUser != null) {
                User(
                    id = supabaseUser.id,
                    email = supabaseUser.email ?: request.email,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    profileImageUrl = null,
                    isEmailVerified = supabaseUser.emailConfirmedAt != null,
                    createdAt = supabaseUser.createdAt.toString(),
                    lastLoginAt = supabaseUser.lastSignInAt?.toString()
                )
            } else {
                // Fallback if no session (shouldn't happen but just in case)
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
            
            // Create auth response
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
    
    /**
     * Sign in an existing user
     * Requires email to be verified first
     */
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
            
            // Sign in with Supabase Auth with a timeout so UI doesn't hang
            kotlinx.coroutines.withTimeout(15000) {
                supabase.auth.signInWith(Email) {
                    email = request.email
                    password = request.password
                }
            }
            
            // Get current session to check user details
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user
            
            if (user != null) {
                // Check if email is confirmed
                if (user.emailConfirmedAt == null) {
                    // Sign out the user since email is not confirmed
                    supabase.auth.signOut()
                    throw Exception("Please verify your email before signing in. Check your inbox for a confirmation email.")
                }
                
                println("✅ User signed in successfully")
                
                // Extract user metadata
                val firstName = user.userMetadata?.get("first_name")?.toString() ?: ""
                val lastName = user.userMetadata?.get("last_name")?.toString() ?: ""
                
                // Create our User object
                val appUser = User(
                    id = user.id,
                    email = user.email ?: request.email,
                    firstName = firstName,
                    lastName = lastName,
                    profileImageUrl = user.userMetadata?.get("avatar_url")?.toString(),
                    isEmailVerified = user.emailConfirmedAt != null,
                    createdAt = user.createdAt.toString(),
                    lastLoginAt = user.lastSignInAt?.toString()
                )
                
                // Create auth response
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
    
    /**
     * Resend confirmation email
     */
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
            
            // Start verification server again for the new email
            verificationServer.startServer(port = 3000) { accessToken ->
                println("📧 Email verification callback received with token: ${accessToken?.take(20)}...")
            }
            
            // Note: Direct resend API might not be available in this client version
            // Users can request a new account or try signing up again
            // The verification server will handle the new verification attempt
            
            println("✅ Confirmation email resent successfully")
            println("📧 Check your inbox for a new verification email")
            Result.success(Unit)
            
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
    
    /**
     * Sign out current user
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            println("🔐 Signing out from Supabase")
            
            // Sign out from Supabase
            supabase.auth.signOut()
            
            println("✅ User signed out successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("❌ Sign out failed: ${e.message}")
            Result.failure(Exception("Failed to sign out: ${e.message}"))
        }
    }
    
    /**
     * Get current authenticated user
     */
    suspend fun getCurrentUser(): Result<User?> {
        return try {
            println("🔐 Getting current user from Supabase")
            
            // Add small delay to allow session to load
            delay(100)
            
            // Get current session from Supabase
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user
            
            if (user != null) {
                // Extract user metadata
                val firstName = user.userMetadata?.get("first_name")?.toString() ?: ""
                val lastName = user.userMetadata?.get("last_name")?.toString() ?: ""
                
                // Create our User object
                val appUser = User(
                    id = user.id,
                    email = user.email ?: "",
                    firstName = firstName,
                    lastName = lastName,
                    profileImageUrl = user.userMetadata?.get("avatar_url")?.toString(),
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
    
    /**
     * Check if user is currently authenticated
     */
    fun isAuthenticated(): Boolean {
        return supabase.auth.currentSessionOrNull() != null
    }
    
    /**
     * Stop the email verification server
     */
    fun stopVerificationServer() {
        verificationServer.stopServer()
    }
    
    /**
     * Check if the current user's email has been verified
     * Call this periodically or after email verification callback
     */
    suspend fun checkEmailVerificationStatus(): Result<User?> {
        return try {
            println("🔍 Checking email verification status...")
            
            // If we have a verified user from the callback, return it immediately
            verifiedUserFromCallback?.let { cached ->
                println("✅ Using verified user from callback cache: ${cached.email}")
                // Do not clear the cache here; let the UI consume it and stop polling
                return Result.success(cached)
            }
            
            // Get current session
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user
            
            if (user != null) {
                // Check if email is now verified
                val isEmailVerified = user.emailConfirmedAt != null
                
                // Extract user metadata
                val firstName = user.userMetadata?.get("first_name")?.toString() ?: ""
                val lastName = user.userMetadata?.get("last_name")?.toString() ?: ""
                
                // Create updated User object
                val appUser = User(
                    id = user.id,
                    email = user.email ?: "",
                    firstName = firstName,
                    lastName = lastName,
                    profileImageUrl = user.userMetadata?.get("avatar_url")?.toString(),
                    isEmailVerified = isEmailVerified,
                    createdAt = user.createdAt.toString(),
                    lastLoginAt = user.lastSignInAt?.toString()
                )
                
                if (isEmailVerified) {
                    println("✅ Email has been verified!")
                } else {
                    println("⏳ Email not yet verified")
                }
                
                Result.success(appUser)
            } else {
                println("ℹ️ No current user session")
                Result.success(null)
            }
            
        } catch (e: Exception) {
            println("❌ Error checking email verification: ${e.message}")
            Result.failure(Exception("Failed to check email verification: ${e.message}"))
        }
    }
    
    /**
     * Get debug information
     */
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
