package org.example.project.admin

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.example.project.admin.ui.AdminApp

/**
 * Separate admin application for managing lesson topics.
 * Uses separate app data directory to avoid interfering with main app.
 */
fun main() =
    application {
        val windowState = rememberWindowState(width = 1300.dp, height = 600.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "WordBridge Admin Panel",
            state = windowState,
        ) {
            AdminApp()
        }
    }
