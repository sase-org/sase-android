package org.sase.mobile.data.notifications.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.sase.mobile.data.api.NetworkAvailability
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.notifications.RefreshReason
import org.sase.mobile.data.notifications.local.LocalNotificationHint
import org.sase.mobile.data.session.HostSessionStorage

fun interface LocalHintDispatcher {
    fun render(hint: LocalNotificationHint): Boolean
}

class PushMessageHandler(
    private val sessionStorage: HostSessionStorage,
    private val notificationRepository: NotificationRepository,
    private val renderer: LocalHintDispatcher,
    private val registrationManager: PushRegistrationManager,
    private val networkAvailability: NetworkAvailability = NetworkAvailability.AlwaysAvailable,
    private val scope: CoroutineScope,
) {
    suspend fun handleDataMessage(data: Map<String, String>): PushMessageHandlingResult {
        val hint = parsePushHintData(data) ?: return PushMessageHandlingResult.DroppedInvalidPayload
        val session = sessionStorage.read()
            ?: return PushMessageHandlingResult.DroppedUnpaired

        registrationManager.recordReceivedHint(hint)
        if (networkAvailability.isNetworkAvailable()) {
            scope.launch {
                notificationRepository.fullRefresh(RefreshReason.PushHint)
            }
        }

        val rendered = renderer.render(hint.toLocalHint())
        return if (rendered) {
            PushMessageHandlingResult.Rendered(session.deviceId)
        } else {
            PushMessageHandlingResult.RefreshOnly(session.deviceId)
        }
    }
}

sealed interface PushMessageHandlingResult {
    data object DroppedInvalidPayload : PushMessageHandlingResult
    data object DroppedUnpaired : PushMessageHandlingResult
    data class RefreshOnly(val deviceId: String) : PushMessageHandlingResult
    data class Rendered(val deviceId: String) : PushMessageHandlingResult
}
