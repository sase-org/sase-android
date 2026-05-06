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
    const val EventResyncRequired = "fixtures/gateway/event_resync_required.json"

    val ErrorFixtures = listOf(
        "fixtures/gateway/error_unauthorized.json",
        "fixtures/gateway/error_not_found.json",
        "fixtures/gateway/error_gone_stale.json",
        "fixtures/gateway/error_bridge_unavailable.json",
    )
}
