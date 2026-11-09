package com.example.ui.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.pdf.PlacedStampType

@Composable
fun FillAndSignToolbar(
    activeQuickFillType: PlacedStampType?,
    settings: AppSettings,
    isNightMode: Boolean,
    onSelectQuickFillType: (PlacedStampType?) -> Unit,
    onSnippetSelected: (String) -> Unit,
    onOpenSignaturesSheet: () -> Unit,
    onOpenAddFieldDialog: () -> Unit,
    onOpenSaveDialog: () -> Unit,
    onCloseToolbar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var snippetsMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isNightMode) Color(0xFF222222) else MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row with Title and Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Fill & Sign Toolkit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onOpenSaveDialog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("btn_toolbar_save_form")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onCloseToolbar,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_close_fill_sign_toolbar")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Fill & Sign")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Scrollable row of quick tool chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Signature Button
                FilterChip(
                    selected = false,
                    onClick = onOpenSignaturesSheet,
                    label = { Text("Sign / Initials") },
                    leadingIcon = {
                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("chip_tool_sign")
                )

                // Checkmark Chip
                FilterChip(
                    selected = activeQuickFillType == PlacedStampType.CHECKMARK,
                    onClick = {
                        onSelectQuickFillType(
                            if (activeQuickFillType == PlacedStampType.CHECKMARK) null else PlacedStampType.CHECKMARK
                        )
                    },
                    label = { Text("Check ✓") },
                    modifier = Modifier.testTag("chip_tool_checkmark")
                )

                // Cross Chip
                FilterChip(
                    selected = activeQuickFillType == PlacedStampType.CROSS,
                    onClick = {
                        onSelectQuickFillType(
                            if (activeQuickFillType == PlacedStampType.CROSS) null else PlacedStampType.CROSS
                        )
                    },
                    label = { Text("Cross ✕") },
                    modifier = Modifier.testTag("chip_tool_cross")
                )

                // Date Chip
                FilterChip(
                    selected = activeQuickFillType == PlacedStampType.DATE,
                    onClick = {
                        onSelectQuickFillType(
                            if (activeQuickFillType == PlacedStampType.DATE) null else PlacedStampType.DATE
                        )
                    },
                    label = { Text("Today's Date") },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.testTag("chip_tool_date")
                )

                // Free Text Chip
                FilterChip(
                    selected = activeQuickFillType == PlacedStampType.TEXT,
                    onClick = {
                        onSelectQuickFillType(
                            if (activeQuickFillType == PlacedStampType.TEXT) null else PlacedStampType.TEXT
                        )
                    },
                    label = { Text("Text") },
                    leadingIcon = {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.testTag("chip_tool_text")
                )

                // Profile Snippets Dropdown Chip
                Box {
                    FilterChip(
                        selected = snippetsMenuExpanded || activeQuickFillType == PlacedStampType.SNIPPET,
                        onClick = { snippetsMenuExpanded = true },
                        label = { Text("Snippets") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("chip_tool_snippets")
                    )

                    DropdownMenu(
                        expanded = snippetsMenuExpanded,
                        onDismissRequest = { snippetsMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name: ${settings.userFullName}") },
                            onClick = {
                                onSnippetSelected(settings.userFullName)
                                snippetsMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Initials: ${settings.userInitials}") },
                            onClick = {
                                onSnippetSelected(settings.userInitials)
                                snippetsMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Email: ${settings.userEmail}") },
                            onClick = {
                                onSnippetSelected(settings.userEmail)
                                snippetsMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Phone: ${settings.userPhone}") },
                            onClick = {
                                onSnippetSelected(settings.userPhone)
                                snippetsMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Address: ${settings.userAddress}") },
                            onClick = {
                                onSnippetSelected(settings.userAddress)
                                snippetsMenuExpanded = false
                            }
                        )
                    }
                }

                // Add Form Field Button
                FilterChip(
                    selected = false,
                    onClick = onOpenAddFieldDialog,
                    label = { Text("Create Field") },
                    leadingIcon = {
                        Icon(Icons.Default.FormatShapes, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("chip_tool_add_field")
                )
            }
        }
    }
}
