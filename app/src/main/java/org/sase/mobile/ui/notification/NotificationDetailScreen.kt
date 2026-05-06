package org.sase.mobile.ui.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun NotificationDetailScreen(
    notificationId: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("notification_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Notification",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = notificationId.ifBlank { "Unknown notification" },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Detail content, action state, and attachments will bind to gateway fixtures in later phases.",
            style = MaterialTheme.typography.bodyMedium,
        )
        AssistChip(
            onClick = {},
            label = { Text("Read-only MVP shell") },
        )
    }
}
