package org.sase.mobile.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun InboxScreen(
    onOpenNotification: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("inbox_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Inbox",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Gateway notifications will appear here after pairing.",
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Plan review waiting") },
            supportingContent = { Text("Placeholder notification detail route") },
            trailingContent = {
                Button(
                    onClick = { onOpenNotification("placeholder-plan-review") },
                    modifier = Modifier.fillMaxWidth(0.34f),
                ) {
                    Text("Open")
                }
            },
        )
    }
}
