package org.sase.mobile.data.notifications.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SaseDeepLinkTargetTest {
    @Test
    fun parsesSupportedRoutes() {
        assertThat(parseSaseDeepLink("sase://mobile/inbox")).isEqualTo(SaseDeepLinkTarget.Inbox)
        assertThat(parseSaseDeepLink("sase://mobile/agents")).isEqualTo(SaseDeepLinkTarget.Agents)
        assertThat(parseSaseDeepLink("sase://mobile/update")).isEqualTo(SaseDeepLinkTarget.Update)
        assertThat(parseSaseDeepLink("sase://mobile/notification/plan0001-review"))
            .isEqualTo(SaseDeepLinkTarget.NotificationDetail("plan0001-review"))
    }

    @Test
    fun rejectsUnsupportedRoutes() {
        assertThat(parseSaseDeepLink("https://mobile/inbox")).isNull()
        assertThat(parseSaseDeepLink("sase://mobile/notification")).isNull()
        assertThat(parseSaseDeepLink("sase://mobile/settings")).isNull()
    }

    @Test
    fun roundTripsTargetUri() {
        val target = SaseDeepLinkTarget.NotificationDetail("plan0001-review")

        assertThat(parseSaseDeepLink(target.toUriString())).isEqualTo(target)
    }
}
