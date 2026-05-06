package org.sase.mobile.data.actions

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Test
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.api.dto.HitlActionChoiceWire
import org.sase.mobile.data.api.dto.MobileActionKindWire
import org.sase.mobile.data.api.dto.PlanActionChoiceWire
import org.sase.mobile.data.notifications.InMemoryNotificationCache
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.session.InMemoryHostSessionStorage
import org.sase.mobile.data.session.InMemoryTokenVault
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.testing.FakeGateway

class ActionRepositoryTest {
    @Test
    fun derivesDirectControlsFromActionDetail() {
        val plan = actionDetail()
        val hitl = plan.copy(kind = MobileActionKindWire.Hitl, choices = listOf("accept", "reject", "feedback"))
        val question = plan.copy(kind = MobileActionKindWire.UserQuestion, choices = listOf("Yes", "No"))

        val planControls = deriveNotificationActionControls(plan)
        val hitlControls = deriveNotificationActionControls(hitl)
        val questionControls = deriveNotificationActionControls(question)

        assertThat(planControls.map { it.key }).containsExactly("approve", "run", "reject", "epic", "legend")
        assertThat(planControls.single { it.key == "approve" }.requiresConfirmation).isFalse()
        assertThat(planControls.single { it.key == "run" }.requiresConfirmation).isTrue()
        assertThat(hitlControls.map { it.key }).containsExactly("accept", "reject")
        assertThat((questionControls.first().choice as NotificationActionChoice.QuestionOption).label).isEqualTo("Yes")
    }

    @Test
    fun submitPlanActionRefreshesInboxAndDetailAfterSuccess() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val notificationRepository = notificationRepository(gateway.baseUrl)
            val repository = actionRepository(gateway.baseUrl, notificationRepository)

            val result = repository.submitAction(
                notificationId = "plan0001-review",
                action = actionDetail(),
                choice = NotificationActionChoice.Plan(PlanActionChoiceWire.Approve),
            )

            assertThat(result).isInstanceOf(ActionSubmissionState.Success::class.java)
            result as ActionSubmissionState.Success
            assertThat(result.result.message).isEqualTo("plan approved")
            assertThat(result.refreshedDetail?.detail?.notification?.id).isEqualTo("plan0001-review")
            assertThat(notificationRepository.inbox.value.cards).hasSize(7)
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/actions/plan/plan0001/approve")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications?include_dismissed=true")
            assertThat(gateway.takeRequest().path).isEqualTo("/api/v1/notifications/plan0001-review")
        }
    }

    @Test
    fun submitHitlAndQuestionUseTypedEndpoints() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = actionRepository(gateway.baseUrl, notificationRepository = null)

            assertThat(
                repository.submitAction(
                    notificationId = "hitl0001-choice",
                    action = actionDetail().copy(kind = MobileActionKindWire.Hitl),
                    choice = NotificationActionChoice.Hitl(HitlActionChoiceWire.Accept),
                ),
            ).isInstanceOf(ActionSubmissionState.Success::class.java)
            assertThat(
                repository.submitAction(
                    notificationId = "quest001-answer",
                    action = actionDetail().copy(kind = MobileActionKindWire.UserQuestion),
                    choice = NotificationActionChoice.QuestionOption(label = "Yes", index = 0),
                ),
            ).isInstanceOf(ActionSubmissionState.Success::class.java)

            val hitlRequest = gateway.takeRequest()
            val questionRequest = gateway.takeRequest()
            assertThat(hitlRequest.path).isEqualTo("/api/v1/actions/hitl/plan0001/accept")
            assertThat(questionRequest.path).isEqualTo("/api/v1/actions/question/plan0001/answer")
            assertThat(questionRequest.body.readUtf8()).contains("\"selected_option_label\":\"Yes\"")
        }
    }

    @Test
    fun staleAndAlreadyHandledFailuresRefreshAuthoritativeState() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = actionRepository(gateway.baseUrl, notificationRepository(gateway.baseUrl))

            val stale = repository.submitAction(
                notificationId = "plan0001-review",
                action = actionDetail(prefix = "stale"),
                choice = NotificationActionChoice.Plan(PlanActionChoiceWire.Approve),
            )
            val handled = repository.submitAction(
                notificationId = "plan0001-review",
                action = actionDetail(prefix = "handled"),
                choice = NotificationActionChoice.Plan(PlanActionChoiceWire.Approve),
            )

            assertThat((stale as ActionSubmissionState.Failure).failure.kind).isEqualTo(ActionFailureKind.Stale)
            assertThat(stale.refreshedDetail).isNotNull()
            assertThat((handled as ActionSubmissionState.Failure).failure.kind)
                .isEqualTo(ActionFailureKind.AlreadyHandled)
            assertThat(handled.refreshedDetail).isNotNull()
        }
    }

    @Test
    fun unavailableActionReturnsDisabledFailureWithoutGatewayCall() = runTest {
        FakeGateway().use { gateway ->
            gateway.installEpicSixHarness()
            val repository = actionRepository(gateway.baseUrl, notificationRepository = null)

            val result = repository.submitAction(
                notificationId = "plan0001-review",
                action = actionDetail().copy(identity = null),
                choice = NotificationActionChoice.Plan(PlanActionChoiceWire.Approve),
            )

            assertThat((result as ActionSubmissionState.Failure).failure.kind)
                .isEqualTo(ActionFailureKind.MissingRequest)
            assertThat(gateway.requestCount).isEqualTo(0)
        }
    }

    private fun actionRepository(
        baseUrl: HttpUrl,
        notificationRepository: NotificationRepository?,
    ): ActionRepository {
        return ActionRepository(
            sessionStorage = InMemoryHostSessionStorage(pairedSession(baseUrl)),
            tokenVault = InMemoryTokenVault(FakeGateway.SmokeAuthToken),
            clientFactory = { url, tokenProvider ->
                GatewayApiClient(
                    baseUrl = url,
                    bearerTokenProvider = tokenProvider,
                    client = OkHttpClient(),
                )
            },
            notificationRepository = notificationRepository,
        )
    }

    private fun notificationRepository(baseUrl: HttpUrl): NotificationRepository {
        return NotificationRepository(
            sessionStorage = InMemoryHostSessionStorage(pairedSession(baseUrl)),
            tokenVault = InMemoryTokenVault(FakeGateway.SmokeAuthToken),
            cache = InMemoryNotificationCache(),
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

    private fun actionDetail(prefix: String = "plan0001") = org.sase.mobile.ui.NotificationUiFixtures.detail.action
        .copy(identity = org.sase.mobile.ui.NotificationUiFixtures.detail.action.identity?.copy(prefix = prefix))
}
