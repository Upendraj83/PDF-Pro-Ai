package com.example.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormNavigationFab(
    currentFieldIndex: Int,
    totalFields: Int,
    currentFieldName: String,
    onNavigateNext: () -> Unit,
    onNavigatePrev: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalFields <= 0) return

    Surface(
        modifier = modifier.testTag("form_navigation_fab"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onNavigatePrev,
                enabled = currentFieldIndex > 0,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("btn_prev_form_field")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous field",
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = "Field ${currentFieldIndex + 1}/$totalFields: ${currentFieldName.take(12)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(
                onClick = onNavigateNext,
                enabled = currentFieldIndex < totalFields - 1,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("btn_next_form_field")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next field",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
