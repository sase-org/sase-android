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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import org.sase.mobile.data.notifications.AndroidNotificationRepositoryFactory
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.session.AndroidSessionRepositoryFactory
import org.sase.mobile.data.session.SessionController
import org.sase.mobile.ui.inbox.InboxScreen
import org.sase.mobile.ui.notification.NotificationDetailScreen
import org.sase.mobile.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaseMobileApp(
    modifier: Modifier = Modifier,
    sessionController: SessionController? = null,
    notificationRepository: NotificationRepository? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = sessionController ?: remember(context.applicationContext, scope) {
        AndroidSessionRepositoryFactory.create(context.applicationContext, scope)
    }
    val notifications = notificationRepository ?: remember(context.applicationContext, scope) {
        AndroidNotificationRepositoryFactory.create(context.applicationContext, scope)
    }
    val navController = rememberNavController()
    val destinations = listOf(SaseDestination.Inbox, SaseDestination.Settings)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val inboxState by notifications.inbox.collectAsState()
    val connectionState by notifications.connection.collectAsState()
    val refreshState by notifications.refresh.collectAsState()

    LaunchedEffect(notifications) {
        notifications.start()
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
                    onBack = { navController.popBackStack() },
                )
            }
            composable(SaseDestination.Settings.route) {
                SettingsScreen(controller = controller)
            }
        }
    }
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

    data object Settings : SaseDestination {
        override val route = "settings"
        override val label = "Settings"
        override val iconResId = R.drawable.ic_settings_24
    }

    data object NotificationDetail {
        const val argumentName = "notificationId"
        const val routePattern = "notification/{$argumentName}"

        fun createRoute(notificationId: String): String = "notification/$notificationId"
    }
}
