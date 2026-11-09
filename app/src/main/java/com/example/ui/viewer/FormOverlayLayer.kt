package com.example.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf.FormFieldItem
import com.example.pdf.FormFieldType
import com.example.pdf.PlacedStampItem
import com.example.pdf.PlacedStampType
import kotlin.math.roundToInt

@Composable
fun FormOverlayLayer(
    pageIndex: Int,
    formFields: List<FormFieldItem>,
    placedStamps: List<PlacedStampItem>,
    focusedFieldId: String?,
    selectedStampId: String?,
    activeQuickFillType: PlacedStampType?,
    pendingSnippetText: String?,
    onFieldFocused: (String) -> Unit,
    onFieldValueChanged: (fieldName: String, value: String) -> Unit,
    onStampSelected: (String?) -> Unit,
    onStampUpdated: (PlacedStampItem) -> Unit,
    onStampDeleted: (String) -> Unit,
    onPlaceStampAt: (pageIndex: Int, normX: Float, normY: Float, type: PlacedStampType, text: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(activeQuickFillType, pendingSnippetText) {
                if (activeQuickFillType != null) {
                    detectTapGestures { offset ->
                        val normX = (offset.x / size.width).coerceIn(0.05f, 0.85f)
                        val normY = (offset.y / size.height).coerceIn(0.05f, 0.85f)
                        onPlaceStampAt(pageIndex, normX, normY, activeQuickFillType, pendingSnippetText)
                    }
                } else {
                    detectTapGestures {
                        onStampSelected(null)
                    }
                }
            }
    ) {
        val layerWidth = maxWidth
        val layerHeight = maxHeight

        // 1. Render AcroForm fields
        formFields.forEach { field ->
            val fieldLeft = layerWidth * field.bounds.left
            val fieldTop = layerHeight * field.bounds.top
            val fieldW = (layerWidth * field.bounds.width).coerceAtLeast(32.dp)
            val fieldH = (layerHeight * field.bounds.height).coerceAtLeast(24.dp)
            val isFocused = focusedFieldId == field.id

            Box(
                modifier = Modifier
                    .offset(x = fieldLeft, y = fieldTop)
                    .size(width = fieldW, height = fieldH)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else Color(0x3342A5F5)
                    )
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) MaterialTheme.colorScheme.primary
                        else if (field.isRequired) Color(0xFFE53935)
                        else Color(0xFF1E88E5),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onFieldFocused(field.id) }
                    .testTag("acro_field_${field.fullyQualifiedName}"),
                contentAlignment = Alignment.CenterStart
            ) {
                when (field.type) {
                    FormFieldType.CHECKBOX -> {
                        val isChecked = field.currentValue.equals("yes", true) ||
                                field.currentValue.equals("true", true) ||
                                field.currentValue.equals("1", true)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    onFieldValueChanged(field.fullyQualifiedName, if (checked) "Yes" else "Off")
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    FormFieldType.RADIO -> {
                        val isChecked = field.currentValue.isNotBlank() && !field.currentValue.equals("off", true)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            RadioButton(
                                selected = isChecked,
                                onClick = {
                                    val newVal = if (isChecked) "Off" else "Yes"
                                    onFieldValueChanged(field.fullyQualifiedName, newVal)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    FormFieldType.DROPDOWN, FormFieldType.LIST -> {
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp)
                                .clickable { dropdownExpanded = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = field.currentValue.ifBlank { field.options.firstOrNull() ?: "Select" },
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            val options = if (field.options.isNotEmpty()) field.options else listOf("Option 1", "Option 2", "Option 3")
                            options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        onFieldValueChanged(field.fullyQualifiedName, opt)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    else -> {
                        // TEXT and DATE fields
                        BasicTextField(
                            value = field.currentValue,
                            onValueChange = { newVal ->
                                onFieldValueChanged(field.fullyQualifiedName, newVal)
                            },
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Normal
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("input_field_${field.name}")
                        )
                    }
                }

                if (field.isRequired && field.currentValue.isBlank()) {
                    Text(
                        text = "*",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 2.dp)
                    )
                }
            }
        }

        // 2. Render Placed Stamps (Signatures, Initials, Checkmarks, Dates, Snippets)
        placedStamps.forEach { stamp ->
            val isSelected = selectedStampId == stamp.id
            val stampLeft = layerWidth * stamp.normX
            val stampTop = layerHeight * stamp.normY
            val stampW = (layerWidth * stamp.normWidth).coerceAtLeast(36.dp)
            val stampH = (layerHeight * stamp.normHeight).coerceAtLeast(24.dp)

            Box(
                modifier = Modifier
                    .offset(x = stampLeft, y = stampTop)
                    .size(width = stampW, height = stampH)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else Color.Transparent
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.5.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .pointerInput(stamp.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaX = dragAmount.x / size.width
                            val deltaY = dragAmount.y / size.height
                            val newX = (stamp.normX + deltaX).coerceIn(0f, 1f - stamp.normWidth)
                            val newY = (stamp.normY + deltaY).coerceIn(0f, 1f - stamp.normHeight)
                            onStampUpdated(stamp.copy(normX = newX, normY = newY))
                        }
                    }
                    .clickable { onStampSelected(stamp.id) }
                    .testTag("placed_stamp_${stamp.id}"),
                contentAlignment = Alignment.Center
            ) {
                when {
                    stamp.imageBitmap != null -> {
                        Image(
                            bitmap = stamp.imageBitmap.asImageBitmap(),
                            contentDescription = "Signature stamp",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    stamp.type == PlacedStampType.CHECKMARK -> {
                        Text(
                            text = "✓",
                            color = Color(stamp.colorArgb),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    stamp.type == PlacedStampType.CROSS -> {
                        Text(
                            text = "✕",
                            color = Color(stamp.colorArgb),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    stamp.type == PlacedStampType.DATE -> {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(stamp.colorArgb).copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(stamp.colorArgb))
                        ) {
                            Text(
                                text = stamp.textValue ?: "Date",
                                color = Color(stamp.colorArgb),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    else -> {
                        // Free Text or User Snippet
                        Text(
                            text = stamp.textValue ?: "",
                            color = Color(stamp.colorArgb),
                            fontSize = stamp.fontSizeSp.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                // Delete button and resize handle for selected stamp
                if (isSelected) {
                    // Delete Button (top right)
                    Surface(
                        onClick = { onStampDeleted(stamp.id) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .testTag("btn_delete_stamp_${stamp.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete stamp",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Resize Handle (bottom right corner)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .pointerInput(stamp.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaW = dragAmount.x / size.width
                                    val deltaH = dragAmount.y / size.height
                                    val newW = (stamp.normWidth + deltaW).coerceIn(0.04f, 0.8f)
                                    val newH = (stamp.normHeight + deltaH).coerceIn(0.02f, 0.5f)
                                    onStampUpdated(stamp.copy(normWidth = newW, normHeight = newH))
                                }
                            }
                    )
                }
            }
        }
    }
}
