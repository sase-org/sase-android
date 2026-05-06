package org.sase.mobile.testing

import java.io.Closeable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.MobileAgentImageLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileAgentKillRequestWire
import org.sase.mobile.data.api.dto.MobileAgentRetryRequestWire
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileUpdateStartRequestWire
import org.sase.mobile.data.api.dto.PairFinishRequestWire
import org.sase.mobile.data.api.readResource

class FakeGateway : Closeable {
    private val server = MockWebServer()
    private val jsonFormat = GatewayJson.format

    val baseUrl: HttpUrl
        get() = server.url("/")

    val requestCount: Int
        get() = server.requestCount

    fun enqueueJson(
        body: String,
        statusCode: Int = 200,
        headers: Map<String, String> = emptyMap(),
    ) {
        val response = MockResponse()
            .setResponseCode(statusCode)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        server.enqueue(response)
    }

    fun enqueueSse(vararg eventJson: String) {
        val body = eventJson.joinToString(separator = "\n\n") { it.toSseDataBlock() } + "\n\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )
    }

    fun installSmokeHarness(
        authToken: String = SmokeAuthToken,
        pairingId: String = SmokePairingId,
        pairingCode: String = SmokePairingCode,
        notificationsBody: String = readResource(GatewayFixturePaths.NotificationsMixed),
        notificationDetailBody: String = readResource(GatewayFixturePaths.NotificationDetailPlan),
        eventBodies: List<String> = listOf(readResource(GatewayFixturePaths.EventResyncRequired)),
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.method == "GET" && request.path == "/api/v1/health" -> {
                        jsonResponse(readResource(GatewayFixturePaths.HealthSuccess))
                    }

                    request.method == "POST" && request.path == "/api/v1/session/pair/finish" -> {
                        pairingResponse(request, pairingId, pairingCode)
                    }

                    request.method == "GET" && request.path == "/api/v1/session" -> {
                        authorized(request, authToken) ?: jsonResponse(readResource(GatewayFixturePaths.SessionSuccess))
                    }

                    request.method == "GET" && request.path?.startsWith("/api/v1/notifications?") == true -> {
                        authorized(request, authToken) ?: jsonResponse(notificationsBody)
                    }

                    request.method == "GET" && request.path == "/api/v1/notifications/plan0001-review" -> {
                        authorized(request, authToken) ?: jsonResponse(notificationDetailBody)
                    }

                    request.method == "POST" &&
                        request.path == "/api/v1/notifications/plan0001-review/mark-read" -> {
                        authorized(request, authToken) ?: jsonResponse(MarkReadResponse)
                    }

                    request.method == "POST" &&
                        request.path == "/api/v1/notifications/plan0001-review/dismiss" -> {
                        authorized(request, authToken) ?: jsonResponse(DismissResponse)
                    }

                    request.method == "GET" && request.path == "/api/v1/events" -> {
                        authorized(request, authToken) ?: sse(eventBodies)
                    }

                    else -> jsonResponse(NotFoundResponse, statusCode = 404)
                }
            }
        }
    }

    fun installEpicSixHarness(
        authToken: String = SmokeAuthToken,
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val routePath = request.path?.substringBefore("?")
                val unauthorized = authorized(request, authToken)
                if (routePath != "/api/v1/health" && unauthorized != null) {
                    return unauthorized
                }

                return when {
                    request.method == "POST" && routePath?.startsWith("/api/v1/actions/plan/") == true -> {
                        actionResponse(request)
                    }

                    request.method == "POST" && routePath?.startsWith("/api/v1/actions/hitl/") == true -> {
                        actionResponse(request)
                    }

                    request.method == "POST" && routePath?.startsWith("/api/v1/actions/question/") == true -> {
                        actionResponse(request)
                    }

                    request.method == "GET" && routePath == "/api/v1/notifications" -> {
                        jsonResponse(readResource(GatewayFixturePaths.NotificationsMixed))
                    }

                    request.method == "GET" && routePath == "/api/v1/notifications/plan0001-review" -> {
                        jsonResponse(readResource(GatewayFixturePaths.NotificationDetailPlan))
                    }

                    request.method == "GET" && routePath == "/api/v1/agents" -> {
                        jsonResponse(readResource(GatewayFixturePaths.AgentsList))
                    }

                    request.method == "GET" && routePath == "/api/v1/agents/resume-options" -> {
                        jsonResponse(readResource(GatewayFixturePaths.AgentResumeOptions))
                    }

                    request.method == "POST" && routePath == "/api/v1/agents/launch" -> {
                        decodeOrInvalid(request, MobileAgentTextLaunchRequestWire.serializer())
                            ?: jsonResponse(readResource(GatewayFixturePaths.AgentLaunchResult))
                    }

                    request.method == "POST" && routePath == "/api/v1/agents/launch-image" -> {
                        decodeOrInvalid(request, MobileAgentImageLaunchRequestWire.serializer())
                            ?: jsonResponse(readResource(GatewayFixturePaths.AgentImageLaunchResult))
                    }

                    request.method == "POST" && routePath?.startsWith("/api/v1/agents/") == true &&
                        routePath.endsWith("/kill") -> {
                        decodeOrInvalid(request, MobileAgentKillRequestWire.serializer())
                            ?: jsonResponse(readResource(GatewayFixturePaths.AgentKillResult))
                    }

                    request.method == "POST" && routePath?.startsWith("/api/v1/agents/") == true &&
                        routePath.endsWith("/retry") -> {
                        decodeOrInvalid(request, MobileAgentRetryRequestWire.serializer())
                            ?: jsonResponse(readResource(GatewayFixturePaths.AgentRetryResult))
                    }

                    request.method == "GET" && routePath == "/api/v1/changespec-tags" -> {
                        jsonResponse(readResource(GatewayFixturePaths.ChangespecTags))
                    }

                    request.method == "GET" && routePath == "/api/v1/xprompts/catalog" -> {
                        jsonResponse(readResource(GatewayFixturePaths.XpromptCatalog))
                    }

                    request.method == "GET" && routePath == "/api/v1/beads" -> {
                        jsonResponse(readResource(GatewayFixturePaths.BeadsList))
                    }

                    request.method == "GET" && routePath == "/api/v1/beads/sase-26.6.1" -> {
                        jsonResponse(readResource(GatewayFixturePaths.BeadShow))
                    }

                    request.method == "POST" && routePath == "/api/v1/update/start" -> {
                        decodeOrInvalid(request, MobileUpdateStartRequestWire.serializer())
                            ?: jsonResponse(readResource(GatewayFixturePaths.UpdateStartRunning))
                    }

                    request.method == "GET" && routePath == "/api/v1/update/update-job-1" -> {
                        jsonResponse(readResource(GatewayFixturePaths.UpdateStatusSuccess))
                    }

                    request.method == "GET" && routePath == "/api/v1/update/update-job-2" -> {
                        jsonResponse(readResource(GatewayFixturePaths.UpdateStatusFailure))
                    }

                    request.method == "GET" && routePath == "/api/v1/attachments/token_1" -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "text/markdown")
                            .setHeader("Content-Length", "11")
                            .setHeader("Content-Disposition", "attachment; filename=\"notes.md\"")
                            .setBody("hello world")
                    }

                    request.method == "GET" && routePath == "/api/v1/events" -> {
                        sse(
                            listOf(
                                readResource(GatewayFixturePaths.EventAgentsChanged),
                                readResource(GatewayFixturePaths.EventHelpersChanged),
                            ),
                        )
                    }

                    else -> jsonResponse(NotFoundResponse, statusCode = 404)
                }
            }
        }
    }

    fun takeRequest(): RecordedRequest = server.takeRequest()

    override fun close() {
        server.shutdown()
    }

    private fun pairingResponse(
        request: RecordedRequest,
        expectedPairingId: String,
        expectedCode: String,
    ): MockResponse {
        if (request.getHeader("Authorization") != null) {
            return jsonResponse(UnauthorizedResponse, statusCode = 401)
        }
        val body = request.body.clone().readUtf8()
        val parsed = try {
            jsonFormat.decodeFromString(PairFinishRequestWire.serializer(), body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return if (parsed?.pairingId == expectedPairingId && parsed.code == expectedCode) {
            jsonResponse(readResource(GatewayFixturePaths.PairFinishSuccess))
        } else {
            jsonResponse(InvalidPairingResponse, statusCode = 400)
        }
    }

    private fun actionResponse(request: RecordedRequest): MockResponse {
        val routePath = request.path?.substringBefore("?").orEmpty()
        val prefix = routePath.split("/").getOrNull(5).orEmpty()
        val body = request.body.clone().readUtf8()
        val bodyError = validateJsonBody(body)
        if (bodyError != null) {
            return bodyError
        }
        if (body.contains("\"prefix\"") || body.contains("\"choice\"")) {
            return jsonResponse(InvalidPathDerivedActionBodyResponse, statusCode = 400)
        }
        return when (prefix) {
            "stale" -> jsonResponse(readResource(GatewayFixturePaths.ErrorGoneStale), statusCode = 410)
            "handled" -> jsonResponse(readResource(GatewayFixturePaths.ErrorConflictAlreadyHandled), statusCode = 409)
            "ambiguous" -> jsonResponse(readResource(GatewayFixturePaths.ErrorAmbiguousPrefix), statusCode = 409)
            "unsupported" -> jsonResponse(readResource(GatewayFixturePaths.ErrorUnsupportedAction), statusCode = 400)
            else -> jsonResponse(readResource(GatewayFixturePaths.ActionSuccess))
        }
    }

    private fun validateJsonBody(body: String): MockResponse? {
        val parsed = try {
            jsonFormat.parseToJsonElement(body).jsonObject
        } catch (_: SerializationException) {
            return jsonResponse(InvalidJsonResponse, statusCode = 400)
        } catch (_: IllegalArgumentException) {
            return jsonResponse(InvalidJsonResponse, statusCode = 400)
        }
        return if (parsed["schema_version"] == null) {
            jsonResponse(InvalidJsonResponse, statusCode = 400)
        } else {
            null
        }
    }

    private fun <T> decodeOrInvalid(
        request: RecordedRequest,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): MockResponse? {
        val body = request.body.clone().readUtf8()
        return try {
            jsonFormat.decodeFromString(serializer, body)
            null
        } catch (_: SerializationException) {
            jsonResponse(InvalidJsonResponse, statusCode = 400)
        } catch (_: IllegalArgumentException) {
            jsonResponse(InvalidJsonResponse, statusCode = 400)
        }
    }

    private fun authorized(
        request: RecordedRequest,
        authToken: String,
    ): MockResponse? {
        return if (request.getHeader("Authorization") == "Bearer $authToken") {
            null
        } else {
            jsonResponse(UnauthorizedResponse, statusCode = 401)
        }
    }

    private fun jsonResponse(
        body: String,
        statusCode: Int = 200,
    ): MockResponse {
        return MockResponse()
            .setResponseCode(statusCode)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }

    private fun sse(eventBodies: List<String>): MockResponse {
        val body = eventBodies.joinToString(separator = "\n\n") { it.toSseDataBlock() } + "\n\n"
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)
    }

    private fun String.toSseDataBlock(): String {
        return lineSequence().joinToString(separator = "\n") { line -> "data: $line" }
    }

    companion object {
        const val SmokeAuthToken = "sase_mobile_example"
        const val SmokePairingId = "pair_abc123"
        const val SmokePairingCode = "123456"

        private val MarkReadResponse = """
            {
              "schema_version": 1,
              "notification_id": "plan0001-review",
              "read": true,
              "dismissed": false,
              "changed": true
            }
        """.trimIndent()

        private val DismissResponse = """
            {
              "schema_version": 1,
              "notification_id": "plan0001-review",
              "read": true,
              "dismissed": true,
              "changed": true
            }
        """.trimIndent()

        private val UnauthorizedResponse = """
            {
              "schema_version": 1,
              "code": "unauthorized",
              "message": "missing or invalid bearer token",
              "target": "Authorization",
              "details": null
            }
        """.trimIndent()

        private val InvalidPairingResponse = """
            {
              "schema_version": 1,
              "code": "invalid_request",
              "message": "invalid pairing payload",
              "target": "pairing_id",
              "details": null
            }
        """.trimIndent()

        private val InvalidJsonResponse = """
            {
              "schema_version": 1,
              "code": "invalid_request",
              "message": "invalid JSON request",
              "target": "body",
              "details": null
            }
        """.trimIndent()

        private val InvalidPathDerivedActionBodyResponse = """
            {
              "schema_version": 1,
              "code": "invalid_request",
              "message": "action body must not duplicate path-derived fields",
              "target": "body",
              "details": null
            }
        """.trimIndent()

        private val NotFoundResponse = """
            {
              "schema_version": 1,
              "code": "not_found",
              "message": "route not found",
              "target": null,
              "details": null
            }
        """.trimIndent()
    }
}
