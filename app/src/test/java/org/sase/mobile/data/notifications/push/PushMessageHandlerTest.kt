package org.sase.mobile.data.notifications.push

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.api.NetworkAvailability
import org.sase.mobile.data.api.readResource
import org.sase.mobile.data.notifications.InMemoryNotificationCache
import org.sase.mobile.data.notifications.NotificationInboxState
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.session.DeviceMetadata
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.testing.FakeGateway

class PushMessageHandlerTest {
    @Test
    fun receivedHintRendersLocalNotificationAndTriggersAuthoritativeRefresh() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.NotificationsMixed))
            val sessionStorage = InMemoryHostSessionStorage(pairedSession(gateway.baseUrl))
            val repository = repository(gateway.baseUrl, sessionStorage)
            val rendered = mutableListOf<String>()
            val registrationManager = registrationManager(gateway.baseUrl, sessionStorage)
            val handler = PushMessageHandler(
                sessionStorage = sessionStorage,
                notificationRepository = repository,
                renderer = { hint ->
                    rendered += hint.notificationId.orEmpty()
                    true
                },
                registrationManager = registrationManager,
                scope = CoroutineScope(SupervisorJob()),
            )

            val result = handler.handleDataMessage(notificationHint())
            waitUntilInbox(repository) { it.cards.isNotEmpty() }

            assertThat(result).isEqualTo(PushMessageHandlingResult.Rendered("dev_pixel"))
            assertThat(rendered).containsExactly("plan0001-review")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications?include_dismissed=true")
            assertThat(registrationManager.state.value.lastHintSummary).isEqualTo("notification: created")
        }
    }

    @Test
    fun unpairedSessionDropsHintWithoutRendering() = runTest {
        val rendered = mutableListOf<String>()
        val sessionStorage = InMemoryHostSessionStorage()
        val handler = PushMessageHandler(
            sessionStorage = sessionStorage,
            notificationRepository = repository(
                GatewayApiClient.normalizeBaseUrl("http://127.0.0.1:7629/"),
                sessionStorage,
            ),
            renderer = { hint ->
                rendered += hint.eventId.orEmpty()
                true
            },
            registrationManager = registrationManager(
                GatewayApiClient.normalizeBaseUrl("http://127.0.0.1:7629/"),
                sessionStorage,
            ),
            scope = CoroutineScope(SupervisorJob()),
        )

        val result = handler.handleDataMessage(notificationHint())

        assertThat(result).isEqualTo(PushMessageHandlingResult.DroppedUnpaired)
        assertThat(rendered).isEmpty()
    }

    @Test
    fun offlineReceiptRendersButDoesNotStartRefresh() = runTest {
        FakeGateway().use { gateway ->
            val sessionStorage = InMemoryHostSessionStorage(pairedSession(gateway.baseUrl))
            val repository = repository(gateway.baseUrl, sessionStorage)
            val handler = PushMessageHandler(
                sessionStorage = sessionStorage,
                notificationRepository = repository,
                renderer = { true },
                registrationManager = registrationManager(gateway.baseUrl, sessionStorage),
                networkAvailability = NetworkAvailability { false },
                scope = CoroutineScope(SupervisorJob()),
            )

            val result = handler.handleDataMessage(notificationHint())

            assertThat(result).isEqualTo(PushMessageHandlingResult.Rendered("dev_pixel"))
            assertThat(gateway.requestCount).isEqualTo(0)
        }
    }

    private fun notificationHint(): Map<String, String> {
        return mapOf(
            "schema_version" to "1",
            "id" to "0000000000000042",
            "category" to "notifications",
            "reason" to "created",
            "title" to "Plan review",
            "body" to "Open SASE Mobile",
            "created_at" to "2026-05-07T12:00:00Z",
            "notification_id" to "plan0001-review",
        )
    }

    private fun repository(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage,
    ): NotificationRepository {
        return NotificationRepository(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault("token-secret"),
            cache = InMemoryNotificationCache(),
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(
                    baseUrl = url,
                    bearerTokenProvider = tokenProvider,
                    client = OkHttpClient(),
                )
            },
            sseClientFactory = { url, tokenProvider ->
                GatewaySseClient(
                    baseUrl = url,
                    bearerTokenProvider = tokenProvider,
                    client = OkHttpClient(),
                )
            },
            clock = Clock.fixed(Now, ZoneOffset.UTC),
            scope = CoroutineScope(SupervisorJob()),
        )
    }

    private fun registrationManager(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage,
    ): PushRegistrationManager {
        return PushRegistrationManager(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault("token-secret"),
            tokenProvider = object : PushTokenProvider {
                override suspend fun currentToken(): PushTokenResult = PushTokenResult.Success("fcm-token")
            },
            appInstanceIdProvider = { "app-instance-1" },
            deviceMetadataProvider = { DeviceMetadata("Pixel 9", "0.1.0") },
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(
                    baseUrl = url,
                    bearerTokenProvider = tokenProvider,
                    client = OkHttpClient(),
                )
            },
            clock = Clock.fixed(Now, ZoneOffset.UTC),
            scope = CoroutineScope(SupervisorJob()),
        )
    }

    private suspend fun waitUntilInbox(
        repository: NotificationRepository,
        predicate: (NotificationInboxState) -> Boolean,
    ) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (!predicate(repository.inbox.value)) {
                    delay(10)
                }
            }
        }
    }

    private fun pairedSession(baseUrl: HttpUrl): PairedHostSession {
        return PairedHostSession(
            hostLabel = "workstation",
            baseUrl = GatewayApiClient.normalizeBaseUrl(baseUrl.toString()).toString(),
            deviceId = "dev_pixel",
            deviceDisplayName = "Pixel 9",
            pairedAt = "2026-05-06T14:00:00Z",
            lastSessionCheckedAt = "2026-05-06T14:01:00Z",
        )
    }

    private companion object {
        val Now: Instant = Instant.parse("2026-05-07T12:00:00Z")
    }
}
