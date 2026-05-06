package org.sase.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import java.io.Closeable
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.notifications.InMemoryNotificationCache
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.session.DeviceMetadata
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.SessionRepository
import org.sase.mobile.data.session.SessionStatus
import org.sase.mobile.ui.SaseMobileApp
import org.sase.mobile.ui.theme.SaseMobileTheme

class AndroidSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fakeGatewaySmokeCoversPairInboxDetailResyncAndForget() {
        SmokeGateway().use { gateway ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            try {
                val storage = InMemoryHostSessionStorage()
                val tokens = InMemoryTokenVault()
                val sessionRepository = SessionRepository(
                    storage = storage,
                    tokenVault = tokens,
                    clientFactory = { baseUrl, tokenProvider ->
                        GatewayApiClient(
                            baseUrl = baseUrl,
                            bearerTokenProvider = tokenProvider,
                            client = OkHttpClient(),
                        )
                    },
                    deviceMetadataProvider = {
                        DeviceMetadata(displayName = "Pixel smoke", appVersion = "0.1.0")
                    },
                    clock = Clock.fixed(Instant.parse("2026-05-06T16:00:00Z"), ZoneOffset.UTC),
                    scope = scope,
                )
                val notificationRepository = NotificationRepository(
                    sessionStorage = storage,
                    tokenVault = tokens,
                    cache = InMemoryNotificationCache(),
                    clientFactory = { baseUrl, tokenProvider ->
                        GatewayApiClient(
                            baseUrl = baseUrl,
                            bearerTokenProvider = tokenProvider,
                            client = OkHttpClient(),
                        )
                    },
                    sseClientFactory = { baseUrl, tokenProvider ->
                        GatewaySseClient(
                            baseUrl = baseUrl,
                            bearerTokenProvider = tokenProvider,
                            client = OkHttpClient(),
                        )
                    },
                    clock = Clock.fixed(Instant.parse("2026-05-06T16:01:00Z"), ZoneOffset.UTC),
                    scope = scope,
                )

                composeRule.setContent {
                    SaseMobileTheme {
                        SaseMobileApp(
                            sessionController = sessionRepository,
                            notificationRepository = notificationRepository,
                        )
                    }
                }

                composeRule.onNodeWithTag("inbox_screen").assertIsDisplayed()
                composeRule.waitUntil { sessionRepository.state.value.status == SessionStatus.Unpaired }

                composeRule.onNodeWithText("Settings").performClick()
                composeRule.onNodeWithTag("gateway_url_input").performTextInput(gateway.baseUrl)
                composeRule.onNodeWithTag("host_label_input").performTextInput("workstation")
                composeRule.onNodeWithTag("pairing_id_input").performTextInput(SmokeGateway.PairingId)
                composeRule.onNodeWithTag("pairing_code_input").performTextInput(SmokeGateway.PairingCode)
                composeRule.onNodeWithTag("pair_host_button").performClick()
                composeRule.waitUntil { sessionRepository.state.value.status is SessionStatus.Paired }
                composeRule.onNodeWithText("Paired host").assertIsDisplayed()

                composeRule.onNodeWithText("Inbox").performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    notificationRepository.inbox.value.cards.isNotEmpty()
                }
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    notificationRepository.inbox.value.lastEventId == SmokeGateway.ResyncEventId
                }
                composeRule.onNodeWithText("Plan review waiting").performClick()
                composeRule.waitUntilText("Review the plan before running the coder.")

                composeRule.onNodeWithText("Mark read").performClick()
                composeRule.waitUntilText("Marked read")

