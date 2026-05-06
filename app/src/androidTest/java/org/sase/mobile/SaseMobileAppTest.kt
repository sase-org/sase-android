package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class SaseMobileAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsInboxAsFirstScreen() {
        composeRule.onNodeWithTag("inbox_screen").assertIsDisplayed()
    }

    @Test
    fun canOpenSettingsDestination() {
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun canOpenAgentsDestination() {
        composeRule.onNodeWithText("Agents").performClick()

        composeRule.onNodeWithTag("agents_screen").assertIsDisplayed()
    }

    @Test
    fun canOpenUpdateDestinationFromSettings() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("open_update_button").performClick()

        composeRule.onNodeWithTag("update_screen").assertIsDisplayed()
    }

    @Test
    fun canOpenHelpersDestinationFromSettings() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("open_helpers_button").performClick()

        composeRule.onNodeWithTag("helpers_screen").assertIsDisplayed()
    }
}
