package org.sase.mobile.data.notifications.push

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.sase.mobile.data.api.dto.PushHintCategoryWire
import org.sase.mobile.data.notifications.local.LocalHintCategory
import org.sase.mobile.data.notifications.local.SaseDeepLinkTarget
import org.sase.mobile.data.notifications.local.sanitized

class PushHintPayloadTest {
    @Test
    fun parsesNotificationHintIntoSafeLocalNotification() {
        val payload = parsePushHintData(
            mapOf(
                "schema_version" to "1",
                "id" to "0000000000000042",
                "category" to "notifications",
                "reason" to "created",
                "title" to "Plan review",
                "body" to "Bearer secret in /home/bryan/project attachment_token=tok",
                "created_at" to "2026-05-07T12:00:00Z",
                "notification_id" to "plan0001-review",
            ),
        )

        val hint = payload?.toLocalHint()?.sanitized()

        assertThat(payload?.category).isEqualTo(PushHintCategoryWire.Notifications)
        assertThat(hint?.stableKey).isEqualTo("plan0001-review")
        assertThat(hint?.category).isEqualTo(LocalHintCategory.Action)
        assertThat(hint?.target).isEqualTo(SaseDeepLinkTarget.NotificationDetail("plan0001-review"))
        assertThat(hint?.body).doesNotContain("secret")
        assertThat(hint?.body).doesNotContain("/home/bryan")
        assertThat(hint?.body).doesNotContain("tok")
    }

    @Test
    fun rejectsInvalidCategoryAndUnsafeIdentifier() {
        assertThat(
            parsePushHintData(
                mapOf(
                    "id" to "event 1",
                    "category" to "notifications",
                ),
            ),
        ).isNull()
        assertThat(
            parsePushHintData(
                mapOf(
                    "id" to "event-1",
                    "category" to "prompts",
                ),
            ),
        ).isNull()
    }

    @Test
    fun routesCategoryHintsToExpectedScreens() {
        assertThat(hint("agents").toLocalHint().target).isEqualTo(SaseDeepLinkTarget.Agents)
        assertThat(hint("helpers").toLocalHint().target).isEqualTo(SaseDeepLinkTarget.Helpers)
        assertThat(hint("update").toLocalHint().target).isEqualTo(SaseDeepLinkTarget.Update)
        assertThat(hint("session").toLocalHint().target).isEqualTo(SaseDeepLinkTarget.Inbox)
    }

    private fun hint(category: String): PushHintPayload {
        return parsePushHintData(
            mapOf(
                "id" to "event-$category",
                "category" to category,
                "title" to "",
                "body" to "",
            ),
        ) ?: error("expected hint")
    }
}
