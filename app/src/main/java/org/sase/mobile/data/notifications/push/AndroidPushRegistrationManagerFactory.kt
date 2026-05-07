package org.sase.mobile.data.notifications.push

import android.content.Context
import android.os.Build
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import com.google.firebase.messaging.FirebaseMessaging
import org.sase.mobile.BuildConfig
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.session.AndroidKeystoreTokenVault
import org.sase.mobile.data.session.DataStoreHostSessionStorage
import org.sase.mobile.data.session.DeviceMetadata

object AndroidPushRegistrationManagerFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        client: OkHttpClient = OkHttpClient(),
    ): PushRegistrationManager {
        val appContext = context.applicationContext
        return PushRegistrationManager(
            sessionStorage = DataStoreHostSessionStorage(appContext),
            tokenVault = AndroidKeystoreTokenVault(appContext),
            tokenProvider = FirebasePushTokenProvider(),
            appInstanceIdProvider = { AppInstanceIdStore(appContext).readOrCreate() },
            deviceMetadataProvider = {
                DeviceMetadata(
                    displayName = listOf(Build.MANUFACTURER, Build.MODEL)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "Android device" },
                    appVersion = BuildConfig.VERSION_NAME,
                )
            },
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

class FirebasePushTokenProvider : PushTokenProvider {
    override suspend fun currentToken(): PushTokenResult {
        val messaging = try {
            FirebaseMessaging.getInstance()
        } catch (error: IllegalStateException) {
            return PushTokenResult.Failure(error.message ?: "Firebase is not configured")
        }
        return suspendCancellableCoroutine { continuation ->
            messaging.token
                .addOnCompleteListener { task ->
                    if (!continuation.isActive) {
                        return@addOnCompleteListener
                    }
                    val token = task.result?.takeIf { task.isSuccessful }
                    if (token.isNullOrBlank()) {
                        continuation.resume(
                            PushTokenResult.Failure(
                                task.exception?.message ?: "Firebase did not return a token",
                            ),
                        )
                    } else {
                        continuation.resume(PushTokenResult.Success(token))
                    }
                }
        }
    }
}

private class AppInstanceIdStore(
    private val context: Context,
) {
    suspend fun readOrCreate(): String {
        val storage = AppInstanceSharedPreferences(context)
        storage.read()?.let { return it }
        val generated = UUID.randomUUID().toString()
        storage.write(generated)
        return generated
    }
}

private class AppInstanceSharedPreferences(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("sase_push", Context.MODE_PRIVATE)

    fun read(): String? = prefs.getString(KeyAppInstanceId, null)

    fun write(value: String) {
        prefs.edit().putString(KeyAppInstanceId, value).apply()
    }

    private companion object {
        const val KeyAppInstanceId = "app_instance_id"
    }
}
