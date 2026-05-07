package org.sase.mobile.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.actions.ActionDraftStore
import org.sase.mobile.data.actions.ActionRepository
import org.sase.mobile.data.actions.ActionSubmissionState
import org.sase.mobile.data.actions.InMemoryActionDraftStore
import org.sase.mobile.data.actions.NotificationActionChoice
import org.sase.mobile.data.agents.AgentActionState
import org.sase.mobile.data.agents.AgentRepository
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.api.NetworkAvailability
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.dto.PlanActionChoiceWire
import org.sase.mobile.data.api.readResource
import org.sase.mobile.data.notifications.InMemoryNotificationCache
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.notifications.RefreshReason
import org.sase.mobile.data.notifications.local.sanitized
import org.sase.mobile.data.notifications.push.PushMessageHandler
import org.sase.mobile.data.notifications.push.PushMessageHandlingResult
import org.sase.mobile.data.notifications.push.PushRegistrationManager
import org.sase.mobile.data.notifications.push.PushTokenProvider
import org.sase.mobile.data.notifications.push.PushTokenResult
import org.sase.mobile.data.session.DeviceMetadata
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.ui.NotificationUiFixtures

class BackgroundHardeningSmokeTest {
    @Test
    fun pushWakeRefreshesThenApprovalLaunchAndKillStillUseAuthoritativeGatewayState() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val sessionStorage = InMemoryHostSessionStorage(pairedSession(gateway.baseUrl))
            val notifications = notificationRepository(gateway.baseUrl, sessionStorage)
            val pushRegistration = pushRegistrationManager(gateway.baseUrl, sessionStorage)
            val renderedHints = mutableListOf<String>()
            val pushHandler = PushMessageHandler(
                sessionStorage = sessionStorage,
                notificationRepository = notifications,
                renderer = { hint ->
                    renderedHints += hint.sanitized()?.stableKey.orEmpty()
                    true
                },
                registrationManager = pushRegistration,
                networkAvailability = NetworkAvailability { false },
                scope = this,
            )

            val pushResult = pushHandler.handleDataMessage(pushHintFixtureMap())
            assertThat(notifications.fullRefresh(RefreshReason.PushHint)).isTrue()

            val actions = actionRepository(gateway.baseUrl, sessionStorage, notifications)
            val approval = actions.submitAction(
                notificationId = "plan0001-review",
                action = NotificationUiFixtures.detail.action,
                choice = NotificationActionChoice.Plan(PlanActionChoiceWire.Approve),
            )

            val agents = agentRepository(gateway.baseUrl, sessionStorage)
            val launch = agents.launchAgent(
                MobileAgentTextLaunchRequestWire(
                    prompt = "Summarize the background delivery smoke state.",
                    requestId = "background-smoke-launch",
                    displayName = "Background smoke",
                    project = "sase",
                ),
            )
            val kill = agents.killAgent("mobile-demo")

            assertThat(pushResult).isEqualTo(PushMessageHandlingResult.Rendered("dev_pixel"))
            assertThat(renderedHints).containsExactly("plan0001-review")
            assertThat(notifications.inbox.value.cards).hasSize(7)
            assertThat(approval).isInstanceOf(ActionSubmissionState.Success::class.java)
            assertThat(launch).isEqualTo(AgentActionState.Succeeded("Launched: mobile-launch-1"))
            assertThat(kill).isEqualTo(AgentActionState.Succeeded("kill signal sent"))
            assertThat(takeRequestPaths(gateway, 9)).containsExactly(
                "/api/v1/notifications?include_dismissed=true",
                "/api/v1/actions/plan/plan0001/approve",
                "/api/v1/notifications?include_dismissed=true",
                "/api/v1/notifications/plan0001-review",
                "/api/v1/agents/launch",
                "/api/v1/agents?include_recent=true",
                "/api/v1/agents/resume-options",
                "/api/v1/agents/mobile-demo/kill",
                "/api/v1/agents?include_recent=true",
            ).inOrder()
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
        }
    }

    private fun notificationRepository(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage,
    ): NotificationRepository {
        return NotificationRepository(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault(FakeGateway.SmokeAuthToken),
            cache = InMemoryNotificationCache(),
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(url, bearerTokenProvider = tokenProvider, client = OkHttpClient())
            },
            sseClientFactory = { url, tokenProvider ->
                GatewaySseClient(url, bearerTokenProvider = tokenProvider, client = OkHttpClient())
            },
            clock = Clock.fixed(Now, ZoneOffset.UTC),
            scope = CoroutineScope(SupervisorJob()),
        )
    }

    private fun actionRepository(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage,
        notificationRepository: NotificationRepository,
        draftStore: ActionDraftStore = InMemoryActionDraftStore(),
    ): ActionRepository {
        return ActionRepository(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault(FakeGateway.SmokeAuthToken),
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(url, bearerTokenProvider = tokenProvider, client = OkHttpClient())
            },
            notificationRepository = notificationRepository,
            draftStore = draftStore,
        )
    }

    private fun agentRepository(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage,
    ): AgentRepository {
        return AgentRepository(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault(FakeGateway.SmokeAuthToken),
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(url, bearerTokenProvider = tokenProvider, client = OkHttpClient())
            },
            sseClientFactory = { url, tokenProvider ->
                GatewaySseClient(url, bearerTokenProvider = tokenProvider, client = OkHttpClient())
            },
            delayProvider = { _ -> },
            scope = CoroutineScope(SupervisorJob()),
        )
    }

    private fun pushRegistrationManager(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage,
    ): PushRegistrationManager {
        return PushRegistrationManager(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault(FakeGateway.SmokeAuthToken),
            tokenProvider = object : PushTokenProvider {
                override suspend fun currentToken(): PushTokenResult = PushTokenResult.Success("fcm-token")
            },
            appInstanceIdProvider = { "app-instance-1" },
            deviceMetadataProvider = { DeviceMetadata("Pixel 9", "0.1.0") },
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(url, bearerTokenProvider = tokenProvider, client = OkHttpClient())
            },
            clock = Clock.fixed(Now, ZoneOffset.UTC),
            scope = CoroutineScope(SupervisorJob()),
        )
    }

    private fun pushHintFixtureMap(): Map<String, String> {
        return GatewayJson.format
            .parseToJsonElement(readResource(GatewayFixturePaths.PushHintNotification))
            .jsonObject
            .mapValues { (_, value) -> value.jsonPrimitive.content }
    }

    private fun takeRequestPaths(gateway: FakeGateway, count: Int): List<String?> {
        return List(count) { gateway.takeRequest().path }
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
