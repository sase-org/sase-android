package org.sase.mobile.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Test
import org.sase.mobile.data.api.dto.ActionResultWire
import org.sase.mobile.data.api.dto.AgentsChangedEventPayloadWire
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.ApiErrorWire
import org.sase.mobile.data.api.dto.EventPayloadTypeWire
import org.sase.mobile.data.api.dto.EventRecordWire
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.HitlActionChoiceWire
import org.sase.mobile.data.api.dto.HitlActionRequestWire
import org.sase.mobile.data.api.dto.HealthResponseWire
import org.sase.mobile.data.api.dto.HeartbeatEventPayloadWire
import org.sase.mobile.data.api.dto.HelpersChangedEventPayloadWire
import org.sase.mobile.data.api.dto.MobileAgentImageLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileAgentKillRequestWire
import org.sase.mobile.data.api.dto.MobileAgentKillResultWire
import org.sase.mobile.data.api.dto.MobileAgentLaunchResultWire
import org.sase.mobile.data.api.dto.MobileAgentLaunchSlotStatusWire
import org.sase.mobile.data.api.dto.MobileAgentListResponseWire
import org.sase.mobile.data.api.dto.MobileAgentResumeOptionKindWire
import org.sase.mobile.data.api.dto.MobileAgentResumeOptionsResponseWire
import org.sase.mobile.data.api.dto.MobileAgentRetryRequestWire
import org.sase.mobile.data.api.dto.MobileAgentRetryResultWire
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileActionKindWire
import org.sase.mobile.data.api.dto.MobileAttachmentKindWire
import org.sase.mobile.data.api.dto.MobileBeadListResponseWire
import org.sase.mobile.data.api.dto.MobileBeadShowResponseWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListResponseWire
import org.sase.mobile.data.api.dto.MobileHelperStatusWire
import org.sase.mobile.data.api.dto.MobileNotificationDetailResponseWire
import org.sase.mobile.data.api.dto.MobileNotificationListResponseWire
import org.sase.mobile.data.api.dto.MobileUpdateJobStatusWire
import org.sase.mobile.data.api.dto.MobileUpdateStartResponseWire
import org.sase.mobile.data.api.dto.MobileUpdateStatusResponseWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogResponseWire
import org.sase.mobile.data.api.dto.NotificationsChangedEventPayloadWire
import org.sase.mobile.data.api.dto.PairFinishResponseWire
import org.sase.mobile.data.api.dto.PairStartResponseWire
import org.sase.mobile.data.api.dto.PlanActionChoiceWire
import org.sase.mobile.data.api.dto.PlanActionRequestWire
import org.sase.mobile.data.api.dto.QuestionActionChoiceWire
import org.sase.mobile.data.api.dto.QuestionActionRequestWire
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
            ApiErrorCodeWire.ConflictAlreadyHandled,
            ApiErrorCodeWire.AmbiguousPrefix,
            ApiErrorCodeWire.UnsupportedAction,
            ApiErrorCodeWire.LaunchFailed,
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
        val agentsChanged = roundTrip(
            EventRecordWire.serializer(),
            GatewayFixturePaths.EventAgentsChanged,
        )
        val helpersChanged = roundTrip(
            EventRecordWire.serializer(),
            GatewayFixturePaths.EventHelpersChanged,
        )

        assertThat(heartbeat.payload.type).isEqualTo(EventPayloadTypeWire.Heartbeat)
        assertThat(heartbeat.decodeHeartbeatPayload().sequence).isEqualTo(1)
        assertThat(notificationsChanged.payload.type)
            .isEqualTo(EventPayloadTypeWire.NotificationsChanged)
        assertThat(notificationsChanged.decodeNotificationsChangedPayload().notificationId)
            .isEqualTo("plan0001-review")
        assertThat(resync.payload.type).isEqualTo(EventPayloadTypeWire.ResyncRequired)
        assertThat(resync.decodeResyncPayload().reason).contains("restarted")
        assertThat(agentsChanged.payload.type).isEqualTo(EventPayloadTypeWire.AgentsChanged)
        assertThat(helpersChanged.payload.type).isEqualTo(EventPayloadTypeWire.HelpersChanged)
    }

    @Test
    fun parsesActionAgentHelperAndUpdateFixtures() {
        val action = roundTrip(ActionResultWire.serializer(), GatewayFixturePaths.ActionSuccess)
        val agents = roundTrip(MobileAgentListResponseWire.serializer(), GatewayFixturePaths.AgentsList)
        val resumeOptions = roundTrip(
            MobileAgentResumeOptionsResponseWire.serializer(),
            GatewayFixturePaths.AgentResumeOptions,
        )
        val launch = roundTrip(
            MobileAgentLaunchResultWire.serializer(),
            GatewayFixturePaths.AgentLaunchResult,
        )
        val imageLaunch = roundTrip(
            MobileAgentLaunchResultWire.serializer(),
            GatewayFixturePaths.AgentImageLaunchResult,
        )
        val kill = roundTrip(MobileAgentKillResultWire.serializer(), GatewayFixturePaths.AgentKillResult)
        val retry = roundTrip(MobileAgentRetryResultWire.serializer(), GatewayFixturePaths.AgentRetryResult)
        val changespecTags = roundTrip(
            MobileChangeSpecTagListResponseWire.serializer(),
            GatewayFixturePaths.ChangespecTags,
        )
        val xprompts = roundTrip(
            MobileXpromptCatalogResponseWire.serializer(),
            GatewayFixturePaths.XpromptCatalog,
        )
        val beads = roundTrip(MobileBeadListResponseWire.serializer(), GatewayFixturePaths.BeadsList)
        val bead = roundTrip(MobileBeadShowResponseWire.serializer(), GatewayFixturePaths.BeadShow)
        val updateStart = roundTrip(
            MobileUpdateStartResponseWire.serializer(),
            GatewayFixturePaths.UpdateStartRunning,
        )
        val updateSuccess = roundTrip(
            MobileUpdateStatusResponseWire.serializer(),
            GatewayFixturePaths.UpdateStatusSuccess,
        )
        val updateFailure = roundTrip(
            MobileUpdateStatusResponseWire.serializer(),
            GatewayFixturePaths.UpdateStatusFailure,
        )

        assertThat(action.actionKind).isEqualTo(MobileActionKindWire.PlanApproval)
        assertThat(agents.agents).hasSize(2)
        assertThat(agents.agents[0].actions.canKill).isTrue()
        assertThat(resumeOptions.options.map { it.kind }).containsExactly(
            MobileAgentResumeOptionKindWire.Resume,
            MobileAgentResumeOptionKindWire.Wait,
        ).inOrder()
        assertThat(launch.slots.map { it.status }).containsExactly(
            MobileAgentLaunchSlotStatusWire.Launched,
            MobileAgentLaunchSlotStatusWire.DryRun,
        ).inOrder()
        assertThat(imageLaunch.primary?.name).isEqualTo("mobile-image-agent")
        assertThat(kill.changed).isTrue()
        assertThat(retry.sourceAgent).isEqualTo("mobile-failed")
        assertThat(changespecTags.result.status).isEqualTo(MobileHelperStatusWire.PartialSuccess)
        assertThat(xprompts.stats.pdfRequested).isTrue()
        assertThat(beads.beads[0].id).isEqualTo("sase-26.6.1")
        assertThat(bead.bead.blocks).contains("sase-26.6.2")
        assertThat(updateStart.job.status).isEqualTo(MobileUpdateJobStatusWire.Running)
        assertThat(updateSuccess.job.status).isEqualTo(MobileUpdateJobStatusWire.Succeeded)
        assertThat(updateFailure.result.status).isEqualTo(MobileHelperStatusWire.Failed)
    }

    @Test
    fun requestDtoDefaultsPreserveGatewaySchemaAndOptionalFields() {
        val plan = PlanActionRequestWire(
            prefix = "plan0001",
            choice = PlanActionChoiceWire.Feedback,
            feedback = "Needs tests",
        )
        val hitl = HitlActionRequestWire(
            prefix = "hitl0001",
            choice = HitlActionChoiceWire.Accept,
        )
        val question = QuestionActionRequestWire(
            prefix = "question0001",
            choice = QuestionActionChoiceWire.Answer,
            selectedOptionId = "yes",
        )
        val textLaunch = MobileAgentTextLaunchRequestWire(
            prompt = "#gh:feature\nRun tests",
            requestId = "launch-1",
        )
        val imageLaunch = MobileAgentImageLaunchRequestWire(
            prompt = "Inspect this screenshot",
            originalFilename = "screen.png",
            contentType = "image/png",
            byteLength = 12,
            base64Image = "aGVsbG8gd29ybGQ=",
        )
        val kill = MobileAgentKillRequestWire(reason = "mobile user request")
        val retry = MobileAgentRetryRequestWire(requestId = "retry-1")

        assertThat(plan.schemaVersion).isEqualTo(1)
        assertThat(hitl.schemaVersion).isEqualTo(1)
        assertThat(question.schemaVersion).isEqualTo(1)
        assertThat(textLaunch.schemaVersion).isEqualTo(1)
        assertThat(imageLaunch.schemaVersion).isEqualTo(1)
        assertThat(kill.schemaVersion).isEqualTo(1)
        assertThat(retry.schemaVersion).isEqualTo(1)
    }

    @Test
    fun eventPayloadDtoSurfaceCoversMobileEventKinds() {
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
