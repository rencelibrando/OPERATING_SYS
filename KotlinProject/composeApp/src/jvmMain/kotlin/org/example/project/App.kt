package org.example.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.example.project.ui.screens.HomeScreen
import org.example.project.ui.theme.WordBridgeTheme

@Composable
@Preview
fun App() {
    WordBridgeTheme {
        HomeScreen(
            modifier = Modifier.fillMaxSize()
        )
    }
}