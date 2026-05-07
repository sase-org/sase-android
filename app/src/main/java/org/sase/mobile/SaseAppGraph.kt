package org.sase.mobile

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.sase.mobile.data.actions.AndroidActionRepositoryFactory
import org.sase.mobile.data.agents.AndroidAgentRepositoryFactory
import org.sase.mobile.data.helpers.AndroidHelperRepositoryFactory
import org.sase.mobile.data.helpers.AndroidUpdateRepositoryFactory
import org.sase.mobile.data.notifications.AndroidNotificationRepositoryFactory
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.notifications.foreground.DataStoreForegroundConnectedModeStore
import org.sase.mobile.data.notifications.foreground.ForegroundConnectedModeController
import org.sase.mobile.data.session.AndroidSessionRepositoryFactory
import org.sase.mobile.service.AndroidForegroundConnectedServiceCommands

class SaseAppGraph private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionController = AndroidSessionRepositoryFactory.create(appContext, scope)
    val updateController = AndroidUpdateRepositoryFactory.create(appContext, scope)
    val agentRepository = AndroidAgentRepositoryFactory.create(appContext, scope)
    val helperRepository = AndroidHelperRepositoryFactory.create(appContext)
    val notificationRepository: NotificationRepository = AndroidNotificationRepositoryFactory.create(
        context = appContext,
        scope = scope,
        onAgentsChanged = { payload -> agentRepository.handleAgentsChanged(payload) },
        onHelpersChanged = { payload -> updateController.handleHelpersChanged(payload) },
    )
    val actionController = AndroidActionRepositoryFactory.create(appContext, notificationRepository)
    val foregroundController = ForegroundConnectedModeController(
        store = DataStoreForegroundConnectedModeStore(appContext),
        commands = AndroidForegroundConnectedServiceCommands(appContext),
        scope = scope,
    )

    companion object {
        @Volatile
        private var instance: SaseAppGraph? = null

        fun get(context: Context): SaseAppGraph {
            return instance ?: synchronized(this) {
                instance ?: SaseAppGraph(context).also { instance = it }
            }
        }
    }
}
