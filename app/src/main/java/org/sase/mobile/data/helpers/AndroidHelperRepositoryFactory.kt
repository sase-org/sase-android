package org.sase.mobile.data.helpers

import android.content.Context
import okhttp3.OkHttpClient
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.session.AndroidKeystoreTokenVault
import org.sase.mobile.data.session.DataStoreHostSessionStorage

object AndroidHelperRepositoryFactory {
    fun create(
        context: Context,
        client: OkHttpClient = OkHttpClient(),
    ): HelperRepository {
        return HelperRepository(
            sessionStorage = DataStoreHostSessionStorage(context),
            tokenVault = AndroidKeystoreTokenVault(context),
            clientFactory = { baseUrl, tokenProvider ->
                GatewayApiClient(
                    baseUrl = baseUrl,
                    bearerTokenProvider = tokenProvider,
                    client = client,
                )
            },
        )
    }
}
