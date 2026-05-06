package org.sase.mobile.data.api

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.GatewayWireSchemaVersion
import org.sase.mobile.data.api.dto.PairFinishRequestWire
import org.sase.mobile.data.api.dto.PairingDeviceMetadataWire
import org.sase.mobile.data.api.dto.PairStartRequestWire
import org.sase.mobile.testing.FakeGateway

class GatewayApiClientTest {
    @Test
    fun normalizesGatewayBaseUrlToVersionedApiRoot() {
        assertThat(normalized("http://127.0.0.1:7629")).isEqualTo("http://127.0.0.1:7629/api/v1/")
        assertThat(normalized("http://127.0.0.1:7629/")).isEqualTo("http://127.0.0.1:7629/api/v1/")
        assertThat(normalized("http://127.0.0.1:7629/api/v1")).isEqualTo("http://127.0.0.1:7629/api/v1/")
        assertThat(normalized("http://127.0.0.1:7629/api/v1/")).isEqualTo("http://127.0.0.1:7629/api/v1/")
    }

    @Test
    fun rejectsBaseUrlsWithUnexpectedPathsQueriesOrFragments() {
        assertRejectsBaseUrl("http://127.0.0.1:7629/other")
        assertRejectsBaseUrl("http://127.0.0.1:7629?token=secret")
        assertRejectsBaseUrl("http://127.0.0.1:7629/#fragment")
    }

