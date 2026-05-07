package org.sase.mobile.data.notifications.local

enum class LocalHintCategory {
    Action,
    Agent,
    Helper,
    Update,
    Foreground,
}

data class LocalNotificationHint(
    val notificationId: String?,
    val eventId: String?,
    val category: LocalHintCategory,
    val title: String,
    val body: String,
    val createdAt: String,
    val target: SaseDeepLinkTarget,
)

data class SanitizedLocalNotificationHint(
    val stableKey: String,
    val category: LocalHintCategory,
    val title: String,
    val body: String,
    val createdAt: String,
    val target: SaseDeepLinkTarget,
)

fun LocalNotificationHint.sanitized(): SanitizedLocalNotificationHint? {
    val stableKey = notificationId?.takeIf { it.isSafeIdentifier() }
        ?: eventId?.takeIf { it.isSafeIdentifier() }
        ?: return null
    val safeTitle = title.toSafeDisplayText(maxLength = 80).ifBlank { "SASE update" }
    val safeBody = body.toSafeDisplayText(maxLength = 140).ifBlank { "Open SASE Mobile to refresh." }
    return SanitizedLocalNotificationHint(
        stableKey = stableKey,
        category = category,
        title = safeTitle,
        body = safeBody,
        createdAt = createdAt.toSafeDisplayText(maxLength = 40),
        target = target,
    )
}

private fun String.isSafeIdentifier(): Boolean {
    return length in 1..160 && all { it.isLetterOrDigit() || it in "-_:.@" }
}

private fun String.toSafeDisplayText(maxLength: Int): String {
    val compact = trim()
        .replace(ControlWhitespaceRegex, " ")
        .replace(SensitiveTokenRegex, "[redacted]")
        .replace(HostPathRegex, "[path]")
    return compact.take(maxLength).trim()
}

private val ControlWhitespaceRegex = Regex("\\s+")
private val SensitiveTokenRegex = Regex(
    pattern = "(?i)(bearer\\s+[a-z0-9._~+/=-]+|pairing[_ -]?code[:=]?\\s*\\S+|attachment[_ -]?token[:=]?\\s*\\S+)",
)
private val HostPathRegex = Regex("(?<![A-Za-z0-9])(?:/Users|/home|/var|/tmp|[A-Za-z]:\\\\)\\S*")
