package org.example.project.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class AdminNavItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val badge: String? = null,
)

@Composable
fun AdminSidebar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    topicsCount: Int = 0,
    usersCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val navItems =
        listOf(
            AdminNavItem(
                id = "topics",
                title = "Topics",
                icon = Icons.Default.Book,
                badge = if (topicsCount > 0) topicsCount.toString() else null,
            ),
            AdminNavItem(
                id = "users",
                title = "Users Management",
                icon = Icons.Default.People,
                badge = if (usersCount > 0) usersCount.toString() else null,
            ),
        )

    Surface(
        modifier =
            modifier
                .fillMaxHeight()
                .width(260.dp),
        color = Color(0xFF1E1B2E), // Dark sidebar background
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
        ) {
            // Logo/Title Section
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "WordBridge Admin",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Items
            navItems.forEach { navItem ->
                AdminSidebarItem(
                    item = navItem,
                    isSelected = selectedTab == navItem.id,
                    onClick = { onTabSelected(navItem.id) },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun AdminSidebarItem(
    item: AdminNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (isSelected) {
            Color(0xFF8B5CF6) // Purple accent for selected
        } else {
            Color.Transparent
        }

    val contentColor =
        if (isSelected) {
            Color.White
        } else {
            Color(0xFFB4B4C4) // Light gray for unselected
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor,
                )
            }

            // Badge
            if (item.badge != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (isSelected) {
                            Color.White.copy(alpha = 0.2f)
                        } else {
                            Color(0xFF2D2A3E)
                        },
                ) {
                    Text(
                        text = item.badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
