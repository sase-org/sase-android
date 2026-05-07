package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.sase.mobile.data.notifications.foreground.ForegroundConnectedModeUiState
import org.sase.mobile.data.notifications.local.NotificationPermissionState
import org.sase.mobile.data.session.ManualPairingRequest
import org.sase.mobile.data.session.PairingResult
import org.sase.mobile.data.session.SessionController
import org.sase.mobile.data.session.SessionStatus
import org.sase.mobile.data.session.SessionUiState
import org.sase.mobile.ui.settings.SettingsScreen
import org.sase.mobile.ui.theme.SaseMobileTheme

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsPairingAndHostManagementControls() {
        composeRule.setContent {
            SaseMobileTheme {
                SettingsScreen(controller = FakeSessionController())
            }
        }

        composeRule.onNodeWithText("No gateway paired").assertIsDisplayed()
        composeRule.onNodeWithText("Pair host").assertIsDisplayed()
        composeRule.onNodeWithText("Scan QR").assertIsDisplayed()
        composeRule.onNodeWithText("Forget host").assertIsDisplayed()
    }

    @Test
    fun showsNotificationPermissionControls() {
        composeRule.setContent {
            SaseMobileTheme {
                SettingsScreen(
                    controller = FakeSessionController(),
                    notificationPermissionState = NotificationPermissionState.DeniedCanAsk,
                )
            }
        }

        composeRule.onNodeWithText("Mobile notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Allow notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Background notification delivery is inactive until permission is allowed.")
            .assertIsDisplayed()
    }

    @Test
    fun showsForegroundConnectedModeControls() {
        composeRule.setContent {
            SaseMobileTheme {
                SettingsScreen(
                    controller = FakeSessionController(),
                    foregroundConnectedModeState = ForegroundConnectedModeUiState(
                        enabled = false,
                        canStart = true,
                        hostLabel = "workstation",
                        connectionLabel = "Stopped",
                        lastRefreshAt = null,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Foreground connected mode").assertIsDisplayed()
        composeRule.onNodeWithText("Keep connected").assertIsDisplayed()
        composeRule.onNodeWithText("Connection: Stopped").assertIsDisplayed()
    }
}

private class FakeSessionController : SessionController {
    override val state: StateFlow<SessionUiState> = MutableStateFlow(
        SessionUiState(SessionStatus.Unpaired),
    )
    override val defaultDeviceDisplayName: String = "Pixel 9"

    override suspend fun pairManually(request: ManualPairingRequest): PairingResult {
        return PairingResult.Failure("not implemented")
    }

    override suspend fun pairWithQr(payload: String, deviceDisplayName: String): PairingResult {
        return PairingResult.Failure("not implemented")
    }

    override suspend fun refreshSession() = Unit

    override suspend fun forgetHost() = Unit
}
