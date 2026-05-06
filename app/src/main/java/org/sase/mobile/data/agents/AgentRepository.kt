package org.sase.mobile.data.agents

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.GatewayApiResult
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.api.GatewaySseResult
import org.sase.mobile.data.api.SseReconnectPolicy
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.AgentsChangedEventPayloadWire
import org.sase.mobile.data.api.dto.EventPayloadTypeWire
import org.sase.mobile.data.api.dto.EventRecordWire
import org.sase.mobile.data.api.dto.MobileAgentKillRequestWire
import org.sase.mobile.data.api.dto.MobileAgentLaunchResultWire
import org.sase.mobile.data.api.dto.MobileAgentListRequestWire
import org.sase.mobile.data.api.dto.MobileAgentResumeOptionWire
import org.sase.mobile.data.api.dto.MobileAgentRetryRequestWire
import org.sase.mobile.data.api.dto.MobileAgentSummaryWire
import org.sase.mobile.data.session.HostSessionStorage
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.data.session.TokenVault
import org.sase.mobile.data.session.toUserMessage

class AgentRepository(
    private val sessionStorage: HostSessionStorage,
    private val tokenVault: TokenVault,
    private val clientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewayApiClient,
    private val sseClientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewaySseClient,
    private val reconnectPolicy: SseReconnectPolicy = SseReconnectPolicy(),
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AgentsState())
    val state: StateFlow<AgentsState> = mutableState.asStateFlow()

    @Volatile
    private var stopped = false
    private var activeJob: Job? = null
    private var lastEventId: String? = null

    @Synchronized
    fun start() {
        activeJob?.cancel()
        stopped = false
        activeJob = scope.launch {
            refresh(AgentsRefreshReason.AppStart)
            runSseLoop()
        }
    }

    @Synchronized
    fun stop() {
        stopped = true
        activeJob?.cancel()
        activeJob = null
        mutableState.value = mutableState.value.copy(connection = AgentConnectionState.Stopped)
    }

    suspend fun refresh(reason: AgentsRefreshReason = AgentsRefreshReason.Manual): Boolean {
        val session = sessionStorage.read()
        if (session == null) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                connection = AgentConnectionState.LoggedOut,
                action = AgentActionState.Idle,
            )
            return false
        }
        val client = client(session)
        mutableState.value = mutableState.value.copy(loading = true, refreshReason = reason)

        val agentsResult = client.agents(MobileAgentListRequestWire(includeRecent = true))
        val optionsResult = client.agentResumeOptions()
        val nextState = when {
            agentsResult is GatewayApiResult.Success && optionsResult is GatewayApiResult.Success -> {
                mutableState.value.copy(
                    agents = agentsResult.value.agents,
                    resumeOptions = optionsResult.value.options,
                    totalCount = agentsResult.value.totalCount,
                    loading = false,
                    refreshReason = null,
                    connection = AgentConnectionState.Connected,
                    failure = null,
                )
            }

            agentsResult is GatewayApiResult.Failure -> failureState(session, agentsResult.error)
            optionsResult is GatewayApiResult.Failure -> failureState(session, optionsResult.error)
            else -> mutableState.value.copy(loading = false, refreshReason = null)
        }
        mutableState.value = nextState
        return nextState.failure == null && nextState.connection == AgentConnectionState.Connected
    }

    suspend fun killAgent(name: String): AgentActionState {
        val session = sessionStorage.read() ?: return publishAction(AgentActionState.Failed("Not paired"))
        mutableState.value = mutableState.value.copy(action = AgentActionState.Running("Killing $name"))
        return when (val result = client(session).killAgent(name, MobileAgentKillRequestWire())) {
            is GatewayApiResult.Success -> {
                refresh(AgentsRefreshReason.ActionCompleted)
                publishAction(
                    AgentActionState.Succeeded(
                        result.value.message ?: "Kill requested for ${result.value.name}",
                    ),
                )
            }

            is GatewayApiResult.Failure -> publishAction(handleActionFailure(session, result.error))
        }
    }

    suspend fun retryAgent(name: String): AgentActionState {
        val session = sessionStorage.read() ?: return publishAction(AgentActionState.Failed("Not paired"))
        mutableState.value = mutableState.value.copy(action = AgentActionState.Running("Retrying $name"))
        return when (val result = client(session).retryAgent(name, MobileAgentRetryRequestWire())) {
            is GatewayApiResult.Success -> {
                val primary = result.value.launch.primary?.name ?: result.value.sourceAgent
                refresh(AgentsRefreshReason.ActionCompleted)
                publishAction(AgentActionState.Succeeded("Retry launched: $primary"))
            }

            is GatewayApiResult.Failure -> publishAction(handleActionFailure(session, result.error))
        }
    }

    fun clearActionResult() {
        mutableState.value = mutableState.value.copy(action = AgentActionState.Idle)
    }

    suspend fun handleAgentsChanged(payload: AgentsChangedEventPayloadWire) {
        refresh(
            if (payload.reason == "resync_required") {
                AgentsRefreshReason.ResyncRequired
            } else {
                AgentsRefreshReason.AgentsChanged
            },
        )
    }

    suspend fun runSseLoop(maxConnections: Int = Int.MAX_VALUE) {
        var connectionCount = 0
        var reconnectAttempt = 0

        while (!stopped && connectionCount < maxConnections) {
            val session = sessionStorage.read()
            if (session == null) {
                mutableState.value = mutableState.value.copy(connection = AgentConnectionState.LoggedOut)
                return
            }
            val token = runCatching { tokenVault.readToken() }.getOrNull()
            val client = sseClientFactory(session.baseUrl) { token }
            mutableState.value = mutableState.value.copy(
                connection = if (lastEventId == null) {
                    AgentConnectionState.Connecting
                } else {
                    AgentConnectionState.Reconnecting(lastEventId.orEmpty())
                },
            )
            if (connectionCount > 0) {
                refresh(AgentsRefreshReason.Reconnect)
            }
            when (val result = client.collectEvents(lastEventId) { event -> handleEvent(event) }) {
                is GatewaySseResult.Success -> {
                    connectionCount += 1
                    reconnectAttempt = 0
                    mutableState.value = mutableState.value.copy(connection = AgentConnectionState.Connected)
                    if (!stopped && connectionCount < maxConnections) {
                        mutableState.value = mutableState.value.copy(
                            connection = AgentConnectionState.Offline("event stream ended"),
                        )
                        delayProvider(reconnectPolicy.delayMillis(reconnectAttempt))
                    }
                }

                is GatewaySseResult.Failure -> {
                    connectionCount += 1
                    val next = failureState(session, result.error)
                    mutableState.value = next
                    if (result.error.isUnauthorized()) {
                        stopped = true
                        return
                    }
                    if (!stopped && connectionCount < maxConnections) {
                        delayProvider(reconnectPolicy.delayMillis(reconnectAttempt))
                        reconnectAttempt += 1
                    }
                }
            }
        }
    }

    private suspend fun handleEvent(event: EventRecordWire) {
        lastEventId = event.id
        mutableState.value = mutableState.value.copy(lastEventId = event.id)
        when (event.payload.type) {
            EventPayloadTypeWire.AgentsChanged -> refresh(AgentsRefreshReason.AgentsChanged)
            EventPayloadTypeWire.ResyncRequired,
            EventPayloadTypeWire.Session,
            -> refresh(AgentsRefreshReason.ResyncRequired)

            EventPayloadTypeWire.Heartbeat,
            EventPayloadTypeWire.NotificationsChanged,
            EventPayloadTypeWire.HelpersChanged,
            -> Unit
        }
    }

    private fun client(session: PairedHostSession): GatewayApiClient {
        val token = runCatching { tokenVault.readToken() }.getOrNull()
        return clientFactory(session.baseUrl) { token }
    }

    private fun failureState(
        session: PairedHostSession,
        error: GatewayApiError,
    ): AgentsState {
        return mutableState.value.copy(
            loading = false,
            refreshReason = null,
            failure = error.toAgentFailure(),
            connection = if (error.isUnauthorized()) {
                AgentConnectionState.LoggedOut
            } else {
                AgentConnectionState.Offline(error.toUserMessage())
            },
            hostLabel = session.hostLabel,
        )
    }

    private fun handleActionFailure(
        session: PairedHostSession,
        error: GatewayApiError,
    ): AgentActionState {
        mutableState.value = failureState(session, error)
        return AgentActionState.Failed(error.toAgentFailure().message)
    }

    private fun publishAction(action: AgentActionState): AgentActionState {
        mutableState.value = mutableState.value.copy(action = action)
        return action
    }
}

