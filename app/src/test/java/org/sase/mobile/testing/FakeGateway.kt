package org.sase.mobile.testing

import java.io.Closeable
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.dto.GatewayJson
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
        val body = eventJson.joinToString(separator = "\n\n") { "data: $it" } + "\n\n"
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
        val body = request.body.readUtf8()
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
        val body = eventBodies.joinToString(separator = "\n\n") { "data: $it" } + "\n\n"
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)
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
