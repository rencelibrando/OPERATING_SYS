package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.domain.model.VocabularyFilter
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun VocabularySearchBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedFilter: VocabularyFilter,
    onFilterSelected: (VocabularyFilter) -> Unit,
    onAddWordClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0xFF8B5CF6).copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1625).copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search field row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Modern search field with soft styling
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            text = "Search your vocabulary...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8B5CF6).copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                        cursorColor = Color(0xFF8B5CF6),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )

                // Modern Add Word button with gradient
                Button(
                    onClick = onAddWordClick,
                    modifier = Modifier
                        .height(52.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(14.dp),
                            spotColor = Color(0xFF8B5CF6).copy(alpha = 0.4f)
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add Word",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = Color.White,
                        )
                    }
                }
            }

            // Filter chips row with modern styling
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VocabularyFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val chipColor = when (filter) {
                        VocabularyFilter.ALL -> Color(0xFF8B5CF6)
                        VocabularyFilter.MASTERED -> Color(0xFF10B981)
                        VocabularyFilter.LEARNING -> Color(0xFFF59E0B)
                        VocabularyFilter.REVIEW -> Color(0xFFEF4444)
                    }
                    
                    FilterChip(
                        onClick = { onFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        },
                        selected = isSelected,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor.copy(alpha = 0.9f),
                            containerColor = Color.White.copy(alpha = 0.08f),
                            selectedLabelColor = Color.White,
                            labelColor = Color.White.copy(alpha = 0.7f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.1f),
                            selectedBorderColor = chipColor.copy(alpha = 0.5f),
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
        }
    }
}
