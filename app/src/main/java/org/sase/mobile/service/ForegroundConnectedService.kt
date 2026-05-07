package org.sase.mobile.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.sase.mobile.MainActivity
import org.sase.mobile.R
import org.sase.mobile.SaseAppGraph
import org.sase.mobile.data.notifications.NotificationConnectionState
import org.sase.mobile.data.notifications.NotificationRepository
import org.sase.mobile.data.notifications.foreground.DataStoreForegroundConnectedModeStore
import org.sase.mobile.data.notifications.foreground.ForegroundConnectedServiceCommands
import org.sase.mobile.data.notifications.local.AndroidNotificationChannelRegistrar
import org.sase.mobile.data.notifications.local.SaseNotificationChannel
import org.sase.mobile.data.notifications.local.createSaseNotificationChannels
import org.sase.mobile.data.session.SessionStatus

class AndroidForegroundConnectedServiceCommands(
    private val context: Context,
) : ForegroundConnectedServiceCommands {
    private val appContext = context.applicationContext

    override fun start() {
        ContextCompat.startForegroundService(
            appContext,
            ForegroundConnectedService.startIntent(appContext),
        )
    }

    override fun stop() {
        appContext.startService(ForegroundConnectedService.stopIntent(appContext))
    }
}

class ForegroundConnectedService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            serviceScope.launch { stopConnectedMode() }
            return START_NOT_STICKY
        }

        createSaseNotificationChannels(AndroidNotificationChannelRegistrar(this))
        startForegroundCompat(buildNotification("Connecting", null, null))
        startConnectedMode()
        return START_STICKY
    }

    override fun onDestroy() {
        updateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startConnectedMode() {
        if (updateJob?.isActive == true) {
            return
        }
        val graph = SaseAppGraph.get(applicationContext)
        graph.notificationRepository.start(NotificationRepository.OwnerForegroundService)
        updateJob = serviceScope.launch {
            DataStoreForegroundConnectedModeStore(applicationContext).setEnabled(true)
            combine(
                graph.sessionController.state,
                graph.notificationRepository.connection,
                graph.notificationRepository.inbox,
            ) { sessionState, connectionState, inboxState ->
                val hostLabel = when (val status = sessionState.status) {
                    is SessionStatus.Paired -> status.session.hostLabel
                    is SessionStatus.AuthExpired -> status.session.hostLabel
                    is SessionStatus.GatewayUnavailable -> status.session?.hostLabel
                    else -> sessionState.savedSession?.hostLabel
                }
                ForegroundNotificationState(
                    connectionLabel = connectionState.toForegroundLabel(),
                    hostLabel = hostLabel,
                    lastRefreshAt = inboxState.lastFullRefreshAt,
                    stopForAuth = connectionState == NotificationConnectionState.LoggedOut,
                )
            }.collect { state ->
                updateForegroundNotification(state)
                if (state.stopForAuth) {
                    stopConnectedMode()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateForegroundNotification(state: ForegroundNotificationState) {
        NotificationManagerCompat.from(this)
            .notify(
                NotificationId,
                buildNotification(state.connectionLabel, state.hostLabel, state.lastRefreshAt),
            )
    }

    private suspend fun stopConnectedMode() {
        DataStoreForegroundConnectedModeStore(applicationContext).setEnabled(false)
        SaseAppGraph.get(applicationContext)
            .notificationRepository
            .stop(NotificationRepository.OwnerForegroundService)
        updateJob?.cancel()
        updateJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
            )
        } else {
            startForeground(NotificationId, notification)
        }
    }

    private fun buildNotification(
        connectionLabel: String,
        hostLabel: String?,
        lastRefreshAt: String?,
    ): Notification {
        val host = hostLabel.toSafeNotificationText() ?: "paired host"
        val refresh = lastRefreshAt.toSafeNotificationText()?.let { "Last refresh $it" } ?: "Waiting for refresh"
        return NotificationCompat.Builder(this, SaseNotificationChannel.ForegroundConnection.id)
            .setSmallIcon(R.drawable.ic_sase_mobile)
            .setContentTitle("SASE connected mode")
            .setContentText("${connectionLabel.toSafeNotificationText() ?: "Connecting"}: $host")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$host\n$refresh"))
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop", stopActionIntent())
            .build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopActionIntent(): PendingIntent {
        return PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class ForegroundNotificationState(
        val connectionLabel: String,
        val hostLabel: String?,
        val lastRefreshAt: String?,
        val stopForAuth: Boolean,
    )

    companion object {
        private const val ActionStart = "org.sase.mobile.action.START_FOREGROUND_CONNECTED"
        private const val ActionStop = "org.sase.mobile.action.STOP_FOREGROUND_CONNECTED"
        private const val NotificationId = 40_007

        fun startIntent(context: Context): Intent {
            return Intent(context, ForegroundConnectedService::class.java).setAction(ActionStart)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, ForegroundConnectedService::class.java).setAction(ActionStop)
        }
    }
}

private fun NotificationConnectionState.toForegroundLabel(): String {
    return when (this) {
        NotificationConnectionState.Stopped -> "Stopped"
        NotificationConnectionState.Connecting -> "Connecting"
        is NotificationConnectionState.Reconnecting -> "Reconnecting"
        NotificationConnectionState.Connected -> "Live"
        is NotificationConnectionState.Offline -> "Offline"
        NotificationConnectionState.LoggedOut -> "Auth expired"
    }
}

private fun String?.toSafeNotificationText(): String? {
    val compact = this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(80)
        ?.trim()
    return compact?.takeIf { it.isNotEmpty() }
}
