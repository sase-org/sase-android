package org.sase.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.sase.mobile.data.session.ManualPairingRequest
import org.sase.mobile.data.session.SessionController
import org.sase.mobile.data.session.SessionStatus

@Composable
fun SettingsScreen(
    controller: SessionController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var hostUrl by remember { mutableStateOf("") }
    var hostLabel by remember { mutableStateOf("") }
    var pairingId by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(controller.defaultDeviceDisplayName) }
    var qrPayload by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
        )
        SessionStatusCard(
            status = state.status,
            onRefresh = {
                scope.launch { controller.refreshSession() }
            },
            onForget = {
                scope.launch { controller.forgetHost() }
            },
        )
        ManualPairingFields(
            hostUrl = hostUrl,
            onHostUrlChange = { hostUrl = it },
            hostLabel = hostLabel,
            onHostLabelChange = { hostLabel = it },
            pairingId = pairingId,
            onPairingIdChange = { pairingId = it },
            pairingCode = pairingCode,
            onPairingCodeChange = { pairingCode = it },
            deviceName = deviceName,
            onDeviceNameChange = { deviceName = it },
            onPair = {
                scope.launch {
                    controller.pairManually(
                        ManualPairingRequest(
                            baseUrl = hostUrl,
                            pairingId = pairingId,
                            code = pairingCode,
                            hostLabel = hostLabel,
                            deviceDisplayName = deviceName,
                        ),
                    )
                }
            },
        )
        QrPairingFields(
            qrPayload = qrPayload,
            onQrPayloadChange = { qrPayload = it },
            onPairQr = {
                scope.launch {
                    controller.pairWithQr(qrPayload, deviceName)
                }
            },
            onScanQr = { showScanner = true },
        )
    }

    if (showScanner) {
        QrScannerDialog(
            onPayloadScanned = { payload ->
                qrPayload = payload
                showScanner = false
                scope.launch { controller.pairWithQr(payload, deviceName) }
            },
            onDismiss = { showScanner = false },
        )
    }
}

@Composable
private fun SessionStatusCard(
    status: SessionStatus,
    onRefresh: () -> Unit,
    onForget: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                SessionStatus.Loading -> Text("Loading paired host")
                SessionStatus.Unpaired -> Text("No gateway paired", style = MaterialTheme.typography.titleMedium)
                SessionStatus.Pairing -> Text("Pairing host", style = MaterialTheme.typography.titleMedium)
                SessionStatus.Checking -> Text("Checking session", style = MaterialTheme.typography.titleMedium)
                is SessionStatus.Paired -> {
                    Text("Paired host", style = MaterialTheme.typography.titleMedium)
                    Text("Host: ${status.session.baseUrl}")
                    Text("Device: ${status.session.deviceDisplayName}")
                    Text("Last sync: ${status.session.lastSessionCheckedAt ?: "Never"}")
                }

                is SessionStatus.AuthExpired -> {
                    Text("Authentication expired", style = MaterialTheme.typography.titleMedium)
                    Text(status.message)
                    Text("Host: ${status.session.baseUrl}")
                }

                is SessionStatus.GatewayUnavailable -> {
                    Text("Gateway unavailable", style = MaterialTheme.typography.titleMedium)
                    Text(status.message)
                    status.session?.let { Text("Host: ${it.baseUrl}") }
                }

                is SessionStatus.PairingFailed -> {
                    Text("Pairing failed", style = MaterialTheme.typography.titleMedium)
                    Text(status.message)
                }

                is SessionStatus.InvalidInput -> {
                    Text("Invalid pairing input", style = MaterialTheme.typography.titleMedium)
                    Text(status.message)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh) {
                    Text("Check session")
                }
                OutlinedButton(onClick = onForget) {
                    Text("Forget host")
                }
            }
        }
    }
}

@Composable
private fun ManualPairingFields(
    hostUrl: String,
    onHostUrlChange: (String) -> Unit,
    hostLabel: String,
    onHostLabelChange: (String) -> Unit,
    pairingId: String,
    onPairingIdChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    onPair: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Manual pairing", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = hostUrl,
            onValueChange = onHostUrlChange,
            label = { Text("Gateway URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = hostLabel,
            onValueChange = onHostLabelChange,
            label = { Text("Host label") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = pairingId,
            onValueChange = onPairingIdChange,
            label = { Text("Pairing ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = pairingCode,
            onValueChange = onPairingCodeChange,
            label = { Text("One-time code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = deviceName,
            onValueChange = onDeviceNameChange,
            label = { Text("Device display name") },
            singleLine = true,
        )
        Button(onClick = onPair) {
            Text("Pair host")
        }
    }
}

@Composable
private fun QrPairingFields(
    qrPayload: String,
    onQrPayloadChange: (String) -> Unit,
    onPairQr: () -> Unit,
    onScanQr: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("QR pairing", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = qrPayload,
            onValueChange = onQrPayloadChange,
            label = { Text("QR payload") },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScanQr) {
                Text("Scan QR")
            }
            OutlinedButton(onClick = onPairQr) {
                Text("Pair from payload")
            }
        }
    }
}
