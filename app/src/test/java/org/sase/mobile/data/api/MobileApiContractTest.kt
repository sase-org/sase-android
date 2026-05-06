package org.sase.mobile.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test

class MobileApiContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun contractSnapshotHasExpectedMetadata() {
        val contract = loadContract()

        assertThat(contract.schemaVersion).isEqualTo(1)
        assertThat(contract.contract).isEqualTo("sase_mobile_gateway_api_v1")
        assertThat(contract.basePath).isEqualTo("/api/v1")
        assertThat(contract.responseShape.success).isEqualTo("direct_json_record")
        assertThat(contract.responseShape.error).isEqualTo("ApiErrorWire")
    }

    @Test
    fun contractContainsExpectedRoutes() {
        val routes = loadContract().routes.associateBy { "${it.method} ${it.path}" }

        EXPECTED_ROUTES.forEach { expected ->
            assertThat(routes).containsKey(expected.key)
            assertThat(routes.getValue(expected.key).success).isEqualTo(expected.success)
            assertThat(routes.getValue(expected.key).auth).isEqualTo(expected.auth)
        }
    }

    @Test
    fun contractKeepsStructuredErrorsOnEveryExpectedRoute() {
        val routes = loadContract().routes.associateBy { "${it.method} ${it.path}" }

        EXPECTED_ROUTES.forEach { expected ->
            assertThat(routes.getValue(expected.key).errors).contains("ApiErrorWire")
        }
    }

    private fun loadContract(): MobileApiContract {
        return json.decodeFromString(
            MobileApiContract.serializer(),
            readResource(GatewayFixturePaths.Contract),
        )
    }

    private data class ExpectedRoute(
        val key: String,
        val success: String,
        val auth: Boolean,
    )

    private companion object {
        val EXPECTED_ROUTES = listOf(
            ExpectedRoute("GET /api/v1/health", "HealthResponseWire", false),
            ExpectedRoute("POST /api/v1/session/pair/start", "PairStartResponseWire", false),
            ExpectedRoute("POST /api/v1/session/pair/finish", "PairFinishResponseWire", false),
            ExpectedRoute("GET /api/v1/session", "SessionResponseWire", true),
            ExpectedRoute("GET /api/v1/events", "EventRecordWire stream", true),
            ExpectedRoute("GET /api/v1/agents", "MobileAgentListResponseWire", true),
            ExpectedRoute("GET /api/v1/agents/resume-options", "MobileAgentResumeOptionsResponseWire", true),
            ExpectedRoute("POST /api/v1/agents/launch", "MobileAgentLaunchResultWire", true),
            ExpectedRoute("POST /api/v1/agents/launch-image", "MobileAgentLaunchResultWire", true),
            ExpectedRoute("POST /api/v1/agents/{name}/kill", "MobileAgentKillResultWire", true),
            ExpectedRoute("POST /api/v1/agents/{name}/retry", "MobileAgentRetryResultWire", true),
            ExpectedRoute("GET /api/v1/changespec-tags", "MobileChangeSpecTagListResponseWire", true),
            ExpectedRoute("GET /api/v1/xprompts/catalog", "MobileXpromptCatalogResponseWire", true),
            ExpectedRoute("GET /api/v1/beads", "MobileBeadListResponseWire", true),
            ExpectedRoute("GET /api/v1/beads/{id}", "MobileBeadShowResponseWire", true),
            ExpectedRoute("POST /api/v1/update/start", "MobileUpdateStartResponseWire", true),
            ExpectedRoute("GET /api/v1/update/{job_id}", "MobileUpdateStatusResponseWire", true),
            ExpectedRoute("GET /api/v1/notifications", "MobileNotificationListResponseWire", true),
            ExpectedRoute("GET /api/v1/notifications/{id}", "MobileNotificationDetailResponseWire", true),
            ExpectedRoute("POST /api/v1/notifications/{id}/mark-read", "NotificationStateMutationResponseWire", true),
            ExpectedRoute("POST /api/v1/notifications/{id}/dismiss", "NotificationStateMutationResponseWire", true),
            ExpectedRoute("GET /api/v1/attachments/{token}", "attachment bytes", true),
            ExpectedRoute("POST /api/v1/actions/plan/{prefix}/approve", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/plan/{prefix}/run", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/plan/{prefix}/reject", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/plan/{prefix}/epic", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/plan/{prefix}/legend", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/plan/{prefix}/feedback", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/hitl/{prefix}/accept", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/hitl/{prefix}/reject", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/hitl/{prefix}/feedback", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/question/{prefix}/answer", "ActionResultWire", true),
            ExpectedRoute("POST /api/v1/actions/question/{prefix}/custom", "ActionResultWire", true),
        )
    }
}

@Serializable
private data class MobileApiContract(
    @SerialName("schema_version") val schemaVersion: Int,
    val contract: String,
    @SerialName("base_path") val basePath: String,
    val routes: List<MobileApiRoute>,
    @SerialName("response_shape") val responseShape: MobileApiResponseShape,
)

@Serializable
private data class MobileApiRoute(
    val method: String,
    val path: String,
    val success: String,
    val auth: Boolean,
    val errors: List<String> = emptyList(),
)

@Serializable
private data class MobileApiResponseShape(
    val success: String,
    val error: String,
)
