package org.sase.mobile.testing

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.readResource

class FakeGatewayTest {
    private val client = OkHttpClient()

    @Test
    fun fakeGatewayServesJsonFixturesAndRecordsRequests() {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.HealthSuccess))

            val response = client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/health")!!)
                    .build(),
            ).execute()

            assertThat(response.code).isEqualTo(200)
            assertThat(response.body?.string()).contains("sase_gateway")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/health")
        }
    }

    @Test
    fun fakeGatewayCanServeSseRecords() {
        FakeGateway().use { gateway ->
            gateway.enqueueSse(readResource(GatewayFixturePaths.EventHeartbeat))

            val response = client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/events")!!)
                    .header("Accept", "text/event-stream")
                    .build(),
            ).execute()

            assertThat(response.header("Content-Type")).contains("text/event-stream")
            assertThat(response.body?.string()).contains("data:")
            assertThat(gateway.takeRequest().getHeader("Accept")).isEqualTo("text/event-stream")
        }
    }
}
