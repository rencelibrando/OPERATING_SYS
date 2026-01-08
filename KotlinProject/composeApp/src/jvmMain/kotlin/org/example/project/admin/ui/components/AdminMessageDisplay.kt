package org.example.project.admin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AdminMessageDisplay(
    errorMessage: String?,
    successMessage: String?,
    modifier: Modifier = Modifier,
) {
    // Error message
    if (errorMessage != null) {
        Surface(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            color = Color(0xFFEF4444).copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFEF4444),
                )
            }
        }
    }

    // Success message
    if (successMessage != null) {
        Surface(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            color = Color(0xFF10B981).copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = successMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF10B981),
                )
            }
        }
    }
}
