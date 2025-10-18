package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.ai.BackendManager
import org.example.project.core.auth.AuthState
import org.example.project.core.initialization.AppInitializer
import org.example.project.presentation.viewmodel.AuthViewModel
import org.example.project.ui.screens.AuthScreen
import org.example.project.ui.screens.EmailVerificationScreen
import org.example.project.ui.screens.EmailVerificationSuccessScreen
import org.example.project.ui.screens.HomeScreen
import org.example.project.ui.screens.SignupCompleteScreen
import org.example.project.ui.screens.SplashScreen
import org.example.project.ui.theme.WordBridgeTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    WordBridgeTheme {
        var showSplash by remember { mutableStateOf(true) }
        
        if (showSplash) {
            // Show splash screen while initializing
            SplashScreen(
                onInitializationComplete = { showSplash = false },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Show main app after initialization
            MainApp()
        }
    }
}

@Composable
private fun MainApp() {
    val authViewModel: AuthViewModel = viewModel()

    when (authViewModel.authState.value) {
        is AuthState.Loading -> {
            LoadingScreen()
        }
        is AuthState.Unauthenticated -> {
            AuthScreen(
                modifier = Modifier.fillMaxSize(),
                authViewModel = authViewModel,
            )
        }
        is AuthState.Authenticated -> {
            var backendError by remember { mutableStateOf<String?>(null) }
            var isInitializingBackend by remember { mutableStateOf(true) }
            
            // Start backend when user is authenticated
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val success = BackendManager.ensureBackendIsRunning()
                    if (!success) {
                        backendError = BackendManager.getLastSetupError()
                    }
                    isInitializingBackend = false
                }
            }
            
            when {
                backendError != null -> {
                    BackendSetupErrorScreen(
                        errorMessage = backendError!!,
                        onRetry = {
                            backendError = null
                            isInitializingBackend = true
                        },
                        onContinueAnyway = {
                            backendError = null
                        }
                    )
                }
                isInitializingBackend -> {
                    BackendInitializingScreen()
                }
                else -> {
                    AuthenticatedApp(
                        user = (authViewModel.authState.value as AuthState.Authenticated).user,
                        onSignOut = authViewModel::signOut,
                    )
                }
            }
        }
        is AuthState.AwaitingEmailVerification -> {
            val verificationState = authViewModel.authState.value as AuthState.AwaitingEmailVerification
            EmailVerificationScreen(
                email = verificationState.email,
                message = verificationState.message,
                onResendEmail = { authViewModel.resendVerificationEmail(verificationState.email) },
                onGoBack = authViewModel::goBackToLogin,
                isLoading = authViewModel.isLoading,
                modifier = Modifier.fillMaxSize(),
            )
        }
        is AuthState.EmailVerified -> {
            val verificationState = authViewModel.authState.value as AuthState.EmailVerified
            EmailVerificationSuccessScreen(
                user = verificationState.user,
                message = verificationState.message,
                modifier = Modifier.fillMaxSize(),
            )
        }
        is AuthState.SignupComplete -> {
            val signupState = authViewModel.authState.value as AuthState.SignupComplete
            SignupCompleteScreen(
                email = signupState.email,
                message = signupState.message,
                onContinueToSignIn = authViewModel::onSignupComplete,
                modifier = Modifier.fillMaxSize(),
            )
        }
        is AuthState.Error -> {
            ErrorScreen(
                message = (authViewModel.authState.value as AuthState.Error).message,
                onRetry = authViewModel::checkAuthenticationStatus,
            )
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
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading WordBridge...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Card(
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.large,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connection Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
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
    onSignOut: () -> Unit,
) {
    HomeScreen(
        authenticatedUser = user,
        onSignOut = onSignOut,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * User header with profile info and sign out option
 */
@Composable
private fun UserHeader(
    user: org.example.project.core.auth.User,
    onSignOut: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = user.initials,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Welcome back,",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign Out",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Screen shown while backend is initializing
 */
@Composable
private fun BackendInitializingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp,
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Setting Up AI Backend",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "This may take a moment on first launch...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "• Checking Python installation\n• Creating virtual environment\n• Installing dependencies",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Screen shown when backend setup fails (e.g., Python not installed)
 */
@Composable
private fun BackendSetupErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit,
    onContinueAnyway: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Card(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.large,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AI Backend Setup Required",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Show helpful instructions based on error type
            if (errorMessage.contains("Python not found", ignoreCase = true)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "Setup Instructions:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Python is being installed automatically.\n" +
                                   "If it fails:\n" +
                                   "1. Download Python 3.9+ from python.org\n" +
                                   "2. Run the installer\n" +
                                   "3. Check 'Add Python to PATH'\n" +
                                   "4. Restart this application",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = onContinueAnyway,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Continue Without AI")
                }
                
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Text("Try Again")
                }
            }
        }
    }
}
