package org.sase.mobile.data.session

data class PairedHostSession(
    val hostLabel: String,
    val baseUrl: String,
    val deviceId: String,
    val deviceDisplayName: String,
    val pairedAt: String,
    val lastSessionCheckedAt: String?,
)

data class DeviceMetadata(
    val displayName: String,
    val appVersion: String?,
)

data class ManualPairingRequest(
    val baseUrl: String,
    val pairingId: String,
    val code: String,
    val hostLabel: String?,
    val deviceDisplayName: String,
)

data class QrPairingPayload(
    val baseUrl: String,
    val pairingId: String,
    val code: String,
    val hostLabel: String? = null,
)

sealed interface SessionStatus {
    data object Loading : SessionStatus
    data object Unpaired : SessionStatus
    data object Pairing : SessionStatus
    data object Checking : SessionStatus
    data class Paired(val session: PairedHostSession) : SessionStatus
    data class AuthExpired(val session: PairedHostSession, val message: String) : SessionStatus
    data class GatewayUnavailable(val session: PairedHostSession?, val message: String) : SessionStatus
    data class PairingFailed(val message: String) : SessionStatus
    data class InvalidInput(val message: String) : SessionStatus
}

data class SessionUiState(
    val status: SessionStatus,
    val savedSession: PairedHostSession? = null,
)

sealed interface PairingResult {
    data class Success(val session: PairedHostSession) : PairingResult
    data class Failure(val message: String) : PairingResult
}

