package org.sase.mobile.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Test
import org.sase.mobile.data.api.dto.AgentsChangedEventPayloadWire
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.ApiErrorWire
import org.sase.mobile.data.api.dto.EventPayloadTypeWire
import org.sase.mobile.data.api.dto.EventRecordWire
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.HealthResponseWire
import org.sase.mobile.data.api.dto.HeartbeatEventPayloadWire
import org.sase.mobile.data.api.dto.HelpersChangedEventPayloadWire
import org.sase.mobile.data.api.dto.MobileActionKindWire
import org.sase.mobile.data.api.dto.MobileAttachmentKindWire
import org.sase.mobile.data.api.dto.MobileNotificationDetailResponseWire
import org.sase.mobile.data.api.dto.MobileNotificationListResponseWire
import org.sase.mobile.data.api.dto.NotificationsChangedEventPayloadWire
import org.sase.mobile.data.api.dto.PairFinishResponseWire
import org.sase.mobile.data.api.dto.PairStartResponseWire
import org.sase.mobile.data.api.dto.ResyncRequiredEventPayloadWire
import org.sase.mobile.data.api.dto.SessionEventPayloadWire
import org.sase.mobile.data.api.dto.SessionResponseWire

class GatewayDtoFixtureTest {
    private val json = GatewayJson.format

    @Test
    fun parsesHealthSessionAndPairingFixtures() {
        val health = roundTrip(
            HealthResponseWire.serializer(),
            GatewayFixturePaths.HealthSuccess,
        )
        val session = roundTrip(
            SessionResponseWire.serializer(),
            GatewayFixturePaths.SessionSuccess,
        )
        val pairStart = roundTrip(
            PairStartResponseWire.serializer(),
            GatewayFixturePaths.PairStartSuccess,
        )
        val pairFinish = roundTrip(
            PairFinishResponseWire.serializer(),
            GatewayFixturePaths.PairFinishSuccess,
        )

        assertThat(health.schemaVersion).isEqualTo(1)
        assertThat(health.basePathHint()).isEqualTo("/api/v1")
        assertThat(session.device.platform).isEqualTo("android")
        assertThat(session.capabilities).contains("notifications.read")
        assertThat(pairStart.pairingId).isEqualTo("pair_abc123")
        assertThat(pairFinish.tokenType).isEqualTo("bearer")
    }

    @Test
    fun parsesStructuredErrorFixtures() {
        val errors = GatewayFixturePaths.ErrorFixtures.map { path ->
            roundTrip(ApiErrorWire.serializer(), path)
        }

        assertThat(errors.map { it.code }).containsExactly(
            ApiErrorCodeWire.Unauthorized,
            ApiErrorCodeWire.NotFound,
            ApiErrorCodeWire.GoneStale,
            ApiErrorCodeWire.BridgeUnavailable,
        ).inOrder()
        assertThat(errors[1].details).isNotNull()
    }

    @Test
    fun parsesNotificationListAndDetailFixtures() {
        val empty = roundTrip(
            MobileNotificationListResponseWire.serializer(),
            GatewayFixturePaths.NotificationsEmpty,
        )
        val mixed = roundTrip(
            MobileNotificationListResponseWire.serializer(),
            GatewayFixturePaths.NotificationsMixed,
        )
        val detail = roundTrip(
            MobileNotificationDetailResponseWire.serializer(),
            GatewayFixturePaths.NotificationDetailPlan,
        )

        assertThat(empty.notifications).isEmpty()
        assertThat(mixed.notifications).hasSize(7)
        assertThat(mixed.notifications.mapNotNull { it.actionSummary?.kind }).containsAtLeast(
            MobileActionKindWire.PlanApproval,
            MobileActionKindWire.Hitl,
            MobileActionKindWire.UserQuestion,
            MobileActionKindWire.Unsupported,
        )
        assertThat(detail.notes).hasSize(2)
        assertThat(detail.action.kind).isEqualTo(MobileActionKindWire.PlanApproval)
        assertThat(detail.attachments.map { it.kind }).containsExactly(
            MobileAttachmentKindWire.Markdown,
            MobileAttachmentKindWire.Pdf,
        ).inOrder()
    }

    @Test
    fun parsesEventFixturesAndTypedPayloadData() {
        val heartbeat = roundTrip(
            EventRecordWire.serializer(),
            GatewayFixturePaths.EventHeartbeat,
        )
        val notificationsChanged = roundTrip(
            EventRecordWire.serializer(),
            GatewayFixturePaths.EventNotificationsChanged,
        )
        val resync = roundTrip(
            EventRecordWire.serializer(),
            GatewayFixturePaths.EventResyncRequired,
        )

        assertThat(heartbeat.payload.type).isEqualTo(EventPayloadTypeWire.Heartbeat)
        assertThat(heartbeat.decodeHeartbeatPayload().sequence).isEqualTo(1)
        assertThat(notificationsChanged.payload.type)
            .isEqualTo(EventPayloadTypeWire.NotificationsChanged)
        assertThat(notificationsChanged.decodeNotificationsChangedPayload().notificationId)
            .isEqualTo("plan0001-review")
        assertThat(resync.payload.type).isEqualTo(EventPayloadTypeWire.ResyncRequired)
        assertThat(resync.decodeResyncPayload().reason).contains("restarted")
    }

    @Test
    fun eventPayloadDtoSurfaceCoversEpicFiveEventKinds() {
        val session = SessionEventPayloadWire(deviceId = "dev_pixel")
        val agents = AgentsChangedEventPayloadWire(
            reason = "refresh",
            agentName = "agent-a",
            timestamp = "2026-05-06T15:00:00Z",
        )
        val helpers = HelpersChangedEventPayloadWire(
            reason = "update",
            helper = "chat_install",
            jobId = "job_123",
            timestamp = "2026-05-06T15:00:00Z",
        )

        assertThat(session.deviceId).isEqualTo("dev_pixel")
        assertThat(agents.agentName).isEqualTo("agent-a")
        assertThat(helpers.jobId).isEqualTo("job_123")
    }

    private fun HealthResponseWire.basePathHint(): String {
        assertThat(service).isEqualTo("sase_gateway")
        return "/api/v1"
    }

    private inline fun <reified T> roundTrip(
        serializer: KSerializer<T>,
        path: String,
    ): T {
        val decoded = json.decodeFromString(serializer, readResource(path))
        val encoded = json.encodeToString(serializer, decoded)
        return json.decodeFromString(serializer, encoded)
    }

    private fun EventRecordWire.decodeHeartbeatPayload(): HeartbeatEventPayloadWire {
        return json.decodeFromJsonElement(
            HeartbeatEventPayloadWire.serializer(),
            payload.data,
        )
    }

    private fun EventRecordWire.decodeNotificationsChangedPayload(): NotificationsChangedEventPayloadWire {
        return json.decodeFromJsonElement(
            NotificationsChangedEventPayloadWire.serializer(),
            payload.data,
        )
    }

    private fun EventRecordWire.decodeResyncPayload(): ResyncRequiredEventPayloadWire {
        return json.decodeFromJsonElement(
            ResyncRequiredEventPayloadWire.serializer(),
            payload.data,
        )
    }
}
