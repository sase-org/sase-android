package org.sase.mobile.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.sase.mobile.R
import org.sase.mobile.SaseAppGraph
import org.sase.mobile.data.actions.NotificationActionController
import org.sase.mobile.data.agents.AgentRepository
import org.sase.mobile.data.helpers.HelperRepository
import org.sase.mobile.data.helpers.UpdateController
import org.sase.mobile.data.notifications.NotificationConnectionState
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.notifications.RefreshReason
import org.sase.mobile.data.notifications.foreground.ForegroundConnectedModeController
import org.sase.mobile.data.notifications.foreground.ForegroundConnectedModeUiState
import org.sase.mobile.data.notifications.local.AndroidNotificationPermissionController
import org.sase.mobile.data.notifications.local.LocalHintCategory
import org.sase.mobile.data.notifications.local.LocalHintNotificationRenderer
import org.sase.mobile.data.notifications.local.LocalNotificationHint
import org.sase.mobile.data.notifications.local.NotificationPermissionState
import org.sase.mobile.data.notifications.local.SaseDeepLinkTarget
import org.sase.mobile.data.notifications.push.PushDeliveryState
import org.sase.mobile.data.notifications.push.PushRegistrationManager
import org.sase.mobile.data.notifications.push.PushRegistrationStatus
import org.sase.mobile.data.session.PairedHostSession
import org.sase.mobile.data.session.SessionController
import org.sase.mobile.data.session.SessionStatus
import org.sase.mobile.data.session.SessionUiState
import org.sase.mobile.ui.agents.AgentsScreen
import org.sase.mobile.ui.helpers.HelpersScreen
import org.sase.mobile.ui.inbox.InboxScreen
import org.sase.mobile.ui.launch.LaunchScreen
import org.sase.mobile.ui.notification.NotificationDetailScreen
import org.sase.mobile.ui.settings.SettingsScreen
import org.sase.mobile.ui.settings.PushDeliveryUiState
import org.sase.mobile.ui.update.UpdateScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaseMobileApp(
    modifier: Modifier = Modifier,
    sessionController: SessionController? = null,
    notificationRepository: NotificationRepository? = null,
    actionController: NotificationActionController? = null,
    agentRepository: AgentRepository? = null,
    helperRepository: HelperRepository? = null,
    updateController: UpdateController? = null,
    foregroundController: ForegroundConnectedModeController? = null,
    pushRegistrationManager: PushRegistrationManager? = null,
    pendingDeepLinkTarget: SaseDeepLinkTarget? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val appGraph = remember(context.applicationContext) {
        SaseAppGraph.get(context.applicationContext)
    }
    val controller = sessionController ?: remember(appGraph) {
        appGraph.sessionController
    }
    val updates = updateController ?: remember(appGraph) {
        appGraph.updateController
    }
    val agents = agentRepository ?: remember(appGraph) {
        appGraph.agentRepository
    }
    val helpers = helperRepository ?: remember(appGraph) {
        appGraph.helperRepository
    }
    val notifications = notificationRepository ?: remember(appGraph) {
        appGraph.notificationRepository
    }
    val actions = actionController ?: remember(appGraph) {
        appGraph.actionController
    }
    val foreground = foregroundController ?: remember(appGraph) {
        appGraph.foregroundController
    }
    val pushRegistration = pushRegistrationManager ?: remember(appGraph) {
        appGraph.pushRegistrationManager
    }
    val permissionController = remember(context.applicationContext) {
        AndroidNotificationPermissionController(context.applicationContext)
    }
    val localHintRenderer = remember(context.applicationContext) {
        LocalHintNotificationRenderer(context.applicationContext)
    }
    val notificationPermissionState by permissionController.state.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionController.markRequested(activity)
    }
    val navController = rememberNavController()
    val destinations = listOf(
        SaseDestination.Inbox,
        SaseDestination.Launch,
        SaseDestination.Agents,
        SaseDestination.Settings,
    )
    var launchPrefillPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val inboxState by notifications.inbox.collectAsState()
    val connectionState by notifications.connection.collectAsState()
    val refreshState by notifications.refresh.collectAsState()
    val helperEventVersion by notifications.helperEvents.collectAsState()
    val agentsState by agents.state.collectAsState()
    val sessionState by controller.state.collectAsState()
    val foregroundEnabled by foreground.enabled.collectAsState()
    val pushDeliveryState by pushRegistration.state.collectAsState()
    val pairedHostKey = notificationHostKey(sessionState)

    LaunchedEffect(activity, permissionController) {
        permissionController.refresh(activity)
    }
    LaunchedEffect(notifications, pairedHostKey, foregroundEnabled) {
        if (pairedHostKey == null) {
            notifications.stop(NotificationRepository.OwnerUi)
            agents.stop()
            pushRegistration.markUnpaired()
            if (foregroundEnabled) {
                foreground.stopAfterHostUnavailable()
            }
        } else {
            notifications.start(NotificationRepository.OwnerUi)
            scope.launch { agents.refresh() }
        }
    }
    LaunchedEffect(pushRegistration, pairedHostKey) {
        if (pairedHostKey != null) {
            pushRegistration.refreshTokenRegistration()
        }
    }
    DisposableEffect(notifications) {
        onDispose { notifications.stop(NotificationRepository.OwnerUi) }
    }
    LaunchedEffect(pendingDeepLinkTarget) {
        val target = pendingDeepLinkTarget ?: return@LaunchedEffect
        notifications.fullRefresh(RefreshReason.PushHint)
        navController.navigate(target.route()) {
            launchSingleTop = true
        }
        onDeepLinkConsumed()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("SASE Mobile") },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = ImageVector.vectorResource(destination.iconResId),
                                contentDescription = null,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SaseDestination.Inbox.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(SaseDestination.Inbox.route) {
                InboxScreen(
                    state = inboxState,
                    connectionState = connectionState,
                    refreshState = refreshState,
                    onRefresh = {
                        scope.launch { notifications.fullRefresh() }
                    },
                    onOpenNotification = { notificationId ->
                        navController.navigate(SaseDestination.NotificationDetail.createRoute(notificationId))
                    },
                    onOpenSettings = {
                        navController.navigate(SaseDestination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(SaseDestination.NotificationDetail.routePattern) { detailBackStackEntry ->
                val notificationId = detailBackStackEntry.arguments?.getString(
                    SaseDestination.NotificationDetail.argumentName,
                ).orEmpty()
                NotificationDetailScreen(
                    notificationId = notificationId,
                    repository = notifications,
                    actionController = actions,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = {
                        navController.navigate(SaseDestination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(SaseDestination.Agents.route) {
                AgentsScreen(
                    state = agentsState,
                    onRefresh = {
                        scope.launch { agents.refresh() }
                    },
                    onKill = { name ->
                        scope.launch { agents.killAgent(name) }
                    },
                    onRetry = { name ->
                        scope.launch { agents.retryAgent(name) }
                    },
                    onOpenLaunchPrompt = { prompt ->
                        launchPrefillPrompt = prompt
                        navController.navigate(SaseDestination.Launch.route) {
                            launchSingleTop = true
                        }
                    },
                    onClearActionResult = { agents.clearActionResult() },
                    onOpenSettings = {
                        navController.navigate(SaseDestination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(SaseDestination.Launch.route) {
                LaunchScreen(
                    state = agentsState,
                    onLaunch = { request -> agents.launchAgent(request) },
                    onLaunchImage = { request -> agents.launchImageAgent(request) },
                    helperRepository = helpers,
                    helperEventVersion = helperEventVersion,
                    prefillPrompt = launchPrefillPrompt,
                    onPrefillConsumed = { launchPrefillPrompt = null },
                    onOpenAgents = {
                        navController.navigate(SaseDestination.Agents.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(SaseDestination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(SaseDestination.Settings.route) {
                SettingsScreen(
                    controller = controller,
                    notificationPermissionState = notificationPermissionState,
                    pushDeliveryState = pushDeliveryUiState(
                        state = pushDeliveryState,
                        permissionMissing = notificationPermissionState.deniesNotifications(),
                        foregroundModeRunning = foregroundEnabled,
                    ),
                    foregroundConnectedModeState = foregroundModeUiState(
                        enabled = foregroundEnabled,
                        canStart = pairedHostKey != null,
                        sessionState = sessionState,
                        connectionState = connectionState,
                        lastRefreshAt = inboxState.lastFullRefreshAt,
                    ),
                    onStartForegroundConnectedMode = {
                        foreground.startUntilStopped()
                    },
                    onStopForegroundConnectedMode = {
                        foreground.stop()
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            permissionController.refresh(activity)
                        }
                    },
                    onOpenNotificationSettings = {
                        permissionController.openSystemSettings()
                    },
                    onRenderTestNotification = {
                        localHintRenderer.render(testLocalHint())
                    },
                    onOpenUpdate = {
                        navController.navigate(SaseDestination.Update.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenHelpers = {
                        navController.navigate(SaseDestination.Helpers.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(SaseDestination.Update.route) {
                UpdateScreen(
                    controller = updates,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(SaseDestination.Helpers.route) {
                HelpersScreen(
                    repository = helpers,
                    helperEventVersion = helperEventVersion,
                )
            }
        }
    }
}

private fun notificationHostKey(state: SessionUiState): String? {
    val session = when (val status = state.status) {
        is SessionStatus.Paired -> status.session
        is SessionStatus.AuthExpired -> status.session
        is SessionStatus.GatewayUnavailable -> status.session
        SessionStatus.Unpaired -> null
        else -> state.savedSession
    }
    return session?.notificationHostKey()
}

private fun pushDeliveryUiState(
    state: PushDeliveryState,
    permissionMissing: Boolean,
    foregroundModeRunning: Boolean,
): PushDeliveryUiState {
    return PushDeliveryUiState(
        statusLabel = when (state.registrationStatus) {
            PushRegistrationStatus.Unpaired -> "Pair a host before enabling push hints."
            PushRegistrationStatus.NotRegistered -> "Push token has not been registered yet."
            PushRegistrationStatus.Registering -> "Registering FCM token with the paired gateway."
            PushRegistrationStatus.Registered -> "FCM token is registered with the paired gateway."
            PushRegistrationStatus.AuthExpired -> "Authentication expired. Pair or check the host again."
            PushRegistrationStatus.Failed -> "Push registration failed."
        },
        tokenLabel = state.registeredTokenSuffix?.let { "Token: ...$it" },
        permissionMissing = permissionMissing,
        foregroundModeRunning = foregroundModeRunning,
        lastHintReceivedAt = state.lastHintReceivedAt,
        lastHintSummary = state.lastHintSummary,
        registrationFailure = state.registrationFailure,
    )
}

private fun NotificationPermissionState.deniesNotifications(): Boolean {
    return this == NotificationPermissionState.DeniedCanAsk ||
        this == NotificationPermissionState.DeniedNeedsSettings
}

private fun foregroundModeUiState(
    enabled: Boolean,
    canStart: Boolean,
    sessionState: SessionUiState,
    connectionState: NotificationConnectionState,
    lastRefreshAt: String?,
): ForegroundConnectedModeUiState {
    val hostLabel = when (val status = sessionState.status) {
        is SessionStatus.Paired -> status.session.hostLabel
        is SessionStatus.AuthExpired -> status.session.hostLabel
        is SessionStatus.GatewayUnavailable -> status.session?.hostLabel
        else -> sessionState.savedSession?.hostLabel
    }
    return ForegroundConnectedModeUiState(
        enabled = enabled,
        canStart = canStart,
        hostLabel = hostLabel,
        connectionLabel = connectionStateLabel(connectionState),
        lastRefreshAt = lastRefreshAt,
    )
}

private fun connectionStateLabel(
    connectionState: NotificationConnectionState,
): String {
    return when (connectionState) {
        NotificationConnectionState.Stopped -> "Stopped"
        NotificationConnectionState.Connecting -> "Connecting"
        is NotificationConnectionState.Reconnecting -> "Reconnecting"
        NotificationConnectionState.Connected -> "Live"
        is NotificationConnectionState.Offline -> "Offline"
        NotificationConnectionState.LoggedOut -> "Auth expired"
    }
}

private fun PairedHostSession.notificationHostKey(): String = "$baseUrl|$deviceId"

private fun SaseDeepLinkTarget.route(): String {
    return when (this) {
        SaseDeepLinkTarget.Inbox -> SaseDestination.Inbox.route
        is SaseDeepLinkTarget.NotificationDetail -> SaseDestination.NotificationDetail.createRoute(notificationId)
        SaseDeepLinkTarget.Agents -> SaseDestination.Agents.route
        SaseDeepLinkTarget.Helpers -> SaseDestination.Helpers.route
        SaseDeepLinkTarget.Update -> SaseDestination.Update.route
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun testLocalHint(): LocalNotificationHint {
    return LocalNotificationHint(
        notificationId = "local-test-hint",
        eventId = null,
        category = LocalHintCategory.Action,
        title = "SASE action hint",
        body = "Open SASE Mobile to refresh current host state.",
        createdAt = "",
        target = SaseDeepLinkTarget.Inbox,
    )
}

sealed interface SaseDestination {
    val route: String
    val label: String
    val iconResId: Int

    data object Inbox : SaseDestination {
        override val route = "inbox"
        override val label = "Inbox"
        override val iconResId = R.drawable.ic_inbox_24
    }

    data object Agents : SaseDestination {
        override val route = "agents"
        override val label = "Agents"
        override val iconResId = R.drawable.ic_terminal_24
    }

    data object Launch : SaseDestination {
        override val route = "launch"
        override val label = "Launch"
        override val iconResId = R.drawable.ic_play_arrow_24
    }

    data object Settings : SaseDestination {
        override val route = "settings"
        override val label = "Settings"
        override val iconResId = R.drawable.ic_settings_24
    }

    data object Update : SaseDestination {
        override val route = "update"
        override val label = "Update"
        override val iconResId = R.drawable.ic_settings_24
    }

    data object Helpers : SaseDestination {
        override val route = "helpers"
        override val label = "Helpers"
        override val iconResId = R.drawable.ic_helpers_24
    }

    data object NotificationDetail {
        const val argumentName = "notificationId"
        const val routePattern = "notification/{$argumentName}"

        fun createRoute(notificationId: String): String = "notification/$notificationId"
    }
}
