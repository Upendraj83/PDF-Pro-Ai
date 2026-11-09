package com.example.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdf.SearchMatch
import com.example.pdf.SearchProgress
import com.example.ui.PdfViewModel

@Composable
fun PdfSearchBar(
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.pdfSearchQuery.collectAsStateWithLifecycle()
    val searchProgress by viewModel.searchProgress.collectAsStateWithLifecycle()
    val matches by viewModel.searchMatches.collectAsStateWithLifecycle()
    val selectedIndex by viewModel.selectedMatchIndex.collectAsStateWithLifecycle()

    var showResultsList by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onPdfSearchQueryChanged(it) },
                    placeholder = { Text("Search text in PDF...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search in document",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onPdfSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search query")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_pdf_search")
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Previous Match Button
                IconButton(
                    onClick = { viewModel.previousSearchMatch() },
                    enabled = matches.isNotEmpty(),
                    modifier = Modifier.testTag("btn_search_prev")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous match"
                    )
                }

                // Next Match Button
                IconButton(
                    onClick = { viewModel.nextSearchMatch() },
                    enabled = matches.isNotEmpty(),
                    modifier = Modifier.testTag("btn_search_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next match"
                    )
                }

                // Toggle Results List
                IconButton(
                    onClick = { showResultsList = !showResultsList },
                    enabled = matches.isNotEmpty(),
                    modifier = Modifier.testTag("btn_toggle_search_results")
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Show results list",
                        tint = if (showResultsList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Close Search Bar
                IconButton(
                    onClick = { viewModel.closeSearch() },
                    modifier = Modifier.testTag("btn_close_search_bar")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search"
                    )
                }
            }

            // Search Progress & Status
            when (val progress = searchProgress) {
                is SearchProgress.Searching -> {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Searching page ${progress.currentPage} of ${progress.totalPages}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${progress.matchesFound} found",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress.currentPage.toFloat() / progress.totalPages.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is SearchProgress.Complete -> {
                    if (searchQuery.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (matches.isEmpty()) "No matches found"
                                else "Match ${selectedIndex + 1} of ${matches.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (matches.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )

                            if (matches.isNotEmpty()) {
                                Text(
                                    text = "Tap list icon to view snippets",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is SearchProgress.Failed -> {
                    Text(
                        text = "Error: ${progress.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Expandable List of Matching Snippets
            AnimatedVisibility(
                visible = showResultsList && matches.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNightMode) Color(0xFF242424) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        itemsIndexed(matches) { index, match ->
                            SearchSnippetItem(
                                match = match,
                                isSelected = index == selectedIndex,
                                query = searchQuery,
                                isNightMode = isNightMode,
                                onClick = {
                                    viewModel.jumpToMatch(index)
                                }
                            )
                            if (index < matches.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSnippetItem(
    match: SearchMatch,
    isSelected: Boolean,
    query: String,
    isNightMode: Boolean,
    onClick: () -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotatedSnippet = remember(match.snippet, query) {
        buildAnnotatedString {
            val lowerSnippet = match.snippet.lowercase()
            val lowerQuery = query.lowercase()
            var start = 0
            while (start < match.snippet.length) {
                val index = lowerSnippet.indexOf(lowerQuery, start)
                if (index == -1) {
                    append(match.snippet.substring(start))
                    break
                }
                append(match.snippet.substring(start, index))
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        color = highlightColor
                    )
                ) {
                    append(match.snippet.substring(index, index + query.length))
                }
                start = index + query.length
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "p. ${match.pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = annotatedSnippet,
            style = MaterialTheme.typography.bodySmall,
            color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
