package org.sase.mobile.ui

import org.sase.mobile.data.api.dto.MobileActionDetailWire
import org.sase.mobile.data.api.dto.MobileActionKindWire
import org.sase.mobile.data.api.dto.MobileActionStateWire
import org.sase.mobile.data.api.dto.MobileActionSummaryWire
import org.sase.mobile.data.api.dto.MobileAttachmentKindWire
import org.sase.mobile.data.api.dto.MobileAttachmentManifestWire
import org.sase.mobile.data.api.dto.MobileNotificationCardWire
import org.sase.mobile.data.api.dto.MobileNotificationDetailResponseWire
import org.sase.mobile.data.api.dto.PendingActionIdentityWire
import org.sase.mobile.data.api.dto.PendingActionPrefixResolutionWire
import org.sase.mobile.data.notifications.NotificationInboxState

object NotificationUiFixtures {
    val cards = listOf(
        notificationCard(
            id = "plan0001-review",
            sender = "planner",
            summary = "Plan review waiting",
            priority = true,
            actionable = true,
            read = false,
            actionSummary = actionSummary(
                MobileActionKindWire.PlanApproval,
                MobileActionStateWire.Available,
                "Plan approval",
            ),
            fileCount = 2,
        ),
        notificationCard(
            id = "hitl0001-choice",
            timestamp = "2026-05-06T15:09:00Z",
            sender = "workflow",
            summary = "HITL prompt needs input",
            actionable = true,
            actionSummary = actionSummary(MobileActionKindWire.Hitl, MobileActionStateWire.Available, "HITL"),
        ),
        notificationCard(
            id = "quest001-answer",
            timestamp = "2026-05-06T15:08:00Z",
            sender = "user-agent",
            summary = "Question waiting",
            priority = true,
            actionable = true,
            actionSummary = actionSummary(
                MobileActionKindWire.UserQuestion,
                MobileActionStateWire.Available,
                "Question",
            ),
        ),
        notificationCard(
            id = "workflow001-status",
            timestamp = "2026-05-06T15:07:00Z",
            sender = "workflow",
            summary = "Workflow finished",
            read = true,
        ),
        notificationCard(
            id = "error001-digest",
            timestamp = "2026-05-06T15:06:00Z",
            sender = "axe",
            summary = "Error digest available",
            priority = true,
            actionSummary = actionSummary(
                MobileActionKindWire.Unsupported,
                MobileActionStateWire.Unsupported,
                "Unsupported",
            ),
            fileCount = 1,
        ),
        notificationCard(
            id = "image001-ready",
            timestamp = "2026-05-06T15:05:00Z",
            sender = "image",
            summary = "Generated image ready",
            fileCount = 1,
        ),
        notificationCard(
            id = "generic001",
            timestamp = "2026-05-06T15:04:00Z",
            sender = "system",
            summary = "Generic notification",
        ),
    )

    val inboxState = NotificationInboxState(
        cards = cards,
        isStale = false,
        lastEventId = "0000000000000007",
        lastFullRefreshAt = "2026-05-06T15:11:00Z",
    )

    val staleInboxState = inboxState.copy(isStale = true)

    val detail = MobileNotificationDetailResponseWire(
        schemaVersion = 1,
        notification = cards.first(),
        notes = listOf(
            "Review the plan before running the coder.",
            "Focus on tests and Android client boundaries.",
        ),
        attachments = listOf(
            MobileAttachmentManifestWire(
                id = "att_000",
                token = "att_plan_md",
                displayName = "~/plans/plan.md",
                kind = MobileAttachmentKindWire.Markdown,
                contentType = "text/markdown",
                byteSize = 1280,
                sourceNotificationId = "plan0001-review",
                downloadable = true,
                downloadRequiresAuth = true,
                canInline = true,
                pathAvailable = true,
            ),
            MobileAttachmentManifestWire(
                id = "att_001",
                token = null,
                displayName = "~/plans/report.pdf",
                kind = MobileAttachmentKindWire.Pdf,
                contentType = "application/pdf",
                byteSize = 262144,
                sourceNotificationId = "plan0001-review",
                downloadable = false,
                downloadRequiresAuth = true,
                canInline = false,
                pathAvailable = false,
            ),
        ),
        action = MobileActionDetailWire(
            kind = MobileActionKindWire.PlanApproval,
            identity = PendingActionIdentityWire(
                notificationId = "plan0001-review",
                prefix = "plan0001",
                prefixLen = 8,
                resolution = PendingActionPrefixResolutionWire.UniquePrefix,
            ),
            state = MobileActionStateWire.Available,
            responseDir = "~/.sase/responses/plan0001",
            planFile = "~/plans/plan.md",
            choices = listOf("approve", "run", "reject", "epic", "legend", "feedback"),
        ),
    )

    private fun notificationCard(
        id: String,
        timestamp: String = "2026-05-06T15:10:00Z",
        sender: String,
        summary: String,
        priority: Boolean = false,
        actionable: Boolean = false,
        read: Boolean = false,
        dismissed: Boolean = false,
        silent: Boolean = false,
        muted: Boolean = false,
        fileCount: Long = 0,
        actionSummary: MobileActionSummaryWire? = null,
    ): MobileNotificationCardWire {
        return MobileNotificationCardWire(
            id = id,
            timestamp = timestamp,
            sender = sender,
            priority = priority,
            actionable = actionable,
            read = read,
            dismissed = dismissed,
            silent = silent,
            muted = muted,
            notesSummary = summary,
            fileCount = fileCount,
            actionSummary = actionSummary,
        )
    }

    private fun actionSummary(
        kind: MobileActionKindWire,
        state: MobileActionStateWire,
        label: String,
    ): MobileActionSummaryWire {
        return MobileActionSummaryWire(
            kind = kind,
            state = state,
            label = label,
        )
    }
}
