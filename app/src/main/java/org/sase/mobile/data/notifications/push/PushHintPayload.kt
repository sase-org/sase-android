package org.sase.mobile.data.notifications.push

import org.sase.mobile.data.api.dto.PushHintCategoryWire
import org.sase.mobile.data.notifications.local.LocalHintCategory
import org.sase.mobile.data.notifications.local.LocalNotificationHint
import org.sase.mobile.data.notifications.local.SaseDeepLinkTarget

data class PushHintPayload(
    val id: String,
    val category: PushHintCategoryWire,
    val reason: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val notificationId: String? = null,
    val agentName: String? = null,
    val helper: String? = null,
    val jobId: String? = null,
) {
    fun toLocalHint(): LocalNotificationHint {
        return LocalNotificationHint(
            notificationId = notificationId,
            eventId = id,
            category = category.toLocalCategory(),
            title = title,
            body = body,
            createdAt = createdAt,
            target = target(),
        )
    }

    private fun target(): SaseDeepLinkTarget {
        return when (category) {
            PushHintCategoryWire.Notifications ->
                notificationId
                    ?.takeIf { it.isSafeIdentifier() }
                    ?.let { SaseDeepLinkTarget.NotificationDetail(it) }
                    ?: SaseDeepLinkTarget.Inbox

            PushHintCategoryWire.Agents -> SaseDeepLinkTarget.Agents
            PushHintCategoryWire.Helpers -> SaseDeepLinkTarget.Helpers
            PushHintCategoryWire.Update -> SaseDeepLinkTarget.Update
            PushHintCategoryWire.Session -> SaseDeepLinkTarget.Inbox
        }
    }
}

fun parsePushHintData(data: Map<String, String>): PushHintPayload? {
    val schemaVersion = data["schema_version"]?.toIntOrNull() ?: 1
    if (schemaVersion != 1) {
        return null
    }
    val id = data["id"]?.takeIf { it.isSafeIdentifier() } ?: return null
    val category = data["category"]?.toPushHintCategory() ?: return null
    val createdAt = data["created_at"]?.takeIf { it.length <= MaxCreatedAtLength }.orEmpty()
    return PushHintPayload(
        id = id,
        category = category,
        reason = data["reason"].orEmpty().safeText(MaxReasonLength),
        title = data["title"].orEmpty().safeText(MaxTitleLength).ifBlank { defaultTitle(category) },
        body = data["body"].orEmpty().safeText(MaxBodyLength).ifBlank { "Open SASE Mobile to refresh." },
        createdAt = createdAt,
        notificationId = data["notification_id"]?.takeIf { it.isSafeIdentifier() },
        agentName = data["agent_name"]?.takeIf { it.isSafeIdentifier() },
        helper = data["helper"]?.takeIf { it.isSafeIdentifier() },
        jobId = data["job_id"]?.takeIf { it.isSafeIdentifier() },
    )
}

private fun String.toPushHintCategory(): PushHintCategoryWire? {
    return when (this) {
        "notifications" -> PushHintCategoryWire.Notifications
        "agents" -> PushHintCategoryWire.Agents
        "helpers" -> PushHintCategoryWire.Helpers
        "update" -> PushHintCategoryWire.Update
        "session" -> PushHintCategoryWire.Session
        else -> null
    }
}

private fun PushHintCategoryWire.toLocalCategory(): LocalHintCategory {
    return when (this) {
        PushHintCategoryWire.Notifications -> LocalHintCategory.Action
        PushHintCategoryWire.Agents -> LocalHintCategory.Agent
        PushHintCategoryWire.Helpers -> LocalHintCategory.Helper
        PushHintCategoryWire.Update -> LocalHintCategory.Update
        PushHintCategoryWire.Session -> LocalHintCategory.Foreground
    }
}

private fun defaultTitle(category: PushHintCategoryWire): String {
    return when (category) {
        PushHintCategoryWire.Notifications -> "SASE action hint"
        PushHintCategoryWire.Agents -> "SASE agent hint"
        PushHintCategoryWire.Helpers -> "SASE helper hint"
        PushHintCategoryWire.Update -> "SASE update hint"
        PushHintCategoryWire.Session -> "SASE refresh hint"
    }
}

private fun String.safeText(maxLength: Int): String {
    return trim()
        .replace(ControlWhitespaceRegex, " ")
        .replace(SensitiveTokenRegex, "[redacted]")
        .replace(HostPathRegex, "[path]")
        .take(maxLength)
        .trim()
}

private fun String.isSafeIdentifier(): Boolean {
    return length in 1..160 && all { it.isLetterOrDigit() || it in "-_:.@" }
}

private const val MaxCreatedAtLength = 40
private const val MaxReasonLength = 80
private const val MaxTitleLength = 80
private const val MaxBodyLength = 140

private val ControlWhitespaceRegex = Regex("\\s+")
private val SensitiveTokenRegex = Regex(
    pattern = "(?i)(bearer\\s+[a-z0-9._~+/=-]+|pairing[_ -]?code[:=]?\\s*\\S+|attachment[_ -]?token[:=]?\\s*\\S+)",
)
private val HostPathRegex = Regex("(?<![A-Za-z0-9])(?:/Users|/home|/var|/tmp|[A-Za-z]:\\\\)\\S*")
