package org.sase.mobile.data.session

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.GatewayApiResult
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.PairFinishRequestWire
import org.sase.mobile.data.api.dto.PairFinishResponseWire
import org.sase.mobile.data.api.dto.PairingDeviceMetadataWire
import org.sase.mobile.data.api.dto.SessionResponseWire

interface SessionController {
    val state: StateFlow<SessionUiState>
    val defaultDeviceDisplayName: String
    suspend fun pairManually(request: ManualPairingRequest): PairingResult
    suspend fun pairWithQr(payload: String, deviceDisplayName: String): PairingResult
    suspend fun refreshSession()
    suspend fun forgetHost()
}

class SessionRepository(
    private val storage: HostSessionStorage,
    private val tokenVault: TokenVault,
    private val clientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewayApiClient,
    private val deviceMetadataProvider: () -> DeviceMetadata,
    private val clock: Clock = Clock.systemUTC(),
    scope: CoroutineScope,
    private val onBeforeForgetHost: suspend () -> Unit = {},
) : SessionController {
    private val mutableState = MutableStateFlow(SessionUiState(SessionStatus.Loading))
    override val state: StateFlow<SessionUiState> = mutableState.asStateFlow()

    override val defaultDeviceDisplayName: String
        get() = deviceMetadataProvider().displayName

    init {
        scope.launch {
            val saved = storage.read()
            mutableState.value = if (saved == null) {
                SessionUiState(SessionStatus.Unpaired)
            } else {
                SessionUiState(SessionStatus.Paired(saved), savedSession = saved)
            }
        }
    }

    override suspend fun pairManually(request: ManualPairingRequest): PairingResult {
        val normalized = normalizeOrFail(request.baseUrl) ?: return failed("Invalid gateway URL")
        if (request.pairingId.isBlank() || request.code.isBlank()) {
            return failed("Pairing ID and code are required")
        }
        val previousSession = mutableState.value.savedSession
        mutableState.value = SessionUiState(SessionStatus.Pairing, savedSession = previousSession)

        val metadata = deviceMetadataProvider().let { providerMetadata ->
            PairingDeviceMetadataWire(
                displayName = request.deviceDisplayName.ifBlank { providerMetadata.displayName },
                platform = "android",
                appVersion = providerMetadata.appVersion,
            )
        }
        val client = clientFactory(normalized) { null }
        return when (
            val result = client.finishPairing(
                PairFinishRequestWire(
                    pairingId = request.pairingId.trim(),
                    code = request.code.trim(),
                    device = metadata,
                ),
            )
        ) {
            is GatewayApiResult.Success -> persistPairing(
                baseUrl = normalized,
                hostLabel = request.hostLabel?.takeIf { it.isNotBlank() } ?: normalized,
                response = result.value,
            )

            is GatewayApiResult.Failure -> failed(result.error.toUserMessage())
        }
    }

    override suspend fun pairWithQr(
        payload: String,
        deviceDisplayName: String,
    ): PairingResult {
        val qr = try {
            QrPairingPayloadParser.parse(payload)
        } catch (error: IllegalArgumentException) {
            return failed(error.message ?: "QR payload is invalid")
        }
        return pairManually(
            ManualPairingRequest(
                baseUrl = qr.baseUrl,
                pairingId = qr.pairingId,
                code = qr.code,
                hostLabel = qr.hostLabel,
                deviceDisplayName = deviceDisplayName,
            ),
        )
    }

    override suspend fun refreshSession() {
        val saved = storage.read()
        if (saved == null) {
            mutableState.value = SessionUiState(SessionStatus.Unpaired)
            return
        }
        mutableState.value = SessionUiState(SessionStatus.Checking, savedSession = saved)
        val token = runCatching { tokenVault.readToken() }.getOrNull()
        val client = clientFactory(saved.baseUrl) { token }
        when (val result = client.session()) {
            is GatewayApiResult.Success -> updateSessionFromGateway(saved, result.value)
            is GatewayApiResult.Failure -> handleSessionFailure(saved, result.error)
        }
    }

    override suspend fun forgetHost() {
        onBeforeForgetHost()
        tokenVault.clearToken()
        storage.clear()
        mutableState.value = SessionUiState(SessionStatus.Unpaired)
    }

    private suspend fun persistPairing(
        baseUrl: String,
        hostLabel: String,
        response: PairFinishResponseWire,
    ): PairingResult {
        if (!response.tokenType.equals("bearer", ignoreCase = true)) {
            return failed("Unsupported token type: ${response.tokenType}")
        }
        val session = PairedHostSession(
            hostLabel = hostLabel,
            baseUrl = baseUrl,
            deviceId = response.device.deviceId,
            deviceDisplayName = response.device.displayName,
            pairedAt = response.device.pairedAt ?: now(),
            lastSessionCheckedAt = now(),
        )
        tokenVault.writeToken(response.token)
        storage.write(session)
        mutableState.value = SessionUiState(SessionStatus.Paired(session), savedSession = session)
        return PairingResult.Success(session)
    }

    private suspend fun updateSessionFromGateway(
        previous: PairedHostSession,
        response: SessionResponseWire,
    ) {
        val updated = previous.copy(
            deviceId = response.device.deviceId,
            deviceDisplayName = response.device.displayName,
            pairedAt = response.device.pairedAt ?: previous.pairedAt,
            lastSessionCheckedAt = now(),
        )
        storage.write(updated)
        mutableState.value = SessionUiState(SessionStatus.Paired(updated), savedSession = updated)
    }

    private fun handleSessionFailure(saved: PairedHostSession, error: GatewayApiError) {
        val message = error.toUserMessage()
        val status = if (error.isUnauthorized()) {
            SessionStatus.AuthExpired(saved, message)
        } else {
            SessionStatus.GatewayUnavailable(saved, message)
        }
        mutableState.value = SessionUiState(status, savedSession = saved)
    }

    private fun normalizeOrFail(baseUrl: String): String? {
        return try {
            GatewayApiClient.normalizeBaseUrl(baseUrl).toString()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun failed(message: String): PairingResult.Failure {
        mutableState.value = SessionUiState(
            status = SessionStatus.PairingFailed(message),
            savedSession = mutableState.value.savedSession,
        )
        return PairingResult.Failure(message)
    }

    private fun now(): String = Instant.now(clock).toString()
}

private fun GatewayApiError.isUnauthorized(): Boolean {
    return this is GatewayApiError.Http &&
        (statusCode == 401 || apiError?.code == ApiErrorCodeWire.Unauthorized)
}

fun GatewayApiError.toUserMessage(): String {
    return when (this) {
        is GatewayApiError.Http -> apiError?.message ?: "Gateway returned HTTP $statusCode"
        is GatewayApiError.InvalidJson -> "Gateway returned invalid JSON"
        is GatewayApiError.Transport -> "Gateway unavailable: ${kind.name.lowercase()}"
    }
}
