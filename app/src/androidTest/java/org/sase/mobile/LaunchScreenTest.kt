package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.sase.mobile.data.agents.AgentActionState
import org.sase.mobile.data.agents.AgentsState
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileBeadSummaryWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagEntryWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogEntryWire
import org.sase.mobile.ui.launch.LaunchHelperState
import org.sase.mobile.ui.launch.LaunchScreen
import org.sase.mobile.ui.theme.SaseMobileTheme

class LaunchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun insertsHelpersIntoRawPromptEditor() {
        composeRule.setContent {
            SaseMobileTheme {
                LaunchScreen(
                    state = AgentsState(),
                    onLaunch = { AgentActionState.Succeeded("launched") },
                    onOpenAgents = {},
                    onOpenSettings = {},
                    initialHelperState = helperState(),
                    requestIdFactory = { "android-test" },
                )
            }
        }

        composeRule.onNodeWithTag("launch_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("launch_prompt_input").performTextInput("%model gpt-5.4\nKeep 100% ")
        composeRule.onNodeWithText("#gh:mobile-helper").performClick()
        composeRule.onNodeWithText("#bd/work_phase_bead").performClick()
        composeRule.onNodeWithText("sase-26.6.5").performClick()

        composeRule.onNodeWithTag("launch_prompt_input")
            .assertTextContains("%model gpt-5.4\nKeep 100% #gh:mobile-helper#bd/work_phase_bead#bd/work_phase_bead:sase-26.6.5")
    }

    @Test
    fun submitsRawPromptAndKeepsPromptAfterFailedLaunch() {
        var captured: MobileAgentTextLaunchRequestWire? = null
        composeRule.setContent {
            SaseMobileTheme {
                LaunchScreen(
                    state = AgentsState(),
                    onLaunch = {
                        captured = it
                        AgentActionState.Failed("launch failed")
                    },
                    onOpenAgents = {},
                    onOpenSettings = {},
                    initialHelperState = helperState(),
                    requestIdFactory = { "android-test" },
                )
            }
        }

        composeRule.onNodeWithTag("launch_prompt_input")
            .performTextInput("#gh:mobile\n%runtime codex\nKeep 100%")
        composeRule.onNodeWithTag("launch_submit").performClick()
        composeRule.waitForIdle()

        org.junit.Assert.assertEquals("#gh:mobile\n%runtime codex\nKeep 100%", captured?.prompt)
        org.junit.Assert.assertEquals("android-test", captured?.requestId)
        composeRule.onNodeWithTag("launch_prompt_input")
            .assertTextContains("#gh:mobile\n%runtime codex\nKeep 100%")
    }

    private fun helperState(): LaunchHelperState {
        return LaunchHelperState(
            changespecs = listOf(
                MobileChangeSpecTagEntryWire(
                    tag = "#gh:mobile-helper",
                    project = "sase",
                    changespec = "mobile-helper",
                    title = "Mobile helper",
                    status = "WIP",
                ),
            ),
            xprompts = listOf(
                MobileXpromptCatalogEntryWire(
                    name = "bd/work_phase_bead",
                    displayLabel = "bd/work_phase_bead",
                    sourceBucket = "project",
                    project = "sase",
                    tags = listOf("bead"),
                    isSkill = false,
                ),
            ),
            beads = listOf(
                MobileBeadSummaryWire(
                    id = "sase-26.6.5",
                    title = "Text launch",
                    status = "in_progress",
                    beadType = "phase",
                    tier = null,
                    project = "sase",
                    dependencyCount = 1,
                    blockCount = 0,
                    childCount = 0,
                ),
            ),
            projects = listOf("sase"),
        )
    }
}
