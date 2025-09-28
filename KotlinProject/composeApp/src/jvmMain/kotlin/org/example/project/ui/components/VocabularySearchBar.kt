package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.VocabularyFilter
import org.example.project.ui.theme.WordBridgeColors


@Composable
fun VocabularySearchBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedFilter: VocabularyFilter,
    onFilterSelected: (VocabularyFilter) -> Unit,
    onAddWordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = {
                Text(
                    text = "Search vocabulary...",
                    color = WordBridgeColors.TextMuted
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WordBridgeColors.PrimaryPurple,
                unfocusedBorderColor = WordBridgeColors.TextMuted.copy(alpha = 0.3f)
            ),
            singleLine = true
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VocabularyFilter.values().forEach { filter ->
                FilterChip(
                    onClick = { onFilterSelected(filter) },
                    label = {
                        Text(
                            text = filter.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (selectedFilter == filter) FontWeight.Medium else FontWeight.Normal
                            )
                        )
                    },
                    selected = selectedFilter == filter
                )
            }
        }
        
        Button(
            onClick = onAddWordClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = WordBridgeColors.PrimaryPurple
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "+ Add Word",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White
            )
        }
    }
}
