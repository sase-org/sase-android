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
import androidx.compose.material3.Switch
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
import org.sase.mobile.data.notifications.foreground.ForegroundConnectedModeUiState
import org.sase.mobile.data.notifications.local.NotificationPermissionState
import org.sase.mobile.data.session.ManualPairingRequest
import org.sase.mobile.data.session.SessionController
import org.sase.mobile.data.session.SessionStatus

@Composable
fun SettingsScreen(
    controller: SessionController,
    modifier: Modifier = Modifier,
    notificationPermissionState: NotificationPermissionState? = null,
    foregroundConnectedModeState: ForegroundConnectedModeUiState? = null,
    onStartForegroundConnectedMode: () -> Unit = {},
    onStopForegroundConnectedMode: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onRenderTestNotification: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
    onOpenHelpers: () -> Unit = {},
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
            onOpenUpdate = onOpenUpdate,
            onOpenHelpers = onOpenHelpers,
        )
        if (notificationPermissionState != null) {
            NotificationSettingsCard(
                state = notificationPermissionState,
                onRequest = onRequestNotificationPermission,
                onOpenSettings = onOpenNotificationSettings,
                onRenderTestNotification = onRenderTestNotification,
            )
        }
        if (foregroundConnectedModeState != null) {
            ForegroundConnectedModeCard(
                state = foregroundConnectedModeState,
                onStart = onStartForegroundConnectedMode,
                onStop = onStopForegroundConnectedMode,
            )
        }
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
private fun ForegroundConnectedModeCard(
    state: ForegroundConnectedModeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("foreground_connected_mode_card"),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Foreground connected mode", style = MaterialTheme.typography.titleMedium)
            Text(foregroundModeLabel(state))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.hostLabel ?: "No paired host")
                    Text("Connection: ${state.connectionLabel}")
                    Text("Last refresh: ${state.lastRefreshAt ?: "Never"}")
                }
                Switch(
                    modifier = Modifier.testTag("foreground_connected_mode_switch"),
                    checked = state.enabled,
                    enabled = state.canStart || state.enabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onStart()
                        } else {
                            onStop()
                        }
                    },
                )
            }
            if (state.enabled) {
                OutlinedButton(
                    modifier = Modifier.testTag("stop_foreground_connected_mode_button"),
                    onClick = onStop,
                ) {
                    Text("Stop")
                }
            } else {
                Button(
                    modifier = Modifier.testTag("start_foreground_connected_mode_button"),
                    enabled = state.canStart,
                    onClick = onStart,
                ) {
                    Text("Keep connected")
                }
            }
        }
    }
}

private fun foregroundModeLabel(state: ForegroundConnectedModeUiState): String {
    return if (state.enabled) {
        "Android is keeping an active foreground notification for the paired gateway."
    } else if (state.canStart) {
        "Off. Turn on to keep the gateway event stream active until stopped."
    } else {
        "Pair a host before enabling foreground connected mode."
    }
}

@Composable
private fun NotificationSettingsCard(
    state: NotificationPermissionState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onRenderTestNotification: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_settings_card"),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Mobile notifications", style = MaterialTheme.typography.titleMedium)
            Text(notificationPermissionLabel(state))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    NotificationPermissionState.NotRequired,
                    NotificationPermissionState.Allowed,
                    -> {
                        OutlinedButton(
                            modifier = Modifier.testTag("send_test_notification_button"),
                            onClick = onRenderTestNotification,
                        ) {
                            Text("Send test hint")
                        }
                    }

                    NotificationPermissionState.DeniedCanAsk -> {
                        Button(
                            modifier = Modifier.testTag("request_notifications_button"),
                            onClick = onRequest,
                        ) {
                            Text("Allow notifications")
                        }
                    }

                    NotificationPermissionState.DeniedNeedsSettings -> {
                        OutlinedButton(
                            modifier = Modifier.testTag("open_notification_settings_button"),
                            onClick = onOpenSettings,
                        ) {
                            Text("Open system settings")
                        }
                    }
                }
            }
        }
    }
}

private fun notificationPermissionLabel(state: NotificationPermissionState): String {
    return when (state) {
        NotificationPermissionState.NotRequired -> "Notifications are available on this Android version."
        NotificationPermissionState.Allowed -> "Notifications are allowed for safe SASE hints."
        NotificationPermissionState.DeniedCanAsk -> "Background notification delivery is inactive until permission is allowed."
        NotificationPermissionState.DeniedNeedsSettings ->
            "Notifications are blocked. Enable them in Android app settings to receive background hints."
    }
}

@Composable
private fun SessionStatusCard(
    status: SessionStatus,
    onRefresh: () -> Unit,
    onForget: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenHelpers: () -> Unit,
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
                OutlinedButton(
                    modifier = Modifier.testTag("forget_host_button"),
                    onClick = onForget,
                ) {
                    Text("Forget host")
                }
                OutlinedButton(
                    modifier = Modifier.testTag("open_update_button"),
                    onClick = onOpenUpdate,
                ) {
                    Text("SASE update")
                }
                OutlinedButton(
                    modifier = Modifier.testTag("open_helpers_button"),
                    onClick = onOpenHelpers,
                ) {
                    Text("Workflow helpers")
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
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gateway_url_input"),
            value = hostUrl,
            onValueChange = onHostUrlChange,
            label = { Text("Gateway URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("host_label_input"),
            value = hostLabel,
            onValueChange = onHostLabelChange,
            label = { Text("Host label") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pairing_id_input"),
            value = pairingId,
            onValueChange = onPairingIdChange,
            label = { Text("Pairing ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pairing_code_input"),
            value = pairingCode,
            onValueChange = onPairingCodeChange,
            label = { Text("One-time code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("device_name_input"),
            value = deviceName,
            onValueChange = onDeviceNameChange,
            label = { Text("Device display name") },
            singleLine = true,
        )
        Button(
            modifier = Modifier.testTag("pair_host_button"),
            onClick = onPair,
        ) {
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
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_payload_input"),
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
