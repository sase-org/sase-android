package org.sase.mobile.data.notifications.local

import android.annotation.SuppressLint
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.sase.mobile.MainActivity
import org.sase.mobile.R

class LocalHintNotificationRenderer(
    private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createSaseNotificationChannels(AndroidNotificationChannelRegistrar(context))
    }

    @SuppressLint("MissingPermission")
    fun render(hint: LocalNotificationHint): Boolean {
        val sanitized = hint.sanitized() ?: return false
        if (!canNotify()) {
            return false
        }
        val channel = notificationChannelForCategory(sanitized.category)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_sase_mobile)
            .setContentTitle(sanitized.title)
            .setContentText(sanitized.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(sanitized.body))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(sanitized))
            .build()
        return try {
            notificationManager.notify(sanitized.stableKey.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun canNotify(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && notificationManager.areNotificationsEnabled()
    }

    private fun contentIntent(hint: SanitizedLocalNotificationHint): PendingIntent {
        val targetUri = hint.target.toUri()
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(targetUri)
            .putExtra(SaseDeepLinkIntent.ExtraTarget, hint.target.toUriString())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            hint.stableKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
