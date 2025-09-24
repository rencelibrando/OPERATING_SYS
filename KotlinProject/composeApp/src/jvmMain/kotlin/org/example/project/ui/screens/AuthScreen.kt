package org.example.project.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.AuthViewModel
import org.example.project.ui.components.AnimatedNetworkBackground
import org.example.project.core.auth.AuthState

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel()
) {
    // Observe auth state to decide which screen to show
    val currentAuthState = authViewModel.authState.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D1117), // Dark GitHub-like background
                        Color(0xFF161B22), // Slightly lighter dark
                        Color(0xFF0D1117)  // Back to darker at edges
                    ),
                    radius = 1000f
                )
            )
    ) {
        // Animated network background
        AnimatedNetworkBackground(
            modifier = Modifier.fillMaxSize(),
            nodeCount = 35,
            connectionDistance = 130f,
            speed = 0.2f
        )
        when (currentAuthState) {
            is AuthState.AwaitingEmailVerification -> {
                // Show the email verification screen and stop rendering the auth form
                EmailVerificationScreen(
                    email = currentAuthState.email,
                    message = currentAuthState.message,
                    onResendEmail = { authViewModel.resendVerificationEmail(currentAuthState.email) },
                    onGoBack = { authViewModel.goBackToLogin() },
                    isLoading = authViewModel.isLoading,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is AuthState.EmailVerified -> {
                // Show success screen while ViewModel transitions to SignupComplete
                EmailVerificationSuccessScreen(
                    user = currentAuthState.user,
                    message = currentAuthState.message,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // App branding
                    AppBranding()
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Authentication form
                    AuthenticationForm(
                        authViewModel = authViewModel,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Compact app branding section with enhanced visibility
 */
@Composable
private fun AppBranding() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF21262D).copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Compact app icon
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "WB",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = "WordBridge",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                
                Text(
                    text = "Language learning platform",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Main authentication form
 */
@Composable
private fun AuthenticationForm(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF21262D).copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize()
        ) {
            // Form header
            FormHeader(
                isLoginMode = authViewModel.isLoginMode,
                onToggleMode = authViewModel::toggleAuthMode
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Success message
            if (authViewModel.successMessage.isNotEmpty()) {
                SuccessMessage(
                    message = authViewModel.successMessage,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            // Error message
            if (authViewModel.errorMessage.isNotEmpty()) {
                ErrorMessage(
                    message = authViewModel.errorMessage,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            // Form fields
            if (authViewModel.isLoginMode) {
                LoginForm(authViewModel = authViewModel)
            } else {
                SignUpForm(authViewModel = authViewModel)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Submit button
            SubmitButton(
                isLoginMode = authViewModel.isLoginMode,
                isLoading = authViewModel.isLoading,
                onSubmit = if (authViewModel.isLoginMode) authViewModel::signIn else authViewModel::signUp
            )
        }
    }
}

/**
 * Compact form header with title and mode toggle
 */
@Composable
private fun FormHeader(
    isLoginMode: Boolean,
    onToggleMode: () -> Unit
) {
    Column {
        Text(
            text = if (isLoginMode) "Welcome Back" else "Create Account",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Toggle between login and signup
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isLoginMode) 
                    "Don't have an account?" 
                else 
                    "Already have an account?",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            TextButton(
                onClick = onToggleMode,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isLoginMode) "Sign Up" else "Sign In",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Compact success message display
 */
@Composable
private fun SuccessMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF22C55E).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF22C55E),
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * Compact error message display
 */
@Composable
private fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * Compact login form fields
 */
@Composable
private fun LoginForm(authViewModel: AuthViewModel) {
    Column {
        OutlinedTextField(
            value = authViewModel.loginEmail,
            onValueChange = authViewModel::updateLoginEmail,
            label = { Text("Email", color = Color.White.copy(alpha = 0.7f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            )
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = authViewModel.loginPassword,
            onValueChange = authViewModel::updateLoginPassword,
            label = { Text("Password", color = Color.White.copy(alpha = 0.7f)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            )
        )
    }
}

/**
 * Compact sign up form fields
 */
@Composable
private fun SignUpForm(authViewModel: AuthViewModel) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = authViewModel.signUpFirstName,
                onValueChange = authViewModel::updateSignUpFirstName,
                label = { Text("First Name", color = Color.White.copy(alpha = 0.7f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = Color.White
                )
            )
            
            OutlinedTextField(
                value = authViewModel.signUpLastName,
                onValueChange = authViewModel::updateSignUpLastName,
                label = { Text("Last Name", color = Color.White.copy(alpha = 0.7f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = Color.White
                )
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = authViewModel.signUpEmail,
            onValueChange = authViewModel::updateSignUpEmail,
            label = { Text("Email", color = Color.White.copy(alpha = 0.7f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            )
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = authViewModel.signUpPassword,
            onValueChange = authViewModel::updateSignUpPassword,
            label = { Text("Password", color = Color.White.copy(alpha = 0.7f)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            )
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = authViewModel.signUpConfirmPassword,
            onValueChange = authViewModel::updateSignUpConfirmPassword,
            label = { Text("Confirm Password", color = Color.White.copy(alpha = 0.7f)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            )
        )
    }
}

/**
 * Compact submit button with loading state
 */
@Composable
private fun SubmitButton(
    isLoginMode: Boolean,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    Button(
        onClick = onSubmit,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = if (isLoginMode) "Sign In" else "Create Account",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
