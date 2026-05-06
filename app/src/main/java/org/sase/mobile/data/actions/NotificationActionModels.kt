package org.sase.mobile.data.actions

import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.GatewayTransportErrorKind
import org.sase.mobile.data.api.dto.ActionResultWire
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.HitlActionChoiceWire
import org.sase.mobile.data.api.dto.MobileActionDetailWire
import org.sase.mobile.data.api.dto.MobileActionKindWire
import org.sase.mobile.data.api.dto.MobileActionStateWire
import org.sase.mobile.data.api.dto.PlanActionChoiceWire
import org.sase.mobile.data.notifications.NotificationDetailState

interface NotificationActionController {
    suspend fun submitAction(
        notificationId: String,
        action: MobileActionDetailWire,
        choice: NotificationActionChoice,
    ): ActionSubmissionState
}

data class NotificationActionControl(
    val key: String,
    val label: String,
    val choice: NotificationActionChoice,
    val requiresConfirmation: Boolean = false,
)

sealed interface NotificationActionChoice {
    data class Plan(val choice: PlanActionChoiceWire) : NotificationActionChoice
    data class Hitl(val choice: HitlActionChoiceWire) : NotificationActionChoice
    data class QuestionOption(
        val label: String,
        val index: Int,
    ) : NotificationActionChoice
}

sealed interface ActionSubmissionState {
    data class Success(
        val result: ActionResultWire,
        val refreshedDetail: NotificationDetailState.Ready?,
    ) : ActionSubmissionState

    data class Failure(
        val failure: ActionFailure,
        val refreshedDetail: NotificationDetailState.Ready?,
    ) : ActionSubmissionState
}

data class ActionFailure(
    val kind: ActionFailureKind,
    val message: String,
    val canRefresh: Boolean = false,
    val canRetry: Boolean = false,
    val routeToSettings: Boolean = false,
)

enum class ActionFailureKind {
    MissingRequest,
    MissingTarget,
    Stale,
    AlreadyHandled,
    AmbiguousPrefix,
    Unsupported,
    Disconnected,
    AuthExpired,
    BridgeUnavailable,
    InvalidResponse,
    Unknown,
}

fun deriveNotificationActionControls(
    action: MobileActionDetailWire,
): List<NotificationActionControl> {
    if (action.state != MobileActionStateWire.Available || action.identity == null) {
        return emptyList()
    }
    return when (action.kind) {
        MobileActionKindWire.PlanApproval -> planControls(action.choices)
        MobileActionKindWire.Hitl -> hitlControls(action.choices)
        MobileActionKindWire.UserQuestion -> questionControls(action.choices)
        MobileActionKindWire.NonAction,
        MobileActionKindWire.Unsupported,
        -> emptyList()
    }
}

fun actionUnavailableFailure(action: MobileActionDetailWire): ActionFailure? {
    if (action.identity == null) {
        return ActionFailure(
            kind = ActionFailureKind.MissingRequest,
            message = "Action request is missing. Refresh notification detail.",
            canRefresh = true,
        )
    }
    return when (action.state) {
        MobileActionStateWire.Available -> null
        MobileActionStateWire.AlreadyHandled -> ActionFailure(
            kind = ActionFailureKind.AlreadyHandled,
            message = "This action was already handled on the host.",
            canRefresh = true,
        )
        MobileActionStateWire.Stale -> ActionFailure(
            kind = ActionFailureKind.Stale,
            message = "Action state is stale. Refresh before acting.",
            canRefresh = true,
        )
        MobileActionStateWire.MissingRequest -> ActionFailure(
            kind = ActionFailureKind.MissingRequest,
            message = "Action request is missing. Refresh notification detail.",
            canRefresh = true,
        )
        MobileActionStateWire.MissingTarget -> ActionFailure(
            kind = ActionFailureKind.MissingTarget,
            message = "The target resource is missing on the host.",
            canRefresh = true,
        )
        MobileActionStateWire.Unsupported -> ActionFailure(
            kind = ActionFailureKind.Unsupported,
            message = "This action is not supported by the Android client.",
        )
    }
}

