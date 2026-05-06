package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
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
        composeRule.onNodeWithText("Action controls arrive in the next epic.").assertIsDisplayed()
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
}
