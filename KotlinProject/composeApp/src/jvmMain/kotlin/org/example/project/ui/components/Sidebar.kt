package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.NavigationItem
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.ui.theme.WordBridgeIcons

@Composable
fun Sidebar(
    navigationItems: List<NavigationItem>,
    onNavigationItemClick: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 256.dp else 72.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "sidebar_width",
    )

    val sidebarPadding by animateDpAsState(
        targetValue = if (isExpanded) 20.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "sidebar_padding",
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isExpanded) 200 else 100,
            easing = FastOutSlowInEasing,
        ),
        label = "text_alpha",
    )

    val borderColor = WordBridgeColors.SidebarBorder
    val backgroundColor = WordBridgeColors.SidebarBackground

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(sidebarWidth)
                .drawBehind {
                    drawLine(
                        color = borderColor,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .background(backgroundColor)
                .padding(sidebarPadding),
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isExpanded) Arrangement.End else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleExpand() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isExpanded) WordBridgeIcons.ChevronLeft else WordBridgeIcons.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse Sidebar" else "Expand Sidebar",
                    tint = WordBridgeColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Crossfade(
            targetState = isExpanded,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "header_crossfade",
        ) { expanded ->
            if (expanded) {
                SidebarHeader(textAlpha)
            } else {
                SidebarHeaderCompact()
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(100)),
        ) {
            SidebarFooter()
        }
    }
}

@Composable
private fun SidebarHeader(textAlpha: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WordBridgeColors.PrimaryPurple),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "W",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = Color.White,
            )
        }

        Column(
            modifier = Modifier.alpha(textAlpha),
        ) {
            Text(
                text = "WordBridge",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = WordBridgeColors.TextPrimary,
            )

            Text(
                text = "Language Learning",
                style = MaterialTheme.typography.bodySmall,
                color = WordBridgeColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun SidebarHeaderCompact() {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WordBridgeColors.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "W",
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = WordBridgeColors.OnPrimary,
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

    val activeBg = WordBridgeColors.SidebarActiveItem
    val hoverBg = WordBridgeColors.BackgroundLight

    val containerBg by animateColorAsState(
        targetValue =
            when {
                item.isSelected -> activeBg
                isHovered -> hoverBg
                else -> Color.Transparent
            },
        animationSpec = tween(durationMillis = 150),
        label = "navBg",
    )

    val contentColor =
        if (item.isSelected) {
            WordBridgeColors.TextPrimary
        } else if (isHovered) {
            WordBridgeColors.TextPrimary
        } else {
            WordBridgeColors.TextSecondary
        }

    Box(
        modifier =
            Modifier
                .then(
                    if (isExpanded) {
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    } else {
                        Modifier.size(40.dp)
                    },
                )
                .clip(RoundedCornerShape(8.dp))
                .background(containerBg)
                .hoverable(interactionSource)
                .clickable { onClick() },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isExpanded) {
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        } else {
                            Modifier.padding(10.dp)
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = WordBridgeIcons.getByName(item.icon),
                contentDescription = item.title,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = fadeOut(animationSpec = tween(durationMillis = 100)),
            ) {
                Text(
                    text = item.title,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    color = contentColor,
                    modifier = Modifier.alpha(textAlpha),
                )
            }
        }
    }
}

@Composable
private fun SidebarFooter() {
    Text(
        text = "WordBridge v1.0.0",
        style = MaterialTheme.typography.bodySmall,
        color = WordBridgeColors.TextMuted,
    )
}
