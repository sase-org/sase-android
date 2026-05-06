package org.sase.mobile.data.agents

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.api.dto.AgentsChangedEventPayloadWire
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.ApiErrorWire
import org.sase.mobile.data.api.dto.MobileAgentImageLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.readResource
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.testing.FakeGateway

class AgentRepositoryTest {
    @Test
    fun refreshLoadsAgentListAndResumeOptions() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = repository(gateway.baseUrl)

            assertThat(repository.refresh()).isTrue()

            assertThat(repository.state.value.agents.map { it.name })
                .containsExactly("mobile-demo", "mobile-failed")
                .inOrder()
            assertThat(repository.state.value.resumeOptions).hasSize(2)
            assertThat(repository.state.value.connection).isEqualTo(AgentConnectionState.Connected)
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents?include_recent=true")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
        }
    }

    @Test
    fun killAndRetryPublishResultStatesAndRefreshList() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = repository(gateway.baseUrl)

            val kill = repository.killAgent("mobile-demo")
            val retry = repository.retryAgent("mobile-failed")

            assertThat(kill).isEqualTo(AgentActionState.Succeeded("kill signal sent"))
            assertThat(retry).isEqualTo(AgentActionState.Succeeded("Retry launched: mobile-failed-retry"))
            assertThat(repository.state.value.agents).hasSize(2)
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/mobile-demo/kill")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents?include_recent=true")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/mobile-failed/retry")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents?include_recent=true")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
        }
    }

    @Test
    fun launchAgentPreservesPromptDirectivesAndStoresRecentResult() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = repository(gateway.baseUrl)
            val prompt = "#gh:mobile\n%runtime codex\nKeep 100% of #bd/work_phase_bead:sase-26.6.5"

            val result = repository.launchAgent(
                MobileAgentTextLaunchRequestWire(
                    prompt = prompt,
                    requestId = "launch-android-1",
                    displayName = "Mobile launch",
                    provider = "openai",
                    runtime = "codex",
                    model = "gpt-5.4",
                    project = "sase",
                ),
            )

            assertThat(result).isEqualTo(AgentActionState.Succeeded("Launched: mobile-launch-1"))
            assertThat(repository.state.value.recentLaunchResults).hasSize(1)
            assertThat(repository.state.value.recentLaunchResults.single().slots).hasSize(2)

            val launchRequest = gateway.takeRequest()
            val launchBody = launchRequest.body.readUtf8()
            assertThat(launchRequest.path).isEqualTo("/api/v1/agents/launch")
            assertThat(launchBody).contains("\"prompt\":\"#gh:mobile\\n%runtime codex\\nKeep 100%")
            assertThat(launchBody).contains("\"device_id\":\"dev_pixel\"")
            assertThat(launchBody).contains("\"request_id\":\"launch-android-1\"")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents?include_recent=true")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
        }
    }

    @Test
    fun launchImageAgentUploadsMetadataBytesAndStoresRecentResult() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = repository(gateway.baseUrl)

            val result = repository.launchImageAgent(
                MobileAgentImageLaunchRequestWire(
                    prompt = "Review screenshot",
                    requestId = "image-android-1",
                    originalFilename = "screen.png",
                    contentType = "image/png",
                    byteLength = 8,
                    base64Image = "cGl4ZWxzIQ==",
                    project = "sase",
                ),
            )

            assertThat(result).isEqualTo(AgentActionState.Succeeded("Launched: mobile-image-agent"))
            assertThat(repository.state.value.recentLaunchResults).hasSize(1)
            assertThat(repository.state.value.recentLaunchResults.single().primary?.name)
                .isEqualTo("mobile-image-agent")

            val launchRequest = gateway.takeRequest()
            val launchBody = launchRequest.body.readUtf8()
            assertThat(launchRequest.path).isEqualTo("/api/v1/agents/launch-image")
            assertThat(launchBody).contains("\"original_filename\":\"screen.png\"")
            assertThat(launchBody).contains("\"content_type\":\"image/png\"")
            assertThat(launchBody).contains("\"byte_length\":8")
            assertThat(launchBody).contains("\"base64_image\":\"cGl4ZWxzIQ==\"")
            assertThat(launchBody).contains("\"device_id\":\"dev_pixel\"")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents?include_recent=true")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
        }
    }

    @Test
    fun agentsChangedEventRefreshesAgentState() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = repository(gateway.baseUrl)

            repository.runSseLoop(maxConnections = 1)

            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/events")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents?include_recent=true")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
            assertThat(repository.state.value.lastEventId).isEqualTo("0000000000000005")
            assertThat(repository.state.value.agents).hasSize(2)
        }
    }

    @Test
    fun notificationBridgeAgentsChangedRefreshesAgentState() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = repository(gateway.baseUrl)

            repository.handleAgentsChanged(
                AgentsChangedEventPayloadWire(
                    reason = "agent_finished",
                    agentName = "mobile-demo",
                    timestamp = "2026-05-06T18:01:00Z",
                ),
            )

            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents?include_recent=true")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/agents/resume-options")
            assertThat(repository.state.value.refreshReason).isNull()
            assertThat(repository.state.value.agents).hasSize(2)
        }
    }

    @Test
    fun mapsAgentLifecycleFailuresToActionableStates() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.ErrorBridgeUnavailable), statusCode = 503)
            val repository = repository(gateway.baseUrl)

            assertThat(repository.refresh()).isFalse()

            val failure = repository.state.value.failure
            assertThat(failure?.kind).isEqualTo(AgentFailureKind.BridgeUnavailable)
            assertThat(repository.state.value.connection)
                .isInstanceOf(AgentConnectionState.Offline::class.java)
        }
    }

    @Test
    fun structuredAgentErrorsMapToSpecificKinds() {
        val failure = org.sase.mobile.data.api.GatewayApiError.Http(
            statusCode = 404,
            apiError = ApiErrorWire(
                schemaVersion = 1,
                code = ApiErrorCodeWire.AgentNotFound,
                message = "agent missing",
            ),
            rawBody = "",
        ).toAgentFailure()

        assertThat(failure.kind).isEqualTo(AgentFailureKind.AgentMissing)
        assertThat(failure.message).isEqualTo("agent missing")
    }

    private fun repository(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage = InMemoryHostSessionStorage(pairedSession(baseUrl)),
    ): AgentRepository {
        return AgentRepository(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault(FakeGateway.SmokeAuthToken),
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
            delayProvider = { _ -> },
            scope = CoroutineScope(SupervisorJob()),
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
}
