package org.sase.mobile.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MobileNotificationListResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val notifications: List<MobileNotificationCardWire>,
    @SerialName("total_count") val totalCount: Long,
    @SerialName("next_high_water") val nextHighWater: String? = null,
)

@Serializable
data class MobileNotificationDetailResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val notification: MobileNotificationCardWire,
    val notes: List<String>,
    val attachments: List<MobileAttachmentManifestWire>,
    val action: MobileActionDetailWire,
)

@Serializable
data class MobileNotificationCardWire(
    val id: String,
    val timestamp: String,
    val sender: String,
    val priority: Boolean,
    val actionable: Boolean,
    val read: Boolean,
    val dismissed: Boolean,
    val silent: Boolean,
    val muted: Boolean,
    @SerialName("notes_summary") val notesSummary: String,
    @SerialName("file_count") val fileCount: Long,
    @SerialName("action_summary") val actionSummary: MobileActionSummaryWire? = null,
)

@Serializable
data class MobileActionSummaryWire(
    val kind: MobileActionKindWire,
    val state: MobileActionStateWire,
    val label: String,
)

@Serializable
enum class MobileActionKindWire {
    @SerialName("plan_approval")
    PlanApproval,

    @SerialName("hitl")
    Hitl,

    @SerialName("user_question")
    UserQuestion,

    @SerialName("non_action")
    NonAction,

    @SerialName("unsupported")
    Unsupported,
}

@Serializable
enum class MobileActionStateWire {
    @SerialName("available")
    Available,

    @SerialName("already_handled")
    AlreadyHandled,

    @SerialName("stale")
    Stale,

    @SerialName("missing_request")
    MissingRequest,

    @SerialName("missing_target")
    MissingTarget,

    @SerialName("unsupported")
    Unsupported,
}

@Serializable
data class PendingActionIdentityWire(
    @SerialName("notification_id") val notificationId: String,
    val prefix: String,
    @SerialName("prefix_len") val prefixLen: Int,
    val resolution: PendingActionPrefixResolutionWire,
)

@Serializable
enum class PendingActionPrefixResolutionWire {
    @SerialName("exact")
    Exact,

    @SerialName("unique_prefix")
    UniquePrefix,

    @SerialName("ambiguous_prefix")
    AmbiguousPrefix,

    @SerialName("duplicate_full_id")
    DuplicateFullId,

    @SerialName("missing")
    Missing,
}

@Serializable
data class MobileActionDetailWire(
    val kind: MobileActionKindWire,
    val identity: PendingActionIdentityWire? = null,
    val state: MobileActionStateWire,
    @SerialName("response_dir") val responseDir: String? = null,
    @SerialName("plan_file") val planFile: String? = null,
    @SerialName("artifacts_dir") val artifactsDir: String? = null,
    @SerialName("workflow_name") val workflowName: String? = null,
    @SerialName("question_count") val questionCount: Int? = null,
    val action: String? = null,
    val choices: List<String> = emptyList(),
)

@Serializable
data class MobileAttachmentManifestWire(
    val id: String,
    val token: String? = null,
    @SerialName("display_name") val displayName: String,
    val kind: MobileAttachmentKindWire,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("byte_size") val byteSize: Long? = null,
    @SerialName("source_notification_id") val sourceNotificationId: String,
    val downloadable: Boolean,
    @SerialName("download_requires_auth") val downloadRequiresAuth: Boolean,
    @SerialName("can_inline") val canInline: Boolean,
    @SerialName("path_available") val pathAvailable: Boolean,
)

@Serializable
enum class MobileAttachmentKindWire {
    @SerialName("markdown")
    Markdown,

    @SerialName("pdf")
    Pdf,

    @SerialName("diff")
    Diff,

    @SerialName("image")
    Image,

    @SerialName("text")
    Text,

    @SerialName("json")
    Json,

    @SerialName("directory")
    Directory,

    @SerialName("unknown")
    Unknown,
}
