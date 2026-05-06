package org.sase.mobile.data.api

import java.io.InterruptedIOException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.sase.mobile.data.api.dto.ApiErrorWire
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.HealthResponseWire
import org.sase.mobile.data.api.dto.MobileNotificationDetailResponseWire
import org.sase.mobile.data.api.dto.MobileNotificationListResponseWire
import org.sase.mobile.data.api.dto.NotificationStateMutationResponseWire
import org.sase.mobile.data.api.dto.PairFinishRequestWire
import org.sase.mobile.data.api.dto.PairFinishResponseWire
import org.sase.mobile.data.api.dto.PairStartRequestWire
import org.sase.mobile.data.api.dto.PairStartResponseWire
import org.sase.mobile.data.api.dto.SessionResponseWire

class GatewayApiClient(
    baseUrl: String,
    private val bearerTokenProvider: () -> String? = { null },
    client: OkHttpClient = defaultClient(),
) {
    private val apiBaseUrl: HttpUrl = normalizeBaseUrl(baseUrl)
    private val client: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val json = GatewayJson.format

    suspend fun health(): GatewayApiResult<HealthResponseWire> {
        return get(
            url = endpoint("health"),
            serializer = HealthResponseWire.serializer(),
            authenticated = false,
        )
    }

    suspend fun startPairing(
        request: PairStartRequestWire,
    ): GatewayApiResult<PairStartResponseWire> {
        return postJson(
            url = endpoint("session", "pair", "start"),
            serializer = PairStartResponseWire.serializer(),
            body = json.encodeToString(PairStartRequestWire.serializer(), request),
            authenticated = false,
        )
    }

    suspend fun finishPairing(
        request: PairFinishRequestWire,
    ): GatewayApiResult<PairFinishResponseWire> {
        return postJson(
            url = endpoint("session", "pair", "finish"),
            serializer = PairFinishResponseWire.serializer(),
            body = json.encodeToString(PairFinishRequestWire.serializer(), request),
            authenticated = false,
        )
    }

    suspend fun session(): GatewayApiResult<SessionResponseWire> {
        return get(
            url = endpoint("session"),
            serializer = SessionResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun notifications(
        query: NotificationListQuery = NotificationListQuery(),
    ): GatewayApiResult<MobileNotificationListResponseWire> {
        val url = endpoint("notifications").newBuilder()
            .applyNotificationQuery(query)
            .build()
        return get(
            url = url,
            serializer = MobileNotificationListResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun notificationDetail(
        id: String,
    ): GatewayApiResult<MobileNotificationDetailResponseWire> {
        return get(
            url = endpoint("notifications", id),
            serializer = MobileNotificationDetailResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun markNotificationRead(
        id: String,
    ): GatewayApiResult<NotificationStateMutationResponseWire> {
        return postJson(
            url = endpoint("notifications", id, "mark-read"),
            serializer = NotificationStateMutationResponseWire.serializer(),
            body = "",
            authenticated = true,
        )
    }

    suspend fun dismissNotification(
        id: String,
    ): GatewayApiResult<NotificationStateMutationResponseWire> {
        return postJson(
            url = endpoint("notifications", id, "dismiss"),
            serializer = NotificationStateMutationResponseWire.serializer(),
            body = "",
            authenticated = true,
        )
    }

    private fun endpoint(vararg pathSegments: String): HttpUrl {
        val builder = apiBaseUrl.newBuilder()
        pathSegments.forEach(builder::addPathSegment)
        return builder.build()
    }

    private suspend fun <T> get(
        url: HttpUrl,
        serializer: KSerializer<T>,
        authenticated: Boolean,
    ): GatewayApiResult<T> {
        return execute(
            request = requestBuilder(url, authenticated).get().build(),
            serializer = serializer,
        )
    }

    private suspend fun <T> postJson(
        url: HttpUrl,
        serializer: KSerializer<T>,
        body: String,
        authenticated: Boolean,
    ): GatewayApiResult<T> {
        return execute(
            request = requestBuilder(url, authenticated)
                .post(body.toRequestBody(JsonMediaType))
                .build(),
            serializer = serializer,
        )
    }

    private suspend fun <T> execute(
        request: Request,
        serializer: KSerializer<T>,
    ): GatewayApiResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext decodeHttpError(response.code, body)
                    }
                    try {
                        GatewayApiResult.Success(json.decodeFromString(serializer, body))
                    } catch (error: SerializationException) {
                        GatewayApiResult.Failure(
                            GatewayApiError.InvalidJson(
                                message = error.message.orEmpty(),
                                statusCode = response.code,
                            ),
                        )
                    } catch (error: IllegalArgumentException) {
                        GatewayApiResult.Failure(
                            GatewayApiError.InvalidJson(
                                message = error.message.orEmpty(),
                                statusCode = response.code,
                            ),
                        )
                    }
                }
            } catch (error: IOException) {
                GatewayApiResult.Failure(error.toGatewayTransportError())
            }
        }
    }

    private fun requestBuilder(
        url: HttpUrl,
        authenticated: Boolean,
    ): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")

        if (authenticated) {
            bearerTokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }
        }

        return builder
    }

    private fun decodeHttpError(
        statusCode: Int,
        body: String,
    ): GatewayApiResult.Failure {
        val apiError = try {
            json.decodeFromString(ApiErrorWire.serializer(), body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return GatewayApiResult.Failure(
            GatewayApiError.Http(
                statusCode = statusCode,
                apiError = apiError,
                rawBody = body,
            ),
        )
    }

    private fun HttpUrl.Builder.applyNotificationQuery(
        query: NotificationListQuery,
    ): HttpUrl.Builder {
        query.unreadOnly?.let { addQueryParameter("unread_only", it.toString()) }
        query.includeDismissed?.let { addQueryParameter("include_dismissed", it.toString()) }
        query.includeSilent?.let { addQueryParameter("include_silent", it.toString()) }
        query.limit?.let { addQueryParameter("limit", it.toString()) }
        query.newerThan?.let { addQueryParameter("newer_than", it) }
        return this
    }

    companion object {
        private val JsonMediaType = "application/json; charset=utf-8".toMediaType()

        fun normalizeBaseUrl(rawBaseUrl: String): HttpUrl {
            val parsed = rawBaseUrl.trim().toHttpUrl()
            require(parsed.scheme == "http" || parsed.scheme == "https") {
                "Gateway URL must use http or https"
            }
            require(parsed.query == null && parsed.fragment == null) {
                "Gateway URL must not include query or fragment"
            }

            val encodedPath = parsed.encodedPath.trimEnd('/')
            require(
                encodedPath.isEmpty() ||
                    encodedPath == "/api/v1" ||
                    encodedPath == "/",
            ) {
                "Gateway URL path must be empty, /, or /api/v1"
            }

            return parsed.newBuilder()
                .encodedPath("/api/v1/")
                .query(null)
                .fragment(null)
                .build()
        }

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

data class NotificationListQuery(
    val unreadOnly: Boolean? = null,
    val includeDismissed: Boolean? = null,
    val includeSilent: Boolean? = null,
    val limit: Int? = null,
    val newerThan: String? = null,
)

internal fun IOException.toGatewayTransportError(): GatewayApiError.Transport {
    val kind = when (this) {
        is UnknownHostException -> GatewayTransportErrorKind.Dns
        is SocketTimeoutException -> GatewayTransportErrorKind.Timeout
        is InterruptedIOException -> GatewayTransportErrorKind.Timeout
        is ConnectException -> GatewayTransportErrorKind.ConnectionRefused
        is SSLException -> GatewayTransportErrorKind.TlsOrCleartextPolicy
        is UnknownServiceException -> GatewayTransportErrorKind.TlsOrCleartextPolicy
        else -> GatewayTransportErrorKind.Network
    }
    return GatewayApiError.Transport(kind = kind, message = message.orEmpty())
}
