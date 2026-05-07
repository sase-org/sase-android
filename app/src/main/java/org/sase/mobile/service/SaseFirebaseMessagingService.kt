package org.sase.mobile.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import org.sase.mobile.SaseAppGraph
import org.sase.mobile.data.api.AndroidNetworkAvailability
import org.sase.mobile.data.notifications.local.LocalHintNotificationRenderer
import org.sase.mobile.data.notifications.push.PushMessageHandler
import org.sase.mobile.data.session.DataStoreHostSessionStorage

class SaseFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        SaseAppGraph.get(applicationContext).pushRegistrationManager.registerKnownToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data.isEmpty()) {
            return
        }
        val graph = SaseAppGraph.get(applicationContext)
        graph.scope.launch {
            PushMessageHandler(
                sessionStorage = DataStoreHostSessionStorage(applicationContext),
                notificationRepository = graph.notificationRepository,
                renderer = { hint -> LocalHintNotificationRenderer(applicationContext).render(hint) },
                registrationManager = graph.pushRegistrationManager,
                networkAvailability = AndroidNetworkAvailability(applicationContext),
                scope = graph.scope,
            ).handleDataMessage(message.data)
        }
    }
}
