package org.example.project.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.LessonTopic
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun LessonTimelineView(
    lessonTopics: List<LessonTopic>,
    onLessonClick: (String) -> Unit,
    scrollProgress: Float = 0f, 
    visibleItemIndex: Int = 0, 
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        lessonTopics.forEachIndexed { index, topic ->
            LessonTimelineItem(
                topic = topic,
                isLeft = index % 2 == 0,
                isFirst = index == 0,
                isLast = index == lessonTopics.lastIndex,
                lessonNumber = index + 1,
                scrollProgress = scrollProgress,
                itemIndex = index,
                totalItems = lessonTopics.size,
                isVisible = index == visibleItemIndex || index == visibleItemIndex - 1 || index == visibleItemIndex + 1,
                onClick = { onLessonClick(topic.id) }
            )
        }
    }
}

@Composable
private fun LessonTimelineItem(
    topic: LessonTopic,
    isLeft: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    lessonNumber: Int,
    scrollProgress: Float,
    itemIndex: Int,
    totalItems: Int,
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    
    val freshGreen = Color(0xFF22C55E) 
    
    val timelineGray = Color(0xFFE2E8F0) 
    val inactiveGray = Color(0xFFCBD5E1) 

    val itemProgress = itemIndex.toFloat() / totalItems.toFloat()
    val nextItemProgress = (itemIndex + 1).toFloat() / totalItems.toFloat()
    
    val targetNodeColor = remember(scrollProgress, itemProgress, topic.isLocked) {
        when {
            topic.isLocked -> inactiveGray
            scrollProgress >= itemProgress -> freshGreen 
            scrollProgress >= itemProgress - 0.15f -> {
                
                val localProgress = ((scrollProgress - (itemProgress - 0.15f)) / 0.15f).coerceIn(0f, 1f)
                Color(
                    red = timelineGray.red + (freshGreen.red - timelineGray.red) * localProgress,
                    green = timelineGray.green + (freshGreen.green - timelineGray.green) * localProgress,
                    blue = timelineGray.blue + (freshGreen.blue - timelineGray.blue) * localProgress
                )
            }
            else -> timelineGray 
        }
    }
    
    
    val nodeColorState by animateColorAsState(
        targetValue = targetNodeColor,
        animationSpec = tween(
            durationMillis = 50,  
            easing = LinearEasing  
        ),
        label = "nodeColor"
    )
    
    
    
    val targetLineProgress = remember(scrollProgress, itemProgress, nextItemProgress) {
        
        val responsiveItemProgress = itemProgress - 0.02f  
        val responsiveNextProgress = nextItemProgress
        
        when {
            scrollProgress <= responsiveItemProgress -> 0f 
            scrollProgress >= responsiveNextProgress -> 1f 
            else -> {
                
                
                ((scrollProgress - responsiveItemProgress) / (responsiveNextProgress - responsiveItemProgress)).coerceIn(0f, 1f)
            }
        }
    }
    
    
    val lineColorProgress by animateFloatAsState(
        targetValue = targetLineProgress,
        animationSpec = tween(
            durationMillis = 50,  
            easing = LinearEasing  
        ),
        label = "lineProgress"
    )
    
    
    var isInViewport by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    
    
    val nodeScale by animateFloatAsState(
        targetValue = if (isInViewport || isVisible) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nodeScale"
    )
    
    
    val cardScale by animateFloatAsState(
        targetValue = if (isHovered && !topic.isLocked) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )
    
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isHovered && !topic.isLocked) 0.3f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "glowAlpha"
    )
    
    
    val nodeGlowScale by animateFloatAsState(
        targetValue = if (isHovered && !topic.isLocked) 1.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nodeGlowScale"
    )
    
    Box(
        modifier = modifier
            .height(280.dp)  
            .onGloballyPositioned { coordinates ->
                
                val position = coordinates.positionInRoot()
                val size = coordinates.size
                
                
                val nodeY = position.y + with(density) { 20.dp.toPx() }
                isInViewport = nodeY > 200 && nodeY < 800 
            }
    ) {
        
        
        if (!isLast) {
            
            
            
            
            val nodeTopOffset = 20.dp  
            val nodeRadius = 12.dp  
            val lineStartOffset = nodeTopOffset + nodeRadius  
            val nextNodeTopOffset = 280.dp + nodeTopOffset - nodeRadius  
            val segmentHeight = nextNodeTopOffset - lineStartOffset  
            
            
            val coloredHeight = with(density) { 
                (segmentHeight.toPx() * lineColorProgress).dp 
            }
            val remainingHeight = segmentHeight - coloredHeight
            
            Box(
                modifier = Modifier
                    .width(4.dp)  
                    .height(segmentHeight)
                    .align(Alignment.TopCenter)  
                    .offset(y = lineStartOffset)  
            ) {
                
                if (lineColorProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(coloredHeight)
                            .background(freshGreen)
                    )
                }
                
                if (lineColorProgress < 1f) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(remainingHeight)
                            .offset(y = coloredHeight)
                            .background(timelineGray)
                    )
                }
            }
        } else {
            
            
        }
        
        
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)  
                .padding(top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            
            if (glowAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .scale(nodeGlowScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    freshGreen.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }
            
            
            Surface(
                modifier = Modifier
                    .scale(nodeScale)
                    .clickable(enabled = !topic.isLocked) { onClick() },
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = if (isHovered && !topic.isLocked) 8.dp else if (isInViewport || isVisible) 6.dp else 3.dp,
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(nodeColorState)
                        .border(
                            width = if (isHovered && !topic.isLocked) 2.dp else 1.5.dp,
                            color = Color.White.copy(alpha = if (isHovered && !topic.isLocked) 1f else 0.9f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }
        
        
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
                if (isLeft) {
                    
                    LessonTimelineCard(
                        topic = topic,
                        onClick = onClick,
                        isHovered = isHovered,
                        interactionSource = interactionSource,
                        scale = cardScale,
                        modifier = Modifier
                            .width(480.dp)  
                            .padding(end = 120.dp)  
                    )
                    
                    
                    Spacer(modifier = Modifier.width(80.dp))  
                    
                    
                    LessonNumberLabel(
                        lessonNumber = lessonNumber,
                        isHovered = isHovered,
                        isLocked = topic.isLocked,
                        freshGreen = freshGreen,
                        modifier = Modifier.padding(start = 120.dp)  
                    )
                } else {
                    
                    LessonNumberLabel(
                        lessonNumber = lessonNumber,
                        isHovered = isHovered,
                        isLocked = topic.isLocked,
                        freshGreen = freshGreen,
                        modifier = Modifier.padding(end = 120.dp)  
                    )
                    
                    
                    Spacer(modifier = Modifier.width(80.dp))  
                    
                    
                    LessonTimelineCard(
                        topic = topic,
                        onClick = onClick,
                        isHovered = isHovered,
                        interactionSource = interactionSource,
                        scale = cardScale,
                        modifier = Modifier
                            .width(480.dp)  
                            .padding(start = 120.dp)  
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonNumberLabel(
    lessonNumber: Int,
    isHovered: Boolean,
    isLocked: Boolean,
    freshGreen: Color,
    modifier: Modifier = Modifier,
) {
    val labelScale by animateFloatAsState(
        targetValue = if (isHovered && !isLocked) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "labelScale"
    )
    
    Surface(
        modifier = modifier
            .scale(labelScale)
            .padding(top = 20.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isHovered && !isLocked) {
            freshGreen.copy(alpha = 0.15f)
        } else {
            Color(0xFFF8FAFC) 
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isHovered && !isLocked) {
                freshGreen.copy(alpha = 0.3f)
            } else {
                Color(0xFFE2E8F0) 
            }
        )
    ) {
        Text(
            text = "Lesson $lessonNumber",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = if (isHovered && !isLocked) freshGreen else WordBridgeColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LessonTimelineCard(
    topic: LessonTopic,
    onClick: () -> Unit,
    isHovered: Boolean,
    interactionSource: MutableInteractionSource,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    
    val freshGreen = Color(0xFF22C55E) 
    val neutralBorder = Color(0xFFE2E8F0) 
    
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered && !topic.isLocked) 16.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevation"
    )
    
    
    val borderWidth by animateDpAsState(
        targetValue = if (isHovered && !topic.isLocked) 2.dp else 1.dp,
        animationSpec = tween(300),
        label = "borderWidth"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isHovered && !topic.isLocked) 0.4f else 0f,
        animationSpec = tween(300),
        label = "cardGlowAlpha"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .hoverable(interactionSource)
            .clickable(enabled = !topic.isLocked) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
        ),
        border = if (isHovered && !topic.isLocked) {
            null 
        } else {
            BorderStroke(borderWidth, neutralBorder)
        },
    ) {
        Box {
            
            if (isHovered && !topic.isLocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = borderWidth,
                            color = freshGreen,
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
            
            CardContent(topic = topic)
        }
    }
}

@Composable
private fun CardContent(
    topic: LessonTopic,
) {
    Card(
        modifier = Modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),  
        ) {
            
            Column {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleLarge.copy(  
                        fontWeight = FontWeight.Bold,
                    ),
                    color = WordBridgeColors.TextPrimary,
                )
                
                
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(3.dp)
                        .background(
                            color = Color(0xFF22C55E), 
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
            
            
            if (topic.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = topic.description,
                    style = MaterialTheme.typography.bodyLarge,  
                    color = WordBridgeColors.TextSecondary,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4f,
                    maxLines = 3,  
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            
            
            if (topic.durationMinutes != null || topic.isCompleted || topic.isLocked) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    if (topic.durationMinutes != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFECFDF5), 
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏱",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${topic.durationMinutes} min",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Color(0xFF059669), 
                                )
                            }
                        }
                    }
                    
                    
                    when {
                        topic.isCompleted -> {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFD1FAE5), 
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF059669), 
                                    )
                                    Text(
                                        text = "Completed",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color(0xFF059669),
                                    )
                                }
                            }
                        }
                        topic.isLocked -> {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFF1F5F9), 
                                border = BorderStroke(1.dp, Color(0xFF94A3B8).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔒",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "Locked",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color(0xFF64748B), 
                                    )
                                }
                            }
                        }
                        else -> {
                            
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFCCFBF1), 
                                border = BorderStroke(1.dp, Color(0xFF14B8A6).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "▶",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF0D9488), 
                                    )
                                    Text(
                                        text = "Start",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color(0xFF0D9488), 
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
