package org.sase.mobile.data.helpers

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.dto.HelpersChangedEventPayloadWire
import org.sase.mobile.data.api.dto.MobileUpdateJobStatusWire
import org.sase.mobile.data.api.dto.MobileUpdateJobWire
import org.sase.mobile.data.api.readResource
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.testing.FakeGateway

class UpdateRepositoryTest {
    @Test
    fun startUpdatePollsUntilTerminalStatusAndPersistsJob() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.UpdateStartRunning))
            gateway.enqueueJson(readResource(GatewayFixturePaths.UpdateStatusSuccess))
            val cache = InMemoryUpdateJobCache()
            val repository = repository(gateway.baseUrl, cache = cache)

            repository.startUpdateAndPoll()
            advanceUntilIdle()
            waitUntilState(repository) { it.job?.status == MobileUpdateJobStatusWire.Succeeded }

            val startRequest = gateway.takeRequest()
            val statusRequest = gateway.takeRequest()
            assertThat(startRequest.path).isEqualTo("/api/v1/update/start")
            assertThat(startRequest.body.readUtf8()).contains("\"device_id\":\"dev_pixel\"")
            assertThat(statusRequest.path).isEqualTo("/api/v1/update/update-job-1")
            assertThat(repository.state.value.job?.status).isEqualTo(MobileUpdateJobStatusWire.Succeeded)
            assertThat(repository.state.value.helperResult?.message).isEqualTo("update succeeded")
            assertThat(cache.read()?.jobId).isEqualTo("update-job-1")
        }
    }

    @Test
    fun rememberedRunningJobResumesPollingAfterLoad() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.UpdateStatusSuccess))
            val cache = InMemoryUpdateJobCache(runningJob("update-job-1"))
            val repository = repository(gateway.baseUrl, cache = cache)

            repository.loadCachedState()
            advanceUntilIdle()

            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/update/update-job-1")
            assertThat(repository.state.value.job?.status).isEqualTo(MobileUpdateJobStatusWire.Succeeded)
        }
    }

    @Test
    fun matchingHelperEventRefreshesRememberedJob() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.UpdateStatusFailure))
            val cache = InMemoryUpdateJobCache(runningJob("update-job-2"))
            val repository = repository(gateway.baseUrl, cache = cache)

            repository.handleHelpersChanged(
                HelpersChangedEventPayloadWire(
                    reason = "update_status",
                    helper = "update",
                    jobId = "update-job-2",
                ),
            )

            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/update/update-job-2")
            assertThat(repository.state.value.job?.status).isEqualTo(MobileUpdateJobStatusWire.Failed)
            assertThat(repository.state.value.helperResult?.warnings).contains("exit code 1")
        }
    }

    @Test
    fun nonMatchingHelperEventDoesNotRefresh() = runTest {
        FakeGateway().use { gateway ->
            val cache = InMemoryUpdateJobCache(runningJob("update-job-1"))
            val repository = repository(gateway.baseUrl, cache = cache)

            repository.handleHelpersChanged(
                HelpersChangedEventPayloadWire(
                    reason = "changespec",
                    helper = "changespec",
                    jobId = "update-job-1",
                ),
            )

            assertThat(gateway.requestCount).isEqualTo(0)
        }
    }

    @Test
    fun alreadyRunningErrorMapsToStructuredState() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(UpdateAlreadyRunningError, statusCode = 409)
            val repository = repository(gateway.baseUrl)

            repository.startUpdateAndPoll()
            waitUntilState(repository) { it.error == UpdateError.AlreadyRunning }

            assertThat(repository.state.value.error).isEqualTo(UpdateError.AlreadyRunning)
            assertThat(repository.state.value.status).isEqualTo(UpdateStatus.Idle)
        }
    }

    @Test
    fun missingSessionBlocksStart() = runTest {
        val repository = repository(
            baseUrl = GatewayApiClient.normalizeBaseUrl("http://127.0.0.1:7629/"),
            sessionStorage = InMemoryHostSessionStorage(),
        )

        repository.startUpdateAndPoll()
        advanceUntilIdle()

        assertThat(repository.state.value.error).isEqualTo(UpdateError.NotPaired)
    }

    private suspend fun waitUntilState(
        repository: UpdateRepository,
        predicate: (UpdateUiState) -> Boolean,
    ) {
        withTimeout(5_000) {
            while (!predicate(repository.state.value)) {
                delay(10)
            }
        }
    }

    private fun TestScope.repository(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage = InMemoryHostSessionStorage(pairedSession(baseUrl)),
        cache: UpdateJobCache = InMemoryUpdateJobCache(),
    ): UpdateRepository {
        return UpdateRepository(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault("token-secret"),
            cache = cache,
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(
                    baseUrl = url,
                    bearerTokenProvider = tokenProvider,
                    client = OkHttpClient(),
                )
            },
            scope = this,
            requestIdFactory = { "update-request-1" },
            delayProvider = { _ -> },
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

    private fun runningJob(jobId: String): MobileUpdateJobWire {
        return MobileUpdateJobWire(
            jobId = jobId,
            status = MobileUpdateJobStatusWire.Running,
            startedAt = "2026-05-06T18:05:00Z",
            message = "update running",
            logPathDisplay = "logs/$jobId.log",
        )
    }

    private companion object {
        val UpdateAlreadyRunningError = """
            {
              "schema_version": 1,
              "code": "update_already_running",
              "message": "update already running",
              "target": "update",
              "details": null
            }
        """.trimIndent()
    }
}
