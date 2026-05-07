package org.sase.mobile.data.notifications.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

enum class SaseNotificationChannel(
    val id: String,
    val displayName: String,
    val description: String,
) {
    ActionHints(
        id = "sase_action_hints",
        displayName = "SASE action hints",
        description = "Safe hints for approvals, questions, and action requests.",
    ),
    AgentLifecycle(
        id = "sase_agent_lifecycle",
        displayName = "SASE agent lifecycle",
        description = "Safe hints for agent status changes.",
    ),
    HelperUpdates(
        id = "sase_helper_updates",
        displayName = "SASE helpers and updates",
        description = "Safe hints for helper workflows and SASE updates.",
    ),
    ForegroundConnection(
        id = "sase_foreground_connection",
        displayName = "SASE foreground connection",
        description = "Foreground connection status while connected mode is active.",
    ),
}

fun notificationChannelForCategory(category: LocalHintCategory): SaseNotificationChannel {
    return when (category) {
        LocalHintCategory.Action -> SaseNotificationChannel.ActionHints
        LocalHintCategory.Agent -> SaseNotificationChannel.AgentLifecycle
        LocalHintCategory.Helper,
        LocalHintCategory.Update,
        -> SaseNotificationChannel.HelperUpdates
        LocalHintCategory.Foreground -> SaseNotificationChannel.ForegroundConnection
    }
}

fun interface NotificationChannelRegistrar {
    fun ensureChannel(channel: SaseNotificationChannel)
}

fun createSaseNotificationChannels(registrar: NotificationChannelRegistrar) {
    SaseNotificationChannel.entries.forEach(registrar::ensureChannel)
}

class AndroidNotificationChannelRegistrar(
    context: Context,
) : NotificationChannelRegistrar {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun ensureChannel(channel: SaseNotificationChannel) {
        val androidChannel = NotificationChannel(
            channel.id,
            channel.displayName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = channel.description
        }
        notificationManager.createNotificationChannel(androidChannel)
    }
}
