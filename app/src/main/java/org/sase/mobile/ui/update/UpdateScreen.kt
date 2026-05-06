package org.sase.mobile.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.sase.mobile.data.api.dto.MobileHelperResultWire
import org.sase.mobile.data.api.dto.MobileUpdateJobStatusWire
import org.sase.mobile.data.api.dto.MobileUpdateJobWire
import org.sase.mobile.data.helpers.UpdateController
import org.sase.mobile.data.helpers.UpdateError
import org.sase.mobile.data.helpers.UpdateStatus
import org.sase.mobile.data.helpers.UpdateUiState
import org.sase.mobile.data.helpers.isTerminal

@Composable
fun UpdateScreen(
    controller: UpdateController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmStart by remember { mutableStateOf(false) }

    LaunchedEffect(controller) {
        controller.loadCachedState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("update_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("SASE update", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }

        UpdateStatusCard(state = state)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !state.isBusy,
                onClick = { confirmStart = true },
                modifier = Modifier.testTag("start_update_button"),
            ) {
                Text("Start update")
            }
            OutlinedButton(
                enabled = state.job != null && !state.isBusy,
                onClick = { scope.launch { controller.refreshCurrentJob() } },
                modifier = Modifier.testTag("refresh_update_button"),
            ) {
                Text("Refresh")
            }
            if (state.isBusy) {
                OutlinedButton(
                    onClick = controller::cancelPolling,
                    modifier = Modifier.testTag("cancel_update_polling_button"),
                ) {
                    Text("Stop polling")
                }
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start SASE update?") },
            text = { Text("The paired host will run its configured SASE update command.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmStart = false
                        controller.startUpdateAndPoll()
                    },
                    modifier = Modifier.testTag("confirm_start_update_button"),
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun UpdateStatusCard(
    state: UpdateUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .testTag("update_status_card"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.title(),
                style = MaterialTheme.typography.titleMedium,
            )
            state.error?.let { error ->
                Text(
                    text = error.displayMessage(),
                    modifier = Modifier.testTag("update_error_message"),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.job?.let { job ->
                JobRows(job)
            } ?: Text("No update job has been started from this device.")
            state.helperResult?.let { result ->
                HelperResultRows(result)
            }
        }
    }
}

@Composable
private fun JobRows(job: MobileUpdateJobWire) {
    val clipboard = LocalClipboardManager.current
    Text("Job: ${job.jobId}")
    Text("Status: ${job.status.displayName()}")
    job.startedAt?.let { Text("Started: $it") }
    job.finishedAt?.let { Text("Finished: $it") }
    job.message?.let { Text(it, modifier = Modifier.testTag("update_job_message")) }
    job.logPathDisplay?.let { path ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Log: $path", modifier = Modifier.weight(1f))
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(path)) },
                modifier = Modifier.testTag("copy_update_log_button"),
            ) {
                Text("Copy")
            }
        }
    }
    job.completionPathDisplay?.let { Text("Completion: $it") }
}

@Composable
private fun HelperResultRows(result: MobileHelperResultWire) {
    result.message?.let { Text(it) }
    result.warnings.forEach { warning ->
        Text(
            text = "Warning: $warning",
            color = MaterialTheme.colorScheme.error,
        )
    }
    result.skipped.forEach { skipped ->
        Text("Skipped: ${skipped.target ?: "update"} - ${skipped.reason}")
    }
}

private fun UpdateUiState.title(): String {
    return when {
        status == UpdateStatus.Starting -> "Starting update"
        status == UpdateStatus.Polling -> "Polling update status"
        job?.status == MobileUpdateJobStatusWire.Succeeded -> "Update succeeded"
        job?.status == MobileUpdateJobStatusWire.Failed -> "Update failed"
        job?.status?.isTerminal() == false -> "Update in progress"
        else -> "Update status"
    }
}

private fun MobileUpdateJobStatusWire.displayName(): String {
    return when (this) {
        MobileUpdateJobStatusWire.Queued -> "Queued"
        MobileUpdateJobStatusWire.Running -> "Running"
        MobileUpdateJobStatusWire.Succeeded -> "Succeeded"
        MobileUpdateJobStatusWire.Failed -> "Failed"
    }
}

private fun UpdateError.displayMessage(): String {
    return when (this) {
        UpdateError.NotPaired -> "Pair a host before starting an update."
        UpdateError.AuthExpired -> "Authentication expired. Re-pair or refresh the session in Settings."
        UpdateError.AlreadyRunning -> "An update is already running on the host."
        UpdateError.JobNotFound -> "The update job was not found on the host."
        UpdateError.LaunchFailed -> "The host could not start the update command."
        UpdateError.BridgeUnavailable -> "The host bridge is unavailable."
        UpdateError.Disconnected -> "The gateway is disconnected. Check the host and try again."
        is UpdateError.Unexpected -> message
    }
}
