package org.sase.mobile.data.api

internal fun readResource(path: String): String {
    val resource = Thread.currentThread().contextClassLoader?.getResource(path)
        ?: error("Missing test resource: $path")
    return resource.readText()
}

internal object GatewayFixturePaths {
    const val Contract = "contracts/mobile_api_v1.json"
    const val HealthSuccess = "fixtures/gateway/health_success.json"
    const val SessionSuccess = "fixtures/gateway/session_success.json"
    const val PairStartSuccess = "fixtures/gateway/pair_start_success.json"
    const val PairFinishSuccess = "fixtures/gateway/pair_finish_success.json"
    const val NotificationsEmpty = "fixtures/gateway/notifications_empty.json"
    const val NotificationsMixed = "fixtures/gateway/notifications_mixed.json"
    const val NotificationDetailPlan = "fixtures/gateway/notification_detail_plan.json"
    const val EventHeartbeat = "fixtures/gateway/event_heartbeat.json"
    const val EventNotificationsChanged = "fixtures/gateway/event_notifications_changed.json"
    const val EventAgentsChanged = "fixtures/gateway/event_agents_changed.json"
    const val EventHelpersChanged = "fixtures/gateway/event_helpers_changed.json"
    const val EventResyncRequired = "fixtures/gateway/event_resync_required.json"
    const val PushHintNotification = "fixtures/gateway/push_hint_notification.json"
    const val ActionSuccess = "fixtures/gateway/action_success.json"
    const val AgentsList = "fixtures/gateway/agents_list.json"
    const val AgentResumeOptions = "fixtures/gateway/agent_resume_options.json"
    const val AgentLaunchResult = "fixtures/gateway/agent_launch_result.json"
    const val AgentImageLaunchResult = "fixtures/gateway/agent_image_launch_result.json"
    const val AgentKillResult = "fixtures/gateway/agent_kill_result.json"
    const val AgentRetryResult = "fixtures/gateway/agent_retry_result.json"
    const val ChangespecTags = "fixtures/gateway/changespec_tags.json"
    const val XpromptCatalog = "fixtures/gateway/xprompt_catalog.json"
    const val BeadsList = "fixtures/gateway/beads_list.json"
    const val BeadShow = "fixtures/gateway/bead_show.json"
    const val UpdateStartRunning = "fixtures/gateway/update_start_running.json"
    const val UpdateStatusSuccess = "fixtures/gateway/update_status_success.json"
    const val UpdateStatusFailure = "fixtures/gateway/update_status_failure.json"
    const val ErrorGoneStale = "fixtures/gateway/error_gone_stale.json"
    const val ErrorConflictAlreadyHandled = "fixtures/gateway/error_conflict_already_handled.json"
    const val ErrorAmbiguousPrefix = "fixtures/gateway/error_ambiguous_prefix.json"
    const val ErrorUnsupportedAction = "fixtures/gateway/error_unsupported_action.json"
    const val ErrorLaunchFailed = "fixtures/gateway/error_launch_failed.json"
    const val ErrorBridgeUnavailable = "fixtures/gateway/error_bridge_unavailable.json"

    val ErrorFixtures = listOf(
        "fixtures/gateway/error_unauthorized.json",
        "fixtures/gateway/error_not_found.json",
        ErrorGoneStale,
        ErrorBridgeUnavailable,
        ErrorConflictAlreadyHandled,
        ErrorAmbiguousPrefix,
        ErrorUnsupportedAction,
        ErrorLaunchFailed,
    )
}
