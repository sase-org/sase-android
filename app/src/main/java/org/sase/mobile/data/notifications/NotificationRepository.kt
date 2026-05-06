package org.sase.mobile.data.notifications

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.GatewayApiResult
import org.sase.mobile.data.api.GatewaySseClient
import org.sase.mobile.data.api.GatewaySseResult
import org.sase.mobile.data.api.NotificationListQuery
import org.sase.mobile.data.api.SseReconnectPolicy
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.AgentsChangedEventPayloadWire
import org.sase.mobile.data.api.dto.EventPayloadTypeWire
import org.sase.mobile.data.api.dto.EventRecordWire
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.HelpersChangedEventPayloadWire
import org.sase.mobile.data.api.dto.MobileNotificationCardWire
import org.sase.mobile.data.api.dto.MobileNotificationDetailResponseWire
import org.sase.mobile.data.api.dto.NotificationStateMutationResponseWire
import org.sase.mobile.data.session.HostSessionStorage
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.data.session.TokenVault

class NotificationRepository(
    private val sessionStorage: HostSessionStorage,
    private val tokenVault: TokenVault,
    private val cache: NotificationCache,
    private val clientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewayApiClient,
    private val sseClientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewaySseClient,
    private val reconnectPolicy: SseReconnectPolicy = SseReconnectPolicy(),
    private val clock: Clock = Clock.systemUTC(),
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
    private val onAgentsChanged: suspend (AgentsChangedEventPayloadWire) -> Unit = {},
    private val onHelpersChanged: suspend (HelpersChangedEventPayloadWire) -> Unit = {},
    private val scope: CoroutineScope,
) {
    private val mutableInbox = MutableStateFlow(NotificationInboxState())
    val inbox: StateFlow<NotificationInboxState> = mutableInbox.asStateFlow()

    private val mutableConnection = MutableStateFlow(NotificationConnectionState.Stopped)
    val connection: StateFlow<NotificationConnectionState> = mutableConnection.asStateFlow()

    private val mutableRefresh = MutableStateFlow(NotificationRefreshState.Idle)
    val refresh: StateFlow<NotificationRefreshState> = mutableRefresh.asStateFlow()

    private val mutableHelperEvents = MutableStateFlow(0)
    val helperEvents: StateFlow<Int> = mutableHelperEvents.asStateFlow()

    @Volatile
    private var stopped = false
    private var activeJob: Job? = null

    @Synchronized
    fun start() {
        activeJob?.cancel()
        stopped = false
        activeJob = scope.launch {
            loadCachedState()
            val session = sessionStorage.read()
            if (session == null) {
                mutableConnection.value = NotificationConnectionState.LoggedOut
                return@launch
            }
            cache.writeSessionSummary(session)
            fullRefresh(RefreshReason.AppStart)
            runSseLoop()
        }
    }

    @Synchronized
    fun stop() {
        stopped = true
        activeJob?.cancel()
        activeJob = null
        mutableConnection.value = NotificationConnectionState.Stopped
    }

    suspend fun loadCachedState() {
        val snapshot = cache.read()
        mutableInbox.value = NotificationInboxState(
            cards = snapshot.cards,
            isStale = snapshot.cards.isNotEmpty(),
            lastEventId = snapshot.syncState.lastEventId,
            lastFullRefreshAt = snapshot.syncState.lastFullRefreshAt,
        )
    }

    suspend fun fullRefresh(reason: RefreshReason = RefreshReason.Manual): Boolean {
        val session = sessionStorage.read()
        if (session == null) {
            mutableConnection.value = NotificationConnectionState.LoggedOut
            mutableRefresh.value = NotificationRefreshState.Idle
            return false
        }
        val token = runCatching { tokenVault.readToken() }.getOrNull()
        val client = clientFactory(session.baseUrl) { token }
        mutableRefresh.value = NotificationRefreshState.Refreshing(reason)
        return when (val result = client.notifications(NotificationListQuery(includeDismissed = true))) {
            is GatewayApiResult.Success -> {
                val refreshedAt = now()
                cache.writeCards(result.value.notifications)
                cache.writeSyncState(
                    cache.read().syncState.copy(lastFullRefreshAt = refreshedAt),
                )
                cache.writeSessionSummary(session)
                mutableInbox.value = NotificationInboxState(
                    cards = result.value.notifications,
                    isStale = false,
                    lastEventId = cache.read().syncState.lastEventId,
                    lastFullRefreshAt = refreshedAt,
                )
                mutableConnection.value = NotificationConnectionState.Connected
                mutableRefresh.value = NotificationRefreshState.Idle
                true
            }

            is GatewayApiResult.Failure -> {
                handleFailure(session, result.error)
                mutableInbox.value = mutableInbox.value.copy(isStale = mutableInbox.value.cards.isNotEmpty())
                mutableRefresh.value = NotificationRefreshState.Failed(result.error.toString())
                false
            }
        }
    }

    suspend fun refreshDetail(notificationId: String): NotificationDetailState {
        val session = sessionStorage.read() ?: return NotificationDetailState.LoggedOut
        val token = runCatching { tokenVault.readToken() }.getOrNull()
        val client = clientFactory(session.baseUrl) { token }
        return when (val result = client.notificationDetail(notificationId)) {
            is GatewayApiResult.Success -> {
                cache.writeDetail(result.value)
                NotificationDetailState.Ready(result.value, stale = false)
            }

            is GatewayApiResult.Failure -> {
                handleFailure(session, result.error)
                val cached = cache.read().details[notificationId]
                if (cached == null) {
                    NotificationDetailState.Failed(result.error.toString())
                } else {
                    NotificationDetailState.Ready(cached, stale = true)
                }
            }
        }
    }

    suspend fun markRead(notificationId: String): Boolean {
        return mutateState(notificationId) { client ->
            client.markNotificationRead(notificationId)
        }
    }

    suspend fun dismiss(notificationId: String): Boolean {
        return mutateState(notificationId) { client ->
            client.dismissNotification(notificationId)
        }
    }

    suspend fun runSseLoop(maxConnections: Int = Int.MAX_VALUE) {
        var connectionCount = 0
        var reconnectAttempt = 0

        while (!stopped && connectionCount < maxConnections) {
            val session = sessionStorage.read()
            if (session == null) {
                mutableConnection.value = NotificationConnectionState.LoggedOut
                return
            }
            val token = runCatching { tokenVault.readToken() }.getOrNull()
            val lastEventId = cache.read().syncState.lastEventId
            val client = sseClientFactory(session.baseUrl) { token }
            mutableConnection.value = if (lastEventId == null) {
                NotificationConnectionState.Connecting
            } else {
                NotificationConnectionState.Reconnecting(lastEventId)
            }
            if (connectionCount > 0) {
                fullRefresh(RefreshReason.Reconnect)
            }
            when (val result = client.collectEvents(lastEventId) { event -> handleEvent(event) }) {
                is GatewaySseResult.Success -> {
                    connectionCount += 1
                    reconnectAttempt = 0
                    mutableConnection.value = NotificationConnectionState.Connected
                    if (!stopped && connectionCount < maxConnections) {
                        mutableConnection.value = NotificationConnectionState.Offline("event stream ended")
                        delayProvider(reconnectPolicy.delayMillis(reconnectAttempt))
                    }
                }

                is GatewaySseResult.Failure -> {
                    connectionCount += 1
                    handleFailure(session, result.error)
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
        val syncState = cache.read().syncState.copy(lastEventId = event.id)
        cache.writeSyncState(syncState)
        mutableInbox.value = mutableInbox.value.copy(lastEventId = event.id)

        when (event.payload.type) {
            EventPayloadTypeWire.Heartbeat -> Unit
            EventPayloadTypeWire.NotificationsChanged -> fullRefresh(RefreshReason.NotificationsChanged)
            EventPayloadTypeWire.ResyncRequired -> fullRefresh(RefreshReason.ResyncRequired)
            EventPayloadTypeWire.Session -> fullRefresh(RefreshReason.SessionChanged)
            EventPayloadTypeWire.AgentsChanged -> handleAgentsChanged(event)
            EventPayloadTypeWire.HelpersChanged -> {
                mutableHelperEvents.value += 1
                handleHelpersChanged(event)
            }
        }
    }

    private suspend fun handleAgentsChanged(event: EventRecordWire) {
        val payload = try {
            GatewayJson.format.decodeFromJsonElement(
                AgentsChangedEventPayloadWire.serializer(),
                event.payload.data,
            )
        } catch (_: kotlinx.serialization.SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        onAgentsChanged(payload)
    }

    private suspend fun handleHelpersChanged(event: EventRecordWire) {
        val payload = try {
            GatewayJson.format.decodeFromJsonElement(
                HelpersChangedEventPayloadWire.serializer(),
                event.payload.data,
            )
        } catch (_: kotlinx.serialization.SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        onHelpersChanged(payload)
    }

    private suspend fun mutateState(
        notificationId: String,
        call: suspend (GatewayApiClient) -> GatewayApiResult<NotificationStateMutationResponseWire>,
    ): Boolean {
        val session = sessionStorage.read() ?: return false
        val token = runCatching { tokenVault.readToken() }.getOrNull()
        val client = clientFactory(session.baseUrl) { token }
        return when (val result = call(client)) {
            is GatewayApiResult.Success -> {
                cache.updateCardState(
                    id = notificationId,
                    read = result.value.read,
                    dismissed = result.value.dismissed,
                )
                mutableInbox.value = mutableInbox.value.copy(
                    cards = mutableInbox.value.cards.map { card ->
                        if (card.id == notificationId) {
                            card.copy(read = result.value.read, dismissed = result.value.dismissed)
                        } else {
                            card
                        }
                    },
                )
                true
            }

            is GatewayApiResult.Failure -> {
                handleFailure(session, result.error)
                false
            }
        }
    }

    private fun handleFailure(session: PairedHostSession, error: GatewayApiError) {
        mutableConnection.value = if (error.isUnauthorized()) {
            NotificationConnectionState.LoggedOut
        } else {
            NotificationConnectionState.Offline(error.toString())
        }
        if (!error.isUnauthorized()) {
            mutableInbox.value = mutableInbox.value.copy(isStale = mutableInbox.value.cards.isNotEmpty())
        }
    }

    private fun now(): String = Instant.now(clock).toString()
}

data class NotificationInboxState(
    val cards: List<MobileNotificationCardWire> = emptyList(),
    val isStale: Boolean = false,
    val lastEventId: String? = null,
    val lastFullRefreshAt: String? = null,
)

sealed interface NotificationDetailState {
    data object LoggedOut : NotificationDetailState
    data class Ready(val detail: MobileNotificationDetailResponseWire, val stale: Boolean) : NotificationDetailState
    data class Failed(val message: String) : NotificationDetailState
}

sealed interface NotificationConnectionState {
    data object Stopped : NotificationConnectionState
    data object Connecting : NotificationConnectionState
    data class Reconnecting(val lastEventId: String) : NotificationConnectionState
    data object Connected : NotificationConnectionState
    data class Offline(val message: String) : NotificationConnectionState
    data object LoggedOut : NotificationConnectionState
}

sealed interface NotificationRefreshState {
    data object Idle : NotificationRefreshState
    data class Refreshing(val reason: RefreshReason) : NotificationRefreshState
    data class Failed(val message: String) : NotificationRefreshState
}

enum class RefreshReason {
    AppStart,
    Manual,
    Reconnect,
    NotificationsChanged,
    ResyncRequired,
    SessionChanged,
}

private fun GatewayApiError.isUnauthorized(): Boolean {
    return this is GatewayApiError.Http &&
        (statusCode == 401 || apiError?.code == ApiErrorCodeWire.Unauthorized)
}
