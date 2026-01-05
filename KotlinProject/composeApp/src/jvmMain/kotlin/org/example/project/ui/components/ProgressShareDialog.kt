package org.example.project.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.example.project.domain.model.LanguageProgress
import org.example.project.ui.theme.WordBridgeColors

/**
 * Dialog for sharing progress via different methods.
 */
@Composable
fun ProgressShareDialog(
    progress: LanguageProgress,
    onDismiss: () -> Unit,
    onExportPNG: () -> Unit,
    onExportHTML: () -> Unit,
    onCopyText: () -> Unit,
    onShareLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.width(400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = WordBridgeColors.CardBackgroundDark
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📤 Share Progress",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Share your ${progress.language.displayName} learning achievements",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WordBridgeColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Share options
                ShareOption(
                    icon = "🖼️",
                    title = "Export as Image",
                    description = "Save progress card as PNG",
                    onClick = {
                        onExportPNG()
                        onDismiss()
                    }
                )

                ShareOption(
                    icon = "📄",
                    title = "Export as Report",
                    description = "Generate HTML report",
                    onClick = {
                        onExportHTML()
                        onDismiss()
                    }
                )

                ShareOption(
                    icon = "📋",
                    title = "Copy as Text",
                    description = "Copy summary to clipboard",
                    onClick = {
                        onCopyText()
                        onDismiss()
                    }
                )

                ShareOption(
                    icon = "🔗",
                    title = "Share Link",
                    description = "Generate shareable link",
                    onClick = {
                        onShareLink()
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ShareOption(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = WordBridgeColors.CardBackgroundDark
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondary
                )
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.titleMedium,
                color = WordBridgeColors.TextSecondary
            )
        }
    }
}

/**
 * Simple notification snackbar for export feedback.
 */
@Composable
fun ExportFeedbackSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier.padding(16.dp),
        action = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        containerColor = WordBridgeColors.PrimaryPurple,
        contentColor = Color.White
    ) {
        Text(message)
    }
}
