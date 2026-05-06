package org.sase.mobile.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ActionResultWire(
    @SerialName("schema_version") val schemaVersion: Int,
    val prefix: String,
    @SerialName("notification_id") val notificationId: String? = null,
    @SerialName("action_kind") val actionKind: MobileActionKindWire,
    val state: MobileActionStateWire,
    @SerialName("response_file") val responseFile: String,
    @SerialName("response_json") val responseJson: JsonElement,
    val message: String? = null,
)

@Serializable
data class PlanActionRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val prefix: String,
    val choice: PlanActionChoiceWire,
    val feedback: String? = null,
    @SerialName("commit_plan") val commitPlan: Boolean? = null,
    @SerialName("run_coder") val runCoder: Boolean? = null,
    @SerialName("coder_prompt") val coderPrompt: String? = null,
    @SerialName("coder_model") val coderModel: String? = null,
)

@Serializable
enum class PlanActionChoiceWire {
    @SerialName("approve")
    Approve,

    @SerialName("run")
    Run,

    @SerialName("reject")
    Reject,

    @SerialName("epic")
    Epic,

    @SerialName("legend")
    Legend,

    @SerialName("feedback")
    Feedback,
}

@Serializable
data class HitlActionRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val prefix: String,
    val choice: HitlActionChoiceWire,
    val feedback: String? = null,
)

@Serializable
enum class HitlActionChoiceWire {
    @SerialName("accept")
    Accept,

    @SerialName("reject")
    Reject,

    @SerialName("feedback")
    Feedback,
}

@Serializable
data class QuestionActionRequestWire(
    @SerialName("schema_version") val schemaVersion: Int = GatewayWireSchemaVersion,
    val prefix: String,
    val choice: QuestionActionChoiceWire,
    @SerialName("question_index") val questionIndex: Int? = null,
    @SerialName("selected_option_id") val selectedOptionId: String? = null,
    @SerialName("selected_option_label") val selectedOptionLabel: String? = null,
    @SerialName("selected_option_index") val selectedOptionIndex: Int? = null,
    @SerialName("custom_answer") val customAnswer: String? = null,
    @SerialName("global_note") val globalNote: String? = null,
)

@Serializable
enum class QuestionActionChoiceWire {
    @SerialName("answer")
    Answer,

    @SerialName("custom")
    Custom,
}
