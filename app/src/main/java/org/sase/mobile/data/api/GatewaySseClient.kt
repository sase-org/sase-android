package org.sase.mobile.data.api

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.sase.mobile.data.api.dto.ApiErrorWire
import org.sase.mobile.data.api.dto.EventRecordWire
import org.sase.mobile.data.api.dto.GatewayJson

class GatewaySseClient(
    baseUrl: String,
    private val bearerTokenProvider: () -> String?,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val apiBaseUrl: HttpUrl = GatewayApiClient.normalizeBaseUrl(baseUrl)
    private val json = GatewayJson.format

    suspend fun readEvents(
        lastEventId: String? = null,
    ): GatewaySseResult {
        val events = mutableListOf<EventRecordWire>()
        return when (val result = collectEvents(lastEventId) { event -> events += event }) {
            is GatewaySseResult.Failure -> result
            is GatewaySseResult.Success -> GatewaySseResult.Success(events)
        }
    }

    suspend fun collectEvents(
        lastEventId: String? = null,
        onEvent: suspend (EventRecordWire) -> Unit,
    ): GatewaySseResult {
        val request = Request.Builder()
            .url(apiBaseUrl.newBuilder().addPathSegment("events").build())
            .header("Accept", "text/event-stream")
            .apply {
                bearerTokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                lastEventId?.takeIf { it.isNotBlank() }?.let { id ->
                    header("Last-Event-ID", id)
                }
            }
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            val call = client.newCall(request)
            val cancellation = currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    call.cancel()
                }
            }
            try {
                call.execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful) {
                        return@withContext decodeHttpError(response.code, body?.string().orEmpty())
                    }
                    val source = body?.source() ?: return@withContext GatewaySseResult.Success(emptyList())
                    val dataLines = mutableListOf<String>()

                    suspend fun flushEvent(): GatewaySseResult.Failure? {
                        if (dataLines.isEmpty()) {
                            return null
                        }
                        val data = dataLines.joinToString(separator = "\n")
                        dataLines.clear()
                        return try {
                            onEvent(json.decodeFromString(EventRecordWire.serializer(), data))
                            null
                        } catch (error: SerializationException) {
                            GatewaySseResult.Failure(
                                GatewayApiError.InvalidJson(message = error.message.orEmpty()),
                            )
                        } catch (error: IllegalArgumentException) {
                            GatewaySseResult.Failure(
                                GatewayApiError.InvalidJson(message = error.message.orEmpty()),
                            )
                        }
                    }

                    while (true) {
                        val rawLine = source.readUtf8Line() ?: break
                        val line = rawLine.removeSuffix("\r")
                        when {
                            line.isEmpty() -> flushEvent()?.let { return@withContext it }
                            line.startsWith(":") -> Unit
                            line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                        }
                    }
                    flushEvent()?.let { return@withContext it }
                    GatewaySseResult.Success(emptyList())
                }
            } catch (error: IOException) {
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("SSE request cancelled").also { it.initCause(error) }
                }
                GatewaySseResult.Failure(error.toGatewayTransportError())
            } finally {
                cancellation.dispose()
            }
        }
    }

    private fun decodeHttpError(
        statusCode: Int,
        body: String,
    ): GatewaySseResult.Failure {
        val apiError = try {
            json.decodeFromString(ApiErrorWire.serializer(), body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return GatewaySseResult.Failure(
            GatewayApiError.Http(
                statusCode = statusCode,
                apiError = apiError,
                rawBody = body,
            ),
        )
    }
}

sealed interface GatewaySseResult {
    data class Success(val events: List<EventRecordWire>) : GatewaySseResult
    data class Failure(val error: GatewayApiError) : GatewaySseResult
}

class SseReconnectPolicy(
    private val baseDelayMillis: Long = 500,
    private val maxDelayMillis: Long = 30_000,
    private val jitterRatio: Double = 0.25,
    private val jitterSource: () -> Double = { Math.random() },
) {
    fun delayMillis(attempt: Int): Long {
        val exponent = attempt.coerceAtLeast(0).coerceAtMost(10)
        val capped = (baseDelayMillis * (1L shl exponent)).coerceAtMost(maxDelayMillis)
        val jitterWindow = (capped * jitterRatio).toLong()
        if (jitterWindow <= 0) {
            return capped
        }
        val offset = ((jitterSource().coerceIn(0.0, 1.0) * 2.0 - 1.0) * jitterWindow).toLong()
        return (capped + offset).coerceIn(0, maxDelayMillis)
    }
}