data class AgentsState(
    val agents: List<MobileAgentSummaryWire> = emptyList(),
    val resumeOptions: List<MobileAgentResumeOptionWire> = emptyList(),
    val recentLaunchResults: List<MobileAgentLaunchResultWire> = emptyList(),
    val totalCount: Long = 0,
    val loading: Boolean = false,
    val refreshReason: AgentsRefreshReason? = null,
    val connection: AgentConnectionState = AgentConnectionState.Stopped,
    val failure: AgentFailure? = null,
    val action: AgentActionState = AgentActionState.Idle,
    val lastEventId: String? = null,
    val hostLabel: String? = null,
)

enum class AgentsRefreshReason {
    AppStart,
    Manual,
    Reconnect,
    AgentsChanged,
    ResyncRequired,
    ActionCompleted,
}

sealed interface AgentConnectionState {
    data object Stopped : AgentConnectionState
    data object Connecting : AgentConnectionState
    data class Reconnecting(val lastEventId: String) : AgentConnectionState
    data object Connected : AgentConnectionState
    data class Offline(val message: String) : AgentConnectionState
    data object LoggedOut : AgentConnectionState
}

sealed interface AgentActionState {
    data object Idle : AgentActionState
    data class Running(val label: String) : AgentActionState
    data class Succeeded(val message: String) : AgentActionState
    data class Failed(val message: String) : AgentActionState
}

