package org.sase.mobile.data.helpers

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.GatewayFixturePaths
import org.sase.mobile.data.api.readResource
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.testing.FakeGateway

class HelperRepositoryTest {
    @Test
    fun loadsHelperDataWithStructuredFilters() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = repository(gateway.baseUrl)

            val tags = repository.changespecTags(project = "sase", limit = 5)
            val xprompts = repository.xpromptCatalog(
                project = "sase",
                tag = "mobile",
                query = "bd",
                includePdf = true,
                limit = 20,
            )
            val beads = repository.beads(
                project = "sase",
                status = "in_progress",
                beadType = "phase",
                tier = "epic",
                includeClosed = true,
                limit = 10,
            )
            val bead = repository.beadDetail("sase-26.6.1", project = "sase")

            assertThat((tags as HelperLoadResult.Success).value.result.status.name)
                .isEqualTo("PartialSuccess")
            assertThat((xprompts as HelperLoadResult.Success).value.entries.single().name)
                .isEqualTo("bd/work_phase_bead")
            assertThat((beads as HelperLoadResult.Success).value.beads.single().id)
                .isEqualTo("sase-26.6.1")
            assertThat((bead as HelperLoadResult.Success).value.bead.summary.id)
                .isEqualTo("sase-26.6.1")

            assertThat(gateway.takeRequest().path)
                .isEqualTo("/api/v1/changespec-tags?project=sase&limit=5")
            assertThat(gateway.takeRequest().path)
                .isEqualTo("/api/v1/xprompts/catalog?project=sase&tag=mobile&query=bd&include_pdf=true&limit=20")
            assertThat(gateway.takeRequest().path)
                .isEqualTo(
                    "/api/v1/beads?project=sase&all_projects=false&status=in_progress&bead_type=phase&tier=epic&include_closed=true&limit=10",
                )
            assertThat(gateway.takeRequest().path)
                .isEqualTo("/api/v1/beads/sase-26.6.1?project=sase&all_projects=false")
        }
    }

    @Test
    fun mapsMissingSessionAndGatewayFailures() = runTest {
        val missingSession = repository(
            baseUrl = GatewayApiClient.normalizeBaseUrl("http://127.0.0.1:7629/"),
            sessionStorage = InMemoryHostSessionStorage(),
        )

        assertThat(missingSession.changespecTags()).isEqualTo(HelperLoadResult.LoggedOut)

        FakeGateway().use { gateway ->
            gateway.enqueueJson(readResource(GatewayFixturePaths.ErrorFixtures.first()), statusCode = 401)
            val failed = repository(gateway.baseUrl).changespecTags()

            val error = (failed as HelperLoadResult.Failure).error as GatewayApiError.Http
            assertThat(error.statusCode).isEqualTo(401)
        }
    }

    @Test
    fun rejectsBlankBeadDetailRequestBeforeNetworkCall() = runTest {
        FakeGateway().use { gateway ->
            val result = repository(gateway.baseUrl).beadDetail(" ")

            assertThat(result).isInstanceOf(HelperLoadResult.InvalidRequest::class.java)
            assertThat(gateway.requestCount).isEqualTo(0)
        }
    }

    private fun repository(
        baseUrl: HttpUrl,
        sessionStorage: InMemoryHostSessionStorage = InMemoryHostSessionStorage(pairedSession(baseUrl)),
    ): HelperRepository {
        return HelperRepository(
            sessionStorage = sessionStorage,
            tokenVault = InMemoryTokenVault(FakeGateway.SmokeAuthToken),
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(
                    baseUrl = url,
                    bearerTokenProvider = tokenProvider,
                    client = OkHttpClient(),
                )
            },
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
