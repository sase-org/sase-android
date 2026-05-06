package org.sase.mobile.ui.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.sase.mobile.R
import org.sase.mobile.data.api.dto.MobileActionKindWire
import org.sase.mobile.data.api.dto.MobileActionStateWire
import org.sase.mobile.data.api.dto.MobileAttachmentKindWire
import org.sase.mobile.data.api.dto.MobileNotificationDetailResponseWire
import org.sase.mobile.data.notifications.NotificationDetailState
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.ui.NotificationUiFixtures
import org.sase.mobile.ui.theme.SaseMobileTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationDetailScreen(
    notificationId: String,
    modifier: Modifier = Modifier,
    repository: NotificationRepository? = null,
    initialState: NotificationDetailState? = null,
    onBack: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var detailState by remember(notificationId, initialState) { mutableStateOf(initialState) }
    var mutationMessage by remember(notificationId) { mutableStateOf<String?>(null) }

    LaunchedEffect(notificationId, repository) {
        if (repository != null) {
            detailState = repository.refreshDetail(notificationId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("notification_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_24),
                    contentDescription = "Back to inbox",
                )
            }
            Text(
                text = "Notification",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        when (val state = detailState) {
            null -> LoadingDetail(notificationId)
            NotificationDetailState.LoggedOut -> LoggedOutDetail()
            is NotificationDetailState.Failed -> FailedDetail(state.message)
            is NotificationDetailState.Ready -> {
                DetailContent(
                    state = state,
                    mutationMessage = mutationMessage,
                    onMarkRead = {
                        scope.launch {
                            val success = repository?.markRead(notificationId) ?: false
                            mutationMessage = if (success) "Marked read" else "Unable to mark read"
                            if (repository != null) {
                                detailState = repository.refreshDetail(notificationId)
                            }
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            val success = repository?.dismiss(notificationId) ?: false
                            mutationMessage = if (success) "Dismissed" else "Unable to dismiss"
                            if (repository != null) {
                                detailState = repository.refreshDetail(notificationId)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingDetail(notificationId: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(notificationId.ifBlank { "Unknown notification" }, style = MaterialTheme.typography.titleMedium)
        Text("Loading detail from the paired gateway or local cache.")
    }
}

@Composable
private fun LoggedOutDetail() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No paired host", style = MaterialTheme.typography.titleMedium)
        Text("Pair a gateway in settings before opening live notification details.")
    }
}

@Composable
private fun FailedDetail(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Detail unavailable", style = MaterialTheme.typography.titleMedium)
        Text(message, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    state: NotificationDetailState.Ready,
    mutationMessage: String?,
    onMarkRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val detail = state.detail
    val card = detail.notification
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.stale) {
                ElevatedAssistChip(onClick = {}, label = { Text("Cached") })
            }
            AssistChip(onClick = {}, label = { Text(card.sender) })
            AssistChip(onClick = {}, label = { Text(compactTimestamp(card.timestamp)) })
            if (card.priority) {
                AssistChip(onClick = {}, label = { Text("Priority") })
            }
            card.actionSummary?.let {
                AssistChip(onClick = {}, label = { Text("${it.label}: ${it.state.label}") })
            }
        }

        Text(
            text = card.notesSummary,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "ID: ${card.id}",
            style = MaterialTheme.typography.bodySmall,
        )

        ActionState(detail)

        HorizontalDivider()

        Text("Notes", style = MaterialTheme.typography.titleMedium)
        if (detail.notes.isEmpty()) {
            Text("No notes supplied.")
        } else {
            detail.notes.forEach { note ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = note,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        Text("Attachments", style = MaterialTheme.typography.titleMedium)
        if (detail.attachments.isEmpty()) {
            Text("No attachments.")
        } else {
            detail.attachments.forEach { attachment ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = attachment.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = attachmentSummary(attachment.kind, attachment.byteSize, attachment.downloadable),
                        )
                    },
                )
                HorizontalDivider()
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = onMarkRead,
                enabled = !card.read,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mark_read_24),
                    contentDescription = null,
                )
                Text("Mark read", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onDismiss,
                enabled = !card.dismissed,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_dismiss_24),
                    contentDescription = null,
                )
                Text("Dismiss", modifier = Modifier.padding(start = 8.dp))
            }
        }
        mutationMessage?.let {
            Text(it, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionState(detail: MobileNotificationDetailResponseWire) {
    val action = detail.action
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Action state", style = MaterialTheme.typography.titleMedium)
            Text("${action.kind.label}: ${action.state.label}")
            val message = when (action.state) {
                MobileActionStateWire.Available -> "Action controls arrive in the next epic."
                MobileActionStateWire.AlreadyHandled -> "Already handled on the host."
                MobileActionStateWire.Stale -> "State is stale; refresh before acting."
                MobileActionStateWire.MissingRequest -> "The original request is missing."
                MobileActionStateWire.MissingTarget -> "The target resource is missing."
                MobileActionStateWire.Unsupported -> "This action is not supported on Android yet."
            }
            Text(message)
            action.planFile?.let { Text("Plan: $it") }
            action.responseDir?.let { Text("Response dir: $it") }
        }
    }
}

private val MobileActionKindWire.label: String
    get() = when (this) {
        MobileActionKindWire.PlanApproval -> "Plan approval"
        MobileActionKindWire.Hitl -> "HITL"
        MobileActionKindWire.UserQuestion -> "Question"
        MobileActionKindWire.NonAction -> "Notification"
        MobileActionKindWire.Unsupported -> "Unsupported"
    }

private val MobileActionStateWire.label: String
    get() = when (this) {
        MobileActionStateWire.Available -> "available"
        MobileActionStateWire.AlreadyHandled -> "already handled"
        MobileActionStateWire.Stale -> "stale"
        MobileActionStateWire.MissingRequest -> "missing request"
        MobileActionStateWire.MissingTarget -> "missing target"
        MobileActionStateWire.Unsupported -> "unsupported"
    }

private fun attachmentSummary(
    kind: MobileAttachmentKindWire,
    byteSize: Long?,
    downloadable: Boolean,
): String {
    val size = byteSize?.let { "${it / 1024} KB" } ?: "unknown size"
    val access = if (downloadable) "metadata only, downloadable later" else "metadata only"
    return "${kind.name.lowercase()} - $size - $access"
}

private fun compactTimestamp(value: String): String {
    return value
        .removeSuffix("Z")
        .replace("T", " ")
        .take(19)
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NotificationDetailPreview() {
    SaseMobileTheme {
        NotificationDetailScreen(
            notificationId = "plan0001-review",
            initialState = NotificationDetailState.Ready(NotificationUiFixtures.detail, stale = false),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StaleNotificationDetailPreview() {
    SaseMobileTheme {
        NotificationDetailScreen(
            notificationId = "plan0001-review",
            initialState = NotificationDetailState.Ready(NotificationUiFixtures.detail, stale = true),
        )
    }
}
