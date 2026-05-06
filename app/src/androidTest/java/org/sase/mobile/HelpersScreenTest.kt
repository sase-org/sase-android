package org.sase.mobile

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.sase.mobile.data.api.dto.MobileBeadDetailWire
import org.sase.mobile.data.api.dto.MobileBeadListResponseWire
import org.sase.mobile.data.api.dto.MobileBeadSummaryWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagEntryWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListResponseWire
import org.sase.mobile.data.api.dto.MobileHelperProjectContextWire
import org.sase.mobile.data.api.dto.MobileHelperProjectScopeWire
import org.sase.mobile.data.api.dto.MobileHelperResultWire
import org.sase.mobile.data.api.dto.MobileHelperSkippedWire
import org.sase.mobile.data.api.dto.MobileHelperStatusWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogEntryWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogResponseWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogStatsWire
import org.sase.mobile.ui.helpers.HelperPaneState
import org.sase.mobile.ui.helpers.HelpersScreen
import org.sase.mobile.ui.helpers.HelpersScreenState
import org.sase.mobile.ui.theme.SaseMobileTheme

class HelpersScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersChangespecHelpersAndReturnsInsertValue() {
        var inserted: String? = null
        composeRule.setContent {
            SaseMobileTheme {
                HelpersScreen(
                    initialState = helperState(),
                    onInsertHelper = { inserted = it },
                )
            }
        }

        composeRule.onNodeWithTag("helpers_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Partial success").assertIsDisplayed()
        composeRule.onNodeWithText("Warning: one project was skipped").assertIsDisplayed()
        composeRule.onNodeWithText("#gh:mobile-helper").assertIsDisplayed()

        composeRule.onAllNodesWithText("Insert")[0].performClick()

        assertEquals("#gh:mobile-helper", inserted)
    }

    @Test
    fun filtersXpromptsAndRendersBeadContext() {
        composeRule.setContent {
            SaseMobileTheme {
                HelpersScreen(initialState = helperState())
            }
        }

        composeRule.onNodeWithText("Xprompts").performClick()
        composeRule.onNodeWithText("bd/work_phase_bead").assertIsDisplayed()
        composeRule.onNodeWithTag("helper_search_input").performTextInput("missing")
        composeRule.onNodeWithText("bd/work_phase_bead").assertDoesNotExist()

        composeRule.onNodeWithText("Beads").performClick()
        composeRule.onNodeWithText("Workflow Helper Screens And Pickers").assertIsDisplayed()
        composeRule.onNodeWithText("sase-26.6.7").assertIsDisplayed()
    }

    private fun helperState(): HelpersScreenState {
        val result = MobileHelperResultWire(
            status = MobileHelperStatusWire.PartialSuccess,
            message = "loaded helpers",
            warnings = listOf("one project was skipped"),
            skipped = listOf(MobileHelperSkippedWire(target = "sase/skipped", reason = "missing metadata")),
            partialFailureCount = 1,
        )
        val context = MobileHelperProjectContextWire("sase", MobileHelperProjectScopeWire.Explicit)
        val bead = MobileBeadSummaryWire(
            id = "sase-26.6.7",
            title = "Workflow Helper Screens And Pickers",
            status = "in_progress",
            beadType = "phase",
            tier = null,
            project = "sase",
            parentId = "sase-26.6",
            assignee = "sase-26.6.7",
            updatedAt = "2026-05-06T22:06:04Z",
            dependencyCount = 1,
            blockCount = 1,
            childCount = 0,
            planPathDisplay = null,
            changespecName = null,
            changespecStatus = null,
        )
        return HelpersScreenState(
            changespecTags = HelperPaneState.Ready(
                MobileChangeSpecTagListResponseWire(
                    schemaVersion = 1,
                    result = result,
                    context = context,
                    tags = listOf(
                        MobileChangeSpecTagEntryWire(
                            tag = "#gh:mobile-helper",
                            project = "sase",
                            changespec = "mobile-helper",
                            title = "Mobile helper UI",
                            status = "WIP",
                            workflow = "gh",
                            sourcePathDisplay = ".gp/sase.gp",
                        ),
                    ),
                    totalCount = 1,
                ),
            ),
            xpromptCatalog = HelperPaneState.Ready(
                MobileXpromptCatalogResponseWire(
                    schemaVersion = 1,
                    result = result,
                    context = context,
                    entries = listOf(
                        MobileXpromptCatalogEntryWire(
                            name = "bd/work_phase_bead",
                            displayLabel = "bd/work_phase_bead",
                            description = "Work a claimed phase bead",
                            sourceBucket = "project",
                            project = "sase",
                            tags = listOf("bead", "mobile"),
                            inputSignature = "bead_id",
                            isSkill = false,
                            contentPreview = "Read the bead and complete the phase.",
                            sourcePathDisplay = "xprompts/bd/work_phase_bead.md",
                        ),
                    ),
                    stats = MobileXpromptCatalogStatsWire(1, 1, 0, true),
                ),
            ),
            beadList = HelperPaneState.Ready(
                MobileBeadListResponseWire(
                    schemaVersion = 1,
                    result = result,
                    context = context,
                    beads = listOf(bead),
                    totalCount = 1,
                ),
            ),
            beadDetail = HelperPaneState.Ready(
                MobileBeadDetailWire(
                    summary = bead,
                    description = "Expose native helper flows.",
                    notes = null,
                    designPathDisplay = "sdd/epics/202605/mobile_gateway_epic_6.md",
                    dependencies = listOf("sase-26.6.1"),
                    blocks = listOf("sase-26.6.9"),
                    children = emptyList(),
                    workspaceDisplay = "sase-android",
                ),
            ),
        )
    }
}
