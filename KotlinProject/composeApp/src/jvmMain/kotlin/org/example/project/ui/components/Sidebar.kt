package org.example.project.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.NavigationItem
import org.example.project.ui.theme.WordBridgeColors

/**
 * Responsive sidebar navigation component for the WordBridge application
 * Shows only icons when not hovered, expands to show full content on hover
 * 
 * @param navigationItems List of navigation items to display
 * @param onNavigationItemClick Callback when a navigation item is clicked
 * @param modifier Optional modifier for styling
 */
@Composable
fun Sidebar(
    navigationItems: List<NavigationItem>,
    onNavigationItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Hover state management
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // Animated width based on hover state
    val sidebarWidth by animateDpAsState(
        targetValue = if (isHovered) 240.dp else 72.dp,
        animationSpec = tween(durationMillis = 200),
        label = "sidebar_width"
    )
    
    // Animated padding based on hover state
    val sidebarPadding by animateDpAsState(
        targetValue = if (isHovered) 16.dp else 12.dp,
        animationSpec = tween(durationMillis = 200),
        label = "sidebar_padding"
    )
    
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .background(WordBridgeColors.SidebarBackground)
            .hoverable(interactionSource)
            .padding(sidebarPadding),
        horizontalAlignment = if (isHovered) Alignment.Start else Alignment.CenterHorizontally
    ) {
        // App title and branding (only show when expanded)
        if (isHovered) {
            SidebarHeader()
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            // Show compact logo when collapsed
            SidebarHeaderCompact()
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Navigation items
        navigationItems.forEach { item ->
            NavigationItemRow(
                item = item,
                isExpanded = isHovered,
                onClick = { onNavigationItemClick(item.id) }
            )
            
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Footer (only show when expanded)
        if (isHovered) {
            SidebarFooter()
        }
    }
}

/**
 * Header section of the sidebar with app branding (expanded state)
 */
@Composable
private fun SidebarHeader() {
    Column {
        Text(
            text = "WordBridge",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = WordBridgeColors.SidebarText
        )
        
        Text(
            text = "AI Language Learning",
            style = MaterialTheme.typography.bodySmall,
            color = WordBridgeColors.SidebarTextSecondary
        )
    }
}

/**
 * Compact header section of the sidebar (collapsed state)
 */
@Composable
private fun SidebarHeaderCompact() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "W",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = WordBridgeColors.PrimaryPurple
        )
    }
}

/**
 * Individual navigation item row with responsive layout
 */
@Composable
private fun NavigationItemRow(
    item: NavigationItem,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (item.isSelected) {
        WordBridgeColors.SidebarActiveItem.copy(alpha = 0.15f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    
    val textColor = if (item.isSelected) {
        WordBridgeColors.SidebarText
    } else {
        WordBridgeColors.SidebarTextSecondary
    }
    
    if (isExpanded) {
        // Expanded layout with icon and text
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationIcon(
                icon = item.icon,
                isSelected = item.isSelected
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = textColor
            )
        }
    } else {
        // Collapsed layout with only icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            NavigationIcon(
                icon = item.icon,
                isSelected = item.isSelected,
                isCompact = true
            )
        }
    }
}

/**
 * Navigation icon component that displays emoji icons
 * In a real application, this would use actual icon resources
 */
@Composable
private fun NavigationIcon(
    icon: String,
    isSelected: Boolean,
    isCompact: Boolean = false
) {
    val emoji = when (icon) {
        "home" -> "🏠"
        "lessons" -> "📚"
        "vocabulary" -> "📝"
        "speaking" -> "🎤"
        "ai_chat" -> "💬"
        "progress" -> "📊"
        "settings" -> "⚙️"
        else -> "📱"
    }
    
    Text(
        text = emoji,
        style = if (isCompact) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.titleMedium
        }
    )
}

/**
 * Footer section of the sidebar
 */
@Composable
private fun SidebarFooter() {
    Column {
        HorizontalDivider(
            color = WordBridgeColors.SidebarTextSecondary.copy(alpha = 0.3f),
            thickness = 1.dp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = WordBridgeColors.SidebarTextSecondary.copy(alpha = 0.7f)
        )
    }
}
