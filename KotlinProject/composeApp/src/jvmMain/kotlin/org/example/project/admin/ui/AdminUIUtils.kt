package org.example.project.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/**
 * Automatically clears error and success messages after 3 seconds.
 * Thread-safe and non-blocking - uses coroutines for async delay.
 */
@Composable
internal fun AutoClearMessages(
    errorMessage: String?,
    successMessage: String?,
    onClear: () -> Unit,
) {
    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null || successMessage != null) {
            delay(3000)
            onClear()
        }
    }
}
