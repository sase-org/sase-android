package org.sase.mobile.testing

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.notifications.push.parsePushHintData
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

    @Test
    fun smokeHarnessServesGatewayRoutesAndValidatesAuthAndPairing() {
        FakeGateway().use { gateway ->
            gateway.installSmokeHarness()

            client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/health")!!)
                    .build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(response.body?.string()).contains("sase_gateway")
            }

            client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/session/pair/finish")!!)
                    .post(PairFinishBody.toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(response.body?.string()).contains(FakeGateway.SmokeAuthToken)
            }

            client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/notifications?include_dismissed=true")!!)
                    .build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(401)
            }

            client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/notifications?include_dismissed=true")!!)
                    .header("Authorization", "Bearer ${FakeGateway.SmokeAuthToken}")
                    .build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(response.body?.string()).contains("plan0001-review")
            }

            client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/events")!!)
                    .header("Authorization", "Bearer ${FakeGateway.SmokeAuthToken}")
                    .header("Accept", "text/event-stream")
                    .header("Last-Event-ID", "0000000000000002")
                    .build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(response.header("Content-Type")).contains("text/event-stream")
                assertThat(response.body?.string()).contains("resync_required")
            }
        }
    }

    @Test
    fun hardeningHarnessCoversPushSubscriptionRoutesAndHintFixtures() {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()

            client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/session/push-subscriptions")!!)
                    .header("Authorization", "Bearer ${FakeGateway.SmokeAuthToken}")
                    .post(PushSubscriptionBody.toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(response.body?.string()).contains("\"provider\":\"test\"")
            }

            client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/session/push-subscriptions")!!)
                    .header("Authorization", "Bearer ${FakeGateway.SmokeAuthToken}")
                    .build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(response.body?.string()).contains("sub_test_1")
            }

            client.newCall(
                Request.Builder()
                    .url(gateway.baseUrl.resolve("/api/v1/session/push-subscriptions/sub_test_1")!!)
                    .header("Authorization", "Bearer ${FakeGateway.SmokeAuthToken}")
                    .delete()
                    .build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(response.body?.string()).contains("\"revoked\":true")
            }

            val hint = parsePushHintData(pushHintFixtureMap())
            assertThat(hint?.notificationId).isEqualTo("plan0001-review")
            assertThat(hint?.toLocalHint()?.target.toString()).contains("plan0001-review")
        }
    }

    private fun pushHintFixtureMap(): Map<String, String> {
        return GatewayJson.format
            .parseToJsonElement(readResource(GatewayFixturePaths.PushHintNotification))
            .jsonObject
            .mapValues { (_, value) -> value.jsonPrimitive.content }
    }

    private companion object {
        val PairFinishBody = """
            {
              "schema_version": 1,
              "pairing_id": "${FakeGateway.SmokePairingId}",
              "code": "${FakeGateway.SmokePairingCode}",
              "device": {
                "display_name": "Pixel 9",
                "platform": "android",
                "app_version": "0.1.0"
              }
            }
        """.trimIndent()

        val PushSubscriptionBody = """
            {
              "schema_version": 1,
              "provider": "test",
              "provider_token": "test-token",
              "app_instance_id": "app-instance-1",
              "platform": "android",
              "app_version": "0.1.0",
              "device_display_name": "Pixel 9",
              "hint_categories": ["notifications", "agents"]
            }
        """.trimIndent()
    }
}
