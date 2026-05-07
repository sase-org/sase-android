package org.sase.mobile.data.notifications.local

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sase.mobile.BuildConfig

enum class NotificationPermissionState {
    NotRequired,
    Allowed,
    DeniedCanAsk,
    DeniedNeedsSettings,
}

fun resolveNotificationPermissionState(
    apiLevel: Int,
    permissionGranted: Boolean,
    shouldShowRationale: Boolean,
    hasAskedBefore: Boolean,
): NotificationPermissionState {
    if (apiLevel < Build.VERSION_CODES.TIRAMISU) {
        return NotificationPermissionState.NotRequired
    }
    if (permissionGranted) {
        return NotificationPermissionState.Allowed
    }
    return if (!shouldShowRationale && hasAskedBefore) {
        NotificationPermissionState.DeniedNeedsSettings
    } else {
        NotificationPermissionState.DeniedCanAsk
    }
}

class AndroidNotificationPermissionController(
    private val context: Context,
) {
    private val preferences = context.getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(currentState(activity = null))
    val state: StateFlow<NotificationPermissionState> = mutableState.asStateFlow()

    fun refresh(activity: Activity?) {
        mutableState.value = currentState(activity)
    }

    fun markRequested(activity: Activity?) {
        preferences.edit().putBoolean(AskedBeforeKey, true).apply()
        refresh(activity)
    }

    fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun currentState(activity: Activity?): NotificationPermissionState {
        val granted = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        val shouldShowRationale = activity?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            } else {
                false
            }
        } ?: false
        return resolveNotificationPermissionState(
            apiLevel = Build.VERSION.SDK_INT,
            permissionGranted = granted,
            shouldShowRationale = shouldShowRationale,
            hasAskedBefore = preferences.getBoolean(AskedBeforeKey, false),
        )
    }

    private companion object {
        const val AskedBeforeKey = "asked_before"
    }
}
