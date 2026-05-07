package org.sase.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.sase.mobile.data.notifications.local.SaseDeepLinkIntent
import org.sase.mobile.data.notifications.local.SaseDeepLinkTarget
import org.sase.mobile.ui.SaseMobileApp
import org.sase.mobile.ui.theme.SaseMobileTheme

class MainActivity : ComponentActivity() {
    private var pendingDeepLinkTarget by mutableStateOf<SaseDeepLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkTarget = SaseDeepLinkIntent.from(intent)
        setContent {
            SaseMobileTheme {
                SaseMobileApp(
                    pendingDeepLinkTarget = pendingDeepLinkTarget,
                    onDeepLinkConsumed = { pendingDeepLinkTarget = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkTarget = SaseDeepLinkIntent.from(intent)
    }
}
