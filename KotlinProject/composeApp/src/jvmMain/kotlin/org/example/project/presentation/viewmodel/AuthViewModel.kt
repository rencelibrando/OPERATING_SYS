package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.example.project.core.auth.*
import org.example.project.core.utils.ValidationUtils

/**
 * ViewModel for managing authentication state and operations with real email verification
 */
class AuthViewModel : ViewModel() {
    
    private val authService = RealSupabaseAuthService()
    
    // Authentication state  
    private val _authState = mutableStateOf<AuthState>(AuthState.Loading)
    val authState: State<AuthState> = _authState
    
    // Form states
    private val _loginEmail = mutableStateOf("")
    val loginEmail: String get() = _loginEmail.value
    
    private val _loginPassword = mutableStateOf("")
    val loginPassword: String get() = _loginPassword.value
    
    private val _signUpFirstName = mutableStateOf("")
    val signUpFirstName: String get() = _signUpFirstName.value
    
    private val _signUpLastName = mutableStateOf("")
    val signUpLastName: String get() = _signUpLastName.value
    
    private val _signUpEmail = mutableStateOf("")
    val signUpEmail: String get() = _signUpEmail.value
    
    private val _signUpPassword = mutableStateOf("")
    val signUpPassword: String get() = _signUpPassword.value
    
    private val _signUpConfirmPassword = mutableStateOf("")
    val signUpConfirmPassword: String get() = _signUpConfirmPassword.value
    
    // UI states
    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading.value
    
    private val _errorMessage = mutableStateOf("")
    val errorMessage: String get() = _errorMessage.value
    
    private val _successMessage = mutableStateOf("")
    val successMessage: String get() = _successMessage.value
    
    private val _isLoginMode = mutableStateOf(true)
    val isLoginMode: Boolean get() = _isLoginMode.value
    
    init {
        // Debug: Print configuration status
        val configStatus = org.example.project.core.config.SupabaseConfig.getConfigStatus()
        println("🔧 Supabase Configuration Status:")
        configStatus.forEach { (key, value) -> 
            println("   $key: $value")
        }
        
        checkAuthenticationStatus()
    }
    
