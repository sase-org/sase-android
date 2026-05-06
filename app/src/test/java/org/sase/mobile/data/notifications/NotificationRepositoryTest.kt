package org.sase.mobile.data.notifications

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.api.dto.HelpersChangedEventPayloadWire
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.MobileNotificationListResponseWire
import org.sase.mobile.data.api.readResource
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.testing.FakeGateway

class NotificationRepositoryTest {
    @Test
    fun loadCachedStateKeepsInboxVisibleAndMarksItStale() = runTest {
        val cards = notificationCards()
        val repository = repository(
            cache = InMemoryNotificationCache(
                NotificationCacheSnapshot(
                    cards = cards,
                    syncState = NotificationSyncState(
                        lastEventId = "0000000000000009",
                        lastFullRefreshAt = "2026-05-06T15:00:00Z",
                    ),
                ),
            ),
        )

        repository.loadCachedState()

        assertThat(repository.inbox.value.cards).hasSize(cards.size)
        assertThat(repository.inbox.value.isStale).isTrue()
        assertThat(repository.inbox.value.lastEventId).isEqualTo("0000000000000009")
    }

    @Test
    fun fullRefreshFetchesAuthoritativeNotificationStateAndCachesSessionSummary() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.NotificationsMixed))
            val cache = InMemoryNotificationCache()
            val repository = repository(gateway.baseUrl, cache = cache)

            assertThat(repository.fullRefresh()).isTrue()

            val request = gateway.takeRequest()
            assertThat(request.path).isEqualTo("/api/v1/notifications?include_dismissed=true")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-secret")
            assertThat(repository.inbox.value.cards).hasSize(7)
            assertThat(repository.inbox.value.isStale).isFalse()
            assertThat(cache.read().sessionSummary?.deviceId).isEqualTo("dev_pixel")
            assertThat(cache.read().syncState.lastFullRefreshAt).isEqualTo(Now.toString())
        }
    }

    @Test
    fun startCanRestartAfterSessionIsAddedAndPerformFullRefresh() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.NotificationsMixed))
            gateway.enqueueJson(
                body = readResource(GatewayFixturePaths.ErrorFixtures.first()),
                statusCode = 401,
            )
            val storage = InMemoryHostSessionStorage()
            val repository = repository(
                gateway.baseUrl,
                sessionStorage = storage,
            )

            repository.start()
            advanceUntilIdle()

            assertThat(repository.connection.value).isEqualTo(NotificationConnectionState.LoggedOut)
            assertThat(gateway.requestCount).isEqualTo(0)

            storage.write(pairedSession(gateway.baseUrl))
            repository.start()

            val refreshRequest = gateway.takeRequest()
            val sseRequest = gateway.takeRequest()
            advanceUntilIdle()

            assertThat(refreshRequest.path).isEqualTo("/api/v1/notifications?include_dismissed=true")
            assertThat(sseRequest.path).isEqualTo("/api/v1/events")
            assertThat(repository.inbox.value.cards).hasSize(7)
            repository.stop()
        }
    }

    @Test
    fun sseReconnectUsesLastEventIdAndRefreshesAfterReconnect() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueSse(readResource(GatewayFixturePaths.EventHeartbeat))
            gateway.enqueueJson(readResource(GatewayFixturePaths.NotificationsMixed))
            gateway.enqueueSse(readResource(GatewayFixturePaths.EventHeartbeat))
            val cache = InMemoryNotificationCache(
                NotificationCacheSnapshot(
                    syncState = NotificationSyncState(lastEventId = "0000000000000001"),
                ),
            )
            val repository = repository(gateway.baseUrl, cache = cache)

            repository.runSseLoop(maxConnections = 2)

            val firstSse = gateway.takeRequest()
            val reconnectRefresh = gateway.takeRequest()
            val secondSse = gateway.takeRequest()
            assertThat(firstSse.path).isEqualTo("/api/v1/events")
            assertThat(firstSse.getHeader("Last-Event-ID")).isEqualTo("0000000000000001")
            assertThat(reconnectRefresh.path).isEqualTo("/api/v1/notifications?include_dismissed=true")
            assertThat(secondSse.getHeader("Last-Event-ID")).isEqualTo("0000000000000001")
            assertThat(repository.inbox.value.cards).hasSize(7)
        }
    }

    @Test
    fun resyncRequiredEventTriggersFullRefreshAndPersistsEventCursor() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueSse(readResource(GatewayFixturePaths.EventResyncRequired))
            gateway.enqueueJson(readResource(GatewayFixturePaths.NotificationsMixed))
            val cache = InMemoryNotificationCache()
            val repository = repository(gateway.baseUrl, cache = cache)

            repository.runSseLoop(maxConnections = 1)

            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/events")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications?include_dismissed=true")
            assertThat(cache.read().syncState.lastEventId).isEqualTo("0000000000000003")
            assertThat(repository.inbox.value.cards).hasSize(7)
        }
    }

    @Test
    fun sseAuthFailureStopsInLoggedOutState() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(
                body = readResource(GatewayFixturePaths.ErrorFixtures.first()),
                statusCode = 401,
            )
            val repository = repository(gateway.baseUrl)

            repository.runSseLoop(maxConnections = 1)

            assertThat(repository.connection.value).isEqualTo(NotificationConnectionState.LoggedOut)
        }
    }

    @Test
    fun helpersChangedEventIsForwardedWithStructuredPayload() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueSse(readResource(GatewayFixturePaths.EventHelpersChanged))
            val helperEvents = mutableListOf<HelpersChangedEventPayloadWire>()
            val repository = repository(
                gateway.baseUrl,
                onHelpersChanged = { payload -> helperEvents += payload },
            )

            repository.runSseLoop(maxConnections = 1)

            assertThat(helperEvents).hasSize(1)
            assertThat(helperEvents.single().helper).isEqualTo("update")
            assertThat(helperEvents.single().jobId).isEqualTo("update-job-1")
            assertThat(repository.helperEvents.value).isEqualTo(1)
        }
    }

    @Test
    fun markReadAndDismissUpdateLocalCacheFromMutationResponses() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(MutationReadResponse)
            gateway.enqueueJson(MutationDismissResponse)
            val cache = InMemoryNotificationCache(
                NotificationCacheSnapshot(cards = notificationCards()),
            )
            val repository = repository(gateway.baseUrl, cache = cache)
            repository.loadCachedState()

            assertThat(repository.markRead("plan0001-review")).isTrue()
            assertThat(repository.dismiss("plan0001-review")).isTrue()

            val updated = cache.read().cards.single { it.id == "plan0001-review" }
            assertThat(updated.read).isTrue()
            assertThat(updated.dismissed).isTrue()
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications/plan0001-review/mark-read")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications/plan0001-review/dismiss")
        }
    }

    private fun repository(
        baseUrl: HttpUrl = GatewayApiClient.normalizeBaseUrl("http://127.0.0.1:7629/"),
        sessionStorage: InMemoryHostSessionStorage = InMemoryHostSessionStorage(pairedSession(baseUrl)),
        cache: NotificationCache = InMemoryNotificationCache(),
        onHelpersChanged: suspend (HelpersChangedEventPayloadWire) -> Unit = {},
    ): NotificationRepository {
        return NotificationRepository(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault("token-secret"),
            cache = cache,
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
            delayProvider = { _ -> },
            onHelpersChanged = onHelpersChanged,
            scope = this,
        )
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

    private fun notificationCards() = GatewayJson.format
        .decodeFromString(
            MobileNotificationListResponseWire.serializer(),
            readResource(GatewayFixturePaths.NotificationsMixed),
        )
        .notifications

    private companion object {
        val Now: Instant = Instant.parse("2026-05-06T15:20:00Z")

        val MutationReadResponse = """
            {
              "schema_version": 1,
              "notification_id": "plan0001-review",
              "read": true,
              "dismissed": false,
              "changed": true
            }
        """.trimIndent()

        val MutationDismissResponse = """
            {
              "schema_version": 1,
              "notification_id": "plan0001-review",
              "read": true,
              "dismissed": true,
              "changed": true
            }
        """.trimIndent()
    }
}
