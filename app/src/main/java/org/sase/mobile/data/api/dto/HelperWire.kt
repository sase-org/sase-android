package org.sase.mobile.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MobileHelperResultWire(
    val status: MobileHelperStatusWire,
    val message: String? = null,
    val warnings: List<String>,
    val skipped: List<MobileHelperSkippedWire>,
    @SerialName("partial_failure_count") val partialFailureCount: Int? = null,
)

@Serializable
enum class MobileHelperStatusWire {
    @SerialName("success")
    Success,

    @SerialName("partial_success")
    PartialSuccess,

    @SerialName("skipped")
    Skipped,

    @SerialName("failed")
    Failed,
}

@Serializable
data class MobileHelperSkippedWire(
    val target: String? = null,
    val reason: String,
)

@Serializable
data class MobileHelperProjectContextWire(
    val project: String? = null,
    val scope: MobileHelperProjectScopeWire,
)

@Serializable
enum class MobileHelperProjectScopeWire {
    @SerialName("explicit")
    Explicit,

    @SerialName("device_default")
    DeviceDefault,

    @SerialName("all_known")
    AllKnown,

    @SerialName("unspecified")
    Unspecified,
}

@Serializable
data class MobileChangeSpecTagListRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val project: String? = null,
    val limit: Int? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class MobileChangeSpecTagListResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val result: MobileHelperResultWire,
    val context: MobileHelperProjectContextWire,
    val tags: List<MobileChangeSpecTagEntryWire>,
    @SerialName("total_count") val totalCount: Long,
)

@Serializable
data class MobileChangeSpecTagEntryWire(
    val tag: String,
    val project: String? = null,
    val changespec: String,
    val title: String? = null,
    val status: String,
    val workflow: String? = null,
    @SerialName("source_path_display") val sourcePathDisplay: String? = null,
)

@Serializable
data class MobileXpromptCatalogRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val project: String? = null,
    val source: String? = null,
    val tag: String? = null,
    val query: String? = null,
    @SerialName("include_pdf") val includePdf: Boolean = false,
    val limit: Int? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class MobileXpromptCatalogResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val result: MobileHelperResultWire,
    val context: MobileHelperProjectContextWire,
    val entries: List<MobileXpromptCatalogEntryWire>,
    val stats: MobileXpromptCatalogStatsWire,
    @SerialName("catalog_attachment") val catalogAttachment: MobileXpromptCatalogAttachmentWire? = null,
)

@Serializable
data class MobileXpromptCatalogEntryWire(
    val name: String,
    @SerialName("display_label") val displayLabel: String,
    val description: String? = null,
    @SerialName("source_bucket") val sourceBucket: String,
    val project: String? = null,
    val tags: List<String>,
    @SerialName("input_signature") val inputSignature: String? = null,
    @SerialName("is_skill") val isSkill: Boolean,
    @SerialName("content_preview") val contentPreview: String? = null,
    @SerialName("source_path_display") val sourcePathDisplay: String? = null,
)

@Serializable
data class MobileXpromptCatalogStatsWire(
    @SerialName("total_count") val totalCount: Long,
    @SerialName("project_count") val projectCount: Long,
    @SerialName("skill_count") val skillCount: Long,
    @SerialName("pdf_requested") val pdfRequested: Boolean,
)

@Serializable
data class MobileXpromptCatalogAttachmentWire(
    @SerialName("display_name") val displayName: String,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("byte_size") val byteSize: Long? = null,
    @SerialName("path_display") val pathDisplay: String? = null,
    val generated: Boolean,
)

@Serializable
data class MobileBeadListRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val project: String? = null,
    @SerialName("all_projects") val allProjects: Boolean = false,
    val status: String? = null,
    @SerialName("bead_type") val beadType: String? = null,
    val tier: String? = null,
    @SerialName("include_closed") val includeClosed: Boolean = false,
    val limit: Int? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class MobileBeadListResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val result: MobileHelperResultWire,
    val context: MobileHelperProjectContextWire,
    val beads: List<MobileBeadSummaryWire>,
    @SerialName("total_count") val totalCount: Long,
)

@Serializable
data class MobileBeadShowRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    @SerialName("bead_id") val beadId: String,
    val project: String? = null,
    @SerialName("all_projects") val allProjects: Boolean = false,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class MobileBeadShowResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val result: MobileHelperResultWire,
    val context: MobileHelperProjectContextWire,
    val bead: MobileBeadDetailWire,
)

@Serializable
data class MobileBeadSummaryWire(
    val id: String,
    val title: String,
    val status: String,
    @SerialName("bead_type") val beadType: String,
    val tier: String? = null,
    val project: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val assignee: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("dependency_count") val dependencyCount: Long,
    @SerialName("block_count") val blockCount: Long,
    @SerialName("child_count") val childCount: Long,
    @SerialName("plan_path_display") val planPathDisplay: String? = null,
    @SerialName("changespec_name") val changespecName: String? = null,
    @SerialName("changespec_status") val changespecStatus: String? = null,
)

@Serializable
data class MobileBeadDetailWire(
    val summary: MobileBeadSummaryWire,
    val description: String? = null,
    val notes: String? = null,
    @SerialName("design_path_display") val designPathDisplay: String? = null,
    val dependencies: List<String>,
    val blocks: List<String>,
    val children: List<String>,
    @SerialName("workspace_display") val workspaceDisplay: String? = null,
)

@Serializable
data class MobileUpdateStartRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class MobileUpdateStartResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val result: MobileHelperResultWire,
    val job: MobileUpdateJobWire,
)

@Serializable
data class MobileUpdateStatusRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    @SerialName("job_id") val jobId: String,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class MobileUpdateStatusResponseWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val result: MobileHelperResultWire,
    val job: MobileUpdateJobWire,
)

@Serializable
data class MobileUpdateJobWire(
    @SerialName("job_id") val jobId: String,
    val status: MobileUpdateJobStatusWire,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    val message: String? = null,
    @SerialName("log_path_display") val logPathDisplay: String? = null,
    @SerialName("completion_path_display") val completionPathDisplay: String? = null,
)

@Serializable
enum class MobileUpdateJobStatusWire {
    @SerialName("queued")
    Queued,

    @SerialName("running")
    Running,

    @SerialName("succeeded")
    Succeeded,

    @SerialName("failed")
    Failed,
}