    /**
     * Check current authentication status
     */
    fun checkAuthenticationStatus() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = authService.getCurrentUser()
                _authState.value = if (result.isSuccess) {
                    val user = result.getOrNull()
                    if (user != null) {
                        AuthState.Authenticated(user)
                    } else {
                        AuthState.Unauthenticated
                    }
                } else {
                    AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Failed to check authentication status")
            }
        }
    }
    
    /**
     * Sign in user with email verification check
     */
    fun signIn() {
        if (!validateLoginForm()) return
        
        _isLoading.value = true
        clearError()
        
        viewModelScope.launch {
            try {
                val request = LoginRequest(
                    email = loginEmail.trim(),
                    password = loginPassword
                )
                
                val result = authService.signIn(request)
                
                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    _authState.value = AuthState.Authenticated(response.user)
                    clearLoginForm()
                } else {
                    setError("Invalid email or password")
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                setError(e.message ?: "Sign in failed")
                _authState.value = AuthState.Unauthenticated
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Sign up new user with email verification
     */
    fun signUp() {
        if (!validateSignUpForm()) return
        
        _isLoading.value = true
        clearError()
        
        viewModelScope.launch {
            try {
                val request = SignUpRequest(
                    email = signUpEmail.trim(),
                    password = signUpPassword,
                    firstName = signUpFirstName.trim(),
                    lastName = signUpLastName.trim()
                )
                
                val result = authService.signUp(request)
                
                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    // Check if email is verified
                    if (response.user.isEmailVerified) {
                        _authState.value = AuthState.Authenticated(response.user)
                        clearSignUpForm()
                    } else {
                        // Email verification required
                        _authState.value = AuthState.AwaitingEmailVerification(
                            email = signUpEmail.trim(),
                            message = "We've sent a verification email to ${signUpEmail.trim()}. Please check your inbox and click the verification link to complete your registration."
                        )
                        clearSignUpForm()
                        // Start automatic email verification polling
                        startEmailVerificationPolling()
                    }
                } else {
                    setError("Failed to create account")
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                setError(e.message ?: "Sign up failed")
                _authState.value = AuthState.Unauthenticated
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Sign out current user
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                // Stop any running verification server
                authService.stopVerificationServer()
                authService.signOut()
                _authState.value = AuthState.Unauthenticated
                clearAllForms()
            } catch (e: Exception) {
                setError("Failed to sign out")
            }
        }
    }
    
    /**
     * Resend verification email
     */
    fun resendVerificationEmail(email: String) {
        _isLoading.value = true
        clearError()
        
        viewModelScope.launch {
            try {
                val result = authService.resendVerificationEmail(email)
                
                if (result.isSuccess) {
                    _authState.value = AuthState.AwaitingEmailVerification(
                        email = email,
                        message = "Verification email sent! Please check your inbox and click the verification link."
                    )
                    // Restart automatic email verification polling
                    startEmailVerificationPolling()
                } else {
                    setError("Failed to send verification email")
                }
            } catch (e: Exception) {
                setError(e.message ?: "Failed to send verification email")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Go back to login screen from verification screen
     */
    fun goBackToLogin() {
        // Stop any running verification server
        authService.stopVerificationServer()
        _authState.value = AuthState.Unauthenticated
        _isLoading.value = false
        clearError()
    }
    
    /**
     * Handle completion of signup process - redirect to sign in
     */
    fun onSignupComplete() {
        _authState.value = AuthState.Unauthenticated
        _isLoginMode.value = true
        _isLoading.value = false
        _successMessage.value = "Account created successfully! Please sign in with your new credentials."
        clearError()
        clearAllForms()
    }
    
    /**
     * Start automatic polling for email verification with progressive intervals
     */
    private fun startEmailVerificationPolling() {
        viewModelScope.launch {
            try {
                // Progressive polling: start frequent, then gradually increase intervals
                var attempts = 0
                val maxAttempts = 60 // Total attempts over ~10 minutes
                
                while (attempts < maxAttempts && _authState.value is AuthState.AwaitingEmailVerification) {
                    // Progressive delay: 5s for first 10 attempts, then 10s, then 15s
                    val delayMs: Long = when {
                        attempts < 10 -> 5000L    // First 50 seconds: every 5s
                        attempts < 30 -> 10000L   // Next 3.5 minutes: every 10s  
                        else -> 15000L            // Remaining time: every 15s
                    }
                    delay(delayMs)
                    
                    // Show loading state while checking
                    _isLoading.value = true
                    
                    // Check email verification status using the specialized method
                    val result = authService.checkEmailVerificationStatus()
                    if (result.isSuccess) {
                        val user = result.getOrNull()
                        if (user != null && user.isEmailVerified) {
                            // Show verification success message briefly
                            _authState.value = AuthState.EmailVerified(
                                user = user,
                                message = "Email verified successfully!"
                            )
                            println("🎉 Email verified! Redirecting to sign-in.")
                            
                            // After 2 seconds, transition to signup complete state
                            delay(2000L)
                            _authState.value = AuthState.SignupComplete(
                                email = user.email,
                                message = "Account created successfully! You can now sign in with your credentials."
                            )
                            // Stop the verification server
                            authService.stopVerificationServer()
                            return@launch // Stop polling
                        }
                    }
                    
                    // Clear loading state after check
                    _isLoading.value = false
                    attempts++
                }
                
                // Clear loading state when done
                _isLoading.value = false
                
                // If we've reached max attempts, show a message but continue waiting
                if (attempts >= maxAttempts && _authState.value is AuthState.AwaitingEmailVerification) {
                    val currentState = _authState.value as AuthState.AwaitingEmailVerification
                    _authState.value = AuthState.AwaitingEmailVerification(
                        email = currentState.email,
                        message = "We've been checking for about 10 minutes. Please check your email (including spam folder) and click the verification link. You can also try resending the email if needed."
                    )
                }
            } catch (e: Exception) {
                println("Error during email verification polling: ${e.message}")
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Check authentication status and handle email verification redirects
     */
    fun checkEmailVerification() {
        viewModelScope.launch {
            try {
                println("🔍 Manually checking email verification status...")
                _isLoading.value = true
                
                // Check email verification status using the new method
                val result = authService.checkEmailVerificationStatus()
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    if (user != null && user.isEmailVerified) {
                        // Show verification success message briefly
                        _authState.value = AuthState.EmailVerified(
                            user = user,
                            message = "Email verified successfully!"
                        )
                        println("🎉 Email verified! Redirecting to sign-in.")
                        
                        // After 2 seconds, transition to signup complete state
                        delay(2000L)
                        _authState.value = AuthState.SignupComplete(
                            email = user.email,
                            message = "Account created successfully! You can now sign in with your credentials."
                        )
                        // Stop the verification server
                        authService.stopVerificationServer()
                    } else if (user != null && !user.isEmailVerified) {
                        _authState.value = AuthState.AwaitingEmailVerification(
                            email = user.email,
                            message = "Please check your email and click the verification link to complete your registration."
                        )
                        println("⏳ Email not yet verified for user: ${user.email}")
                    } else {
                        _authState.value = AuthState.Unauthenticated
                        println("ℹ️ No user session found")
                    }
                } else {
                    _authState.value = AuthState.Unauthenticated
                    println("❌ Failed to check verification status")
                }
            } catch (e: Exception) {
                println("❌ Error checking email verification: ${e.message}")
                _authState.value = AuthState.Unauthenticated
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun forceCheckEmailVerification() {
        checkEmailVerification()
    }
    
    fun toggleAuthMode() {
        _isLoginMode.value = !_isLoginMode.value
        _isLoading.value = false
        clearError()
        clearSuccess()
        clearAllForms()
    }
    
    fun updateLoginEmail(email: String) {
        _loginEmail.value = email
        clearError()
        clearSuccess()
    }
    
    fun updateLoginPassword(password: String) {
        _loginPassword.value = password
        clearError()
        clearSuccess()
    }
    
    fun updateSignUpFirstName(firstName: String) {
        _signUpFirstName.value = firstName
        clearError()
        clearSuccess()
    }
    
    fun updateSignUpLastName(lastName: String) {
        _signUpLastName.value = lastName
        clearError()
        clearSuccess()
    }
    
    fun updateSignUpEmail(email: String) {
        _signUpEmail.value = email
        clearError()
        clearSuccess()
    }
    
    fun updateSignUpPassword(password: String) {
        _signUpPassword.value = password
        clearError()
        clearSuccess()
    }
    
    fun updateSignUpConfirmPassword(password: String) {
        _signUpConfirmPassword.value = password
        clearError()
        clearSuccess()
    }
    
    // Validation methods
    private fun validateLoginForm(): Boolean {
        val result = ValidationUtils.validateLoginForm(loginEmail.trim(), loginPassword)
        if (!result.isValid) {
            setError(result.errorMessage)
        }
        return result.isValid
    }
    
    private fun validateSignUpForm(): Boolean {
        val result = ValidationUtils.validateSignUpForm(
            firstName = signUpFirstName.trim(),
            lastName = signUpLastName.trim(),
            email = signUpEmail.trim(),
            password = signUpPassword,
            confirmPassword = signUpConfirmPassword
        )
        if (!result.isValid) {
            setError(result.errorMessage)
        }
        return result.isValid
    }
    
    // Helper methods
    private fun setError(message: String) {
        _errorMessage.value = message
    }
    
    private fun clearError() {
        _errorMessage.value = ""
    }
    
    private fun setSuccess(message: String) {
        _successMessage.value = message
    }
    
    private fun clearSuccess() {
        _successMessage.value = ""
    }
    
    private fun clearLoginForm() {
        _loginEmail.value = ""
        _loginPassword.value = ""
    }
    
    private fun clearSignUpForm() {
        _signUpFirstName.value = ""
        _signUpLastName.value = ""
        _signUpEmail.value = ""
        _signUpPassword.value = ""
        _signUpConfirmPassword.value = ""
    }
    
    private fun clearAllForms() {
        clearLoginForm()
        clearSignUpForm()
    }
    
    /**
     * Clean up resources when ViewModel is destroyed
     */
    override fun onCleared() {
        super.onCleared()
        // Stop any running verification server
        authService.stopVerificationServer()
    }
}
