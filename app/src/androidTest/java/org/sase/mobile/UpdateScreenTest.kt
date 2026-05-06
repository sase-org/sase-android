package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.sase.mobile.data.api.dto.HelpersChangedEventPayloadWire
import org.sase.mobile.data.api.dto.MobileHelperResultWire
import org.sase.mobile.data.api.dto.MobileHelperStatusWire
import org.sase.mobile.data.api.dto.MobileUpdateJobStatusWire
import org.sase.mobile.data.api.dto.MobileUpdateJobWire
import org.sase.mobile.data.helpers.UpdateController
import org.sase.mobile.data.helpers.UpdateUiState
import org.sase.mobile.ui.theme.SaseMobileTheme
import org.sase.mobile.ui.update.UpdateScreen

class UpdateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFailedUpdateStateFromStructuredFields() {
        val controller = FakeUpdateController(
            UpdateUiState(
                job = MobileUpdateJobWire(
                    jobId = "update-job-2",
                    status = MobileUpdateJobStatusWire.Failed,
                    startedAt = "2026-05-06T18:05:00Z",
                    finishedAt = "2026-05-06T18:06:00Z",
                    message = "update command failed",
                    logPathDisplay = "logs/update-job-2.log",
                ),
                helperResult = MobileHelperResultWire(
                    status = MobileHelperStatusWire.Failed,
                    message = "update failed",
                    warnings = listOf("exit code 1"),
                    skipped = emptyList(),
                ),
            ),
        )

        composeRule.setContent {
            SaseMobileTheme {
                UpdateScreen(
                    controller = controller,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("update_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Update failed").assertIsDisplayed()
        composeRule.onNodeWithText("Status: Failed").assertIsDisplayed()
        composeRule.onNodeWithText("update command failed").assertIsDisplayed()
        composeRule.onNodeWithText("Warning: exit code 1").assertIsDisplayed()
        composeRule.onNodeWithTag("copy_update_log_button").assertIsDisplayed()
    }

    private class FakeUpdateController(
        initialState: UpdateUiState,
    ) : UpdateController {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<UpdateUiState> = mutableState

        override suspend fun loadCachedState() = Unit
        override suspend fun refreshCurrentJob() = Unit
        override fun startUpdateAndPoll() = Unit
        override fun cancelPolling() = Unit
        override suspend fun handleHelpersChanged(payload: HelpersChangedEventPayloadWire) = Unit
    }
}
