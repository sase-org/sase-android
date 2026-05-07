package org.sase.mobile.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val GatewayWireSchemaVersion = 1

@Serializable
data class GatewayBuildWire(
    @SerialName("package_version") val packageVersion: String,
    @SerialName("git_sha") val gitSha: String? = null,
)

@Serializable
data class GatewayBindWire(
    val address: String,
    @SerialName("is_loopback") val isLoopback: Boolean,
)

@Serializable
data class HealthResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val status: String,
    val service: String,
    val version: String,
    val build: GatewayBuildWire,
    val bind: GatewayBindWire,
)

@Serializable
data class DeviceRecordWire(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("device_id") val deviceId: String,
    @SerialName("display_name") val displayName: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("paired_at") val pairedAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
)

@Serializable
data class SessionResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val device: DeviceRecordWire,
    val capabilities: List<String>,
)

@Serializable
data class PairStartRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    @SerialName("host_label") val hostLabel: String? = null,
)

@Serializable
data class PairStartResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("pairing_id") val pairingId: String,
    val code: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("host_label") val hostLabel: String,
    @SerialName("host_fingerprint") val hostFingerprint: String? = null,
)

@Serializable
data class PairingDeviceMetadataWire(
    @SerialName("display_name") val displayName: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class PairFinishRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    @SerialName("pairing_id") val pairingId: String,
    val code: String,
    val device: PairingDeviceMetadataWire,
)

@Serializable
data class PairFinishResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val device: DeviceRecordWire,
    @SerialName("token_type") val tokenType: String,
    val token: String,
)

@Serializable
enum class ApiErrorCodeWire {
    @SerialName("unauthorized")
    Unauthorized,

    @SerialName("not_found")
    NotFound,

    @SerialName("invalid_request")
    InvalidRequest,

    @SerialName("pairing_expired")
    PairingExpired,

    @SerialName("pairing_rejected")
    PairingRejected,

    @SerialName("conflict_already_handled")
    ConflictAlreadyHandled,

    @SerialName("gone_stale")
    GoneStale,

    @SerialName("ambiguous_prefix")
    AmbiguousPrefix,

    @SerialName("unsupported_action")
    UnsupportedAction,

    @SerialName("attachment_expired")
    AttachmentExpired,

    @SerialName("agent_not_found")
    AgentNotFound,

    @SerialName("agent_not_running")
    AgentNotRunning,

    @SerialName("launch_failed")
    LaunchFailed,

    @SerialName("invalid_upload")
    InvalidUpload,

    @SerialName("bridge_unavailable")
    BridgeUnavailable,

    @SerialName("helper_not_found")
    HelperNotFound,

    @SerialName("update_already_running")
    UpdateAlreadyRunning,

    @SerialName("update_job_not_found")
    UpdateJobNotFound,

    @SerialName("permission_denied")
    PermissionDenied,

    @SerialName("internal")
    Internal,
}

@Serializable
data class ApiErrorWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val code: ApiErrorCodeWire,
    val message: String,
    val target: String? = null,
    val details: JsonElement? = null,
)

@Serializable
data class EventRecordWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val payload: EventPayloadWire,
)

@Serializable
data class EventPayloadWire(
    val type: EventPayloadTypeWire,
    val data: JsonElement,
)

@Serializable
enum class EventPayloadTypeWire {
    @SerialName("heartbeat")
    Heartbeat,

    @SerialName("session")
    Session,

    @SerialName("resync_required")
    ResyncRequired,

    @SerialName("notifications_changed")
    NotificationsChanged,

    @SerialName("agents_changed")
    AgentsChanged,

    @SerialName("helpers_changed")
    HelpersChanged,
}

@Serializable
data class HeartbeatEventPayloadWire(
    val sequence: Long,
)

@Serializable
data class SessionEventPayloadWire(
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class ResyncRequiredEventPayloadWire(
    val reason: String,
)

@Serializable
data class NotificationsChangedEventPayloadWire(
    val reason: String,
    @SerialName("notification_id") val notificationId: String? = null,
)

@Serializable
data class AgentsChangedEventPayloadWire(
    val reason: String,
    @SerialName("agent_name") val agentName: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class HelpersChangedEventPayloadWire(
    val reason: String,
    val helper: String? = null,
    @SerialName("job_id") val jobId: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class NotificationStateMutationResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("notification_id") val notificationId: String,
    val read: Boolean,
    val dismissed: Boolean,
    val changed: Boolean,
)

@Serializable
enum class PushProviderWire {
    @SerialName("fcm")
    Fcm,

    @SerialName("unified_push")
    UnifiedPush,

    @SerialName("ntfy")
    Ntfy,

    @SerialName("test")
    Test,
}

@Serializable
enum class PushHintCategoryWire {
    @SerialName("notifications")
    Notifications,

    @SerialName("agents")
    Agents,

    @SerialName("helpers")
    Helpers,

    @SerialName("update")
    Update,

    @SerialName("session")
    Session,
}

@Serializable
data class PushHintWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val id: String,
    val category: PushHintCategoryWire,
    val reason: String,
    val title: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("notification_id") val notificationId: String? = null,
    @SerialName("agent_name") val agentName: String? = null,
    val helper: String? = null,
    @SerialName("job_id") val jobId: String? = null,
)

@Serializable
data class PushSubscriptionRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val provider: PushProviderWire,
    @SerialName("provider_token") val providerToken: String,
    @SerialName("app_instance_id") val appInstanceId: String? = null,
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("device_display_name") val deviceDisplayName: String? = null,
    @SerialName("hint_categories") val hintCategories: List<PushHintCategoryWire> = PushHintCategoryWire.entries,
)

@Serializable
data class PushSubscriptionRecordWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val id: String,
    val provider: PushProviderWire,
    @SerialName("provider_token") val providerToken: String,
    @SerialName("app_instance_id") val appInstanceId: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_display_name") val deviceDisplayName: String? = null,
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("hint_categories") val hintCategories: List<PushHintCategoryWire> = emptyList(),
    @SerialName("enabled_at") val enabledAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("disabled_at") val disabledAt: String? = null,
)

@Serializable
data class PushSubscriptionListResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val subscriptions: List<PushSubscriptionRecordWire>,
)

@Serializable
data class PushSubscriptionRegisterResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val created: Boolean,
    val subscription: PushSubscriptionRecordWire,
)

@Serializable
data class PushSubscriptionDeleteResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val revoked: Boolean,
    val subscription: PushSubscriptionRecordWire,
)