data class AgentFailure(
    val kind: AgentFailureKind,
    val message: String,
)

enum class AgentFailureKind {
    Offline,
    AuthExpired,
    BridgeUnavailable,
    AgentMissing,
    AgentNotRunning,
    LaunchFailed,
    InvalidResponse,
    Unknown,
}

fun GatewayApiError.toAgentFailure(): AgentFailure {
    return when (this) {
        is GatewayApiError.Http -> {
            val code = apiError?.code
            AgentFailure(
                kind = when (code) {
                    ApiErrorCodeWire.Unauthorized -> AgentFailureKind.AuthExpired
                    ApiErrorCodeWire.BridgeUnavailable -> AgentFailureKind.BridgeUnavailable
                    ApiErrorCodeWire.AgentNotFound -> AgentFailureKind.AgentMissing
                    ApiErrorCodeWire.AgentNotRunning -> AgentFailureKind.AgentNotRunning
                    ApiErrorCodeWire.LaunchFailed -> AgentFailureKind.LaunchFailed
                    else -> AgentFailureKind.Unknown
                },
                message = apiError?.message ?: "Gateway returned HTTP $statusCode",
            )
        }

        is GatewayApiError.InvalidJson -> AgentFailure(
            kind = AgentFailureKind.InvalidResponse,
            message = "Gateway returned invalid agent data",
        )

        is GatewayApiError.Transport -> AgentFailure(
            kind = AgentFailureKind.Offline,
            message = "Gateway unavailable: ${kind.name.lowercase()}",
        )
    }
}

private fun GatewayApiError.isUnauthorized(): Boolean {
    return this is GatewayApiError.Http &&
        (statusCode == 401 || apiError?.code == ApiErrorCodeWire.Unauthorized)
}
