package org.sase.mobile.data.notifications.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SaseNotificationChannelsTest {
    @Test
    fun createsStableUserReadableChannelsOncePerSpec() {
        val calls = mutableListOf<SaseNotificationChannel>()

        createSaseNotificationChannels { channel -> calls += channel }

        assertThat(calls.map { it.id }).containsExactly(
            "sase_action_hints",
            "sase_agent_lifecycle",
            "sase_helper_updates",
            "sase_foreground_connection",
        )
        assertThat(calls.map { it.displayName }).doesNotContain("")
        assertThat(calls.map { it.id }.toSet()).hasSize(calls.size)
    }

    @Test
    fun categoriesMapToExpectedChannels() {
        assertThat(notificationChannelForCategory(LocalHintCategory.Action))
            .isEqualTo(SaseNotificationChannel.ActionHints)
        assertThat(notificationChannelForCategory(LocalHintCategory.Agent))
            .isEqualTo(SaseNotificationChannel.AgentLifecycle)
        assertThat(notificationChannelForCategory(LocalHintCategory.Helper))
            .isEqualTo(SaseNotificationChannel.HelperUpdates)
        assertThat(notificationChannelForCategory(LocalHintCategory.Update))
            .isEqualTo(SaseNotificationChannel.HelperUpdates)
        assertThat(notificationChannelForCategory(LocalHintCategory.Foreground))
            .isEqualTo(SaseNotificationChannel.ForegroundConnection)
    }
}
