package org.sase.mobile.ui

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
import org.sase.mobile.data.actions.AndroidActionRepositoryFactory
import org.sase.mobile.data.actions.NotificationActionController
import org.sase.mobile.data.agents.AgentRepository
import org.sase.mobile.data.agents.AndroidAgentRepositoryFactory
import org.sase.mobile.data.helpers.AndroidHelperRepositoryFactory
import org.sase.mobile.data.helpers.AndroidUpdateRepositoryFactory
import org.sase.mobile.data.helpers.HelperRepository
import org.sase.mobile.data.helpers.UpdateController
import org.sase.mobile.data.notifications.AndroidNotificationRepositoryFactory
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.session.AndroidSessionRepositoryFactory
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = sessionController ?: remember(context.applicationContext, scope) {
        AndroidSessionRepositoryFactory.create(context.applicationContext, scope)
    }
    val updates = updateController ?: remember(context.applicationContext, scope) {
        AndroidUpdateRepositoryFactory.create(context.applicationContext, scope)
    }
    val agents = agentRepository ?: remember(context.applicationContext, scope) {
        AndroidAgentRepositoryFactory.create(context.applicationContext, scope)
    }
    val helpers = helperRepository ?: remember(context.applicationContext) {
        AndroidHelperRepositoryFactory.create(context.applicationContext)
    }
    val notifications = notificationRepository ?: remember(context.applicationContext, scope, agents, updates) {
        AndroidNotificationRepositoryFactory.create(
            context = context.applicationContext,
            scope = scope,
            onAgentsChanged = { payload -> agents.handleAgentsChanged(payload) },
            onHelpersChanged = { payload -> updates.handleHelpersChanged(payload) },
        )
    }
    val actions = actionController ?: remember(context.applicationContext, notifications) {
        AndroidActionRepositoryFactory.create(context.applicationContext, notifications)
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
    val pairedHostKey = notificationHostKey(sessionState)

    LaunchedEffect(notifications, pairedHostKey) {
        if (pairedHostKey == null) {
            notifications.stop()
            agents.stop()
        } else {
            notifications.start()
            scope.launch { agents.refresh() }
        }
    }
    DisposableEffect(notifications) {
        onDispose { notifications.stop() }
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

private fun PairedHostSession.notificationHostKey(): String = "$baseUrl|$deviceId"

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
