package org.sase.mobile.data.notifications.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalNotificationHintTest {
    @Test
    fun sanitizedHintKeepsOnlySafeDisplayTextAndRoutingFields() {
        val hint = LocalNotificationHint(
            notificationId = "plan0001-review",
            eventId = "event-1",
            category = LocalHintCategory.Action,
            title = "Plan approval",
            body = "Bearer abc123 in /home/bryan/projects/secret attachment_token=tok",
            createdAt = "2026-05-07T00:00:00Z",
            target = SaseDeepLinkTarget.NotificationDetail("plan0001-review"),
        )

        val sanitized = hint.sanitized()

        assertThat(sanitized?.stableKey).isEqualTo("plan0001-review")
        assertThat(sanitized?.body).doesNotContain("abc123")
        assertThat(sanitized?.body).doesNotContain("/home/bryan")
        assertThat(sanitized?.body).doesNotContain("tok")
        assertThat(sanitized?.target).isEqualTo(SaseDeepLinkTarget.NotificationDetail("plan0001-review"))
    }

    @Test
    fun invalidIdentifierDoesNotRender() {
        val hint = LocalNotificationHint(
            notificationId = "bad id with spaces",
            eventId = null,
            category = LocalHintCategory.Agent,
            title = "Agent changed",
            body = "Open SASE Mobile.",
            createdAt = "",
            target = SaseDeepLinkTarget.Agents,
        )

        assertThat(hint.sanitized()).isNull()
    }
}
