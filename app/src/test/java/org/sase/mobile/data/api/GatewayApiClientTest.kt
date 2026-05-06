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
import org.sase.mobile.data.api.dto.HitlActionChoiceWire
import org.sase.mobile.data.api.dto.HitlActionRequestWire
import org.sase.mobile.data.api.dto.MobileAgentImageLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileAgentKillRequestWire
import org.sase.mobile.data.api.dto.MobileAgentListRequestWire
import org.sase.mobile.data.api.dto.MobileAgentRetryRequestWire
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileBeadListRequestWire
import org.sase.mobile.data.api.dto.MobileBeadShowRequestWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListRequestWire
import org.sase.mobile.data.api.dto.MobileUpdateStartRequestWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogRequestWire
import org.sase.mobile.data.api.dto.PairFinishRequestWire
import org.sase.mobile.data.api.dto.PairingDeviceMetadataWire
import org.sase.mobile.data.api.dto.PairStartRequestWire
import org.sase.mobile.data.api.dto.PlanActionChoiceWire
import org.sase.mobile.data.api.dto.PlanActionRequestWire
import org.sase.mobile.data.api.dto.QuestionActionChoiceWire
import org.sase.mobile.data.api.dto.QuestionActionRequestWire
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
    fun executesEpicSixRoutesWithTypedRequestsAndResponses() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()

            val client = client(gateway.baseUrl) { FakeGateway.SmokeAuthToken }

            assertThat(
                client.submitPlanAction(
                    PlanActionRequestWire(
                        prefix = "plan0001",
                        choice = PlanActionChoiceWire.Approve,
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.submitHitlAction(
                    HitlActionRequestWire(
                        prefix = "hitl0001",
                        choice = HitlActionChoiceWire.Feedback,
                        feedback = "Looks good",
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.submitQuestionAction(
                    QuestionActionRequestWire(
                        prefix = "question0001",
                        choice = QuestionActionChoiceWire.Answer,
                        selectedOptionId = "yes",
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.agents(
                    MobileAgentListRequestWire(
                        includeRecent = true,
                        status = "running",
                        project = "sase",
                        limit = 10,
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(client.agentResumeOptions().isSuccess()).isTrue()
            assertThat(
                client.launchAgent(
                    MobileAgentTextLaunchRequestWire(
                        prompt = "#gh:feature\nRun tests",
                        requestId = "launch-1",
                        model = "gpt-5.4",
                        project = "sase",
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.launchImageAgent(
                    MobileAgentImageLaunchRequestWire(
                        prompt = "Inspect this image",
                        requestId = "image-1",
                        originalFilename = "screen.png",
                        contentType = "image/png",
                        byteLength = 11,
                        base64Image = "aGVsbG8gd29ybGQ=",
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.killAgent(
                    name = "mobile-demo",
                    request = MobileAgentKillRequestWire(reason = "user requested"),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.retryAgent(
                    name = "mobile-failed",
                    request = MobileAgentRetryRequestWire(requestId = "retry-1"),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.changespecTags(
                    MobileChangeSpecTagListRequestWire(project = "sase", limit = 5),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.xpromptCatalog(
                    MobileXpromptCatalogRequestWire(
                        project = "sase",
                        tag = "mobile",
                        includePdf = true,
                        limit = 20,
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.beads(
                    MobileBeadListRequestWire(
                        project = "sase",
                        status = "in_progress",
                        includeClosed = true,
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.beadDetail(
                    MobileBeadShowRequestWire(
                        beadId = "sase-26.6.1",
                        project = "sase",
                    ),
                ).isSuccess(),
            ).isTrue()
            assertThat(
                client.startUpdate(MobileUpdateStartRequestWire(requestId = "update-1")).isSuccess(),
            ).isTrue()
            assertThat(client.updateStatus("update-job-1").isSuccess()).isTrue()
            val attachment = client.downloadAttachment("token_1")
            assertThat(attachment.isSuccess()).isTrue()

            val planRequest = gateway.takeRequest()
            val hitlRequest = gateway.takeRequest()
            val questionRequest = gateway.takeRequest()
            val agentsRequest = gateway.takeRequest()
            val planBody = planRequest.body.readUtf8()
            assertThat(planRequest.path).isEqualTo("/api/v1/actions/plan/plan0001/approve")
            assertThat(planBody).doesNotContain("\"prefix\"")
            assertThat(planBody).doesNotContain("\"choice\"")
            assertThat(hitlRequest.path).isEqualTo("/api/v1/actions/hitl/hitl0001/feedback")
            assertThat(questionRequest.path).isEqualTo("/api/v1/actions/question/question0001/answer")
            assertThat(agentsRequest.path).isEqualTo(
                "/api/v1/agents?include_recent=true&status=running&project=sase&limit=10",
            )

            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/launch")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/launch-image")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/mobile-demo/kill")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/mobile-failed/retry")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/changespec-tags?project=sase&limit=5")
            assertThat(gateway.takeRequest().path)
                .isEqualTo("/api/v1/xprompts/catalog?project=sase&tag=mobile&include_pdf=true&limit=20")
            assertThat(gateway.takeRequest().path)
                .isEqualTo("/api/v1/beads?project=sase&all_projects=false&status=in_progress&include_closed=true")
            assertThat(gateway.takeRequest().path)
                .isEqualTo("/api/v1/beads/sase-26.6.1?project=sase&all_projects=false")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/update/start")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/update/update-job-1")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/attachments/token_1")
        }
    }

    @Test
    fun mapsEpicSixStructuredFailureCodes() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()

            val client = client(gateway.baseUrl) { FakeGateway.SmokeAuthToken }

            val stale = client.submitPlanAction(
                PlanActionRequestWire(prefix = "stale", choice = PlanActionChoiceWire.Approve),
            ).failureError() as GatewayApiError.Http
            val handled = client.submitPlanAction(
                PlanActionRequestWire(prefix = "handled", choice = PlanActionChoiceWire.Approve),
            ).failureError() as GatewayApiError.Http
            val ambiguous = client.submitPlanAction(
                PlanActionRequestWire(prefix = "ambiguous", choice = PlanActionChoiceWire.Approve),
            ).failureError() as GatewayApiError.Http
            val unsupported = client.submitPlanAction(
                PlanActionRequestWire(prefix = "unsupported", choice = PlanActionChoiceWire.Approve),
            ).failureError() as GatewayApiError.Http

            assertThat(stale.apiError?.code).isEqualTo(ApiErrorCodeWire.GoneStale)
            assertThat(handled.apiError?.code).isEqualTo(ApiErrorCodeWire.ConflictAlreadyHandled)
            assertThat(ambiguous.apiError?.code).isEqualTo(ApiErrorCodeWire.AmbiguousPrefix)
            assertThat(unsupported.apiError?.code).isEqualTo(ApiErrorCodeWire.UnsupportedAction)
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
