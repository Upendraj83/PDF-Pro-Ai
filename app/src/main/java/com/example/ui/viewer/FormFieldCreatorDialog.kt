package com.example.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pdf.FormFieldType

@Composable
fun FormFieldCreatorDialog(
    currentPage: Int,
    onAddField: (type: FormFieldType, name: String, defaultValue: String, isRequired: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(FormFieldType.TEXT) }
    var fieldName by remember { mutableStateOf("") }
    var defaultValue by remember { mutableStateOf("") }
    var isRequired by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Form Field to Page ${currentPage + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Field Type:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == FormFieldType.TEXT,
                        onClick = { selectedType = FormFieldType.TEXT },
                        label = { Text("Text") },
                        modifier = Modifier.testTag("chip_field_text")
                    )
                    FilterChip(
                        selected = selectedType == FormFieldType.CHECKBOX,
                        onClick = { selectedType = FormFieldType.CHECKBOX },
                        label = { Text("Checkbox") },
                        modifier = Modifier.testTag("chip_field_checkbox")
                    )
                    FilterChip(
                        selected = selectedType == FormFieldType.DROPDOWN,
                        onClick = { selectedType = FormFieldType.DROPDOWN },
                        label = { Text("Dropdown") },
                        modifier = Modifier.testTag("chip_field_dropdown")
                    )
                }

                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    label = { Text("Field Name (Key)") },
                    placeholder = { Text("e.g. employee_signature_date") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_form_field_name")
                )

                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text("Default Value (Optional)") },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                FormFieldType.CHECKBOX -> "true or false"
                                FormFieldType.DROPDOWN -> "Option 1"
                                else -> "Enter default text"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_form_field_default_value")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Required Field",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Flag field as mandatory (*)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRequired,
                        onCheckedChange = { isRequired = it },
                        modifier = Modifier.testTag("switch_form_field_required")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddField(
                        selectedType,
                        fieldName.ifBlank { "field_${System.currentTimeMillis() % 10000}" },
                        defaultValue,
                        isRequired
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("btn_confirm_add_field")
            ) {
                Text("Create Field")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
