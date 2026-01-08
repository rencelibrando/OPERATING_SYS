package org.example.project.ui.components

import androidx.compose.runtime.Composable

/**
 * Common base composable for handling viewModel property delegation patterns.
 * This eliminates duplication across different screens that need to observe multiple viewModel properties.
 */
@Composable
inline fun <reified T : Any> ViewModelProperties(
    viewModel: T,
    content: @Composable (properties: T) -> Unit,
) {
    // The viewModel parameter is already the properties container
    // This composable serves as a pattern wrapper for consistency
    content(viewModel)
}
