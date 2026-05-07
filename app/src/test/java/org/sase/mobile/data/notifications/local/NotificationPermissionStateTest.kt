package org.sase.mobile.data.notifications.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationPermissionStateTest {
    @Test
    fun apiBeforeThirtyThreeDoesNotRequireRuntimePermission() {
        assertThat(
            resolveNotificationPermissionState(
                apiLevel = 32,
                permissionGranted = false,
                shouldShowRationale = false,
                hasAskedBefore = false,
            ),
        ).isEqualTo(NotificationPermissionState.NotRequired)
    }

    @Test
    fun grantedApiThirtyThreeReportsAllowed() {
        assertThat(
            resolveNotificationPermissionState(
                apiLevel = 33,
                permissionGranted = true,
                shouldShowRationale = false,
                hasAskedBefore = true,
            ),
        ).isEqualTo(NotificationPermissionState.Allowed)
    }

    @Test
    fun firstDeniedStateCanStillAsk() {
        assertThat(
            resolveNotificationPermissionState(
                apiLevel = 35,
                permissionGranted = false,
                shouldShowRationale = false,
                hasAskedBefore = false,
            ),
        ).isEqualTo(NotificationPermissionState.DeniedCanAsk)
    }

    @Test
    fun deniedAfterPromptWithoutRationaleNeedsSettings() {
        assertThat(
            resolveNotificationPermissionState(
                apiLevel = 35,
                permissionGranted = false,
                shouldShowRationale = false,
                hasAskedBefore = true,
            ),
        ).isEqualTo(NotificationPermissionState.DeniedNeedsSettings)
    }
}
