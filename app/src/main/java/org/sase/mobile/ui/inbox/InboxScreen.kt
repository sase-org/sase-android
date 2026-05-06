package org.sase.mobile.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.sase.mobile.R
import org.sase.mobile.data.api.dto.MobileActionKindWire
import org.sase.mobile.data.api.dto.MobileActionStateWire
import org.sase.mobile.data.api.dto.MobileNotificationCardWire
import org.sase.mobile.data.notifications.NotificationConnectionState
import org.sase.mobile.data.notifications.NotificationInboxState
import org.sase.mobile.data.notifications.NotificationRefreshState
import org.sase.mobile.data.notifications.RefreshReason
import org.sase.mobile.ui.NotificationUiFixtures
import org.sase.mobile.ui.theme.SaseMobileTheme

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    state: NotificationInboxState,
    connectionState: NotificationConnectionState,
    refreshState: NotificationRefreshState,
    onRefresh: () -> Unit,
    onOpenNotification: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var unreadOnly by remember { mutableStateOf(false) }
    var includeDismissed by remember { mutableStateOf(false) }
    var includeSilent by remember { mutableStateOf(false) }
    val visibleCards = state.cards
        .filter { !unreadOnly || !it.read }
        .filter { includeDismissed || !it.dismissed }
        .filter { includeSilent || !it.silent }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("inbox_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Inbox",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_24),
                    contentDescription = "Refresh inbox",
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24),
                    contentDescription = "Open settings",
                )
            }
        }

        InboxStatusRow(
            state = state,
            connectionState = connectionState,
            refreshState = refreshState,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = unreadOnly,
                onClick = { unreadOnly = !unreadOnly },
                label = { Text("Unread") },
            )
            FilterChip(
                selected = includeDismissed,
                onClick = { includeDismissed = !includeDismissed },
                label = { Text("Dismissed") },
            )
            FilterChip(
                selected = includeSilent,
                onClick = { includeSilent = !includeSilent },
                label = { Text("Silent") },
            )
        }

        if (visibleCards.isEmpty()) {
            EmptyInbox(
                state = state,
                unreadOnly = unreadOnly,
                includeDismissed = includeDismissed,
                includeSilent = includeSilent,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(visibleCards, key = { it.id }) { card ->
                    NotificationRow(
                        card = card,
                        onOpenNotification = onOpenNotification,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxStatusRow(
    state: NotificationInboxState,
    connectionState: NotificationConnectionState,
    refreshState: NotificationRefreshState,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ElevatedAssistChip(
            onClick = {},
            label = { Text(connectionLabel(connectionState)) },
        )
        when (refreshState) {
            NotificationRefreshState.Idle -> {
                state.lastFullRefreshAt?.let {
                    AssistChip(onClick = {}, label = { Text("Synced ${compactTimestamp(it)}") })
                }
            }

            is NotificationRefreshState.Refreshing -> AssistChip(
                onClick = {},
                label = { Text("Refreshing ${refreshState.reason.label}") },
            )

            is NotificationRefreshState.Failed -> AssistChip(
                onClick = {},
                label = { Text("Refresh failed") },
            )
        }
        if (state.isStale) {
            AssistChip(onClick = {}, label = { Text("Cached") })
        }
        state.lastEventId?.let {
            AssistChip(onClick = {}, label = { Text("Event $it") })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationRow(
    card: MobileNotificationCardWire,
    onOpenNotification: (String) -> Unit,
) {
    val containerColor = if (card.dismissed) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenNotification(card.id) },
    ) {
        ListItem(
            leadingContent = {
                PriorityMarker(card)
            },
            overlineContent = {
                Text(
                    text = "${card.sender} - ${compactTimestamp(card.timestamp)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            headlineContent = {
                Text(
                    text = card.notesSummary,
                    fontWeight = if (card.read) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    StateText(card)
                    card.actionSummary?.let { action ->
                        Text("${action.label}: ${action.state.label}")
                    }
                    if (card.fileCount > 0) {
                        Text("${card.fileCount} attachments")
                    }
                    if (card.silent) {
                        Text("silent")
                    }
                }
            },
            trailingContent = {
                Text(
                    text = card.actionSummary?.kind?.label ?: "Info",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            },
        )
    }
}

@Composable
private fun PriorityMarker(card: MobileNotificationCardWire) {
    val label = when {
        card.priority -> "!"
        card.actionable -> "*"
        else -> card.sender.firstOrNull()?.uppercase() ?: "I"
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (card.priority) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StateText(card: MobileNotificationCardWire) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (card.read) "read" else "unread")
        Spacer(Modifier.width(6.dp))
        Text(if (card.dismissed) "dismissed" else "active")
    }
}

@Composable
private fun EmptyInbox(
    state: NotificationInboxState,
    unreadOnly: Boolean,
    includeDismissed: Boolean,
    includeSilent: Boolean,
) {
    val message = when {
        state.cards.isNotEmpty() -> "No notifications match the current filters."
        state.isStale -> "Cached inbox is empty while the gateway is offline."
        unreadOnly || includeDismissed || includeSilent -> "No notifications match the current filters."
        else -> "No notifications yet."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (state.lastFullRefreshAt == null) {
            Text("Pair a gateway or refresh when the host is reachable.")
        }
    }
}

private val RefreshReason.label: String
    get() = when (this) {
        RefreshReason.AppStart -> "app state"
        RefreshReason.Manual -> "inbox"
        RefreshReason.Reconnect -> "after reconnect"
        RefreshReason.NotificationsChanged -> "notifications"
        RefreshReason.ResyncRequired -> "full state"
        RefreshReason.SessionChanged -> "session"
    }

private val MobileActionKindWire.label: String
    get() = when (this) {
        MobileActionKindWire.PlanApproval -> "Plan"
        MobileActionKindWire.Hitl -> "HITL"
        MobileActionKindWire.UserQuestion -> "Question"
        MobileActionKindWire.NonAction -> "Info"
        MobileActionKindWire.Unsupported -> "Unsupported"
    }

private val MobileActionStateWire.label: String
    get() = when (this) {
        MobileActionStateWire.Available -> "available"
        MobileActionStateWire.AlreadyHandled -> "handled"
        MobileActionStateWire.Stale -> "stale"
        MobileActionStateWire.MissingRequest -> "missing request"
        MobileActionStateWire.MissingTarget -> "missing target"
        MobileActionStateWire.Unsupported -> "unsupported"
    }

private fun connectionLabel(connectionState: NotificationConnectionState): String {
    return when (connectionState) {
        NotificationConnectionState.Stopped -> "Stopped"
        NotificationConnectionState.Connecting -> "Connecting"
        is NotificationConnectionState.Reconnecting -> "Reconnecting ${connectionState.lastEventId}"
        NotificationConnectionState.Connected -> "Live"
        is NotificationConnectionState.Offline -> "Offline"
        NotificationConnectionState.LoggedOut -> "No host"
    }
}

private fun compactTimestamp(value: String): String {
    return value
        .removeSuffix("Z")
        .substringAfter("T", value)
        .take(8)
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun InboxPreview() {
    SaseMobileTheme {
        InboxScreen(
            state = NotificationUiFixtures.inboxState,
            connectionState = NotificationConnectionState.Connected,
            refreshState = NotificationRefreshState.Idle,
            onRefresh = {},
            onOpenNotification = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StaleInboxPreview() {
    SaseMobileTheme {
        InboxScreen(
            state = NotificationUiFixtures.staleInboxState,
            connectionState = NotificationConnectionState.Offline("connection refused"),
            refreshState = NotificationRefreshState.Failed("connection refused"),
            onRefresh = {},
            onOpenNotification = {},
            onOpenSettings = {},
        )
    }
}
