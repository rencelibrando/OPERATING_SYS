package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.ChatSession
import org.example.project.ui.theme.WordBridgeColors
import org.jetbrains.skia.Image as SkiaImage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatHistorySidebar(
    chatSessions: List<ChatSession>,
    currentSessionId: String?,
    onSessionClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteSession: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E293B), // slate-800
                        Color(0xFF0F172A)  // slate-900
                    )
                )
            )
            .padding(16.dp),
    ) {
        // History Label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFC084FC)) // purple-400
            )
            
            Text(
                text = "History",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFFC084FC), // purple-300
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // New Chat Button
        Button(
            onClick = onNewChatClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                hoveredElevation = 12.dp
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFA855F7), // purple-500
                                Color(0xFF3B82F6)  // blue-500
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                    )
                    Text(
                        text = "New Chat",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat Sessions List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatSessions) { session ->
                ChatSessionItem(
                    session = session,
                    isSelected = session.id == currentSessionId,
                    onClick = { onSessionClick(session.id) },
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }
    }
}

@Composable
private fun ChatSessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val formattedDate = remember(session.startTime) {
        dateFormat.format(Date(session.startTime))
    }
    
    // Create tags from session data
    val tags = remember(session.topic, session.difficulty) {
        listOf(session.topic, session.difficulty)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color.Transparent // Will use gradient
            } else {
                Color(0xFF334155).copy(alpha = 0.5f) // slate-700/50
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                Color(0xFFA855F7).copy(alpha = 0.5f) // purple-500/50
            )
        } else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFA855F7).copy(alpha = 0.2f), // purple-500/20
                                    Color(0xFF3B82F6).copy(alpha = 0.2f)  // blue-500/20
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onClick() }
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isSelected) Color(0xFFE9D5FF) else Color.White, // purple-200 : white
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${session.messageCount} msgs",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8), // slate-400
                        )
                        
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                        
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                    }

                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tags.take(2).forEachIndexed { index, tag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (index == 0) {
                                        Color(0xFFA855F7).copy(alpha = 0.2f) // purple-500/20
                                    } else {
                                        Color(0xFFF59E0B).copy(alpha = 0.2f) // amber-500/20
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (index == 0) {
                                                Color(0xFFC084FC) // purple-300
                                            } else {
                                                Color(0xFFFBBF24) // amber-300
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Delete Button
                DeleteIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun DeleteIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Try to load delete icon PNG
    val deleteIconBitmap = remember {
        // Method 1: Try classpath resources (for production builds - JAR or file system)
        val classpathResource = runCatching {
            val classLoader = Thread.currentThread().contextClassLoader
                ?: ClassLoader.getSystemClassLoader()
            
            // Try different resource path formats
            val paths = listOf(
                "drawable/delete_icon.png",
                "composeResources/drawable/delete_icon.png",
                "jvmMain/composeResources/drawable/delete_icon.png"
            )
            
            paths.firstNotNullOfOrNull { path ->
                val resource = classLoader.getResource(path)
                if (resource != null) {
                    println("[ChatHistorySidebar] Found classpath resource: $path -> ${resource.toString()}")
                    // Load image bytes from resource (works for both file:// and jar:file://)
                    runCatching {
                        resource.openStream().use { stream ->
                            stream.readBytes()
                        }
                    }.getOrNull()
                } else {
                    null
                }
            }
        }.getOrNull()
        
        if (classpathResource != null) {
            return@remember runCatching {
                SkiaImage.makeFromEncoded(classpathResource).asImageBitmap()
            }.getOrNull()
        }
        
        // Method 2: Try file system paths (for development)
        val userDir = System.getProperty("user.dir") ?: ""
        val possiblePaths = listOf(
            // Relative to current working directory
            java.io.File(userDir, "composeApp/src/jvmMain/composeResources/drawable/delete_icon.png"),
            // Relative to project root
            java.io.File(userDir, "KotlinProject/composeApp/src/jvmMain/composeResources/drawable/delete_icon.png"),
            // Absolute path from workspace
            java.io.File("KotlinProject/composeApp/src/jvmMain/composeResources/drawable/delete_icon.png"),
            // Try from composeApp directory
            java.io.File("src/jvmMain/composeResources/drawable/delete_icon.png"),
            // Try from jvmMain directory
            java.io.File("jvmMain/composeResources/drawable/delete_icon.png")
        )
        
        val foundFile = possiblePaths.firstOrNull { it.exists() && it.isFile }
        
        if (foundFile != null) {
            println("[ChatHistorySidebar] Found file system resource: ${foundFile.absolutePath}")
            return@remember runCatching {
                val imageBytes = foundFile.readBytes()
                SkiaImage.makeFromEncoded(imageBytes).asImageBitmap()
            }.getOrNull()
        }
        
        null
    }
    
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        if (deleteIconBitmap != null) {
            Image(
                bitmap = deleteIconBitmap,
                contentDescription = "Delete chat",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(Color(0xFF94A3B8)) // slate-400
            )
        } else {
            // Fallback to emoji if PNG not found
            Text(
                text = "🗑️",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
