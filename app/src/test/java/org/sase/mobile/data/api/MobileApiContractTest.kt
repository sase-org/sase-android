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
    fun contractContainsEpicFiveRoutes() {
        val routes = loadContract().routes.associateBy { "${it.method} ${it.path}" }

        EXPECTED_ROUTES.forEach { expected ->
            assertThat(routes).containsKey(expected.key)
            assertThat(routes.getValue(expected.key).success).isEqualTo(expected.success)
        }
    }

    @Test
    fun contractKeepsStructuredErrorsOnEveryEpicFiveRoute() {
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
    )

    private companion object {
        val EXPECTED_ROUTES = listOf(
            ExpectedRoute("GET /api/v1/health", "HealthResponseWire"),
            ExpectedRoute("POST /api/v1/session/pair/start", "PairStartResponseWire"),
            ExpectedRoute("POST /api/v1/session/pair/finish", "PairFinishResponseWire"),
            ExpectedRoute("GET /api/v1/session", "SessionResponseWire"),
            ExpectedRoute("GET /api/v1/events", "EventRecordWire stream"),
            ExpectedRoute("GET /api/v1/notifications", "MobileNotificationListResponseWire"),
            ExpectedRoute("GET /api/v1/notifications/{id}", "MobileNotificationDetailResponseWire"),
            ExpectedRoute("POST /api/v1/notifications/{id}/mark-read", "NotificationStateMutationResponseWire"),
            ExpectedRoute("POST /api/v1/notifications/{id}/dismiss", "NotificationStateMutationResponseWire"),
            ExpectedRoute("GET /api/v1/attachments/{token}", "attachment bytes"),
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
    val errors: List<String> = emptyList(),
)

@Serializable
private data class MobileApiResponseShape(
    val success: String,
    val error: String,
)
