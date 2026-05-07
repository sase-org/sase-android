package org.sase.mobile.data.notifications.local

import android.content.Intent
import android.net.Uri
import java.net.URI

sealed interface SaseDeepLinkTarget {
    data object Inbox : SaseDeepLinkTarget
    data class NotificationDetail(val notificationId: String) : SaseDeepLinkTarget
    data object Agents : SaseDeepLinkTarget
    data object Helpers : SaseDeepLinkTarget
    data object Update : SaseDeepLinkTarget
}

fun SaseDeepLinkTarget.toUriString(): String {
    val path = when (this) {
        SaseDeepLinkTarget.Inbox -> "inbox"
        is SaseDeepLinkTarget.NotificationDetail -> "notification/${notificationId.urlPathEncode()}"
        SaseDeepLinkTarget.Agents -> "agents"
        SaseDeepLinkTarget.Helpers -> "helpers"
        SaseDeepLinkTarget.Update -> "update"
    }
    return "$DeepLinkPrefix/$path"
}

fun SaseDeepLinkTarget.toUri(): Uri = Uri.parse(toUriString())

fun parseSaseDeepLink(raw: String?): SaseDeepLinkTarget? {
    val uri = raw?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (uri.scheme != DeepLinkScheme || uri.host != DeepLinkHost) {
        return null
    }
    val segments = uri.path.orEmpty()
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() }
        .map { it.urlPathDecode() }
    return when {
        segments == listOf("inbox") -> SaseDeepLinkTarget.Inbox
        segments == listOf("agents") -> SaseDeepLinkTarget.Agents
        segments == listOf("helpers") -> SaseDeepLinkTarget.Helpers
        segments == listOf("update") -> SaseDeepLinkTarget.Update
        segments.size == 2 && segments[0] == "notification" && segments[1].isNotBlank() ->
            SaseDeepLinkTarget.NotificationDetail(segments[1])

        else -> null
    }
}

object SaseDeepLinkIntent {
    const val ExtraTarget = "org.sase.mobile.extra.DEEP_LINK_TARGET"

    fun from(intent: Intent?): SaseDeepLinkTarget? {
        if (intent == null) {
            return null
        }
        return parseSaseDeepLink(intent.getStringExtra(ExtraTarget))
            ?: parseSaseDeepLink(intent.dataString)
    }
}

private const val DeepLinkScheme = "sase"
private const val DeepLinkHost = "mobile"
private const val DeepLinkPrefix = "$DeepLinkScheme://$DeepLinkHost"

private fun String.urlPathEncode(): String {
    return java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
}

private fun String.urlPathDecode(): String {
    return java.net.URLDecoder.decode(this, Charsets.UTF_8.name())
}
