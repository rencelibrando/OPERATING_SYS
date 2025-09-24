package org.example.project

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.example.project.core.auth.AuthState
import org.example.project.presentation.viewmodel.AuthViewModel
import org.example.project.ui.screens.AuthScreen
import org.example.project.ui.screens.EmailVerificationScreen
import org.example.project.ui.screens.EmailVerificationSuccessScreen
import org.example.project.ui.screens.SignupCompleteScreen
import org.example.project.ui.screens.HomeScreen
import org.example.project.ui.theme.WordBridgeTheme

@Composable
@Preview
fun App() {
    WordBridgeTheme {
        val authViewModel: AuthViewModel = viewModel()
        
        // Database connection testing will be added once Supabase integration is stable
        
        when (authViewModel.authState.value) {
            is AuthState.Loading -> {
                LoadingScreen()
            }
            is AuthState.Unauthenticated -> {
                AuthScreen(
                    modifier = Modifier.fillMaxSize(),
                    authViewModel = authViewModel
                )
            }
            is AuthState.Authenticated -> {
                AuthenticatedApp(
                    user = (authViewModel.authState.value as AuthState.Authenticated).user,
                    onSignOut = authViewModel::signOut
                )
            }
            is AuthState.AwaitingEmailVerification -> {
                val verificationState = authViewModel.authState.value as AuthState.AwaitingEmailVerification
                EmailVerificationScreen(
                    email = verificationState.email,
                    message = verificationState.message,
                    onResendEmail = { authViewModel.resendVerificationEmail(verificationState.email) },
                    onGoBack = authViewModel::goBackToLogin,
                    isLoading = authViewModel.isLoading,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is AuthState.EmailVerified -> {
                val verificationState = authViewModel.authState.value as AuthState.EmailVerified
                EmailVerificationSuccessScreen(
                    user = verificationState.user,
                    message = verificationState.message,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is AuthState.SignupComplete -> {
                val signupState = authViewModel.authState.value as AuthState.SignupComplete
                SignupCompleteScreen(
                    email = signupState.email,
                    message = signupState.message,
                    onContinueToSignIn = authViewModel::onSignupComplete,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is AuthState.Error -> {
                ErrorScreen(
                    message = (authViewModel.authState.value as AuthState.Error).message,
                    onRetry = authViewModel::checkAuthenticationStatus
                )
            }
        }
    }
}

/**
 * Loading screen displayed while checking authentication state
 */
@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading WordBridge...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error screen displayed when authentication check fails
 */
@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Card(
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Connection Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Try Again")
            }
        }
    }
}

/**
 * Main authenticated app content
 */
@Composable
private fun AuthenticatedApp(
    user: org.example.project.core.auth.User,
    onSignOut: () -> Unit
) {
    // Show the HomeScreen directly - it will handle displaying the authenticated user info
    HomeScreen(
        authenticatedUser = user,
        onSignOut = onSignOut,
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * User header with profile info and sign out option
 */
@Composable
private fun UserHeader(
    user: org.example.project.core.auth.User,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User avatar
                Card(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.initials,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "Welcome back,",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Sign out button
            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign Out",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}