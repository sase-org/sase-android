package org.sase.mobile.data.api

import org.sase.mobile.data.api.dto.ApiErrorWire

sealed interface GatewayApiResult<out T> {
    data class Success<T>(val value: T) : GatewayApiResult<T>
    data class Failure(val error: GatewayApiError) : GatewayApiResult<Nothing>
}

sealed interface GatewayApiError {
    data class Http(
        val statusCode: Int,
        val apiError: ApiErrorWire?,
        val rawBody: String,
    ) : GatewayApiError

    data class InvalidJson(
        val message: String,
        val statusCode: Int? = null,
    ) : GatewayApiError

    data class Transport(
        val kind: GatewayTransportErrorKind,
        val message: String,
    ) : GatewayApiError
}

enum class GatewayTransportErrorKind {
    Dns,
    ConnectionRefused,
    Timeout,
    TlsOrCleartextPolicy,
    Network,
}
