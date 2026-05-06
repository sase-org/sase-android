package org.sase.mobile.data.actions

import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayApiResult
import org.sase.mobile.data.api.dto.ActionResultWire
import org.sase.mobile.data.api.dto.HitlActionRequestWire
import org.sase.mobile.data.api.dto.MobileActionDetailWire
import org.sase.mobile.data.api.dto.PlanActionRequestWire
import org.sase.mobile.data.api.dto.QuestionActionChoiceWire
import org.sase.mobile.data.api.dto.QuestionActionRequestWire
import org.sase.mobile.data.notifications.NotificationDetailState
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.notifications.RefreshReason
import org.sase.mobile.data.session.HostSessionStorage
import org.sase.mobile.data.session.TokenVault

class ActionRepository(
    private val sessionStorage: HostSessionStorage,
    private val tokenVault: TokenVault,
    private val clientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewayApiClient,
    private val notificationRepository: NotificationRepository?,
    private val draftStore: ActionDraftStore,
) : NotificationActionController {
    override suspend fun submitAction(
        notificationId: String,
        action: MobileActionDetailWire,
        choice: NotificationActionChoice,
    ): ActionSubmissionState {
        actionUnavailableFailure(action)?.let { failure ->
            return ActionSubmissionState.Failure(failure, refreshedDetail = null)
        }
        val identity = action.identity ?: return ActionSubmissionState.Failure(
            ActionFailure(
                kind = ActionFailureKind.MissingRequest,
                message = "Action request is missing. Refresh notification detail.",
                canRefresh = true,
            ),
            refreshedDetail = null,
        )
        val session = sessionStorage.read() ?: return ActionSubmissionState.Failure(
            ActionFailure(
                kind = ActionFailureKind.AuthExpired,
                message = "Pair a gateway in settings before sending actions.",
                routeToSettings = true,
            ),
            refreshedDetail = null,
        )
        val token = runCatching { tokenVault.readToken() }.getOrNull()
        val client = clientFactory(session.baseUrl) { token }
        return when (val result = submit(client, identity.prefix, choice)) {
            is GatewayApiResult.Success -> {
                draftKeyForSubmission(notificationId, action, choice)?.let { draftStore.clear(it) }
                ActionSubmissionState.Success(
                    result = result.value,
                    refreshedDetail = refreshAfterMutation(notificationId, refreshInbox = true),
                )
            }
            is GatewayApiResult.Failure -> {
                val failure = result.error.toActionFailure()
                val refreshed = if (failure.shouldRefreshAfterFailure()) {
                    refreshAfterMutation(notificationId, refreshInbox = true)
                } else {
                    null
                }
                ActionSubmissionState.Failure(failure, refreshedDetail = refreshed)
            }
        }
    }

    override suspend fun readDraft(key: ActionDraftKey): String? = draftStore.read(key)

    override suspend fun saveDraft(key: ActionDraftKey, text: String) {
        draftStore.write(key, text)
    }

    override suspend fun discardDraft(key: ActionDraftKey) {
        draftStore.clear(key)
    }

    private suspend fun submit(
        client: GatewayApiClient,
        prefix: String,
        choice: NotificationActionChoice,
    ): GatewayApiResult<ActionResultWire> {
        return when (choice) {
            is NotificationActionChoice.Plan -> client.submitPlanAction(
                PlanActionRequestWire(
                    prefix = prefix,
                    choice = choice.choice,
                    feedback = choice.feedback,
                ),
            )
            is NotificationActionChoice.Hitl -> client.submitHitlAction(
                HitlActionRequestWire(
                    prefix = prefix,
                    choice = choice.choice,
                    feedback = choice.feedback,
                ),
            )
            is NotificationActionChoice.QuestionOption -> client.submitQuestionAction(
                QuestionActionRequestWire(
                    prefix = prefix,
                    choice = QuestionActionChoiceWire.Answer,
                    selectedOptionLabel = choice.label,
                    selectedOptionIndex = choice.index,
                    globalNote = choice.globalNote,
                ),
            )
            is NotificationActionChoice.QuestionCustom -> client.submitQuestionAction(
                QuestionActionRequestWire(
                    prefix = prefix,
                    choice = QuestionActionChoiceWire.Custom,
                    customAnswer = choice.answer,
                ),
            )
        }
    }

    private suspend fun refreshAfterMutation(
        notificationId: String,
        refreshInbox: Boolean,
    ): NotificationDetailState.Ready? {
        val repository = notificationRepository ?: return null
        if (refreshInbox) {
            repository.fullRefresh(RefreshReason.Manual)
        }
        return repository.refreshDetail(notificationId) as? NotificationDetailState.Ready
    }

    private fun draftKeyForSubmission(
        notificationId: String,
        action: MobileActionDetailWire,
        choice: NotificationActionChoice,
    ): ActionDraftKey? {
        val key = when (choice) {
            is NotificationActionChoice.Plan ->
                if (choice.feedback == null) null else choice.choice.name.lowercase()
            is NotificationActionChoice.Hitl ->
                if (choice.feedback == null) null else choice.choice.name.lowercase()
            is NotificationActionChoice.QuestionOption ->
                if (choice.globalNote == null) null else "question-${choice.index}-note"
            is NotificationActionChoice.QuestionCustom ->
                if (choice.answer == null) null else "question-custom"
        } ?: return null
        return ActionDraftKey(notificationId, action.kind, key)
    }
}
