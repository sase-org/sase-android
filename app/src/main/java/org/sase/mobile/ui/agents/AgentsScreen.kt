package org.sase.mobile.ui.agents

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.sase.mobile.R
import org.sase.mobile.data.agents.AgentActionState
import org.sase.mobile.data.agents.AgentConnectionState
import org.sase.mobile.data.agents.AgentFailure
import org.sase.mobile.data.agents.AgentFailureKind
import org.sase.mobile.data.agents.AgentsRefreshReason
import org.sase.mobile.data.agents.AgentsState
import org.sase.mobile.data.api.dto.MobileAgentActionAffordancesWire
import org.sase.mobile.data.api.dto.MobileAgentDisplayLabelsWire
import org.sase.mobile.data.api.dto.MobileAgentResumeOptionKindWire
import org.sase.mobile.data.api.dto.MobileAgentResumeOptionWire
import org.sase.mobile.data.api.dto.MobileAgentRetryLineageWire
import org.sase.mobile.data.api.dto.MobileAgentSummaryWire
import org.sase.mobile.ui.theme.SaseMobileTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentsScreen(
    state: AgentsState,
    onRefresh: () -> Unit,
    onKill: (String) -> Unit,
    onRetry: (String) -> Unit,
    onClearActionResult: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf(AgentFilter.Running) }
    var pendingAction by remember { mutableStateOf<PendingAgentAction?>(null) }
    val visibleAgents = state.agents.filter { selectedFilter.matches(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("agents_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Agents",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_24),
                    contentDescription = "Refresh agents",
                )
            }
        }

        AgentStatusRow(
            state = state,
            onOpenSettings = onOpenSettings,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AgentFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) },
                )
            }
        }

        ActionResultBanner(
            action = state.action,
            onDismiss = onClearActionResult,
        )

        ResumeOptions(
            options = state.resumeOptions,
        )

        when {
            state.loading && state.agents.isEmpty() -> LoadingAgents()
            state.failure != null && state.agents.isEmpty() -> FailureAgents(
                failure = state.failure,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
            )

            visibleAgents.isEmpty() -> EmptyAgents(selectedFilter)
            else -> AgentList(
                agents = visibleAgents,
                onRequestKill = { pendingAction = PendingAgentAction.Kill(it) },
                onRequestRetry = { pendingAction = PendingAgentAction.Retry(it) },
            )
        }
    }

    pendingAction?.let { action ->
        ConfirmAgentActionDialog(
            action = action,
            onDismiss = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                when (action) {
                    is PendingAgentAction.Kill -> onKill(action.agentName)
                    is PendingAgentAction.Retry -> onRetry(action.agentName)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentStatusRow(
    state: AgentsState,
    onOpenSettings: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ElevatedAssistChip(
            onClick = {},
            label = { Text(connectionLabel(state.connection)) },
        )
        state.refreshReason?.let { reason ->
            AssistChip(onClick = {}, label = { Text("Refreshing ${reason.label}") })
        }
        if (state.totalCount > 0) {
            AssistChip(onClick = {}, label = { Text("${state.totalCount} total") })
        }
        state.lastEventId?.let {
            AssistChip(onClick = {}, label = { Text("Event $it") })
        }
        if (state.connection is AgentConnectionState.LoggedOut) {
            AssistChip(onClick = onOpenSettings, label = { Text("Open settings") })
        }
    }
}

@Composable
private fun ActionResultBanner(
    action: AgentActionState,
    onDismiss: () -> Unit,
) {
    when (action) {
        AgentActionState.Idle -> Unit
        is AgentActionState.Running -> Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = action.label,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is AgentActionState.Succeeded -> DismissibleMessage(
            text = action.message,
            onDismiss = onDismiss,
        )

        is AgentActionState.Failed -> DismissibleMessage(
            text = action.message,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun DismissibleMessage(
    text: String,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResumeOptions(
    options: List<MobileAgentResumeOptionWire>,
) {
    if (options.isEmpty()) {
        return
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Resume and Wait",
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                AssistChip(
                    onClick = {
                        clipboard.setText(AnnotatedString(option.promptText))
                    },
                    label = { Text(option.label) },
                    leadingIcon = {
                        Text(
                            text = if (option.kind == MobileAgentResumeOptionKindWire.Resume) "R" else "W",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                AssistChip(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, option.promptText)
                        context.startActivity(Intent.createChooser(intent, option.label))
                    },
                    label = { Text("Share") },
                )
            }
        }
    }
}

@Composable
private fun LoadingAgents() {
    Text(
        text = "Loading agents",
        modifier = Modifier.padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun FailureAgents(
    failure: AgentFailure,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (failure.kind) {
                AgentFailureKind.AuthExpired -> "Authentication expired"
                AgentFailureKind.BridgeUnavailable -> "Agent bridge unavailable"
                AgentFailureKind.Offline -> "Host offline"
                else -> "Agents unavailable"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = failure.message, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh) {
                Text("Retry")
            }
            if (failure.kind == AgentFailureKind.AuthExpired) {
                OutlinedButton(onClick = onOpenSettings) {
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
private fun EmptyAgents(filter: AgentFilter) {
    Text(
        text = "No ${filter.label.lowercase()} agents",
        modifier = Modifier.padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AgentList(
    agents: List<MobileAgentSummaryWire>,
    onRequestKill: (String) -> Unit,
    onRequestRetry: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(agents, key = { it.name }) { agent ->
            AgentRow(
                agent = agent,
                onRequestKill = onRequestKill,
                onRequestRetry = onRequestRetry,
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentRow(
    agent: MobileAgentSummaryWire,
    onRequestKill: (String) -> Unit,
    onRequestRetry: (String) -> Unit,
) {
    ListItem(
        overlineContent = {
            Text(
                text = buildString {
                    append(agent.display.statusLabel)
                    agent.project?.let { append(" - ").append(it) }
                    agent.durationSeconds?.let { append(" - ").append(formatDuration(it)) }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        headlineContent = {
            Text(
                text = agent.display.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = agent.promptSnippet ?: agent.display.subtitle.orEmpty(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    agent.provider?.let { Text(it) }
                    agent.model?.let { Text(it) }
                    agent.workspaceNumber?.let { Text("workspace $it") }
                    agent.retryLineage.retryAttempt?.takeIf { it > 0 }?.let { Text("retry $it") }
                }
            }
        },
        trailingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (agent.actions.canKill) {
                    TextButton(
                        onClick = { onRequestKill(agent.name) },
                        modifier = Modifier.testTag("kill_${agent.name}"),
                    ) {
                        Text("Kill")
                    }
                }
                if (agent.actions.canRetry) {
                    TextButton(
                        onClick = { onRequestRetry(agent.name) },
                        modifier = Modifier.testTag("retry_${agent.name}"),
                    ) {
                        Text("Retry")
                    }
                }
            }
        },
    )
}

@Composable
private fun ConfirmAgentActionDialog(
    action: PendingAgentAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isKill = action is PendingAgentAction.Kill
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isKill) "Kill agent" else "Retry agent") },
        text = {
            Text(
                if (isKill) {
                    "Stop ${action.agentName} on the host."
                } else {
                    "Start a retry from ${action.agentName}."
                },
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (isKill) "Kill" else "Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private sealed interface PendingAgentAction {
    val agentName: String

    data class Kill(override val agentName: String) : PendingAgentAction
    data class Retry(override val agentName: String) : PendingAgentAction
}

private enum class AgentFilter(val label: String) {
    Running("Running"),
    Recent("Recent"),
    All("All"),
    Retry("Retry"),
    ;

    fun matches(agent: MobileAgentSummaryWire): Boolean {
        return when (this) {
            Running -> agent.status.equals("running", ignoreCase = true)
            Recent -> !agent.status.equals("running", ignoreCase = true)
            All -> true
            Retry -> agent.actions.canRetry
        }
    }
}

private val AgentsRefreshReason.label: String
    get() = when (this) {
        AgentsRefreshReason.AppStart -> "agents"
        AgentsRefreshReason.Manual -> "agents"
        AgentsRefreshReason.Reconnect -> "after reconnect"
        AgentsRefreshReason.AgentsChanged -> "agent changes"
        AgentsRefreshReason.ResyncRequired -> "state"
        AgentsRefreshReason.ActionCompleted -> "after action"
    }

private fun connectionLabel(connection: AgentConnectionState): String {
    return when (connection) {
        AgentConnectionState.Stopped -> "Stopped"
        AgentConnectionState.Connecting -> "Connecting"
        is AgentConnectionState.Reconnecting -> "Reconnecting"
        AgentConnectionState.Connected -> "Connected"
        is AgentConnectionState.Offline -> "Offline"
        AgentConnectionState.LoggedOut -> "Auth expired"
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${remainingSeconds}s"
    }
}

@Preview(showBackground = true)
@Composable
private fun AgentsScreenPreview() {
    SaseMobileTheme {
        AgentsScreen(
            state = AgentsState(
                agents = listOf(PreviewRunningAgent, PreviewFailedAgent),
                resumeOptions = listOf(
                    MobileAgentResumeOptionWire(
                        id = "resume-mobile-demo",
                        agentName = "mobile-demo",
                        kind = MobileAgentResumeOptionKindWire.Resume,
                        label = "Resume mobile-demo",
                        promptText = "#resume:mobile-demo",
                        directLaunchSupported = true,
                    ),
                ),
                totalCount = 2,
                connection = AgentConnectionState.Connected,
            ),
            onRefresh = {},
            onKill = {},
            onRetry = {},
            onClearActionResult = {},
            onOpenSettings = {},
        )
    }
}

private val PreviewRunningAgent = MobileAgentSummaryWire(
    name = "mobile-demo",
    project = "sase",
    status = "running",
    pid = 12345,
    model = "gpt-5.4",
    provider = "openai",
    workspaceNumber = 101,
    startedAt = "2026-05-06T18:00:00Z",
    durationSeconds = 84,
    promptSnippet = "Implement mobile launch",
    hasArtifactDir = true,
    retryLineage = MobileAgentRetryLineageWire(retryAttempt = 0),
    actions = MobileAgentActionAffordancesWire(
        canResume = true,
        canWait = true,
        canKill = true,
        canRetry = false,
    ),
    display = MobileAgentDisplayLabelsWire(
        title = "mobile-demo",
        subtitle = "sase - running",
        statusLabel = "RUNNING",
    ),
)

private val PreviewFailedAgent = MobileAgentSummaryWire(
    name = "mobile-failed",
    project = "sase",
    status = "failed",
    pid = null,
    model = null,
    provider = null,
    workspaceNumber = 102,
    startedAt = "2026-05-06T17:55:00Z",
    durationSeconds = 31,
    promptSnippet = "Retry mobile action",
    hasArtifactDir = true,
    retryLineage = MobileAgentRetryLineageWire(
        retryOfTimestamp = "20260506_175500",
        retryChainRootTimestamp = "20260506_175500",
        retryAttempt = 1,
        parentAgentName = "mobile-original",
    ),
    actions = MobileAgentActionAffordancesWire(
        canResume = true,
        canWait = false,
        canKill = false,
        canRetry = true,
    ),
    display = MobileAgentDisplayLabelsWire(
        title = "mobile-failed",
        subtitle = "retry available",
        statusLabel = "FAILED",
    ),
)
