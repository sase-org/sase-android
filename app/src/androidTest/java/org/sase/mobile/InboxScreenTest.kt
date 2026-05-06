package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.sase.mobile.data.notifications.NotificationConnectionState
import org.sase.mobile.data.notifications.NotificationInboxState
import org.sase.mobile.data.notifications.NotificationRefreshState
import org.sase.mobile.ui.NotificationUiFixtures
import org.sase.mobile.ui.inbox.InboxScreen
import org.sase.mobile.ui.theme.SaseMobileTheme

class InboxScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersMixedInboxAndOpensDetail() {
        var openedId: String? = null
        composeRule.setContent {
            SaseMobileTheme {
                InboxScreen(
                    state = NotificationUiFixtures.inboxState,
                    connectionState = NotificationConnectionState.Connected,
                    refreshState = NotificationRefreshState.Idle,
                    onRefresh = {},
                    onOpenNotification = { openedId = it },
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Plan review waiting").assertIsDisplayed()
        composeRule.onNodeWithText("Question waiting").assertIsDisplayed()
        composeRule.onNodeWithText("Live").assertIsDisplayed()

        composeRule.onNodeWithText("Plan review waiting").performClick()

        assertEquals("plan0001-review", openedId)
    }

    @Test
    fun rendersEmptyAndStaleStates() {
        composeRule.setContent {
            SaseMobileTheme {
                InboxScreen(
                    state = NotificationInboxState(isStale = true),
                    connectionState = NotificationConnectionState.Offline("connection refused"),
                    refreshState = NotificationRefreshState.Failed("connection refused"),
                    onRefresh = {},
                    onOpenNotification = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Offline").assertIsDisplayed()
        composeRule.onNodeWithText("Refresh failed").assertIsDisplayed()
        composeRule.onNodeWithText("Cached inbox is empty while the gateway is offline.").assertIsDisplayed()
    }

    @Test
    fun filtersUnreadNotifications() {
        composeRule.setContent {
            SaseMobileTheme {
                InboxScreen(
                    state = NotificationUiFixtures.inboxState,
                    connectionState = NotificationConnectionState.Connected,
                    refreshState = NotificationRefreshState.Idle,
                    onRefresh = {},
                    onOpenNotification = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Unread").performClick()

        composeRule.onNodeWithText("Workflow finished").assertDoesNotExist()
        composeRule.onNodeWithText("Plan review waiting").assertIsDisplayed()
    }
}
