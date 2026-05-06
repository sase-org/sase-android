package org.sase.mobile

import android.net.Uri
import androidx.compose.ui.test.assertDoesNotExist
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
import org.sase.mobile.data.api.dto.MobileAgentImageLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileBeadSummaryWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagEntryWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogEntryWire
import org.sase.mobile.ui.launch.ImageAttachmentError
import org.sase.mobile.ui.launch.ImageAttachmentLoadResult
import org.sase.mobile.ui.launch.ImageAttachmentMetadata
import org.sase.mobile.ui.launch.ImageAttachmentPayload
import org.sase.mobile.ui.launch.ImageAttachmentPayloadResult
import org.sase.mobile.ui.launch.ImageAttachmentReader
import org.sase.mobile.ui.launch.ImageAttachmentSource
import org.sase.mobile.ui.launch.LaunchHelperState
import org.sase.mobile.ui.launch.LaunchScreen
import org.sase.mobile.ui.launch.SelectedImageAttachment
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

    @Test
    fun launchesSelectedImageWithMetadataAndBase64Payload() {
        var captured: MobileAgentImageLaunchRequestWire? = null
        val imageUri = Uri.parse("content://images/screen")
        val metadata = ImageAttachmentMetadata(
            displayName = "screen.png",
            contentType = "image/png",
            byteLength = 6,
        )
        composeRule.setContent {
            SaseMobileTheme {
                LaunchScreen(
                    state = AgentsState(),
                    onLaunch = { AgentActionState.Succeeded("text launch") },
                    onLaunchImage = {
                        captured = it
                        AgentActionState.Succeeded("image launch")
                    },
                    onOpenAgents = {},
                    onOpenSettings = {},
                    initialImageAttachment = SelectedImageAttachment(
                        uri = imageUri,
                        metadata = metadata,
                        source = ImageAttachmentSource.Gallery,
                    ),
                    imageAttachmentReader = FakeImageAttachmentReader(metadata),
                    requestIdFactory = { "android-image-test" },
                )
            }
        }

        composeRule.onNodeWithTag("launch_prompt_input").performTextInput("Review this screenshot")
        composeRule.onNodeWithTag("launch_image_summary").assertTextContains("screen.png - image/png - 6 bytes")
        composeRule.onNodeWithTag("launch_image_submit").performClick()
        composeRule.waitForIdle()

        org.junit.Assert.assertEquals("Review this screenshot", captured?.prompt)
        org.junit.Assert.assertEquals("android-image-test", captured?.requestId)
        org.junit.Assert.assertEquals("screen.png", captured?.originalFilename)
        org.junit.Assert.assertEquals("image/png", captured?.contentType)
        org.junit.Assert.assertEquals(6L, captured?.byteLength)
        org.junit.Assert.assertEquals("cGl4ZWxz", captured?.base64Image)
    }

    @Test
    fun clearsSelectedImageAndKeepsPromptAfterUploadFailure() {
        val imageUri = Uri.parse("content://images/screen")
        val metadata = ImageAttachmentMetadata(
            displayName = "screen.png",
            contentType = "image/png",
            byteLength = 6,
        )
        composeRule.setContent {
            SaseMobileTheme {
                LaunchScreen(
                    state = AgentsState(),
                    onLaunch = { AgentActionState.Succeeded("text launch") },
                    onLaunchImage = { AgentActionState.Failed("upload failed") },
                    onOpenAgents = {},
                    onOpenSettings = {},
                    initialImageAttachment = SelectedImageAttachment(
                        uri = imageUri,
                        metadata = metadata,
                        source = ImageAttachmentSource.Gallery,
                    ),
                    imageAttachmentReader = FakeImageAttachmentReader(metadata),
                    requestIdFactory = { "android-image-test" },
                )
            }
        }

        composeRule.onNodeWithTag("launch_prompt_input").performTextInput("Review this screenshot")
        composeRule.onNodeWithTag("launch_image_submit").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("launch_prompt_input").assertTextContains("Review this screenshot")

        composeRule.onNodeWithTag("launch_clear_image").performClick()
        composeRule.onNodeWithTag("launch_image_summary").assertDoesNotExist()
    }

    @Test
    fun keepsPromptWhenSelectedImageIsRejectedAsOversize() {
        val imageUri = Uri.parse("content://images/huge")
        val metadata = ImageAttachmentMetadata(
            displayName = "huge.png",
            contentType = "image/png",
            byteLength = 11L * 1024L * 1024L,
        )
        composeRule.setContent {
            SaseMobileTheme {
                LaunchScreen(
                    state = AgentsState(),
                    onLaunch = { AgentActionState.Succeeded("text launch") },
                    onLaunchImage = { AgentActionState.Succeeded("image launch") },
                    onOpenAgents = {},
                    onOpenSettings = {},
                    initialImageAttachment = SelectedImageAttachment(
                        uri = imageUri,
                        metadata = metadata,
                        source = ImageAttachmentSource.Gallery,
                    ),
                    imageAttachmentReader = FakeImageAttachmentReader(
                        metadata,
                        payloadResult = ImageAttachmentPayloadResult.Failure(ImageAttachmentError.Oversize),
                    ),
                    requestIdFactory = { "android-image-test" },
                )
            }
        }

        composeRule.onNodeWithTag("launch_prompt_input").performTextInput("Review this screenshot")
        composeRule.onNodeWithTag("launch_image_submit").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("launch_image_error")
            .assertTextContains("Image is larger than the 10 MB mobile upload limit.")
        composeRule.onNodeWithTag("launch_prompt_input").assertTextContains("Review this screenshot")
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

    private class FakeImageAttachmentReader(
        private val metadata: ImageAttachmentMetadata,
        private val payloadResult: ImageAttachmentPayloadResult = ImageAttachmentPayloadResult.Success(
            ImageAttachmentPayload(
                metadata = metadata,
                base64Image = "cGl4ZWxz",
            ),
        ),
    ) : ImageAttachmentReader {
        override suspend fun describe(uri: Uri): ImageAttachmentLoadResult {
            return ImageAttachmentLoadResult.Success(metadata)
        }

        override suspend fun encodedPayload(
            uri: Uri,
            metadata: ImageAttachmentMetadata,
        ): ImageAttachmentPayloadResult {
            return payloadResult
        }

        override fun createCameraCaptureUri(): Uri {
            return Uri.parse("content://images/camera")
        }
    }
}
