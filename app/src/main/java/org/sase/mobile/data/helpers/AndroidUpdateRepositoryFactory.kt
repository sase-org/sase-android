package org.sase.mobile.data.helpers

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.session.AndroidKeystoreTokenVault
import org.sase.mobile.data.session.DataStoreHostSessionStorage

object AndroidUpdateRepositoryFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        client: OkHttpClient = OkHttpClient(),
    ): UpdateRepository {
        return UpdateRepository(
            sessionStorage = DataStoreHostSessionStorage(context),
            tokenVault = AndroidKeystoreTokenVault(context),
            cache = DataStoreUpdateJobCache(context),
            clientFactory = { baseUrl, tokenProvider ->
                GatewayApiClient(
                    baseUrl = baseUrl,
                    bearerTokenProvider = tokenProvider,
                    client = client,
                )
            },
            scope = scope,
        )
    }
}
