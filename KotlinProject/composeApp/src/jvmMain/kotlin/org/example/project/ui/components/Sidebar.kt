package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.NavigationItem
import org.example.project.ui.theme.WordBridgeColors
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun Sidebar(
    navigationItems: List<NavigationItem>,
    onNavigationItemClick: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Smooth animated width with custom spring parameters
    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 256.dp else 72.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sidebar_width",
    )

    val sidebarPadding by animateDpAsState(
        targetValue = if (isExpanded) 24.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sidebar_padding",
    )
    
    // Fade animation for text elements
    val textAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isExpanded) 300 else 150,
            easing = FastOutSlowInEasing
        ),
        label = "text_alpha"
    )

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(sidebarWidth)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B), // slate-800
                            Color(0xFF0F172A)  // slate-900
                        )
                    )
                )
                .padding(sidebarPadding),
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        // Manual expand / collapse toggle
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = if (isExpanded) Arrangement.End else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isExpanded) "«" else "»",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = Color(0xFF9CA3AF),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onToggleExpand() }
                    .padding(4.dp),
            )
        }

        // Header with logo - crossfade animation
        Crossfade(
            targetState = isExpanded,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "header_crossfade"
        ) { expanded ->
            if (expanded) {
                SidebarHeader(textAlpha)
            } else {
                SidebarHeaderCompact()
            }
        }
        
        Spacer(modifier = Modifier.height(if (isExpanded) 32.dp else 24.dp))

        // Navigation items
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            navigationItems.forEach { item ->
                NavigationItemRow(
                    item = item,
                    isExpanded = isExpanded,
                    textAlpha = textAlpha,
                    onClick = { onNavigationItemClick(item.id) },
                )
            }
        }

        // Version footer - fade in/out
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 100)) + 
                    expandVertically(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(150)) + 
                   shrinkVertically(animationSpec = tween(150))
        ) {
            SidebarFooter()
        }
    }
}

@Composable
private fun SidebarHeader(textAlpha: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gradient circle with W
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF60A5FA), // blue-400
                                Color(0xFFA78BFA)  // purple-400
                            )
                        )
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "W",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = Color.White,
            )
        }
        
        Column(
            modifier = Modifier.alpha(textAlpha)
        ) {
            Text(
                text = "WordBridge",
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = Color.White,
            )
            
            Text(
                text = "AI Language Learning",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF), // slate-400
            )
        }
    }
}

@Composable
private fun SidebarHeaderCompact() {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF60A5FA), // blue-400
                            Color(0xFFA78BFA)  // purple-400
                        )
                    )
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "W",
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = Color.White,
        )
    }
}

@Composable
private fun NavigationItemRow(
    item: NavigationItem,
    isExpanded: Boolean,
    textAlpha: Float,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            item.isSelected -> Color.Transparent // Will use gradient
            isHovered -> Color(0xFF334155).copy(alpha = 0.5f) // slate-700/50
            else -> Color.Transparent
        },
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "navBg"
    )

    // Always render the same container to prevent layout jumps
    Box(
        modifier = Modifier
            .then(
                if (isExpanded) {
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                } else {
                    Modifier.size(40.dp)
                }
            )
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Active gradient background
        if (item.isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF3B82F6), // blue-500
                                Color(0xFFA855F7)  // purple-500
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(backgroundColor, RoundedCornerShape(12.dp))
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isExpanded) {
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    } else {
                        Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                    }
                )
                .hoverable(interactionSource)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Fixed-size icon container - always stays in the same position
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                NavigationIcon(
                    icon = item.icon,
                    isSelected = item.isSelected,
                )
            }

            // Animate text visibility without affecting icon position
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 150)) + 
                       expandHorizontally(
                           expandFrom = Alignment.Start,
                           animationSpec = tween(durationMillis = 150)
                       ),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)) + 
                      shrinkHorizontally(
                          shrinkTowards = Alignment.Start,
                          animationSpec = tween(durationMillis = 150)
                      )
            ) {
                Text(
                    text = item.title,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (item.isSelected) FontWeight.Medium else FontWeight.Normal,
                        ),
                    color = Color.White,
                    modifier = Modifier.alpha(textAlpha)
                )
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    icon: String,
    isSelected: Boolean,
    isCompact: Boolean = false,
) {
    // Map icon names to PNG file names
    val iconFileName = remember(icon) {
        when (icon) {
            "home" -> "ic_home.png"
            "lessons" -> "ic_lessons.png"
            "vocabulary" -> "ic_vocabulary.png"
            "speaking" -> "ic_speaking.png"
            "ai_chat" -> "ic_ai_chat.png"
            "progress" -> "ic_progress.png"
            "settings" -> "ic_settings.png"
            else -> "ic_home.png"
        }
    }

    // Determine icon color based on selection state
    val iconColor = if (isSelected) {
        Color.White
    } else {
        Color(0xFF9CA3AF) // slate-400 for unselected state
    }

    // Emoji fallback for icons
    val emoji = remember(icon) {
        when (icon) {
            "home" -> "🏠"
            "lessons" -> "📚"
            "vocabulary" -> "💬"
            "speaking" -> "💬"
            "ai_chat" -> "💬"
            "progress" -> "📊"
            "settings" -> "⚙️"
            else -> "📱"
        }
    }

    // Try to load PNG icon using multiple methods
    val imageBitmap = remember(iconFileName) {
        // Method 1: Try classpath resources (for production builds - JAR or file system)
        val classpathResource = runCatching {
            val classLoader = Thread.currentThread().contextClassLoader
                ?: ClassLoader.getSystemClassLoader()
            
            // Try different resource path formats
            val paths = listOf(
                "drawable/$iconFileName",
                "composeResources/drawable/$iconFileName",
                "jvmMain/composeResources/drawable/$iconFileName"
            )
            
            paths.firstNotNullOfOrNull { path ->
                val resource = classLoader.getResource(path)
                if (resource != null) {
                    println("[Sidebar] Found classpath resource: $path -> ${resource.toString()}")
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
            java.io.File(userDir, "composeApp/src/jvmMain/composeResources/drawable/$iconFileName"),
            // Relative to project root
            java.io.File(userDir, "KotlinProject/composeApp/src/jvmMain/composeResources/drawable/$iconFileName"),
            // Absolute path from workspace
            java.io.File("KotlinProject/composeApp/src/jvmMain/composeResources/drawable/$iconFileName"),
            // Try from composeApp directory
            java.io.File("src/jvmMain/composeResources/drawable/$iconFileName"),
            // Try from jvmMain directory
            java.io.File("jvmMain/composeResources/drawable/$iconFileName")
        )
        
        val foundFile = possiblePaths.firstOrNull { it.exists() && it.isFile }
        
        if (foundFile != null) {
            println("[Sidebar] Found file system resource: ${foundFile.absolutePath}")
            return@remember runCatching {
                val imageBytes = foundFile.readBytes()
                SkiaImage.makeFromEncoded(imageBytes).asImageBitmap()
            }.getOrNull()
        }
        
        if (foundFile == null) {
            println("[Sidebar] Icon not found: $iconFileName (user.dir=$userDir)")
            possiblePaths.forEach { path ->
                println("[Sidebar]   Tried: ${path.absolutePath} (exists=${path.exists()})")
            }
        }
        
        null
    }

    // Display icon or emoji fallback
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = icon,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(iconColor)
        )
    } else {
        // Show emoji if PNG not found or failed to load
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleMedium,
            color = iconColor
        )
    }
}

@Composable
private fun SidebarFooter() {
    Text(
        text = "Version 1.0.0",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64748B), // slate-500
    )
}
