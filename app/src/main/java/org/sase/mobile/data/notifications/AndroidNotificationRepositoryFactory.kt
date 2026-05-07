package org.sase.mobile.data.notifications

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.sase.mobile.data.api.AndroidNetworkAvailability
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.api.dto.AgentsChangedEventPayloadWire
import org.sase.mobile.data.api.dto.HelpersChangedEventPayloadWire
import org.sase.mobile.data.session.AndroidKeystoreTokenVault
import org.sase.mobile.data.session.DataStoreHostSessionStorage

object AndroidNotificationRepositoryFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        client: OkHttpClient = OkHttpClient(),
        onAgentsChanged: suspend (AgentsChangedEventPayloadWire) -> Unit = {},
        onHelpersChanged: suspend (HelpersChangedEventPayloadWire) -> Unit = {},
    ): NotificationRepository {
        return NotificationRepository(
            sessionStorage = DataStoreHostSessionStorage(context),
            tokenVault = AndroidKeystoreTokenVault(context),
            cache = DataStoreNotificationCache(context),
            clientFactory = { baseUrl, tokenProvider ->
                GatewayApiClient(
                    baseUrl = baseUrl,
                    bearerTokenProvider = tokenProvider,
                    client = client,
                )
            },
            sseClientFactory = { baseUrl, tokenProvider ->
                GatewaySseClient(
                    baseUrl = baseUrl,
                    bearerTokenProvider = tokenProvider,
                    client = client,
                )
            },
            networkAvailability = AndroidNetworkAvailability(context),
            onAgentsChanged = onAgentsChanged,
            onHelpersChanged = onHelpersChanged,
            scope = scope,
        )
    }
}
