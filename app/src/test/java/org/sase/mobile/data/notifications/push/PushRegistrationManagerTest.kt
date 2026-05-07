package org.sase.mobile.data.notifications.push

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.session.DeviceMetadata
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.testing.FakeGateway

class PushRegistrationManagerTest {
    @Test
    fun registersFcmTokenWithAuthenticatedGateway() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(RegisterResponse)
            val manager = manager(gateway.baseUrl)

            assertThat(manager.registerToken("fcm-token-secret")).isTrue()

            val request = gateway.takeRequest()
            val body = request.body.readUtf8()
            assertThat(request.path).isEqualTo("/api/v1/session/push-subscriptions")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer bearer-secret")
            assertThat(body).contains("\"provider\":\"fcm\"")
            assertThat(body).contains("\"provider_token\":\"fcm-token-secret\"")
            assertThat(body).contains("\"app_instance_id\":\"app-instance-1\"")
            assertThat(manager.state.value.registrationStatus).isEqualTo(PushRegistrationStatus.Registered)
            assertThat(manager.state.value.registeredTokenSuffix).isEqualTo("secret")
        }
    }

    @Test
    fun unpairedSessionDoesNotPersistActiveRegistration() = runTest {
        val manager = manager(
            sessionStorage = InMemoryHostSessionStorage(),
        )

        assertThat(manager.registerToken("fcm-token-secret")).isFalse()

        assertThat(manager.state.value.registrationStatus).isEqualTo(PushRegistrationStatus.Unpaired)
    }

    @Test
    fun revokeBeforeForgetDeletesActiveFcmSubscriptions() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(ListResponse)
            gateway.enqueueJson(DeleteResponse("sub_fcm_1"))
            val manager = manager(gateway.baseUrl)

            assertThat(manager.revokeActiveSubscriptionsBeforeForget()).isEqualTo(1)

            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/session/push-subscriptions")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/session/push-subscriptions/sub_fcm_1")
            assertThat(manager.state.value.registrationStatus).isEqualTo(PushRegistrationStatus.Unpaired)
        }
    }

    @Test
    fun recordsLastReceivedHintWithoutStoringProviderToken() = runTest {
        val manager = manager()

        manager.recordReceivedHint(
            parsePushHintData(
                mapOf(
                    "id" to "event-1",
                    "category" to "agents",
                    "reason" to "changed",
                    "title" to "Agent changed",
                    "body" to "Open SASE Mobile",
                ),
            ) ?: error("expected hint"),
        )

        assertThat(manager.state.value.lastHintReceivedAt).isEqualTo(Now.toString())
        assertThat(manager.state.value.lastHintSummary).isEqualTo("agent: changed")
        assertThat(manager.state.value.registeredTokenSuffix).isNull()
    }

    private fun manager(
        baseUrl: HttpUrl = GatewayApiClient.normalizeBaseUrl("http://127.0.0.1:7629/"),
        sessionStorage: InMemoryHostSessionStorage = InMemoryHostSessionStorage(pairedSession(baseUrl)),
    ): PushRegistrationManager {
        return PushRegistrationManager(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault("bearer-secret"),
            tokenProvider = StaticPushTokenProvider("fcm-token-secret"),
            appInstanceIdProvider = { "app-instance-1" },
            deviceMetadataProvider = { DeviceMetadata("Pixel 9", "0.1.0") },
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(
                    baseUrl = url,
                    bearerTokenProvider = tokenProvider,
                )
            },
            clock = Clock.fixed(Now, ZoneOffset.UTC),
            scope = CoroutineScope(SupervisorJob()),
        )
    }

    private class StaticPushTokenProvider(
        private val token: String,
    ) : PushTokenProvider {
        override suspend fun currentToken(): PushTokenResult = PushTokenResult.Success(token)
    }

    private fun pairedSession(baseUrl: HttpUrl): PairedHostSession {
        return PairedHostSession(
            hostLabel = "workstation",
            baseUrl = baseUrl.toString(),
            deviceId = "dev_pixel",
            deviceDisplayName = "Pixel 9",
            pairedAt = "2026-05-06T00:00:00Z",
            lastSessionCheckedAt = null,
        )
    }

    private companion object {
        val Now: Instant = Instant.parse("2026-05-07T12:00:00Z")

        val RegisterResponse = """
            {
              "schema_version": 1,
              "created": true,
              "subscription": {
                "schema_version": 1,
                "id": "sub_fcm_1",
                "provider": "fcm",
                "provider_token": "fcm-token-secret",
                "app_instance_id": "app-instance-1",
                "device_id": "dev_pixel",
                "device_display_name": "Pixel 9",
                "platform": "android",
                "app_version": "0.1.0",
                "hint_categories": ["notifications", "agents", "helpers", "update", "session"],
                "enabled_at": "2026-05-07T12:00:00Z",
                "last_seen_at": null,
                "disabled_at": null
              }
            }
        """.trimIndent()

        val ListResponse = """
            {
              "schema_version": 1,
              "subscriptions": [
                {
                  "schema_version": 1,
                  "id": "sub_fcm_1",
                  "provider": "fcm",
                  "provider_token": "fcm-token-secret",
                  "app_instance_id": "app-instance-1",
                  "device_id": "dev_pixel",
                  "device_display_name": "Pixel 9",
                  "platform": "android",
                  "app_version": "0.1.0",
                  "hint_categories": ["notifications"],
                  "enabled_at": "2026-05-07T12:00:00Z",
                  "last_seen_at": null,
                  "disabled_at": null
                }
              ]
            }
        """.trimIndent()

        fun DeleteResponse(id: String): String {
            return """
                {
                  "schema_version": 1,
                  "revoked": true,
                  "subscription": {
                    "schema_version": 1,
                    "id": "$id",
                    "provider": "fcm",
                    "provider_token": "fcm-token-secret",
                    "app_instance_id": "app-instance-1",
                    "device_id": "dev_pixel",
                    "device_display_name": "Pixel 9",
                    "platform": "android",
                    "app_version": "0.1.0",
                    "hint_categories": ["notifications"],
                    "enabled_at": "2026-05-07T12:00:00Z",
                    "last_seen_at": null,
                    "disabled_at": "2026-05-07T12:01:00Z"
                  }
                }
            """.trimIndent()
        }
    }
}
