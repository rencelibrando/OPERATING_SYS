package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.core.auth.*
import org.example.project.core.utils.ErrorLogger
import org.example.project.core.utils.ValidationUtils

private const val LOG_TAG = "AuthViewModel.kt"

class AuthViewModel : ViewModel() {
    private val authService = RealSupabaseAuthService()

    private val _authState = mutableStateOf<AuthState>(AuthState.Loading)
    val authState: State<AuthState> = _authState

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

    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _errorMessage = mutableStateOf("")
    val errorMessage: String get() = _errorMessage.value

    private val _successMessage = mutableStateOf("")
    val successMessage: String get() = _successMessage.value

    private val _isLoginMode = mutableStateOf(true)
    val isLoginMode: Boolean get() = _isLoginMode.value

    init {
        checkAuthenticationStatus()
    }

    fun checkAuthenticationStatus() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = authService.getCurrentUser()
                _authState.value =
                    if (result.isSuccess) {
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
                ErrorLogger.logException(LOG_TAG, e, "Failed to check authentication status")
                _authState.value = AuthState.Error("Failed to check authentication status")
            }
        }
    }

    fun signIn() {
        if (!validateLoginForm()) return

        _isLoading.value = true
        clearError()

        viewModelScope.launch {
            try {
                val request =
                    LoginRequest(
                        email = loginEmail.trim(),
                        password = loginPassword,
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

    fun signUp() {
        if (!validateSignUpForm()) return

        _isLoading.value = true
        clearError()

        viewModelScope.launch {
            try {
                val request =
                    SignUpRequest(
                        email = signUpEmail.trim(),
                        password = signUpPassword,
                        firstName = signUpFirstName.trim(),
                        lastName = signUpLastName.trim(),
                    )

                val result = authService.signUp(request)

                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    if (response.user.isEmailVerified) {
                        _authState.value = AuthState.Authenticated(response.user)
                        clearSignUpForm()
                    } else {
                        _authState.value = AuthState.Unauthenticated
                        _isLoginMode.value = true // Switch to login mode
                        setSuccess(
                            "Account created successfully! We've sent a verification email to ${signUpEmail.trim()}. Please check your inbox and click the verification link, then sign in with your credentials.",
                        )

                        _loginEmail.value = signUpEmail.trim()
                        clearSignUpForm()
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

    fun signOut() {
        viewModelScope.launch {
            try {
                authService.signOut()
                _authState.value = AuthState.Unauthenticated
                clearAllForms()
            } catch (e: Exception) {
                setError("Failed to sign out")
            }
        }
    }

    fun resendVerificationEmail(email: String) {
        _isLoading.value = true
        clearError()

        viewModelScope.launch {
            try {
                val result = authService.resendVerificationEmail(email)

                if (result.isSuccess) {
                    _authState.value = AuthState.Unauthenticated
                    _isLoginMode.value = true // Switch to login mode
                    setSuccess(
                        "Verification email sent! Please check your inbox and click the verification link, then sign in with your credentials.",
                    )

                    _loginEmail.value = email
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

    fun goBackToLogin() {
        _authState.value = AuthState.Unauthenticated
        _isLoading.value = false
        clearError()
    }

    fun onSignupComplete() {
        _authState.value = AuthState.Unauthenticated
        _isLoginMode.value = true
        _isLoading.value = false
        _successMessage.value = "Account created successfully! Please sign in with your new credentials."
        clearError()
        clearAllForms()
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
        val result =
            ValidationUtils.validateSignUpForm(
                firstName = signUpFirstName.trim(),
                lastName = signUpLastName.trim(),
                email = signUpEmail.trim(),
                password = signUpPassword,
                confirmPassword = signUpConfirmPassword,
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

}
