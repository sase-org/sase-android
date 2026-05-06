package org.sase.mobile.data.actions

import android.content.Context
import okhttp3.OkHttpClient
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.session.AndroidKeystoreTokenVault
import org.sase.mobile.data.session.DataStoreHostSessionStorage

object AndroidActionRepositoryFactory {
    fun create(
        context: Context,
        notificationRepository: NotificationRepository,
        client: OkHttpClient = OkHttpClient(),
    ): ActionRepository {
        return ActionRepository(
            sessionStorage = DataStoreHostSessionStorage(context),
            tokenVault = AndroidKeystoreTokenVault(context),
            clientFactory = { baseUrl, tokenProvider ->
                GatewayApiClient(
                    baseUrl = baseUrl,
                    bearerTokenProvider = tokenProvider,
                    client = client,
                )
            },
            notificationRepository = notificationRepository,
        )
    }
}
