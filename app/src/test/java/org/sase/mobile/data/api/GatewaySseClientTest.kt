package org.sase.mobile.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.dto.EventPayloadTypeWire
import org.sase.mobile.testing.FakeGateway

class GatewaySseClientTest {
    @Test
    fun readsAuthenticatedEventStreamWithLastEventId() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueSse(
                readResource(GatewayFixturePaths.EventHeartbeat),
                readResource(GatewayFixturePaths.EventNotificationsChanged),
            )

            val result = client(gateway) { "sase_mobile_secret" }
                .readEvents(lastEventId = "0000000000000001")

            val events = (result as GatewaySseResult.Success).events
            assertThat(events.map { it.payload.type }).containsExactly(
                EventPayloadTypeWire.Heartbeat,
                EventPayloadTypeWire.NotificationsChanged,
            ).inOrder()

            val request = gateway.takeRequest()
            assertThat(request.path).isEqualTo("/api/v1/events")
            assertThat(request.getHeader("Accept")).isEqualTo("text/event-stream")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sase_mobile_secret")
            assertThat(request.getHeader("Last-Event-ID")).isEqualTo("0000000000000001")
        }
    }

    @Test
    fun mapsStructuredSseAuthFailure() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(
                body = readResource(GatewayFixturePaths.ErrorFixtures.first()),
                statusCode = 401,
            )

            val result = client(gateway) { "expired" }.readEvents()

            val error = (result as GatewaySseResult.Failure).error as GatewayApiError.Http
            assertThat(error.statusCode).isEqualTo(401)
            assertThat(error.apiError?.message).contains("bearer token")
        }
    }

    @Test
    fun reconnectPolicyAddsBoundedJitter() {
        val policy = SseReconnectPolicy(
            baseDelayMillis = 1_000,
            maxDelayMillis = 5_000,
            jitterRatio = 0.25,
            jitterSource = { 1.0 },
        )

        assertThat(policy.delayMillis(0)).isEqualTo(1_250)
        assertThat(policy.delayMillis(2)).isEqualTo(5_000)
        assertThat(policy.delayMillis(10)).isEqualTo(5_000)
    }

    private fun client(
        gateway: FakeGateway,
        tokenProvider: () -> String?,
    ): GatewaySseClient {
        return GatewaySseClient(
            baseUrl = gateway.baseUrl.toString(),
            bearerTokenProvider = tokenProvider,
            client = OkHttpClient(),
        )
    }
}