fun GatewayApiError.toActionFailure(): ActionFailure {
    return when (this) {
        is GatewayApiError.Http -> when (apiError?.code) {
            ApiErrorCodeWire.Unauthorized -> ActionFailure(
                kind = ActionFailureKind.AuthExpired,
                message = "Authentication expired. Reconnect the host in settings.",
                routeToSettings = true,
            )
            ApiErrorCodeWire.GoneStale -> ActionFailure(
                kind = ActionFailureKind.Stale,
                message = apiError.message,
                canRefresh = true,
            )
            ApiErrorCodeWire.ConflictAlreadyHandled -> ActionFailure(
                kind = ActionFailureKind.AlreadyHandled,
                message = apiError.message,
                canRefresh = true,
            )
            ApiErrorCodeWire.AmbiguousPrefix -> ActionFailure(
                kind = ActionFailureKind.AmbiguousPrefix,
                message = apiError.message,
                canRefresh = true,
            )
            ApiErrorCodeWire.UnsupportedAction -> ActionFailure(
                kind = ActionFailureKind.Unsupported,
                message = apiError.message,
            )
            ApiErrorCodeWire.BridgeUnavailable -> ActionFailure(
                kind = ActionFailureKind.BridgeUnavailable,
                message = apiError.message,
                canRetry = true,
            )
            null -> ActionFailure(
                kind = if (statusCode == 401) ActionFailureKind.AuthExpired else ActionFailureKind.Unknown,
                message = apiError?.message ?: "Gateway returned HTTP $statusCode",
                routeToSettings = statusCode == 401,
                canRetry = statusCode >= 500,
            )
            else -> ActionFailure(
                kind = ActionFailureKind.Unknown,
                message = apiError.message,
                canRetry = statusCode >= 500,
            )
        }
        is GatewayApiError.InvalidJson -> ActionFailure(
            kind = ActionFailureKind.InvalidResponse,
            message = "Gateway returned invalid action JSON.",
            canRetry = true,
        )
        is GatewayApiError.Transport -> ActionFailure(
            kind = ActionFailureKind.Disconnected,
            message = transportActionMessage(kind),
            canRetry = true,
        )
    }
}

fun ActionFailure.shouldRefreshAfterFailure(): Boolean {
    return kind == ActionFailureKind.Stale ||
        kind == ActionFailureKind.AlreadyHandled ||
        kind == ActionFailureKind.AmbiguousPrefix
}

private fun planControls(choices: List<String>): List<NotificationActionControl> {
    return listOf(
        planControl("approve", "Approve", PlanActionChoiceWire.Approve),
        planControl("run", "Run", PlanActionChoiceWire.Run, requiresConfirmation = true),
        planControl("reject", "Reject", PlanActionChoiceWire.Reject, requiresConfirmation = true),
        planControl("epic", "Epic", PlanActionChoiceWire.Epic, requiresConfirmation = true),
        planControl("legend", "Legend", PlanActionChoiceWire.Legend, requiresConfirmation = true),
    ).filterAvailable(choices)
}

private fun hitlControls(choices: List<String>): List<NotificationActionControl> {
    return listOf(
        hitlControl("accept", "Accept", HitlActionChoiceWire.Accept),
        hitlControl("reject", "Reject", HitlActionChoiceWire.Reject, requiresConfirmation = true),
    ).filterAvailable(choices)
}

private fun questionControls(choices: List<String>): List<NotificationActionControl> {
    return choices.mapIndexed { index, label ->
        NotificationActionControl(
            key = "question-$index",
            label = label,
            choice = NotificationActionChoice.QuestionOption(label = label, index = index),
        )
    }
}

private fun planControl(
    key: String,
    label: String,
    choice: PlanActionChoiceWire,
    requiresConfirmation: Boolean = false,
) = NotificationActionControl(
    key = key,
    label = label,
    choice = NotificationActionChoice.Plan(choice),
    requiresConfirmation = requiresConfirmation,
)

private fun hitlControl(
    key: String,
    label: String,
    choice: HitlActionChoiceWire,
    requiresConfirmation: Boolean = false,
) = NotificationActionControl(
    key = key,
    label = label,
    choice = NotificationActionChoice.Hitl(choice),
    requiresConfirmation = requiresConfirmation,
)

private fun List<NotificationActionControl>.filterAvailable(
    choices: List<String>,
): List<NotificationActionControl> {
    if (choices.isEmpty()) {
        return this
    }
    val available = choices.map { it.lowercase() }.toSet()
    return filter { it.key in available }
}

private fun transportActionMessage(kind: GatewayTransportErrorKind): String {
    return when (kind) {
        GatewayTransportErrorKind.Dns -> "Gateway host could not be resolved. Check the host connection."
        GatewayTransportErrorKind.ConnectionRefused -> "Gateway unavailable. Check the host connection and retry."
        GatewayTransportErrorKind.Timeout -> "Gateway timed out. Retry when the host is reachable."
        GatewayTransportErrorKind.TlsOrCleartextPolicy -> "Gateway TLS or cleartext policy blocked the request."
        GatewayTransportErrorKind.Network -> "Network error while contacting the gateway."
    }
}
