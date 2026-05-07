package org.sase.mobile.data.session

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.sase.mobile.BuildConfig
import org.sase.mobile.data.api.GatewayApiClient

object AndroidSessionRepositoryFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        client: OkHttpClient = OkHttpClient(),
        onBeforeForgetHost: suspend () -> Unit = {},
    ): SessionRepository {
        return SessionRepository(
            storage = DataStoreHostSessionStorage(context),
            tokenVault = AndroidKeystoreTokenVault(context),
            clientFactory = { baseUrl, tokenProvider ->
                GatewayApiClient(
                    baseUrl = baseUrl,
                    bearerTokenProvider = tokenProvider,
                    client = client,
                )
            },
            deviceMetadataProvider = {
                DeviceMetadata(
                    displayName = listOf(Build.MANUFACTURER, Build.MODEL)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "Android device" },
                    appVersion = BuildConfig.VERSION_NAME,
                )
            },
            scope = scope,
            onBeforeForgetHost = onBeforeForgetHost,
        )
    }
}
