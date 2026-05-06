package org.sase.mobile.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MobileAgentListRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    @SerialName("include_recent") val includeRecent: Boolean = false,
    val status: String? = null,
    val project: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    val limit: Int? = null,
)

@Serializable
data class MobileAgentListResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val agents: List<MobileAgentSummaryWire>,
    @SerialName("total_count") val totalCount: Long,
)

@Serializable
data class MobileAgentSummaryWire(
    val name: String,
    val project: String? = null,
    val status: String,
    val pid: Int? = null,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("workspace_number") val workspaceNumber: Int? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Long? = null,
    @SerialName("prompt_snippet") val promptSnippet: String? = null,
    @SerialName("has_artifact_dir") val hasArtifactDir: Boolean,
    @SerialName("retry_lineage") val retryLineage: MobileAgentRetryLineageWire,
    val actions: MobileAgentActionAffordancesWire,
    val display: MobileAgentDisplayLabelsWire,
)

@Serializable
data class MobileAgentRetryLineageWire(
    @SerialName("retry_of_timestamp") val retryOfTimestamp: String? = null,
    @SerialName("retried_as_timestamp") val retriedAsTimestamp: String? = null,
    @SerialName("retry_chain_root_timestamp") val retryChainRootTimestamp: String? = null,
    @SerialName("retry_attempt") val retryAttempt: Int? = null,
    @SerialName("parent_agent_name") val parentAgentName: String? = null,
)

@Serializable
data class MobileAgentActionAffordancesWire(
    @SerialName("can_resume") val canResume: Boolean,
    @SerialName("can_wait") val canWait: Boolean,
    @SerialName("can_kill") val canKill: Boolean,
    @SerialName("can_retry") val canRetry: Boolean,
)

@Serializable
data class MobileAgentDisplayLabelsWire(
    val title: String,
    val subtitle: String? = null,
    @SerialName("status_label") val statusLabel: String,
)

@Serializable
data class MobileAgentResumeOptionsResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val options: List<MobileAgentResumeOptionWire>,
)

@Serializable
data class MobileAgentResumeOptionWire(
    val id: String,
    @SerialName("agent_name") val agentName: String,
    val kind: MobileAgentResumeOptionKindWire,
    val label: String,
    @SerialName("prompt_text") val promptText: String,
    @SerialName("direct_launch_supported") val directLaunchSupported: Boolean,
)

@Serializable
enum class MobileAgentResumeOptionKindWire {
    @SerialName("resume")
    Resume,

    @SerialName("wait")
    Wait,
}

@Serializable
data class MobileAgentTextLaunchRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val prompt: String,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val name: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val runtime: String? = null,
    val project: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("dry_run") val dryRun: Boolean? = null,
)

@Serializable
data class MobileAgentImageLaunchRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val prompt: String,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("original_filename") val originalFilename: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("byte_length") val byteLength: Long,
    @SerialName("base64_image") val base64Image: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val name: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val runtime: String? = null,
    val project: String? = null,
    @SerialName("dry_run") val dryRun: Boolean? = null,
)

@Serializable
data class MobileAgentLaunchResultWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val primary: MobileAgentLaunchSlotResultWire? = null,
    val slots: List<MobileAgentLaunchSlotResultWire>,
)

@Serializable
data class MobileAgentLaunchSlotResultWire(
    @SerialName("slot_id") val slotId: String,
    val name: String? = null,
    val status: MobileAgentLaunchSlotStatusWire,
    @SerialName("artifact_dir") val artifactDir: String? = null,
    val message: String? = null,
)

@Serializable
enum class MobileAgentLaunchSlotStatusWire {
    @SerialName("launched")
    Launched,

    @SerialName("dry_run")
    DryRun,

    @SerialName("failed")
    Failed,
}

@Serializable
data class MobileAgentKillRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val reason: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class MobileAgentKillResultWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val name: String,
    val status: String,
    val pid: Int? = null,
    val changed: Boolean,
    val message: String? = null,
)

@Serializable
data class MobileAgentRetryRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("prompt_override") val promptOverride: String? = null,
    @SerialName("dry_run") val dryRun: Boolean? = null,
    @SerialName("kill_source_first") val killSourceFirst: Boolean? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class MobileAgentRetryResultWire(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("source_agent") val sourceAgent: String,
    val launch: MobileAgentLaunchResultWire,
)
