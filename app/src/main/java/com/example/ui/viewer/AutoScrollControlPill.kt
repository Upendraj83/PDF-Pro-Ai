package com.example.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PdfViewModel

@Composable
fun AutoScrollControlPill(
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val isActive by viewModel.isAutoScrollActive.collectAsStateWithLifecycle()
    val speed by viewModel.autoScrollSpeed.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.testTag("auto_scroll_pill"),
        shape = RoundedCornerShape(24.dp),
        color = if (isNightMode) Color(0xFF2E2E2E) else MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { viewModel.toggleAutoScroll() },
                modifier = Modifier.size(36.dp).testTag("btn_autoscroll_play_pause")
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isActive) "Pause Auto-scroll" else "Play Auto-scroll",
                    tint = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { viewModel.setAutoScrollSpeed((speed - 0.5f).coerceAtLeast(0.5f)) },
                enabled = speed > 0.5f,
                modifier = Modifier.size(30.dp).testTag("btn_autoscroll_speed_minus")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease scroll speed",
                    tint = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = "${String.format(java.util.Locale.US, "%.1f", speed)}x",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface
            )

            IconButton(
                onClick = { viewModel.setAutoScrollSpeed((speed + 0.5f).coerceAtMost(5.0f)) },
                enabled = speed < 5.0f,
                modifier = Modifier.size(30.dp).testTag("btn_autoscroll_speed_plus")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase scroll speed",
                    tint = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { viewModel.stopAutoScroll() },
                modifier = Modifier.size(36.dp).testTag("btn_autoscroll_close")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stop auto-scroll",
                    tint = if (isNightMode) Color.LightGray else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
