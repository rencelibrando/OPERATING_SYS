package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.NavigationItem
import org.example.project.ui.theme.WordBridgeColors

/**
 * Sidebar navigation component for the WordBridge application
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
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(WordBridgeColors.SidebarBackground)
            .padding(16.dp)
    ) {
        // App title and branding
        SidebarHeader()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Navigation items
        navigationItems.forEach { item ->
            NavigationItemRow(
                item = item,
                onClick = { onNavigationItemClick(item.id) }
            )
            
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Footer or additional content can go here
        SidebarFooter()
    }
}

/**
 * Header section of the sidebar with app branding
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
 * Individual navigation item row
 */
@Composable
private fun NavigationItemRow(
    item: NavigationItem,
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
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon placeholder
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
}

/**
 * Navigation icon component that displays emoji icons
 * In a real application, this would use actual icon resources
 */
@Composable
private fun NavigationIcon(
    icon: String,
    isSelected: Boolean
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
        style = MaterialTheme.typography.titleMedium
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
