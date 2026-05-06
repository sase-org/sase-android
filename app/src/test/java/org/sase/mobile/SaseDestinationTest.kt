package org.sase.mobile

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.sase.mobile.ui.SaseDestination

class SaseDestinationTest {
    @Test
    fun notificationDetailRouteUsesSuppliedId() {
        assertThat(SaseDestination.NotificationDetail.createRoute("plan-123"))
            .isEqualTo("notification/plan-123")
    }

    @Test
    fun bottomNavigationStartsWithInboxAgentsAndSettings() {
        val routes = listOf(
            SaseDestination.Inbox.route,
            SaseDestination.Agents.route,
            SaseDestination.Settings.route,
        )

        assertThat(routes).containsExactly("inbox", "agents", "settings").inOrder()
    }

    @Test
    fun updateRouteIsSettingsLinkedDestination() {
        assertThat(SaseDestination.Update.route).isEqualTo("update")
    }

    @Test
    fun helpersRouteIsSettingsLinkedDestination() {
        assertThat(SaseDestination.Helpers.route).isEqualTo("helpers")
    }
}
