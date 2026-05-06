package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.sase.mobile.data.actions.ActionDraftKey
import org.sase.mobile.data.actions.ActionFailure
import org.sase.mobile.data.actions.ActionFailureKind
import org.sase.mobile.data.actions.ActionSubmissionState
import org.sase.mobile.data.actions.NotificationActionChoice
import org.sase.mobile.data.actions.NotificationActionController
import org.sase.mobile.data.api.dto.ActionResultWire
import org.sase.mobile.data.api.dto.MobileActionKindWire
import org.sase.mobile.data.api.dto.MobileActionStateWire
import org.sase.mobile.data.notifications.NotificationDetailState
import org.sase.mobile.ui.NotificationUiFixtures
import org.sase.mobile.ui.notification.NotificationDetailScreen
import org.sase.mobile.ui.theme.SaseMobileTheme

class NotificationDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersNotificationDetailContent() {
        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.Ready(NotificationUiFixtures.detail, stale = false),
                )
            }
        }

        composeRule.onNodeWithText("Plan review waiting").assertIsDisplayed()
        composeRule.onNodeWithText("Choose an action below.").assertIsDisplayed()
        composeRule.onNodeWithText("Approve").assertIsDisplayed()
        composeRule.onNodeWithText("Run").assertIsDisplayed()
        composeRule.onNodeWithText("~/plans/plan.md").assertIsDisplayed()
        composeRule.onNodeWithText("Mark read").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }

    @Test
    fun rendersStaleAndLoggedOutStates() {
        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.Ready(NotificationUiFixtures.detail, stale = true),
                )
            }
        }
        composeRule.onNodeWithText("Cached").assertIsDisplayed()

        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.LoggedOut,
                )
            }
        }
        composeRule.onNodeWithText("No paired host").assertIsDisplayed()
    }

    @Test
    fun rendersUnavailableActionStates() {
        fun detailWith(state: MobileActionStateWire) = NotificationUiFixtures.detail.copy(
            action = NotificationUiFixtures.detail.action.copy(state = state),
        )

        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.Ready(detailWith(MobileActionStateWire.AlreadyHandled), false),
                )
            }
        }
        composeRule.onNodeWithText("This action was already handled on the host.").assertIsDisplayed()
        composeRule.onNodeWithText("Refresh").assertIsDisplayed()

        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.Ready(detailWith(MobileActionStateWire.Stale), false),
                )
            }
        }
        composeRule.onNodeWithText("Action state is stale. Refresh before acting.").assertIsDisplayed()

        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.Ready(
                        NotificationUiFixtures.detail.copy(
                            action = NotificationUiFixtures.detail.action.copy(identity = null),
                        ),
                        false,
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Action request is missing. Refresh notification detail.").assertIsDisplayed()

        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "error001-digest",
                    initialState = NotificationDetailState.Ready(
                        NotificationUiFixtures.detail.copy(
                            action = NotificationUiFixtures.detail.action.copy(
                                kind = MobileActionKindWire.Unsupported,
                                state = MobileActionStateWire.Unsupported,
                            ),
                        ),
                        false,
                    ),
                )
            }
        }
        composeRule.onNodeWithText("This action is not supported by the Android client.").assertIsDisplayed()
    }

    @Test
    fun submitsAvailableActionAndShowsResult() {
        val controller = FakeActionController(
            ActionSubmissionState.Success(
                result = ActionResultWire(
                    schemaVersion = 1,
                    prefix = "plan0001",
                    notificationId = "plan0001-review",
                    actionKind = MobileActionKindWire.PlanApproval,
                    state = MobileActionStateWire.Available,
                    responseFile = "plan_response.json",
                    responseJson = buildJsonObject {},
                    message = "plan approved",
                ),
                refreshedDetail = null,
            ),
        )
        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.Ready(NotificationUiFixtures.detail, stale = false),
                    actionController = controller,
                )
            }
        }

        composeRule.onNodeWithText("Approve").performClick()

        composeRule.onNodeWithText("Submitted action").assertIsDisplayed()
        composeRule.onNodeWithText("plan approved").assertIsDisplayed()
    }

    @Test
    fun offlineActionFailureShowsRetryAffordance() {
        val controller = FakeActionController(
            ActionSubmissionState.Failure(
                failure = ActionFailure(
                    kind = ActionFailureKind.Disconnected,
                    message = "Gateway unavailable. Check the host connection and retry.",
                    canRetry = true,
                ),
                refreshedDetail = null,
            ),
        )
        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.Ready(NotificationUiFixtures.detail, stale = false),
                    actionController = controller,
                )
            }
        }

        composeRule.onNodeWithText("Approve").performClick()

        composeRule.onNodeWithText("Action failed").assertIsDisplayed()
        composeRule.onNodeWithText("Gateway unavailable. Check the host connection and retry.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun textActionEditorSubmitsDraftBackedFeedback() {
        val controller = FakeActionController(
            ActionSubmissionState.Success(
                result = ActionResultWire(
                    schemaVersion = 1,
                    prefix = "plan0001",
                    notificationId = "plan0001-review",
                    actionKind = MobileActionKindWire.PlanApproval,
                    state = MobileActionStateWire.Available,
                    responseFile = "plan_response.json",
                    responseJson = buildJsonObject {},
                    message = "feedback sent",
                ),
                refreshedDetail = null,
            ),
        )
        composeRule.setContent {
            SaseMobileTheme {
                NotificationDetailScreen(
                    notificationId = "plan0001-review",
                    initialState = NotificationDetailState.Ready(NotificationUiFixtures.detail, stale = false),
                    actionController = controller,
                )
            }
        }

        composeRule.onNodeWithText("Feedback").performClick()
        composeRule.onNodeWithTag("action_text_draft").performTextInput("Please add UI coverage.")
        composeRule.onNodeWithText("Send feedback").performClick()

        composeRule.onNodeWithText("Submitted action").assertIsDisplayed()
        val choice = controller.lastChoice as NotificationActionChoice.Plan
        if (choice.feedback != "Please add UI coverage.") {
            throw AssertionError("Expected submitted feedback to match the text draft.")
        }
        if (controller.drafts.isNotEmpty()) {
            throw AssertionError("Expected successful submit to clear the draft.")
        }
    }
}

private class FakeActionController(
    private val result: ActionSubmissionState,
) : NotificationActionController {
    var lastChoice: NotificationActionChoice? = null
    val drafts = mutableMapOf<ActionDraftKey, String>()

    override suspend fun submitAction(
        notificationId: String,
        action: org.sase.mobile.data.api.dto.MobileActionDetailWire,
        choice: NotificationActionChoice,
    ): ActionSubmissionState {
        lastChoice = choice
        if (result is ActionSubmissionState.Success) {
            drafts.clear()
        }
        return result
    }

    override suspend fun readDraft(key: ActionDraftKey): String? = drafts[key]

    override suspend fun saveDraft(key: ActionDraftKey, text: String) {
        drafts[key] = text
    }

    override suspend fun discardDraft(key: ActionDraftKey) {
        drafts.remove(key)
    }
}
