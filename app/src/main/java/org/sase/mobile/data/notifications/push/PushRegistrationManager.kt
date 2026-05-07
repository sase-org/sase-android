package org.sase.mobile.data.notifications.push

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.GatewayApiResult
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.PushHintCategoryWire
import org.sase.mobile.data.api.dto.PushProviderWire
import org.sase.mobile.data.api.dto.PushSubscriptionRequestWire
import org.sase.mobile.data.session.DeviceMetadata
import org.sase.mobile.data.session.HostSessionStorage
import org.sase.mobile.data.session.TokenVault

interface PushTokenProvider {
    suspend fun currentToken(): PushTokenResult
}

sealed interface PushTokenResult {
    data class Success(val token: String) : PushTokenResult
    data class Failure(val message: String) : PushTokenResult
}

class PushRegistrationManager(
    private val sessionStorage: HostSessionStorage,
    private val tokenVault: TokenVault,
    private val tokenProvider: PushTokenProvider,
    private val appInstanceIdProvider: suspend () -> String,
    private val deviceMetadataProvider: () -> DeviceMetadata,
    private val clientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewayApiClient,
    private val clock: Clock = Clock.systemUTC(),
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(PushDeliveryState())
    val state: StateFlow<PushDeliveryState> = mutableState.asStateFlow()

    fun refreshTokenRegistration() {
        scope.launch {
            registerCurrentToken()
        }
    }

    fun registerKnownToken(token: String) {
        scope.launch {
            registerToken(token)
        }
    }

    suspend fun registerCurrentToken(): Boolean {
        return when (val token = tokenProvider.currentToken()) {
            is PushTokenResult.Success -> registerToken(token.token)
            is PushTokenResult.Failure -> {
                mutableState.value = mutableState.value.copy(
                    registrationStatus = PushRegistrationStatus.Failed,
                    registrationFailure = token.message,
                )
                false
            }
        }
    }

    suspend fun registerToken(token: String): Boolean {
        val session = sessionStorage.read()
        if (session == null) {
            mutableState.value = mutableState.value.copy(
                registrationStatus = PushRegistrationStatus.Unpaired,
                registeredTokenSuffix = null,
                registrationFailure = null,
            )
            return false
        }
        val providerToken = token.trim()
        if (providerToken.isBlank()) {
            mutableState.value = mutableState.value.copy(
                registrationStatus = PushRegistrationStatus.Failed,
                registrationFailure = "FCM token was empty",
            )
            return false
        }

        mutableState.value = mutableState.value.copy(
            registrationStatus = PushRegistrationStatus.Registering,
            registrationFailure = null,
        )
        val bearer = runCatching { tokenVault.readToken() }.getOrNull()
        val metadata = deviceMetadataProvider()
        val client = clientFactory(session.baseUrl) { bearer }
        val request = PushSubscriptionRequestWire(
            provider = PushProviderWire.Fcm,
            providerToken = providerToken,
            appInstanceId = appInstanceIdProvider(),
            platform = "android",
            appVersion = metadata.appVersion,
            deviceDisplayName = metadata.displayName,
            hintCategories = PushHintCategoryWire.entries,
        )
        return when (val result = client.registerPushSubscription(request)) {
            is GatewayApiResult.Success -> {
                mutableState.value = mutableState.value.copy(
                    registrationStatus = PushRegistrationStatus.Registered,
                    registeredTokenSuffix = providerToken.safeSuffix(),
                    registrationFailure = null,
                )
                true
            }

            is GatewayApiResult.Failure -> {
                mutableState.value = mutableState.value.copy(
                    registrationStatus = if (result.error.isUnauthorized()) {
                        PushRegistrationStatus.AuthExpired
                    } else {
                        PushRegistrationStatus.Failed
                    },
                    registeredTokenSuffix = null,
                    registrationFailure = result.error.toSafePushMessage(),
                )
                false
            }
        }
    }

    suspend fun revokeActiveSubscriptionsBeforeForget(): Int {
        val session = sessionStorage.read() ?: return 0.also { markUnpaired() }
        val bearer = runCatching { tokenVault.readToken() }.getOrNull()
        val client = clientFactory(session.baseUrl) { bearer }
        val list = client.pushSubscriptions()
        if (list !is GatewayApiResult.Success) {
            markUnpaired()
            return 0
        }
        var revoked = 0
        list.value.subscriptions
            .filter { it.provider == PushProviderWire.Fcm && it.disabledAt == null }
            .forEach { subscription ->
                if (client.deletePushSubscription(subscription.id) is GatewayApiResult.Success) {
                    revoked += 1
                }
            }
        markUnpaired()
        return revoked
    }

    fun markUnpaired() {
        mutableState.value = mutableState.value.copy(
            registrationStatus = PushRegistrationStatus.Unpaired,
            registeredTokenSuffix = null,
            registrationFailure = null,
        )
    }

    fun recordReceivedHint(hint: PushHintPayload) {
        mutableState.value = mutableState.value.copy(
            lastHintReceivedAt = Instant.now(clock).toString(),
            lastHintSummary = "${hint.category.label()}: ${hint.reason.ifBlank { hint.id }}",
        )
    }
}

data class PushDeliveryState(
    val registrationStatus: PushRegistrationStatus = PushRegistrationStatus.NotRegistered,
    val registeredTokenSuffix: String? = null,
    val registrationFailure: String? = null,
    val lastHintReceivedAt: String? = null,
    val lastHintSummary: String? = null,
)

enum class PushRegistrationStatus {
    Unpaired,
    NotRegistered,
    Registering,
    Registered,
    AuthExpired,
    Failed,
}

private fun String.safeSuffix(): String = takeLast(6)

private fun PushHintCategoryWire.label(): String {
    return when (this) {
        PushHintCategoryWire.Notifications -> "notification"
        PushHintCategoryWire.Agents -> "agent"
        PushHintCategoryWire.Helpers -> "helper"
        PushHintCategoryWire.Update -> "update"
        PushHintCategoryWire.Session -> "session"
    }
}

private fun GatewayApiError.isUnauthorized(): Boolean {
    return this is GatewayApiError.Http &&
        (statusCode == 401 || apiError?.code == ApiErrorCodeWire.Unauthorized)
}

private fun GatewayApiError.toSafePushMessage(): String {
    return when (this) {
        is GatewayApiError.Http -> apiError?.message ?: "Gateway returned HTTP $statusCode"
        is GatewayApiError.InvalidJson -> "Gateway returned invalid JSON"
        is GatewayApiError.Transport -> "Gateway unavailable: ${kind.name.lowercase()}"
    }
}