                composeRule.onNodeWithText("Settings").performClick()
                composeRule.onNodeWithTag("forget_host_button").performClick()
                composeRule.waitUntil { sessionRepository.state.value.status == SessionStatus.Unpaired }
                composeRule.onNodeWithText("No gateway paired").assertIsDisplayed()
            } finally {
                scope.cancel()
            }
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitUntilText(text: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private class SmokeGateway : Closeable {
    private val server = MockWebServer()

    val baseUrl: String
        get() = server.url("/").toString()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.method == "POST" && request.path == "/api/v1/session/pair/finish" -> {
                        val body = request.body.readUtf8()
                        if (body.contains(PairingId) && body.contains(PairingCode)) {
                            json(PairFinishSuccess)
                        } else {
                            json(InvalidPairing, statusCode = 400)
                        }
                    }

                    request.method == "GET" && request.path == "/api/v1/session" -> {
                        authorized(request) ?: json(SessionSuccess)
                    }

                    request.method == "GET" && request.path?.startsWith("/api/v1/notifications?") == true -> {
                        authorized(request) ?: json(NotificationsList)
                    }

                    request.method == "GET" && request.path == "/api/v1/notifications/plan0001-review" -> {
                        authorized(request) ?: json(NotificationDetail)
                    }

                    request.method == "POST" &&
                        request.path == "/api/v1/notifications/plan0001-review/mark-read" -> {
                        authorized(request) ?: json(MarkRead)
                    }

                    request.method == "POST" &&
                        request.path == "/api/v1/notifications/plan0001-review/dismiss" -> {
                        authorized(request) ?: json(Dismiss)
                    }

                    request.method == "GET" && request.path == "/api/v1/events" -> {
                        authorized(request) ?: sse(ResyncRequired)
                    }

                    else -> json(NotFound, statusCode = 404)
                }
            }
        }
    }

    private fun authorized(request: RecordedRequest): MockResponse? {
        return if (request.getHeader("Authorization") == "Bearer $AuthToken") {
            null
        } else {
            json(Unauthorized, statusCode = 401)
        }
    }

    private fun json(
        body: String,
        statusCode: Int = 200,
    ): MockResponse {
        return MockResponse()
            .setResponseCode(statusCode)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }

    private fun sse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody("data: $body\n\n")
    }

    override fun close() {
        server.shutdown()
    }

    companion object {
        const val AuthToken = "sase_mobile_example"
        const val PairingId = "pair_abc123"
        const val PairingCode = "123456"
        const val ResyncEventId = "0000000000000003"

        private val PairFinishSuccess = """
            {
              "schema_version": 1,
              "device": {
                "schema_version": 1,
                "device_id": "dev_pixel",
                "display_name": "Pixel smoke",
                "platform": "android",
                "app_version": "0.1.0",
                "paired_at": "2026-05-06T16:00:00Z",
                "last_seen_at": null,
                "revoked_at": null
              },
              "token_type": "bearer",
              "token": "$AuthToken"
            }
        """.trimIndent()

        private val SessionSuccess = """
            {
              "schema_version": 1,
              "device": {
                "schema_version": 1,
                "device_id": "dev_pixel",
                "display_name": "Pixel smoke",
                "platform": "android",
                "app_version": "0.1.0",
                "paired_at": "2026-05-06T16:00:00Z",
                "last_seen_at": "2026-05-06T16:00:30Z",
                "revoked_at": null
              },
              "capabilities": ["session.read", "events.read", "notifications.read", "notifications.state.write"]
            }
        """.trimIndent()

        private val NotificationsList = """
            {
              "schema_version": 1,
              "notifications": [
                {
                  "id": "plan0001-review",
                  "timestamp": "2026-05-06T16:00:10Z",
                  "sender": "planner",
                  "priority": true,
                  "actionable": true,
                  "read": false,
                  "dismissed": false,
                  "silent": false,
                  "muted": false,
                  "notes_summary": "Plan review waiting",
                  "file_count": 1,
                  "action_summary": {
                    "kind": "plan_approval",
                    "state": "available",
                    "label": "Plan approval"
                  }
                }
              ],
              "total_count": 1,
              "next_high_water": "2026-05-06T16:00:10Z"
            }
        """.trimIndent()

        private val NotificationDetail = """
            {
              "schema_version": 1,
              "notification": {
                "id": "plan0001-review",
                "timestamp": "2026-05-06T16:00:10Z",
                "sender": "planner",
                "priority": true,
                "actionable": true,
                "read": false,
                "dismissed": false,
                "silent": false,
                "muted": false,
                "notes_summary": "Plan review waiting",
                "file_count": 1,
                "action_summary": {
                  "kind": "plan_approval",
                  "state": "available",
                  "label": "Plan approval"
                }
              },
              "notes": ["Review the plan before running the coder."],
              "attachments": [],
              "action": {
                "kind": "plan_approval",
                "identity": {
                  "notification_id": "plan0001-review",
                  "prefix": "plan0001",
                  "prefix_len": 8,
                  "resolution": "unique_prefix"
                },
                "state": "available",
                "response_dir": "~/.sase/responses/plan0001",
                "plan_file": "~/plans/plan.md",
                "choices": ["approve", "run", "reject"]
              }
            }
        """.trimIndent()

        private val ResyncRequired = """
            {
              "schema_version": 1,
              "id": "$ResyncEventId",
              "created_at": "2026-05-06T16:00:20Z",
              "payload": {
                "type": "resync_required",
                "data": {"reason": "smoke test"}
              }
            }
        """.trimIndent()

        private val MarkRead = """
            {
              "schema_version": 1,
              "notification_id": "plan0001-review",
              "read": true,
              "dismissed": false,
              "changed": true
            }
        """.trimIndent()

        private val Dismiss = """
            {
              "schema_version": 1,
              "notification_id": "plan0001-review",
              "read": true,
              "dismissed": true,
              "changed": true
            }
        """.trimIndent()

        private val Unauthorized = """
            {
              "schema_version": 1,
              "code": "unauthorized",
              "message": "missing or invalid bearer token",
              "target": "Authorization",
              "details": null
            }
        """.trimIndent()

        private val InvalidPairing = """
            {
              "schema_version": 1,
              "code": "invalid_request",
              "message": "invalid pairing payload",
              "target": "pairing_id",
              "details": null
            }
        """.trimIndent()

        private val NotFound = """
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
