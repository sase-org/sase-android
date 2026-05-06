package org.sase.mobile.data.session

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.readResource
import org.sase.mobile.testing.FakeGateway

class SessionRepositoryTest {
    @Test
    fun pairManuallyPersistsSessionAndBearerToken() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.PairFinishSuccess))
            val storage = InMemoryHostSessionStorage()
            val tokens = InMemoryTokenVault()
            val repository = repository(
                storage = storage,
                tokens = tokens,
            )
            advanceUntilIdle()

            val result = repository.pairManually(
                ManualPairingRequest(
                    baseUrl = gateway.baseUrl.toString(),
                    pairingId = "pair_abc123",
                    code = "123456",
                    hostLabel = "workstation",
                    deviceDisplayName = "Pixel 9",
                ),
            )

            assertThat(result).isInstanceOf(PairingResult.Success::class.java)
            assertThat(storage.read()?.baseUrl).isEqualTo("${gateway.baseUrl}api/v1/")
            assertThat(storage.read()?.hostLabel).isEqualTo("workstation")
            assertThat(storage.read()?.deviceId).isEqualTo("dev_pixel")
            assertThat(tokens.readToken()).isEqualTo("sase_mobile_example")
            assertThat(repository.state.value.status).isInstanceOf(SessionStatus.Paired::class.java)

            val request = gateway.takeRequest()
            assertThat(request.path).isEqualTo("/api/v1/session/pair/finish")
            assertThat(request.getHeader("Authorization")).isNull()
        }
    }

    @Test
    fun refreshSessionUsesStoredTokenAndUpdatesLastCheck() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.SessionSuccess))
            val storage = InMemoryHostSessionStorage(
                pairedSession(gateway.baseUrl.toString(), lastSessionCheckedAt = null),
            )
            val repository = repository(
                storage = storage,
                tokens = InMemoryTokenVault("stored_token"),
            )
            advanceUntilIdle()

            repository.refreshSession()

            assertThat(storage.read()?.lastSessionCheckedAt).isEqualTo(Now)
            assertThat(repository.state.value.status).isInstanceOf(SessionStatus.Paired::class.java)
            assertThat(gateway.takeRequest().getHeader("Authorization")).isEqualTo("Bearer stored_token")
        }
    }

    @Test
    fun refreshSessionReportsAuthExpiredWithoutLoggingToken() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(
                body = readResource(GatewayFixturePaths.ErrorFixtures.first()),
                statusCode = 401,
            )
            val repository = repository(
                storage = InMemoryHostSessionStorage(pairedSession(gateway.baseUrl.toString())),
                tokens = InMemoryTokenVault("expired_secret"),
            )
            advanceUntilIdle()

            repository.refreshSession()

            val status = repository.state.value.status
            assertThat(status).isInstanceOf(SessionStatus.AuthExpired::class.java)
            assertThat(status.toString()).doesNotContain("expired_secret")
        }
    }

    @Test
    fun pairWithQrFinishesPairingAndForgetClearsTokenAndSession() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.PairFinishSuccess))
            val storage = InMemoryHostSessionStorage()
            val tokens = InMemoryTokenVault()
            val repository = repository(storage = storage, tokens = tokens)
            advanceUntilIdle()

            val result = repository.pairWithQr(
                payload = """
                    {
                      "schema_version": 1,
                      "type": "sase_mobile_pair",
                      "base_url": "${gateway.baseUrl}",
                      "pairing_id": "pair_abc123",
                      "code": "123456",
                      "host_label": "workstation"
                    }
                """.trimIndent(),
                deviceDisplayName = "Pixel 9",
            )

            assertThat(result).isInstanceOf(PairingResult.Success::class.java)
            assertThat(tokens.readToken()).isEqualTo("sase_mobile_example")

            repository.forgetHost()

            assertThat(storage.read()).isNull()
            assertThat(tokens.readToken()).isNull()
            assertThat(repository.state.value.status).isEqualTo(SessionStatus.Unpaired)
        }
    }

    @Test
    fun pairingFailureExposesStructuredGatewayMessage() = runTest {
        FakeGateway().use { gateway ->
            gateway.enqueueJson(
                body = readResource("fixtures/gateway/error_gone_stale.json"),
                statusCode = 410,
            )
            val repository = repository()
            advanceUntilIdle()

            val result = repository.pairManually(
                ManualPairingRequest(
                    baseUrl = gateway.baseUrl.toString(),
                    pairingId = "pair_expired",
                    code = "000000",
                    hostLabel = null,
                    deviceDisplayName = "Pixel 9",
                ),
            )

            assertThat(result).isInstanceOf(PairingResult.Failure::class.java)
            assertThat((result as PairingResult.Failure).message).isNotEmpty()
            assertThat(repository.state.value.status).isInstanceOf(SessionStatus.PairingFailed::class.java)
        }
    }

    private fun TestScope.repository(
        storage: InMemoryHostSessionStorage = InMemoryHostSessionStorage(),
        tokens: InMemoryTokenVault = InMemoryTokenVault(),
    ): SessionRepository {
        return SessionRepository(
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
                DeviceMetadata(displayName = "Pixel 9", appVersion = "0.1.0")
            },
            clock = Clock.fixed(Instant.parse(Now), ZoneOffset.UTC),
            scope = this,
        )
    }

    private fun pairedSession(
        baseUrl: String,
        lastSessionCheckedAt: String? = "2026-05-06T14:00:00Z",
    ): PairedHostSession {
        return PairedHostSession(
            hostLabel = "workstation",
            baseUrl = GatewayApiClient.normalizeBaseUrl(baseUrl).toString(),
            deviceId = "dev_pixel",
            deviceDisplayName = "Pixel 9",
            pairedAt = "2026-05-06T13:00:00Z",
            lastSessionCheckedAt = lastSessionCheckedAt,
        )
    }

    private companion object {
        const val Now = "2026-05-06T15:05:00Z"
    }
}
