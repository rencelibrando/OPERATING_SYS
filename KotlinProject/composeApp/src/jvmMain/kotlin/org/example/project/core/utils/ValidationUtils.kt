package org.example.project.core.utils

object ValidationUtils {
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && email.contains("@") && email.contains(".")
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    fun isValidName(name: String): Boolean {
        return name.isNotBlank()
    }

    fun passwordsMatch(
        password: String,
        confirmPassword: String,
    ): Boolean {
        return password == confirmPassword
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String = "",
    )

    fun validateLoginForm(
        email: String,
        password: String,
    ): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult(false, "Email is required")
            !isValidEmail(email) -> ValidationResult(false, "Please enter a valid email address")
            password.isBlank() -> ValidationResult(false, "Password is required")
            !isValidPassword(password) -> ValidationResult(false, "Password must be at least 6 characters")
            else -> ValidationResult(true)
        }
    }

    fun validateSignUpForm(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): ValidationResult {
        return when {
            !isValidName(firstName) -> ValidationResult(false, "First name is required")
            !isValidName(lastName) -> ValidationResult(false, "Last name is required")
            !isValidEmail(email) -> ValidationResult(false, "Please enter a valid email address")
            !isValidPassword(password) -> ValidationResult(false, "Password must be at least 6 characters")
            !passwordsMatch(password, confirmPassword) -> ValidationResult(false, "Passwords do not match")
            else -> ValidationResult(true)
        }
    }
}