    @Test
    fun sendsBearerTokenOnlyForAuthenticatedRoutes() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.HealthSuccess))
            gateway.enqueueJson(readResource(GatewayFixturePaths.SessionSuccess))

            val client = client(gateway.baseUrl) { "sase_mobile_secret" }

            assertThat(client.health().isSuccess()).isTrue()
            assertThat(client.session().isSuccess()).isTrue()

            assertThat(gateway.takeRequest().getHeader("Authorization")).isNull()
            assertThat(gateway.takeRequest().getHeader("Authorization"))
                .isEqualTo("Bearer sase_mobile_secret")
        }
    }

    @Test
    fun doesNotFollowRedirectsThatCouldLeakBearerTokens() = runTest {
        FakeGateway().use { gateway ->
            FakeGateway().use { otherGateway ->
                gateway.enqueueJson(
                    body = "",
                    statusCode = 302,
                    headers = mapOf("Location" to otherGateway.baseUrl.resolve("/api/v1/session").toString()),
                )

                val result = client(gateway.baseUrl) { "sase_mobile_secret" }.session()

                val error = result.failureError() as GatewayApiError.Http
                assertThat(error.statusCode).isEqualTo(302)
                assertThat(gateway.takeRequest().getHeader("Authorization"))
                    .isEqualTo("Bearer sase_mobile_secret")
                assertThat(otherGateway.requestCount).isEqualTo(0)
                assertThat(error.toString()).doesNotContain("sase_mobile_secret")
            }
        }
    }

    @Test
    fun postsPairingRequestsWithoutAuthorization() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.PairStartSuccess))
            gateway.enqueueJson(readResource(GatewayFixturePaths.PairFinishSuccess))

            val client = client(gateway.baseUrl) { "sase_mobile_secret" }

            val pairStart = client.startPairing(PairStartRequestWire(hostLabel = "workstation"))
            val pairFinish = client.finishPairing(
                PairFinishRequestWire(
                    pairingId = "pair_abc123",
                    code = "123456",
                    device = PairingDeviceMetadataWire(
                        displayName = "Pixel 9",
                        platform = "android",
                        appVersion = "0.1.0",
                    ),
                ),
            )

            assertThat(pairStart.isSuccess()).isTrue()
            assertThat(pairFinish.isSuccess()).isTrue()

            val startRequest = gateway.takeRequest()
            val finishRequest = gateway.takeRequest()
            assertThat(startRequest.path).isEqualTo("/api/v1/session/pair/start")
            assertThat(finishRequest.path).isEqualTo("/api/v1/session/pair/finish")
            assertThat(startRequest.getHeader("Authorization")).isNull()
            assertThat(finishRequest.getHeader("Authorization")).isNull()
            assertThat(startRequest.getHeader("Content-Type")).contains("application/json")
            assertThat(startRequest.body.readUtf8()).contains("\"host_label\":\"workstation\"")
            assertThat(finishRequest.body.readUtf8()).contains("\"pairing_id\":\"pair_abc123\"")
        }
    }

    @Test
    fun getsNotificationListDetailAndStateMutations() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.NotificationsMixed))
            gateway.enqueueJson(readResource(GatewayFixturePaths.NotificationDetailPlan))
            gateway.enqueueJson(MutationReadResponse)
            gateway.enqueueJson(MutationDismissResponse)

            val client = client(gateway.baseUrl) { "sase_mobile_secret" }

            val listResult = client.notifications(
                NotificationListQuery(
                    unreadOnly = true,
                    includeDismissed = false,
                    includeSilent = true,
                    limit = 25,
                    newerThan = "0000000000000002",
                ),
            )

            assertThat(listResult.isSuccess()).isTrue()
            assertThat(client.notificationDetail("plan0001-review").isSuccess()).isTrue()
            assertThat(client.markNotificationRead("plan0001-review").isSuccess()).isTrue()
            assertThat(client.dismissNotification("plan0001-review").isSuccess()).isTrue()

            val listRequest = gateway.takeRequest()
            assertThat(listRequest.path).isEqualTo(
                "/api/v1/notifications?unread_only=true&include_dismissed=false&include_silent=true&limit=25&newer_than=0000000000000002",
            )
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications/plan0001-review")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications/plan0001-review/mark-read")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications/plan0001-review/dismiss")
        }
    }

    @Test
    fun decodesStructuredApiErrors() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(
                body = readResource(GatewayFixturePaths.ErrorFixtures.first()),
                statusCode = 401,
            )

            val result = client(gateway.baseUrl) { "sase_mobile_secret" }.session()

            val error = result.failureError() as GatewayApiError.Http
            assertThat(error.statusCode).isEqualTo(401)
            assertThat(error.apiError?.schemaVersion).isEqualTo(GatewayWireSchemaVersion)
            assertThat(error.apiError?.code).isEqualTo(ApiErrorCodeWire.Unauthorized)
            assertThat(error.apiError?.message).contains("bearer token")
        }
    }

    @Test
    fun mapsMalformedJsonToAppLevelError() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson("{ malformed json")

            val result = client(gateway.baseUrl).health()

            val error = result.failureError() as GatewayApiError.InvalidJson
            assertThat(error.statusCode).isEqualTo(200)
            assertThat(error.message).isNotEmpty()
        }
    }

    @Test
    fun mapsConnectionFailureToTransportError() = runTest {
        val gateway = FakeGateway()
        val baseUrl = gateway.baseUrl
        gateway.close()

        val result = client(baseUrl).health()

        val error = result.failureError() as GatewayApiError.Transport
        assertThat(error.kind).isEqualTo(GatewayTransportErrorKind.ConnectionRefused)
    }

    @Test
    fun mapsTransportExceptionsDeterministically() {
        assertThat(UnknownHostException("missing").toGatewayTransportError().kind)
            .isEqualTo(GatewayTransportErrorKind.Dns)
        assertThat(ConnectException("refused").toGatewayTransportError().kind)
            .isEqualTo(GatewayTransportErrorKind.ConnectionRefused)
        assertThat(SocketTimeoutException("timeout").toGatewayTransportError().kind)
            .isEqualTo(GatewayTransportErrorKind.Timeout)
        assertThat(SSLHandshakeException("tls").toGatewayTransportError().kind)
            .isEqualTo(GatewayTransportErrorKind.TlsOrCleartextPolicy)
        assertThat(UnknownServiceException("CLEARTEXT communication not permitted").toGatewayTransportError().kind)
            .isEqualTo(GatewayTransportErrorKind.TlsOrCleartextPolicy)
        assertThat(IOException("network").toGatewayTransportError().kind)
            .isEqualTo(GatewayTransportErrorKind.Network)
    }

    private fun normalized(url: String): String {
        return GatewayApiClient.normalizeBaseUrl(url).toString()
    }

    private fun assertRejectsBaseUrl(url: String) {
        try {
            GatewayApiClient.normalizeBaseUrl(url)
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected base URL to be rejected: $url")
    }

    private fun client(
        baseUrl: HttpUrl,
        tokenProvider: () -> String? = { null },
    ): GatewayApiClient {
        return GatewayApiClient(
            baseUrl = baseUrl.toString(),
            bearerTokenProvider = tokenProvider,
            client = OkHttpClient.Builder()
                .followRedirects(true)
                .build(),
        )
    }

    private fun GatewayApiResult<*>.failureError(): GatewayApiError {
        return when (this) {
            is GatewayApiResult.Failure -> error
            is GatewayApiResult.Success -> throw AssertionError("Expected failure, got $value")
        }
    }

    private fun GatewayApiResult<*>.isSuccess(): Boolean {
        return this is GatewayApiResult.Success
    }

    private companion object {
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
