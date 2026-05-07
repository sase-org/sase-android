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
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.sase.mobile.data.api.dto.ActionResultWire
import org.sase.mobile.data.api.dto.ApiErrorWire
import org.sase.mobile.data.api.dto.GatewayJson
import org.sase.mobile.data.api.dto.HitlActionChoiceWire
import org.sase.mobile.data.api.dto.HitlActionRequestWire
import org.sase.mobile.data.api.dto.HealthResponseWire
import org.sase.mobile.data.api.dto.MobileAgentImageLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileAgentKillRequestWire
import org.sase.mobile.data.api.dto.MobileAgentKillResultWire
import org.sase.mobile.data.api.dto.MobileAgentLaunchResultWire
import org.sase.mobile.data.api.dto.MobileAgentListRequestWire
import org.sase.mobile.data.api.dto.MobileAgentListResponseWire
import org.sase.mobile.data.api.dto.MobileAgentResumeOptionsResponseWire
import org.sase.mobile.data.api.dto.MobileAgentRetryRequestWire
import org.sase.mobile.data.api.dto.MobileAgentRetryResultWire
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileBeadListRequestWire
import org.sase.mobile.data.api.dto.MobileBeadListResponseWire
import org.sase.mobile.data.api.dto.MobileBeadShowRequestWire
import org.sase.mobile.data.api.dto.MobileBeadShowResponseWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListRequestWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListResponseWire
import org.sase.mobile.data.api.dto.MobileNotificationDetailResponseWire
import org.sase.mobile.data.api.dto.MobileNotificationListResponseWire
import org.sase.mobile.data.api.dto.MobileUpdateStartRequestWire
import org.sase.mobile.data.api.dto.MobileUpdateStartResponseWire
import org.sase.mobile.data.api.dto.MobileUpdateStatusResponseWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogRequestWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogResponseWire
import org.sase.mobile.data.api.dto.NotificationStateMutationResponseWire
import org.sase.mobile.data.api.dto.PairFinishRequestWire
import org.sase.mobile.data.api.dto.PairFinishResponseWire
import org.sase.mobile.data.api.dto.PairStartRequestWire
import org.sase.mobile.data.api.dto.PairStartResponseWire
import org.sase.mobile.data.api.dto.PlanActionChoiceWire
import org.sase.mobile.data.api.dto.PlanActionRequestWire
import org.sase.mobile.data.api.dto.PushSubscriptionDeleteResponseWire
import org.sase.mobile.data.api.dto.PushSubscriptionListResponseWire
import org.sase.mobile.data.api.dto.PushSubscriptionRegisterResponseWire
import org.sase.mobile.data.api.dto.PushSubscriptionRequestWire
import org.sase.mobile.data.api.dto.QuestionActionChoiceWire
import org.sase.mobile.data.api.dto.QuestionActionRequestWire
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

    suspend fun pushSubscriptions(): GatewayApiResult<PushSubscriptionListResponseWire> {
        return get(
            url = endpoint("session", "push-subscriptions"),
            serializer = PushSubscriptionListResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun registerPushSubscription(
        request: PushSubscriptionRequestWire,
    ): GatewayApiResult<PushSubscriptionRegisterResponseWire> {
        return postJson(
            url = endpoint("session", "push-subscriptions"),
            serializer = PushSubscriptionRegisterResponseWire.serializer(),
            body = json.encodeToString(PushSubscriptionRequestWire.serializer(), request),
            authenticated = true,
        )
    }

    suspend fun deletePushSubscription(
        id: String,
    ): GatewayApiResult<PushSubscriptionDeleteResponseWire> {
        return delete(
            url = endpoint("session", "push-subscriptions", id),
            serializer = PushSubscriptionDeleteResponseWire.serializer(),
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

    suspend fun submitPlanAction(
        request: PlanActionRequestWire,
    ): GatewayApiResult<ActionResultWire> {
        return postJson(
            url = endpoint("actions", "plan", request.prefix, request.choice.pathSegment()),
            serializer = ActionResultWire.serializer(),
            body = json.encodeToString(
                PlanActionBodyWire.serializer(),
                PlanActionBodyWire.from(request),
            ),
            authenticated = true,
        )
    }

    suspend fun submitHitlAction(
        request: HitlActionRequestWire,
    ): GatewayApiResult<ActionResultWire> {
        return postJson(
            url = endpoint("actions", "hitl", request.prefix, request.choice.pathSegment()),
            serializer = ActionResultWire.serializer(),
            body = json.encodeToString(
                HitlActionBodyWire.serializer(),
                HitlActionBodyWire.from(request),
            ),
            authenticated = true,
        )
    }

    suspend fun submitQuestionAction(
        request: QuestionActionRequestWire,
    ): GatewayApiResult<ActionResultWire> {
        return postJson(
            url = endpoint("actions", "question", request.prefix, request.choice.pathSegment()),
            serializer = ActionResultWire.serializer(),
            body = json.encodeToString(
                QuestionActionBodyWire.serializer(),
                QuestionActionBodyWire.from(request),
            ),
            authenticated = true,
        )
    }

    suspend fun agents(
        request: MobileAgentListRequestWire = MobileAgentListRequestWire(),
    ): GatewayApiResult<MobileAgentListResponseWire> {
        val url = endpoint("agents").newBuilder()
            .applyAgentListQuery(request)
            .build()
        return get(
            url = url,
            serializer = MobileAgentListResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun agentResumeOptions(): GatewayApiResult<MobileAgentResumeOptionsResponseWire> {
        return get(
            url = endpoint("agents", "resume-options"),
            serializer = MobileAgentResumeOptionsResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun launchAgent(
        request: MobileAgentTextLaunchRequestWire,
    ): GatewayApiResult<MobileAgentLaunchResultWire> {
        return postJson(
            url = endpoint("agents", "launch"),
            serializer = MobileAgentLaunchResultWire.serializer(),
            body = json.encodeToString(MobileAgentTextLaunchRequestWire.serializer(), request),
            authenticated = true,
        )
    }

    suspend fun launchImageAgent(
        request: MobileAgentImageLaunchRequestWire,
    ): GatewayApiResult<MobileAgentLaunchResultWire> {
        return postJson(
            url = endpoint("agents", "launch-image"),
            serializer = MobileAgentLaunchResultWire.serializer(),
            body = json.encodeToString(MobileAgentImageLaunchRequestWire.serializer(), request),
            authenticated = true,
        )
    }

    suspend fun killAgent(
        name: String,
        request: MobileAgentKillRequestWire = MobileAgentKillRequestWire(),
    ): GatewayApiResult<MobileAgentKillResultWire> {
        return postJson(
            url = endpoint("agents", name, "kill"),
            serializer = MobileAgentKillResultWire.serializer(),
            body = json.encodeToString(MobileAgentKillRequestWire.serializer(), request),
            authenticated = true,
        )
    }

    suspend fun retryAgent(
        name: String,
        request: MobileAgentRetryRequestWire = MobileAgentRetryRequestWire(),
    ): GatewayApiResult<MobileAgentRetryResultWire> {
        return postJson(
            url = endpoint("agents", name, "retry"),
            serializer = MobileAgentRetryResultWire.serializer(),
            body = json.encodeToString(MobileAgentRetryRequestWire.serializer(), request),
            authenticated = true,
        )
    }

    suspend fun changespecTags(
        request: MobileChangeSpecTagListRequestWire = MobileChangeSpecTagListRequestWire(),
    ): GatewayApiResult<MobileChangeSpecTagListResponseWire> {
        val url = endpoint("changespec-tags").newBuilder()
            .applyChangespecTagsQuery(request)
            .build()
        return get(
            url = url,
            serializer = MobileChangeSpecTagListResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun xpromptCatalog(
        request: MobileXpromptCatalogRequestWire = MobileXpromptCatalogRequestWire(),
    ): GatewayApiResult<MobileXpromptCatalogResponseWire> {
        val url = endpoint("xprompts", "catalog").newBuilder()
            .applyXpromptCatalogQuery(request)
            .build()
        return get(
            url = url,
            serializer = MobileXpromptCatalogResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun beads(
        request: MobileBeadListRequestWire = MobileBeadListRequestWire(),
    ): GatewayApiResult<MobileBeadListResponseWire> {
        val url = endpoint("beads").newBuilder()
            .applyBeadListQuery(request)
            .build()
        return get(
            url = url,
            serializer = MobileBeadListResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun beadDetail(
        request: MobileBeadShowRequestWire,
    ): GatewayApiResult<MobileBeadShowResponseWire> {
        val url = endpoint("beads", request.beadId).newBuilder()
            .applyBeadShowQuery(request)
            .build()
        return get(
            url = url,
            serializer = MobileBeadShowResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun startUpdate(
        request: MobileUpdateStartRequestWire = MobileUpdateStartRequestWire(),
    ): GatewayApiResult<MobileUpdateStartResponseWire> {
        return postJson(
            url = endpoint("update", "start"),
            serializer = MobileUpdateStartResponseWire.serializer(),
            body = json.encodeToString(MobileUpdateStartRequestWire.serializer(), request),
            authenticated = true,
        )
    }

    suspend fun updateStatus(
        jobId: String,
    ): GatewayApiResult<MobileUpdateStatusResponseWire> {
        return get(
            url = endpoint("update", jobId),
            serializer = MobileUpdateStatusResponseWire.serializer(),
            authenticated = true,
        )
    }

    suspend fun downloadAttachment(
        token: String,
    ): GatewayApiResult<AttachmentDownloadWire> {
        return executeBytes(
            request = requestBuilder(endpoint("attachments", token), authenticated = true).get().build(),
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

    private suspend fun <T> delete(
        url: HttpUrl,
        serializer: KSerializer<T>,
        authenticated: Boolean,
    ): GatewayApiResult<T> {
        return execute(
            request = requestBuilder(url, authenticated).delete().build(),
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

    private suspend fun executeBytes(
        request: Request,
    ): GatewayApiResult<AttachmentDownloadWire> {
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful) {
                        return@withContext decodeHttpError(response.code, body?.string().orEmpty())
                    }
                    GatewayApiResult.Success(
                        AttachmentDownloadWire(
                            bytes = body?.bytes() ?: ByteArray(0),
                            contentType = response.header("Content-Type"),
                            contentDisposition = response.header("Content-Disposition"),
                            byteSize = response.header("Content-Length")?.toLongOrNull(),
                        ),
                    )
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

    private fun HttpUrl.Builder.applyAgentListQuery(
        query: MobileAgentListRequestWire,
    ): HttpUrl.Builder {
        addQueryParameter("include_recent", query.includeRecent.toString())
        query.status?.let { addQueryParameter("status", it) }
        query.project?.let { addQueryParameter("project", it) }
        query.limit?.let { addQueryParameter("limit", it.toString()) }
        return this
    }

    private fun HttpUrl.Builder.applyChangespecTagsQuery(
        query: MobileChangeSpecTagListRequestWire,
    ): HttpUrl.Builder {
        query.project?.let { addQueryParameter("project", it) }
        query.limit?.let { addQueryParameter("limit", it.toString()) }
        return this
    }

    private fun HttpUrl.Builder.applyXpromptCatalogQuery(
        query: MobileXpromptCatalogRequestWire,
    ): HttpUrl.Builder {
        query.project?.let { addQueryParameter("project", it) }
        query.source?.let { addQueryParameter("source", it) }
        query.tag?.let { addQueryParameter("tag", it) }
        query.query?.let { addQueryParameter("query", it) }
        addQueryParameter("include_pdf", query.includePdf.toString())
        query.limit?.let { addQueryParameter("limit", it.toString()) }
        return this
    }

    private fun HttpUrl.Builder.applyBeadListQuery(
        query: MobileBeadListRequestWire,
    ): HttpUrl.Builder {
        query.project?.let { addQueryParameter("project", it) }
        addQueryParameter("all_projects", query.allProjects.toString())
        query.status?.let { addQueryParameter("status", it) }
        query.beadType?.let { addQueryParameter("bead_type", it) }
        query.tier?.let { addQueryParameter("tier", it) }
        addQueryParameter("include_closed", query.includeClosed.toString())
        query.limit?.let { addQueryParameter("limit", it.toString()) }
        return this
    }

    private fun HttpUrl.Builder.applyBeadShowQuery(
        query: MobileBeadShowRequestWire,
    ): HttpUrl.Builder {
        query.project?.let { addQueryParameter("project", it) }
        addQueryParameter("all_projects", query.allProjects.toString())
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

data class AttachmentDownloadWire(
    val bytes: ByteArray,
    val contentType: String?,
    val contentDisposition: String?,
    val byteSize: Long?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttachmentDownloadWire

        if (!bytes.contentEquals(other.bytes)) return false
        if (contentType != other.contentType) return false
        if (contentDisposition != other.contentDisposition) return false
        if (byteSize != other.byteSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (contentDisposition?.hashCode() ?: 0)
        result = 31 * result + (byteSize?.hashCode() ?: 0)
        return result
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

@Serializable
private data class PlanActionBodyWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val feedback: String? = null,
    @SerialName("commit_plan") val commitPlan: Boolean? = null,
    @SerialName("run_coder") val runCoder: Boolean? = null,
    @SerialName("coder_prompt") val coderPrompt: String? = null,
    @SerialName("coder_model") val coderModel: String? = null,
) {
    companion object {
        fun from(request: PlanActionRequestWire): PlanActionBodyWire {
            return PlanActionBodyWire(
                schemaVersion = request.schemaVersion,
                feedback = request.feedback,
                commitPlan = request.commitPlan,
                runCoder = request.runCoder,
                coderPrompt = request.coderPrompt,
                coderModel = request.coderModel,
            )
        }
    }
}

@Serializable
private data class HitlActionBodyWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val feedback: String? = null,
) {
    companion object {
        fun from(request: HitlActionRequestWire): HitlActionBodyWire {
            return HitlActionBodyWire(
                schemaVersion = request.schemaVersion,
                feedback = request.feedback,
            )
        }
    }
}

@Serializable
private data class QuestionActionBodyWire(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("question_index") val questionIndex: Int? = null,
    @SerialName("selected_option_id") val selectedOptionId: String? = null,
    @SerialName("selected_option_label") val selectedOptionLabel: String? = null,
    @SerialName("selected_option_index") val selectedOptionIndex: Int? = null,
    @SerialName("custom_answer") val customAnswer: String? = null,
    @SerialName("global_note") val globalNote: String? = null,
) {
    companion object {
        fun from(request: QuestionActionRequestWire): QuestionActionBodyWire {
            return QuestionActionBodyWire(
                schemaVersion = request.schemaVersion,
                questionIndex = request.questionIndex,
                selectedOptionId = request.selectedOptionId,
                selectedOptionLabel = request.selectedOptionLabel,
                selectedOptionIndex = request.selectedOptionIndex,
                customAnswer = request.customAnswer,
                globalNote = request.globalNote,
            )
        }
    }
}

private fun PlanActionChoiceWire.pathSegment(): String {
    return when (this) {
        PlanActionChoiceWire.Approve -> "approve"
        PlanActionChoiceWire.Run -> "run"
        PlanActionChoiceWire.Reject -> "reject"
        PlanActionChoiceWire.Epic -> "epic"
        PlanActionChoiceWire.Legend -> "legend"
        PlanActionChoiceWire.Feedback -> "feedback"
    }
}

private fun HitlActionChoiceWire.pathSegment(): String {
    return when (this) {
        HitlActionChoiceWire.Accept -> "accept"
        HitlActionChoiceWire.Reject -> "reject"
        HitlActionChoiceWire.Feedback -> "feedback"
    }
}

private fun QuestionActionChoiceWire.pathSegment(): String {
    return when (this) {
        QuestionActionChoiceWire.Answer -> "answer"
        QuestionActionChoiceWire.Custom -> "custom"
    }
}
