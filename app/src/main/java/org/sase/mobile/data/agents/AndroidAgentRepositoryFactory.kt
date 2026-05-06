package org.sase.mobile.data.agents

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.session.AndroidKeystoreTokenVault
import org.sase.mobile.data.session.DataStoreHostSessionStorage

object AndroidAgentRepositoryFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        client: OkHttpClient = OkHttpClient(),
    ): AgentRepository {
        return AgentRepository(
            sessionStorage = DataStoreHostSessionStorage(context),
            tokenVault = AndroidKeystoreTokenVault(context),
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
            scope = scope,
        )
    }
}
