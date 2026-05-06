package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.sase.mobile.data.agents.AgentConnectionState
import org.sase.mobile.data.agents.AgentFailure
import org.sase.mobile.data.agents.AgentFailureKind
import org.sase.mobile.data.agents.AgentsState
import org.sase.mobile.data.api.dto.MobileAgentActionAffordancesWire
import org.sase.mobile.data.api.dto.MobileAgentDisplayLabelsWire
import org.sase.mobile.data.api.dto.MobileAgentResumeOptionKindWire
import org.sase.mobile.data.api.dto.MobileAgentResumeOptionWire
import org.sase.mobile.data.api.dto.MobileAgentRetryLineageWire
import org.sase.mobile.data.api.dto.MobileAgentSummaryWire
import org.sase.mobile.ui.agents.AgentsScreen
import org.sase.mobile.ui.theme.SaseMobileTheme

class AgentsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersAgentsAndResumeOptions() {
        composeRule.setContent {
            SaseMobileTheme {
                AgentsScreen(
                    state = mixedState(),
                    onRefresh = {},
                    onKill = {},
                    onRetry = {},
                    onClearActionResult = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("agents_screen").assertIsDisplayed()
        composeRule.onNodeWithText("mobile-demo").assertIsDisplayed()
        composeRule.onNodeWithText("Resume mobile-demo").assertIsDisplayed()
        composeRule.onNodeWithText("gpt-5.4").assertIsDisplayed()
    }

    @Test
    fun opensLaunchFromDirectResumeOption() {
        var launchPrompt: String? = null
        composeRule.setContent {
            SaseMobileTheme {
                AgentsScreen(
                    state = mixedState(),
                    onRefresh = {},
                    onKill = {},
                    onRetry = {},
                    onClearActionResult = {},
                    onOpenSettings = {},
                    onOpenLaunchPrompt = { launchPrompt = it },
                )
            }
        }

        composeRule.onNodeWithText("Launch").performClick()

        assertEquals("#resume:mobile-demo", launchPrompt)
    }

    @Test
    fun confirmsKillAndRetryActions() {
        var killed: String? = null
        var retried: String? = null
        composeRule.setContent {
            SaseMobileTheme {
                AgentsScreen(
                    state = mixedState(),
                    onRefresh = {},
                    onKill = { killed = it },
                    onRetry = { retried = it },
                    onClearActionResult = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("kill_mobile-demo").performClick()
        composeRule.onNodeWithText("Kill agent").assertIsDisplayed()
        composeRule.onNodeWithText("Kill").performClick()
        assertEquals("mobile-demo", killed)

        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText("Retry agent").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals("mobile-failed", retried)
    }

    @Test
    fun rendersOfflineAndAuthRecoveryStates() {
        composeRule.setContent {
            SaseMobileTheme {
                AgentsScreen(
                    state = AgentsState(
                        connection = AgentConnectionState.Offline("connection refused"),
                        failure = AgentFailure(AgentFailureKind.Offline, "connection refused"),
                    ),
                    onRefresh = {},
                    onKill = {},
                    onRetry = {},
                    onClearActionResult = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Host offline").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()

        composeRule.setContent {
            SaseMobileTheme {
                AgentsScreen(
                    state = AgentsState(
                        connection = AgentConnectionState.LoggedOut,
                        failure = AgentFailure(AgentFailureKind.AuthExpired, "token expired"),
                    ),
                    onRefresh = {},
                    onKill = {},
                    onRetry = {},
                    onClearActionResult = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Authentication expired").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    private fun mixedState(): AgentsState {
        return AgentsState(
            agents = listOf(runningAgent(), failedAgent()),
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
        )
    }

    private fun runningAgent() = MobileAgentSummaryWire(
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

    private fun failedAgent() = MobileAgentSummaryWire(
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
        retryLineage = MobileAgentRetryLineageWire(retryAttempt = 1),
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
}
